package com.runnow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.UiState
import com.runnow.domain.ActKind
import com.runnow.domain.Recommendation
import com.runnow.domain.Snapshot
import com.runnow.domain.autonomic
import com.runnow.domain.coachLine
import com.runnow.domain.detectType
import com.runnow.domain.f1
import com.runnow.domain.flagInfo
import com.runnow.domain.fmtHm
import com.runnow.domain.intensityLine
import com.runnow.domain.loadDays
import com.runnow.domain.racePhase
import com.runnow.domain.reviewRun
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ID = Locale("id", "ID")
private val DAY_NAMES = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
internal val VX_LABEL = mapOf("easy" to "Easy", "long" to "Long", "thr" to "Threshold", "vo2" to "VO2max", "race" to "Lomba")
internal val VX_TYPES = listOf("easy", "long", "thr", "vo2", "race")

private fun greeting(hour: Int) = when {
    hour < 11 -> "Selamat pagi"
    hour < 15 -> "Selamat siang"
    hour < 18 -> "Selamat sore"
    else -> "Selamat malam"
}

private fun localDate(snap: Snapshot, ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(snap.zone).toLocalDate()

/** Layar "Hari Ini" (renderAll di runsense.html). [onOpenMe] membuka tab Saya (atur race), [onOpenWatch] tab Jam. */
@Composable
fun HomeScreen(ui: UiState, vm: AppViewModel, onOpenMe: () -> Unit, onOpenWatch: () -> Unit) {
    val c = RunTheme.colors
    val r = ui.result
    val snap = ui.snap
    ScreenFrame("") {
        if (r == null || snap == null) {
            Text("Hari Ini", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tx)
            EmptyNote()
            return@ScreenFrame
        }
        val a = r.athlete
        val now = Instant.ofEpochMilli(snap.nowMs).atZone(snap.zone)
        val today = now.toLocalDate()
        val first = a.name.takeIf { it.isNotBlank() && it != "Runner" }?.split(" ")?.firstOrNull()

        // Kepala: sapaan + tanggal
        Column {
            Text(greeting(now.hour) + (if (first != null) ", $first" else ""), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tx)
            Muted(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ID)))
        }

        RaceLine(snap, onOpenMe)

        // Kartu utama
        var walkOnly by rememberSaveable { mutableStateOf(false) }
        var whyOpen by rememberSaveable { mutableStateOf(false) }
        val rested = ui.restDay == today.toString() && r.recommendation.status != "done"
        val rec: Recommendation = when {
            rested -> Recommendation("ok", "done", "Istirahat hari ini", "", "", "", "", emptyList(), 0, null, "ISTIRAHAT")
            r.recommendation.status != "done" && walkOnly -> Recommendation(
                "ok", "train", "Jalan santai", "20–30 menit", "—", "Pelan, napas nyaman",
                "Alternatif ringan hari ini.", listOf("Jalan" to "20–30 mnt santai, tanpa target"), 0, null, "JALAN",
            )
            else -> r.recommendation
        }
        val walk = !rested && walkOnly && r.recommendation.status != "done"

        val au = remember(snap) { snap.autonomic() }
        if (au.warn) Insight(au.why + if (au.lock) " · quality dikunci." else " · pantau.")

        val todays = snap.acts.filter { localDate(snap, it.start) == today && (it.kind == ActKind.RUN || it.kind == ActKind.WALK) }
        MainCard(
            rec = rec, rested = rested, walk = walk, formLabel = a.formLabel, doneToday = todays.isNotEmpty(),
            lowConf = a.stage != 0 && a.est.conf == "rendah", whyOpen = whyOpen,
            onToggleWhy = { whyOpen = !whyOpen },
            onRest = { vm.markRest(it) },
            onWalk = { walkOnly = it; whyOpen = false },
            onSendWatch = onOpenWatch,
        )

        LegRow(snap, ui, vm)

        // Konfirmasi jenis sesi
        val need = snap.acts
            .filter { it.kind == ActKind.RUN && it.km >= 1.5 && snap.nowMs - it.start <= 5 * 864e5 && !snap.manualTypes.containsKey(it.start) }
            .sortedByDescending { it.start }
        if (need.isNotEmpty()) {
            val w = need[0]
            val d = snap.detectType(w)
            RunCard {
                LabelText("Jenis lari")
                Text(
                    localDate(snap, w.start).format(DateTimeFormatter.ofPattern("EEE, d MMM", ID)) + " · " + f1(w.km) + " km",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx,
                )
                Muted("Kelihatannya ini lari ${VX_LABEL[d.type] ?: d.type} (${d.conf}). ${d.why}. Benar?")
                PrimaryBtn("Ya, ${VX_LABEL[d.type] ?: d.type}", { vm.setSessionType(w.start, d.type) })
                ChipRow {
                    for (t in VX_TYPES.filter { it != d.type }) RunChip(VX_LABEL[t] ?: t, false, { vm.setSessionType(w.start, t) })
                }
                if (need.size > 1) Muted("+${need.size - 1} lari lain belum dikonfirmasi.")
            }
        }

        // Ulasan pasca-lari (< 48 jam)
        val recent = snap.acts
            .filter { it.kind == ActKind.RUN && it.km > 0 && snap.nowMs - it.start < 48 * 3600e3 }
            .sortedByDescending { it.start }
        if (recent.isNotEmpty()) {
            val act = recent[0]
            LaunchedEffect(act.start) { vm.ensureStream(act) }
            val rev = remember(snap, act.start) { snap.reviewRun(act) }
            if (rev.sections.isNotEmpty()) {
                RunCard {
                    LabelText("Lari tadi")
                    ReviewBlock(rev)
                }
            }
        }

        ReadinessCard(snap, a.formLabel, au)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val dot = if (ui.hasKey || ui.source == "watch" || ui.isDemo) c.primary else c.grey
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
            Muted(if (ui.busy) "Menyinkronkan…" else r.syncLine.ifBlank { "Belum sinkron" })
        }
        if (ui.message.isNotBlank() && !ui.busy) Muted(ui.message)
    }
}

@Composable
private fun RaceLine(snap: Snapshot, onOpenMe: () -> Unit) {
    val c = RunTheme.colors
    val ph = snap.racePhase()
    val raceDate = runCatching { LocalDate.parse(snap.settings.race) }.getOrNull()
    val text = if (ph != null && raceDate != null) {
        val lb = mapOf("race" to "Hari lomba", "taper" to "Taper", "peak" to "Peak", "build" to "Build")[ph.k] ?: ph.k
        "Race " + raceDate.format(DateTimeFormatter.ofPattern("d MMM", ID)) + " · " + (if (ph.days == 0) "H-0" else "H-${ph.days}") + " · " + lb
    } else if (raceDate != null) "Race sudah lewat" else "Belum ada tanggal race"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 13.sp, color = c.grey)
        TextButton(onClick = onOpenMe) { Text(if (ph != null) "Ubah" else "Atur race", color = c.primary, fontSize = 13.sp) }
    }
}

@Composable
private fun MainCard(
    rec: Recommendation, rested: Boolean, walk: Boolean, formLabel: String, doneToday: Boolean, lowConf: Boolean,
    whyOpen: Boolean, onToggleWhy: () -> Unit, onRest: (Boolean) -> Unit, onWalk: (Boolean) -> Unit, onSendWatch: () -> Unit,
) {
    val c = RunTheme.colors
    if (rec.status == "done" && !rested) {
        val skipped = rec.tag == "SKIP"
        RunCard {
            Pill(if (skipped) "DILEWATI" else "SELESAI HARI INI", c.primary)
            Text(
                if (skipped) "Sesi dilewati" else if (doneToday) "Sudah lari hari ini" else rec.title.ifBlank { "Selesai" },
                fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.tx,
            )
            Text(rec.reason.ifBlank { "Sesi hari ini sudah tercatat. Pulihkan diri, besok lanjut." }, fontSize = 14.sp, color = c.lightGrey)
        }
        return
    }
    val tone = when {
        rested -> "ok"
        rec.status == "rest" -> "bad"
        rec.k == "bad" -> "bad"
        rec.tag == "RECOVERY" || rec.k == "wn" -> "wn"
        else -> "ok"
    }
    val label = if (rested || rec.status == "rest") "ISTIRAHAT" else when (tone) { "wn" -> "HATI-HATI"; "bad" -> "ISTIRAHAT"; else -> "SIAP" }
    val accent = when (tone) { "wn" -> c.yellow; "bad" -> c.red; else -> c.primary }
    val title = (if (rec.status == "rest") "Istirahat hari ini" else rec.title).split(" · ")[0].replace("Tempo / Threshold", "Tempo")
    val hrTxt = if (rec.hr.isNotBlank() && rec.hr != "—" && !walk) rec.hr else ""
    val sub = listOf(
        rec.duration.takeIf { it.isNotBlank() && it != "—" } ?: "", hrTxt, intensityLine(rec, rested),
    ).filter { it.isNotBlank() }.joinToString(" · ")
    val hasWhy = rec.reason.isNotBlank() || rec.steps.isNotEmpty()
    RunCard {
        Pill(label, accent)
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = c.tx)
        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = c.grey)
        Text(coachLine(rec, formLabel, rested, walk), fontSize = 14.sp, color = c.lightGrey)
        when {
            rested -> OutlineBtn("Batalkan istirahat", { onRest(false) })
            rec.status == "rest" -> {
                PrimaryBtn("Tandai istirahat", { onRest(true) })
                OutlineBtn("Jalan santai", { onWalk(true) })
            }
            walk -> {
                PrimaryBtn("Kirim ke jam", onSendWatch)
                OutlineBtn("Kembali ke rencana", { onWalk(false) })
            }
            tone == "wn" -> {
                PrimaryBtn("Kirim ke jam", onSendWatch)
                OutlineBtn("Jalan saja", { onWalk(true) })
            }
            else -> {
                PrimaryBtn("Kirim ke jam", onSendWatch)
                OutlineBtn("Lewati, istirahat", { onRest(true) })
            }
        }
        if (hasWhy && !rested) {
            TextButton(onClick = onToggleWhy) { Text(if (whyOpen) "Tutup penjelasan" else "Kenapa & langkah sesi", color = c.primary) }
            if (whyOpen) {
                for ((n, d) in rec.steps) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(n, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = c.tx, modifier = Modifier.weight(1f))
                        Text(d, fontSize = 13.sp, color = c.grey, modifier = Modifier.weight(2f))
                    }
                }
                if (rec.reason.isNotBlank()) Insight(rec.reason)
            }
        }
        if (lowConf) Muted("Keyakinan estimasi kapasitas masih rendah. Isi usia & detak di tab Saya, atau lakukan tes 12 menit.")
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Baris Kaki (OK / Was-was / Sakit) dan Panas (renderLegRow). */
@Composable
private fun LegRow(snap: Snapshot, ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    val fl = snap.flagInfo()
    val lv = fl?.lv ?: 0
    val heat = snap.settings.heat == today(snap)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kaki", fontSize = 13.sp, color = c.grey)
            RunChip("OK", lv == 0, { vm.setFlag(0) })
            RunChip("Was-was", lv == 1, { vm.setFlag(1) }, warn = true)
            RunChip("Sakit", lv == 2, { vm.setFlag(2) }, warn = true)
            RunChip("Panas", heat, { vm.toggleHeat() }, warn = true)
        }
        if (fl != null) {
            val days = if (fl.lv == 2) 14 else 7
            Muted((if (fl.lv == 2) "Sakit" else "Was-was") + " · hari ${fl.dayN}/$days. " + if (fl.lv == 2) "Rest dulu, lalu jalan. Tanpa lari." else "Quality terkunci, volume −30%.")
        }
    }
}

private fun today(snap: Snapshot): String = Instant.ofEpochMilli(snap.nowMs).atZone(snap.zone).toLocalDate().toString()

/** Kesiapan, minggu ini, baris tubuh, hari-hari minggu, dan tren beban. */
@Composable
private fun ReadinessCard(snap: Snapshot, formLabel: String, au: com.runnow.domain.Autonomic) {
    val c = RunTheme.colors
    val todayD = Instant.ofEpochMilli(snap.nowMs).atZone(snap.zone).toLocalDate()
    val start = todayD.minusDays((todayD.dayOfWeek.value - 1).toLong())
    val runWalk = snap.acts.filter { it.kind == ActKind.RUN || it.kind == ActKind.WALK }
    val perDay = (0 until 7).map { i ->
        val d = start.plusDays(i.toLong())
        d to runWalk.filter { localDate(snap, it.start) == d }.sumOf { it.km }
    }
    val wkm = perDay.sumOf { it.second }
    val ws = perDay.count { it.second > 0 }
    val prevKm = runWalk.filter { val d = localDate(snap, it.start); d >= start.minusDays(28) && d < start }.sumOf { it.km } / 4
    val rdy = when (formLabel) { "Data kurang" -> "Data belum cukup"; "Baik" -> "Baik, boleh lari"; "Lelah" -> "Lelah, hati-hati"; else -> "Cukup" }
    val ac = snap.loadDays(7)
    val ch = snap.loadDays(28) / 4.0
    val trend = if (ch > 25) {
        val q = ac / ch
        if (q > 1.3) "Beban minggu ini agak tinggi dibanding biasanya. Jaga hari-hari santai."
        else if (q < 0.7) "Beban minggu ini lebih ringan dari biasanya, ada ruang untuk menambah sedikit."
        else "Beban minggu ini stabil, sesuai kebiasaanmu."
    } else "Belum cukup data untuk membaca tren beban."
    val body = buildList {
        au.hrv?.let { add("HRV $it" + (au.hrvPct?.let { p -> " (${sgn(p)}%)" } ?: "")) }
        au.rhr?.let { add("RHR $it" + (au.rhrD?.let { p -> " (${sgn(p)})" } ?: "")) }
        au.sleepH?.let { add("Tidur " + fmtHm(it)) }
        au.stress?.let { add("Stres " + Math.round(it)) }
    }
    RunCard {
        KvRow("Kesiapan", rdy)
        KvRow("Minggu ini", f1(wkm) + " km · $ws sesi" + if (prevKm > 0) " · biasanya " + f1(prevKm) else "")
        if (body.isNotEmpty()) Muted("Semalam · " + body.joinToString(" · "))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((i, p) in perDay.withIndex()) {
                val on = p.first == todayD
                val done = p.second > 0
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (done) c.primaryDim else c.el)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(DAY_NAMES[i], fontSize = 10.sp, color = if (on) c.primary else c.grey)
                    Text(if (done) f1(p.second) else "·", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (done) c.primary else c.grey)
                }
            }
        }
        Muted(trend)
    }
}

private fun sgn(n: Int) = (if (n > 0) "+" else if (n < 0) "−" else "±") + Math.abs(n)
