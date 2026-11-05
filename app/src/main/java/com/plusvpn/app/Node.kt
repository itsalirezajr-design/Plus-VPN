package com.plusvpn.app

import android.util.Base64
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

data class Node(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val rawUri: String,
    val latencyMs: Long? = null,
    val status: String = "آزمایش‌نشده"
)

object NodeParser {
    fun parse(input: String): Node? {
        val raw = input.trim()
        return if (raw.startsWith("vmess://", true)) parseVmess(raw) else parseVless(raw)
    }

    fun parseMany(input: String): List<Node> = input.lineSequence()
        .flatMap { it.split(Regex("\\s+")).asSequence() }
        .map { it.trim().trim(',', ';') }
        .filter { it.startsWith("vless://", true) || it.startsWith("vmess://", true) }
        .mapNotNull(::parse)
        .distinctBy { it.rawUri }
        .toList()

    private fun parseVless(raw: String): Node? {
        val uri = runCatching { URI(raw) }.getOrNull() ?: return null
        if (!uri.scheme.equals("vless", true)) return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port.takeIf { it in 1..65535 } ?: return null
        val name = uri.rawFragment?.let {
            runCatching { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }.getOrNull()
        }?.takeIf { it.isNotBlank() } ?: "VLESS • " + host
        return Node(UUID.randomUUID().toString(), name, "VLESS", host, port, raw)
    }

    private fun parseVmess(raw: String): Node? {
        val encoded = raw.removePrefix("vmess://").trim()
        val decoded = runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT or Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val json = runCatching { JSONObject(decoded) }.getOrNull() ?: return null
        val host = json.optString("add").takeIf { it.isNotBlank() } ?: return null
        val port = json.optString("port").toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val name = json.optString("ps").takeIf { it.isNotBlank() } ?: "VMess • " + host
        return Node(UUID.randomUUID().toString(), name, "VMESS", host, port, raw)
    }
}
