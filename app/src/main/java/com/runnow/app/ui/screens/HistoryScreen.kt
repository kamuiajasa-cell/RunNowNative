package com.runnow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.UiState
import com.runnow.domain.ActKind
import com.runnow.domain.ActivityItem
import com.runnow.domain.Snapshot
import com.runnow.domain.detectType
import com.runnow.domain.durText
import com.runnow.domain.f1
import com.runnow.domain.metsOf
import com.runnow.domain.mp
import com.runnow.domain.reviewRun
import com.runnow.domain.stage
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID = Locale("id", "ID")

private fun fmtDur(min: Double): String {
    val t = Math.round(min * 60).toInt()
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

private fun fmtPace(min: Double, km: Double): String {
    if (!(km > 0) || !(min > 0)) return "—"
    val p = min / km
    val m = Math.floor(p).toInt()
    val sc = Math.round((p - m) * 60).toInt()
    return if (sc == 60) "${m + 1}:00" else "$m:${sc.toString().padStart(2, '0')}"
}

private fun dayOf(snap: Snapshot, ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(snap.zone).toLocalDate()

/** Layar Riwayat (renderHistory + openAct di runsense.html). */
@Composable
fun HistoryScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    val r = ui.result
    val snap = ui.snap
    var filter by rememberSaveable { mutableStateOf("week") }
    var openMs by rememberSaveable { mutableStateOf<Long?>(null) }
    ScreenFrame("Riwayat") {
        ChipRow {
            for ((k, t) in listOf("week" to "Minggu ini", "month" to "Bulan ini", "all" to "Semua")) RunChip(t, filter == k, { filter = k })
        }
        if (r == null || snap == null) {
            EmptyNote()
            return@ScreenFrame
        }
        val today = dayOf(snap, snap.nowMs)
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val all = r.activities
        val list = when (filter) {
            "week" -> all.filter { dayOf(snap, it.start) >= weekStart }
            "month" -> all.filter { val d = dayOf(snap, it.start); d.month == today.month && d.year == today.year }
            else -> all
        }
        if (list.isEmpty()) {
            Muted("Belum ada lari di rentang ini")
            return@ScreenFrame
        }
        StatRow(
            listOf(
                list.size.toString() to "Sesi",
                f1(list.sumOf { it.act.km }) to "km",
                fmtDur(list.sumOf { it.act.min }) to "Waktu",
                Math.round(list.sumOf { it.load.toDouble() }).toString() to "Total TL",
            ),
        )
        if (filter == "week") {
            for (a in list) ActCard(a) { openMs = a.start }
        } else {
            val groups = list.groupBy { dayOf(snap, it.start).let { d -> d.minusDays((d.dayOfWeek.value - 1).toLong()) } }
            for ((wk, items) in groups.entries.sortedByDescending { it.key }.map { it.key to it.value }) {
                val label = when (wk) {
                    weekStart -> "Minggu ini"
                    weekStart.minusDays(7) -> "Minggu lalu"
                    else -> wk.format(DateTimeFormatter.ofPattern("d MMM", ID)) + " – " + wk.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", ID))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.lightGrey)
                    Muted("${items.size} sesi • ${f1(items.sumOf { it.act.km })} km")
                }
                for (a in items) ActCard(a) { openMs = a.start }
            }
        }
    }
    val sel = openMs?.let { ms -> r?.activities?.firstOrNull { it.start == ms } }
    if (sel != null && snap != null) ActDetail(sel, snap, vm) { openMs = null }
}

@Composable
private fun ActCard(a: ActivityItem, onClick: () -> Unit) {
    val c = RunTheme.colors
    val hard = a.type == "vo2" || a.type == "thr"
    val accent = when {
        hard -> c.yellow
        a.type == "long" -> c.primary
        else -> c.grey
    }
    RunCard(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted(a.date)
            Text("TL ${a.load}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.grey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(VX_LABEL[a.type] ?: a.type, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
        }
        val km = a.act.km
        val min = a.act.min
        val hr = if (a.act.avgHr > 0) Math.round(a.act.avgHr).toString() else "—"
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Cell(f1(km), "km")
            Cell(fmtDur(min), "waktu")
            Cell(fmtPace(min, km), "/km")
            Cell(hr, "HR")
        }
    }
}

@Composable
private fun Cell(v: String, l: String) {
    Column {
        Text(v, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RunTheme.colors.tx)
        Muted(l)
    }
}

/** Detail satu aktivitas (openAct): jenis sesi, ulasan, intensitas. Ulasan cuaca (climAct) belum dipindahkan. */
@Composable
private fun ActDetail(item: ActivityItem, snap: Snapshot, vm: AppViewModel, onClose: () -> Unit) {
    val c = RunTheme.colors
    val act = item.act
    LaunchedEffect(act.start) { vm.ensureStream(act) }
    val det = remember(snap, act.start) { snap.detectType(act) }
    val rev = remember(snap, act.start) { snap.reviewRun(act) }
    val pace = if (act.km > 0) act.min / act.km else 0.0
    val whenTxt = Instant.ofEpochMilli(act.start).atZone(snap.zone).format(DateTimeFormatter.ofPattern("EEEE, d MMM · HH.mm", ID))
    val sub = whenTxt + " · " + mp(pace) + " /km" +
        (if (act.avgHr > 0) " · HR " + Math.round(act.avgHr) + (if (act.maxHr > 0) "–" + Math.round(act.maxHr) else "") else "") +
        (if (act.elev > 0) " · +" + Math.round(act.elev) + " m" else "")
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f),
            shape = RoundedCornerShape(20.dp), color = c.card,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((if (act.kind == ActKind.RUN) "Lari" else "Aktivitas") + " · " + f1(act.km) + " km", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.tx)
                Muted(sub)
                Muted("Deteksi: ${VX_LABEL[det.type] ?: det.type} (${det.conf}${if (det.confirmed) ", dikonfirmasi" else ""}). ${det.why}.")
                ChipRow {
                    for (t in VX_TYPES) RunChip(VX_LABEL[t] ?: t, snap.manualTypes[act.start] == t, { vm.setSessionType(act.start, t); onClose() }, warn = t == "thr" || t == "vo2")
                }
                ReviewBlock(rev)
                val st = remember(snap) { snap.stage() }
                val m = st.m
                if (m != null && pace > 0) {
                    val pct = metsOf(pace) / m
                    Insight("Intensitas ≈ ${Math.round(pct * 100)}% Max METs (${f1(m)}) · tahan ${durText(pct)} · tahap T${st.s}")
                }
                TextButton(onClick = onClose) { Text("Tutup", color = c.primary) }
            }
        }
    }
}
