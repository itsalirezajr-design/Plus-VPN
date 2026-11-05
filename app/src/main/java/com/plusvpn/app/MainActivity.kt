package com.plusvpn.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlusVpnScreen() }
    }
}

@Composable
private fun PlusVpnScreen() {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("برای آزمایش، لینک VLESS یا VMess را وارد کن.") }
    var busy by remember { mutableStateOf(false) }
    var nodes by remember { mutableStateOf(listOf<Node>()) }
    val navy = Color(0xFF10182B)
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF668BFF), background = navy)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Scaffold(containerColor = navy) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("＋  PLUS VPN", style = MaterialTheme.typography.headlineMedium,
                        color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("اتصال ساده، بدون هزینه", color = Color(0xFFADBBD7))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2C4B)),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("اتصال خودکار", color = Color.White,
                                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("هسته تونل هنوز اضافه نشده؛ اتصال VPN واقعی فعال نیست.",
                                color = Color(0xFFFFD18A))
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                                Text("اتصال فعلاً در دسترس نیست")
                            }
                        }
                    }
                    Text("آزمایش سرورها", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = input, onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("لینک VLESS یا VMess") }, minLines = 2
                    )
                    Button(
                        onClick = {
                            val parsed = NodeParser.parseMany(input)
                            if (parsed.isEmpty()) message = "لینک معتبر VLESS یا VMess پیدا نشد."
                            else {
                                nodes = (nodes + parsed).distinctBy { it.rawUri }
                                input = ""
                                message = parsed.size.toString() + " سرور اضافه شد."
                            }
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("افزودن سرور") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("سرورها (" + nodes.size + ")", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TextButton(enabled = nodes.isNotEmpty() && !busy, onClick = {
                            scope.launch {
                                busy = true
                                message = "در حال بررسی پورت‌ها…"
                                nodes = nodes.map { LatencyProbe.measure(it) }
                                    .sortedBy { it.latencyMs ?: Long.MAX_VALUE }
                                message = "بررسی تمام شد؛ زمان‌ها فقط تأخیر TCP هستند."
                                busy = false
                            }
                        }) { Text(if (busy) "در حال بررسی…" else "بررسی و مرتب‌سازی") }
                    }
                    Text(message, color = Color(0xFFB9C7E2))
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nodes, key = { it.id }) { node ->
                            ElevatedCard(colors = CardDefaults.elevatedCardColors(
                                containerColor = Color(0xFF1A2540))) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(node.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(node.protocol + " • " + node.host + ":" + node.port,
                                        color = Color(0xFFB9C7E2))
                                    Text(node.latencyMs?.let { "TCP: " + it + " ms • " + node.status } ?: node.status,
                                        color = Color(0xFFFFC078))
                                }
                            }
                        }
                    }
                    Text("نسخه آزمایشی: تونل واقعی و کشف خودکار سرورها هنوز پیاده‌سازی نشده‌اند.",
                        color = Color(0xFF8798B8), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
