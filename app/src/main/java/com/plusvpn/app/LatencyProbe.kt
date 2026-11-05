package com.plusvpn.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object LatencyProbe {
    // TCP connect time only; this does not validate a proxy handshake or VPN tunnel.
    suspend fun measure(node: Node, timeoutMs: Int = 4500): Node =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            val result = runCatching {
                Socket().use { it.connect(InetSocketAddress(node.host, node.port), timeoutMs) }
                ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            }
            if (result.isSuccess) node.copy(
                latencyMs = result.getOrThrow(), status = "پورت در دسترس"
            ) else node.copy(latencyMs = null, status = "عدم دسترسی")
        }
}
