package com.plusvpn.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import libXray.DialerController
import libXray.LibXray
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class XrayVpnService : VpnService() {
    private val worker = Executors.newSingleThreadExecutor()
    private var tun: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Plus VPN", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> worker.execute {
                stopCore()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_CONNECT -> {
                val uri = intent.getStringExtra(EXTRA_URI)
                if (uri.isNullOrBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, notification("در حال اتصال…"))
                worker.execute { startCore(uri) }
            }
        }
        return START_NOT_STICKY
    }

    private fun startCore(shareUri: String) {
        runCatching {
            stopCore()
            val vpn = Builder()
                .setSession("Plus VPN")
                .setMtu(1500)
                .addAddress("10.77.0.2", 30)
                .addRoute("0.0.0.0", 0)
                .addAddress("fd00:77::2", 126)
                .addRoute("::", 0)
                .addDnsServer("1.1.1.1")
                .setBlocking(true)
                .establish() ?: error("Android نتوانست رابط VPN را بسازد")
            tun = vpn

            val controller = object : DialerController {
                override fun protectFd(fd: Long): Boolean = protect(fd.toInt())
            }
            LibXray.registerDialerController(controller)
            LibXray.registerListenerController(controller)
            LibXray.setDNS(controller, "1.1.1.1:53")

            val conversionRequest = JSONObject()
                .put("apiVersion", 1)
                .put("method", "convertShareLinksToXrayJson")
                .put("payload", JSONObject().put("text", shareUri))
            val conversion = JSONObject(LibXray.invoke(conversionRequest.toString()))
            check(conversion.optBoolean("success")) {
                conversion.optString("error", "تبدیل کانفیگ سرور ناموفق بود")
            }
            val generated = conversion.optString("data")
            check(generated.isNotBlank()) { "هسته Xray کانفیگ خالی برگرداند" }
            val config = JSONObject(generated)

            config.optJSONArray("outbounds")?.let { outbounds ->
                for (i in 0 until outbounds.length()) {
                    outbounds.optJSONObject(i)?.remove("sendThrough")
                    val outbound = outbounds.optJSONObject(i) ?: continue
                    val stream = outbound.optJSONObject("streamSettings") ?: continue
                    if (!stream.optString("security").equals("reality", true)) continue
                    val reality = stream.optJSONObject("realitySettings")
                        ?: JSONObject().also { stream.put("realitySettings", it) }
                    listOf("target", "dest", "type", "xver", "serverNames", "privateKey",
                        "minClientVer", "maxClientVer", "maxTimeDiff", "shortIds").forEach(reality::remove)
                    val uri = Uri.parse(shareUri)
                    uri.getQueryParameter("fp")?.takeIf { it.isNotBlank() }?.let { reality.put("fingerprint", it) }
                    uri.getQueryParameter("sni")?.takeIf { it.isNotBlank() }?.let { reality.put("serverName", it) }
                    uri.getQueryParameter("pbk")?.takeIf { it.isNotBlank() }?.let {
                        reality.put("password", it)
                        reality.put("publicKey", it)
                    }
                    uri.getQueryParameter("sid")?.let { reality.put("shortId", it) }
                    uri.getQueryParameter("spx")?.takeIf { it.isNotBlank() }?.let { reality.put("spiderX", it) }
                }
            }

            val env = config.optJSONObject("env") ?: JSONObject()
            env.put("xray.tun.fd", vpn.fd.toString())
            config.put("env", env)
            config.put("inbounds", JSONArray().put(
                JSONObject().put("tag", "plusvpn-tun").put("protocol", "tun")
                    .put("settings", JSONObject().put("name", "plusvpn0").put("mtu", 1500))
            ))
            config.put("log", JSONObject().put("loglevel", "warning"))

            val runRequest = JSONObject().put("apiVersion", 1).put("method", "runXrayFromJson")
                .put("payload", JSONObject().put("configJSON", config.toString()))
            val runResponse = JSONObject(LibXray.invoke(runRequest.toString()))
            check(runResponse.optBoolean("success")) {
                runResponse.optString("error", "راه‌اندازی هسته Xray ناموفق بود")
            }

            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_CORE_STARTED, true).remove(KEY_ERROR).apply()
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, notification("تونل فعال است؛ در حال بررسی اینترنت"))
        }.onFailure { error ->
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(KEY_CORE_STARTED, false)
                .putString(KEY_ERROR, error.message ?: "خطای ناشناخته در هسته VPN").apply()
            stopCore()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopCore() {
        runCatching {
            LibXray.invoke(JSONObject().put("apiVersion", 1).put("method", "stopXray")
                .put("payload", JSONObject()).toString())
        }
        runCatching { LibXray.resetDNS() }
        runCatching { tun?.close() }
        tun = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CORE_STARTED, false).apply()
    }

    private fun notification(message: String): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, XrayVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Plus VPN").setContentText(message)
            .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "قطع اتصال", stopIntent).build()
    }

    override fun onRevoke() {
        worker.execute { stopCore(); stopSelf() }
        super.onRevoke()
    }

    override fun onDestroy() {
        runCatching { stopCore() }
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CONNECT = "com.plusvpn.app.CONNECT"
        const val ACTION_DISCONNECT = "com.plusvpn.app.DISCONNECT"
        const val EXTRA_URI = "share_uri"
        const val PREFS = "plusvpn_runtime"
        const val KEY_CORE_STARTED = "core_started"
        const val KEY_ERROR = "core_error"
        private const val CHANNEL_ID = "plusvpn_vpn"
        private const val NOTIFICATION_ID = 501
    }
}
