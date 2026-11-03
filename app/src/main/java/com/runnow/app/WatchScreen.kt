package com.runnow.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyodigitama.huaweiwear.HuaweiWatch
import com.joyodigitama.huaweiwear.WatchDevice
import com.joyodigitama.huaweiwear.WatchException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun linkLabel(link: String): String = when (link) {
    "disconnected" -> "Terputus"
    "connecting" -> "Menyambung…"
    "authenticating" -> "Memverifikasi…"
    "initializing" -> "Menyiapkan…"
    "connected" -> "Tersambung"
    "syncing" -> "Menyinkronkan…"
    else -> link
}

private fun supportLabel(s: String): String = when (s) {
    "tested" -> "teruji"
    "experimental" -> "eksperimental"
    "unsupported" -> "tidak didukung"
    else -> "belum dikenal"
}

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

private fun timeOf(sec: Long): String =
    TIME_FMT.format(Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()))

@Composable
fun WatchScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val watch = remember { HuaweiWatch.get(ctx) }
    val scope = rememberCoroutineScope()

    val state by watch.state.collectAsState()
    val found by watch.found.collectAsState()
    val scanning by watch.scanning.collectAsState()
    val log by watch.log.collectAsState()

    var message by remember { mutableStateOf("") }
    var bonded by remember { mutableStateOf<List<WatchDevice>>(emptyList()) }
    var missing by remember { mutableStateOf(HuaweiWatch.missingPermissions(ctx)) }
    var confirm by remember { mutableStateOf<WatchDevice?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    fun loadBonded() {
        try {
            bonded = watch.bonded()
        } catch (e: WatchException) {
            message = e.message ?: e.code
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        missing = HuaweiWatch.missingPermissions(ctx)
        if (missing.none { it.contains("BLUETOOTH") || it.contains("LOCATION") }) loadBonded()
    }

    LaunchedEffect(missing.isEmpty()) { if (missing.isEmpty()) loadBonded() }

    fun connect(d: WatchDevice, confirmBind: Boolean) {
        scope.launch {
            busy = true
            message = "Menyambung ke ${d.name}…"
            try {
                watch.connect(d.address, confirmBind)
                message = "Tersambung ke ${d.name}."
            } catch (e: WatchException) {
                if (e.code == HuaweiWatch.ERR_BIND_CONFIRMATION) {
                    confirm = d
                    message = ""
                } else {
                    message = "Gagal menyambung: ${e.message}"
                }
            } catch (e: Exception) {
                message = "Gagal menyambung: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    confirm?.let { d ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Pasangkan ${d.name} ke PolarRun?") },
            text = {
                Text(
                    "Jam ini belum pernah diikat dengan HP ini. Ikatan pertama menggantikan pasangan HP-nya yang sekarang " +
                        "(mis. Huawei Health), jadi jam mungkin terlepas dari aplikasi itu. Konfirmasi juga akan diminta di layar jam."
                )
            },
            confirmButton = {
                TextButton(onClick = { val x = d; confirm = null; connect(x, true) }) { Text("Lanjutkan") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } }
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Jam", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(linkLabel(state.link), style = MaterialTheme.typography.titleMedium)
                state.name?.let { Text(it) }
                state.battery?.let { Text("Baterai $it%") }
                state.lastSyncSec?.let { Text("Sinkron terakhir ${timeOf(it)}") }
                state.error?.let { Text("Kesalahan: $it", color = MaterialTheme.colorScheme.error) }
            }
        }

        if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (missing.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Izin Bluetooth diperlukan untuk mencari dan menyambung ke jam.")
                    Button(onClick = { permLauncher.launch(missing.toTypedArray()) }) { Text("Beri izin") }
                }
            }
        } else {
            val connected = state.link == "connected" || state.link == "syncing"
            fun runOp(block: suspend () -> String) {
                scope.launch {
                    busy = true
                    message = "Menyinkronkan…"
                    try {
                        message = block()
                    } catch (e: Exception) {
                        message = "Gagal sinkron: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = connected && !busy,
                    onClick = {
                        runOp {
                            val r = watch.sync()
                            "Selesai: ${r.steps} langkah, ${r.workouts} latihan, ${r.sleep} data tidur, ${r.samples} sampel."
                        }
                    }
                ) { Text("Sinkron") }
                OutlinedButton(
                    enabled = connected && !busy,
                    onClick = {
                        runOp {
                            val r = watch.sync(days = 7)
                            "Selesai (7 hari): ${r.steps} langkah, ${r.workouts} latihan, ${r.sleep} data tidur, ${r.samples} sampel."
                        }
                    }
                ) { Text("7 hari") }
                OutlinedButton(
                    enabled = connected && !busy,
                    onClick = {
                        runOp {
                            val r = watch.syncHrv()
                            "HRV: ${r.stored} malam tersimpan (${r.result})."
                        }
                    }
                ) { Text("HRV") }
            }
            OutlinedButton(enabled = connected, onClick = { watch.disconnect() }) { Text("Putuskan") }

            Text("Jam di HP ini", style = MaterialTheme.typography.titleSmall)
            if (bonded.isEmpty()) Text("Belum ada jam terpasang. Pasangkan lewat Bluetooth Android, atau cari di bawah.")
            bonded.forEach { d -> DeviceRow(d, busy) { connect(d, false) } }
            OutlinedButton(onClick = { loadBonded() }) { Text("Segarkan daftar") }

            Text("Cari di sekitar", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    try {
                        if (scanning) watch.stopScan() else watch.startScan()
                    } catch (e: WatchException) {
                        message = e.message ?: e.code
                    }
                }
            ) { Text(if (scanning) "Berhenti mencari" else "Cari jam") }
            found.forEach { d -> DeviceRow(d, busy) { watch.stopScan(); connect(d, false) } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Sembunyikan log" else "Tampilkan log") }
            TextButton(
                onClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("log jam", log.joinToString("\n")))
                    message = "Log disalin."
                }
            ) { Text("Salin log") }
        }
        if (showLog) {
            Text(
                log.takeLast(60).joinToString("\n").ifBlank { "(log kosong)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun DeviceRow(d: WatchDevice, busy: Boolean, onConnect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(d.model, supportLabel(d.support), if (d.remembered) "diingat" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(enabled = !busy, onClick = onConnect) { Text("Sambungkan") }
        }
    }
}
