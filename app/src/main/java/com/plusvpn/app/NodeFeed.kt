package com.plusvpn.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.URL

object NodeFeed {
    private val feeds = listOf(
        "https://raw.githubusercontent.com/Au1rxx/free-vpn-subscriptions/main/output/protocol/vless/v2ray-base64-0001.txt",
        "https://raw.githubusercontent.com/Au1rxx/free-vpn-subscriptions/main/output/protocol/vless/v2ray-base64-0002.txt",
        "https://raw.githubusercontent.com/Au1rxx/free-vpn-subscriptions/main/output/protocol/vless/v2ray-base64-0003.txt"
    )

    suspend fun load(): List<Node> = coroutineScope {
        feeds.map { feed ->
            async(Dispatchers.IO) { runCatching { fetch(feed) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten().distinctBy { it.rawUri }.take(90)
    }

    private fun fetch(address: String): List<Node> {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 15000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PlusVPN-Android/0.2")
        }
        return try {
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
            val decoded = if (payload.startsWith("vless://", true)) payload else
                runCatching { String(Base64.decode(payload, Base64.DEFAULT or Base64.URL_SAFE), Charsets.UTF_8) }
                    .getOrDefault("")
            NodeParser.parseMany(decoded)
        } finally {
            connection.disconnect()
        }
    }
}
