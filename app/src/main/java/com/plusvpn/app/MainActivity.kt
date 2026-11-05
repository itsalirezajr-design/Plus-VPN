package com.plusvpn.app

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlusVpnScreen() }
    }
}

@Composable
private fun PlusVpnScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nodes by remember { mutableStateOf(listOf<Node>()) }
    var message by remember { mutableStateOf("برای اتصال، دکمهٔ اتصال خودکار را بزن.") }
    var busy by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf("") }
    val navy = Color(0xFF10182B)

    suspend fun disconnect() {
        context.startService(Intent(context, XrayVpnService::class.java).setAction(XrayVpnService.ACTION_DISCONNECT))
        connected = false
        selectedName = ""
        message = "اتصال قطع شد."
    }

    suspend fun autoConnect() {
        busy = true
        connected = false
        message = "در حال دریافت فهرست سرورهای عمومی…"
        val candidates = withContext(Dispatchers.IO) { NodeFeed.load() }
        nodes = candidates
        if (candidates.isEmpty()) {
            message = "فهرست سرورها دریافت نشد؛ اتصال اینترنت را بررسی و دوباره تلاش کن."
            busy = false
            return
        }
        message = "${candidates.size} سرور پیدا شد؛ در حال انتخاب سرور سالم…"
        var success: Node? = null
        val maxAttempts = minOf(candidates.size, 12)
        for (index in 0 until maxAttempts) {
            val node = candidates[index]
            message = "آزمایش ${index + 1} از $maxAttempts: ${node.name}"
            val start = Intent(context, XrayVpnService::class.java)
                .setAction(XrayVpnService.ACTION_CONNECT)
                .putExtra(XrayVpnService.EXTRA_URI, node.rawUri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(start)
            else context.startService(start)
            delay(5500)
            val coreStarted = context.getSharedPreferences(XrayVpnService.PREFS, Activity.MODE_PRIVATE)
                .getBoolean(XrayVpnService.KEY_CORE_STARTED, false)
            val online = coreStarted && withContext(Dispatchers.IO) { verifyInternetThroughTunnel() }
            if (online) {
                success = node
                break
            }
            context.startService(Intent(context, XrayVpnService::class.java).setAction(XrayVpnService.ACTION_DISCONNECT))
            delay(700)
        }
        if (success != null) {
            connected = true
            selectedName = success!!.name
            message = "اتصال برقرار شد • ${success!!.name}"
        } else {
            val error = context.getSharedPreferences(XrayVpnService.PREFS, Activity.MODE_PRIVATE)
                .getString(XrayVpnService.KEY_ERROR, null)
            message = error?.let { "اتصال برقرار نشد: $it" }
                ?: "هیچ‌کدام از سرورها از این شبکه اینترنت عبور ندادند. بعداً دوباره امتحان کن."
            context.startService(Intent(context, XrayVpnService::class.java).setAction(XrayVpnService.ACTION_DISCONNECT))
        }
        busy = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) scope.launch { autoConnect() }
        else message = "اجازهٔ ساخت اتصال VPN داده نشد."
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF7897FF), background = navy)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor = navy) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("＋  PLUS VPN", style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("اتصال خودکار به سرورهای در دسترس", color = Color(0xFFADBBD7))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2C4B)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (connected) "متصل" else "اتصال خودکار",
                                color = if (connected) Color(0xFF80E0B0) else Color.White,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Text(if (connected) selectedName else
                                "برنامه سرورها را می‌گیرد، یکی‌یکی آزمایش می‌کند و فقط پس از عبور واقعی اینترنت، اتصال را موفق اعلام می‌کند.",
                                color = Color(0xFFCBD6EB))
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = {
                                if (connected) scope.launch { disconnect() }
                                else {
                                    val permission = VpnService.prepare(context)
                                    if (permission == null) scope.launch { autoConnect() }
                                    else permissionLauncher.launch(permission)
                                }
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                Text(when {
                                    busy -> "در حال پیدا کردن سرور سالم…"
                                    connected -> "قطع اتصال"
                                    else -> "اتصال خودکار"
                                })
                            }
                        }
                    }
                    Text(message, color = Color(0xFFB9C7E2))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("فهرست سرورها", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${nodes.size} سرور", color = Color(0xFF9EAFCE))
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nodes, key = { it.id }) { node ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1A2540))) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(node.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("VLESS • ${node.host}:${node.port}", color = Color(0xFFB9C7E2))
                                }
                            }
                        }
                    }
                    Text("سرورها متعلق به اشخاص ثالث هستند؛ Plus VPN آن‌ها را می‌یابد و آزمایش می‌کند، اما مالک یا تضمین‌کنندهٔ آن‌ها نیست.",
                        color = Color(0xFF8798B8), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun verifyInternetThroughTunnel(): Boolean {
    val checks = listOf("https://www.google.com/generate_204", "https://cp.cloudflare.com/generate_204")
    for (address in checks) {
        val connection = runCatching {
            (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                instanceFollowRedirects = false
                useCaches = false
            }
        }.getOrNull() ?: continue
        try {
            val code = connection.responseCode
            if (code == 204 || code in 200..299) return true
        } catch (_: Exception) {
            // Try the next independent HTTPS endpoint.
        } finally {
            connection.disconnect()
        }
    }
    return false
}
