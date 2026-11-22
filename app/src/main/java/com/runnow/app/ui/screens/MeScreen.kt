package com.runnow.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.SettingsActivity
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.ThemeMode
import com.runnow.app.ui.UiState
import com.runnow.data.SettingsStore
import com.runnow.domain.BacktestResult
import com.runnow.domain.Snapshot
import com.runnow.domain.band
import com.runnow.domain.durText
import com.runnow.domain.f1
import com.runnow.domain.mp
import com.runnow.domain.planWeekKm
import com.runnow.domain.rhr28
import com.runnow.domain.runBacktest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private val PLAN_DOW = listOf(1 to "Sen", 2 to "Sel", 3 to "Rab", 4 to "Kam", 5 to "Jum", 6 to "Sab", 0 to "Min")
private val PLAN_TYPES = listOf("auto" to "Auto", "easy" to "Easy", "quality" to "Quality", "long" to "Long", "rest" to "Rest")
private val GOAL_LABEL = mapOf("5K" to "5K", "10K" to "10K", "Half" to "Half", "Marathon" to "Marathon", "Umum" to "Umum")
private val STYLE_LABEL = listOf(
    Triple("moderate", "Moderat", "Seimbang (disarankan)"),
    Triple("conservative", "Konservatif", "Lebih banyak recovery"),
    Triple("aggressive", "Agresif", "Lebih banyak sesi kualitas"),
)
private val RACE_DIST = listOf(0.0 to "Pilih jarak", 5.0 to "5K", 10.0 to "10K", 21.1 to "Half", 42.2 to "Maraton")

/** Layar Saya (renderMe + pengaturan di runsense.html). Pengaturan jam & aplikasi lanjutan tetap di SettingsActivity (WebView). */
@Composable
fun MeScreen(ui: UiState, vm: AppViewModel, onOpenWatch: () -> Unit) {
    val c = RunTheme.colors
    val ctx = LocalContext.current
    val r = ui.result
    val snap = ui.snap
    val s = ui.settings
    ScreenFrame("Saya") {
        // ---- Profil lari ----
        if (snap != null) ProfileCard(ui, snap, vm)

        // ---- Tujuan & gaya ----
        RunCard {
            LabelText("Tujuan & gaya coaching")
            ChipRow { for (g in SettingsStore.GOALS) RunChip(GOAL_LABEL[g] ?: g, s.goal == g, { vm.setGoal(g) }) }
            if (r != null) {
                KvRow("Estimasi ${s.goal} saat ini", r.goalEst.current.ifBlank { "—:—" })
                KvRow("Target bagus", r.goalEst.good.ifBlank { "—" })
                KvRow("Keyakinan", r.goalEst.projection.ifBlank { "—" })
            }
            for ((k, t, sub) in STYLE_LABEL) {
                RunCard(onClick = { vm.setStyle(k) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column2(t, sub)
                        if (s.style == k) Text("✓", color = c.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ---- Rencana minggu ----
        PlanCard(ui, snap, vm)

        // ---- Integrasi ----
        RunCard {
            LabelText("Integrasi")
            KvRow("Intervals.icu", if (ui.isDemo) "Mode demo" else if (ui.source == "watch") "Tidak dipakai" else r?.syncLine?.ifBlank { null } ?: "Belum sinkron")
            PrimaryBtn(if (ui.busy) "Menyinkronkan…" else "Sinkron ulang", { vm.syncNow() }, enabled = !ui.busy && !ui.isDemo)
            OutlineBtn("Jam & koneksi", onOpenWatch)
            OutlineBtn("Pengaturan jam & aplikasi", { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) })
            if (ui.message.isNotBlank()) Muted(ui.message)
        }

        // ---- Notifikasi ----
        ReminderCard(ui, vm)

        // ---- Tampilan ----
        RunCard {
            LabelText("Tampilan")
            ChipRow {
                for ((m, t) in listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.DARK to "Gelap", ThemeMode.LIGHT to "Terang")) {
                    RunChip(t, ui.theme == m, { vm.setTheme(m) })
                }
            }
        }

        // ---- Tentang coach + mode lanjutan ----
        RunCard {
            LabelText("Tentang coach")
            Text(
                "Coach membaca lari, detak, dan beban latihanmu dari Intervals.icu atau jam, lalu memilih sesi hari ini: lari, run-walk, jalan, atau istirahat. Semua hitungan tetap jalan di belakang layar.",
                fontSize = 14.sp, color = c.lightGrey,
            )
            OutlineBtn("Mode lanjutan: " + if (ui.advanced) "nyala" else "mati", { vm.setAdvanced(!ui.advanced) })
        }
        if (ui.advanced && snap != null && r != null) AdvancedCard(ui, snap)

        OutlineBtn("Reset data & logout", { vm.logout() }, danger = true)
    }
}

@Composable
private fun Column2(title: String, sub: String) {
    androidx.compose.foundation.layout.Column {
        Text(title, fontWeight = FontWeight.SemiBold, color = RunTheme.colors.tx)
        Muted(sub)
    }
}

@Composable
private fun NumField(label: String, value: String, placeholder: String = "", onChange: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Number) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        placeholder = { Text(placeholder) }, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Usia, RHR, HR maks, LTHR dan LTPace custom (saveProfile). Disimpan 700 ms setelah berhenti mengetik. */
@Composable
private fun ProfileCard(ui: UiState, snap: Snapshot, vm: AppViewModel) {
    val p = snap.profile
    val a = ui.result?.athlete
    var touched by remember { mutableStateOf(false) }
    var age by rememberSaveable { mutableStateOf(if (p.age > 0) p.age.toString() else "") }
    var rest by rememberSaveable { mutableStateOf(if (p.rest > 0) Math.round(p.rest).toString() else "") }
    var max by rememberSaveable { mutableStateOf(if (p.max > 0) Math.round(p.max).toString() else "") }
    var lthr by rememberSaveable { mutableStateOf(if (p.lthrC > 0) p.lthrC.toString() else "") }
    var ltp by rememberSaveable { mutableStateOf(if (p.ltpC > 0) mp(p.ltpC / 60.0) else "") }
    LaunchedEffect(age, rest, max, lthr, ltp) {
        if (!touched) return@LaunchedEffect
        delay(700)
        vm.saveProfile(age, rest, max, lthr, ltp)
    }
    val r28 = remember(snap) { snap.rhr28() }
    RunCard {
        LabelText("Profil lari")
        NumField("Usia", age, placeholder = "—", onChange = { age = it; touched = true })
        NumField("Detak istirahat (28 hari)", rest, placeholder = "—", onChange = { rest = it; touched = true })
        Muted(
            if (p.restManual) "Memakai angka yang kamu isi" + (if (r28 != null) " (rata-rata 28 hari: ${r28.avg} bpm). Kosongkan untuk kembali otomatis." else ". Kosongkan untuk otomatis.")
            else if (r28 != null) "Otomatis: rata-rata ${r28.n} hari dalam 28 hari terakhir. Ketik angka untuk menimpa."
            else "Otomatis rata-rata 28 hari, menunggu minimal 5 hari data RHR.",
        )
        NumField("Detak maksimal", max, placeholder = "—", onChange = { max = it; touched = true })
        NumField("LTHR custom (bpm)", lthr, placeholder = "auto", onChange = { lthr = it; touched = true })
        Muted("Saran auto: " + (a?.lthrAuto?.let { "$it bpm" } ?: "belum cukup data") + if (p.lthrC > 0) " · dipakai: ${p.lthrC} bpm (custom)" else "")
        NumField("LTPace custom (mnt/km)", ltp, placeholder = "mis. 8:30", onChange = { ltp = it; touched = true }, keyboard = KeyboardType.Text)
        Muted("Saran auto: " + (a?.ltPaceAuto?.let { "$it /km" } ?: "belum cukup data") + if (p.ltpC > 0) " · dipakai: ${mp(p.ltpC / 60.0)} /km (custom)" else "")
        Muted("Terisi otomatis dari Intervals.icu bila tersedia.")
    }
}

/** Rencana minggu, tanggal race, jarak/target race, run-walk (renderPlanUI, setRace, setRaceX, setRw). */
@Composable
private fun PlanCard(ui: UiState, snap: Snapshot?, vm: AppViewModel) {
    val c = RunTheme.colors
    val s = ui.settings
    RunCard {
        LabelText("Rencana minggu")
        Muted("Jenis + km per hari. \"Auto\" = coach yang memutuskan.")
        for ((dow, name) in PLAN_DOW) PlanRow(dow, name, ui, vm)
        snap?.let { sn -> planWarnings(sn).takeIf { it.isNotBlank() }?.let { Muted(it) } }

        LabelText("Tanggal race")
        var raceTxt by rememberSaveable { mutableStateOf(s.race) }
        OutlinedTextField(
            value = raceTxt,
            onValueChange = {
                raceTxt = it
                if (it.isEmpty()) vm.setRace("") else if (runCatching { LocalDate.parse(it) }.isSuccess) vm.setRace(it)
            },
            singleLine = true, label = { Text("yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlineBtn("Hapus tanggal race", { raceTxt = ""; vm.setRace("") })

        LabelText("Jarak race dan target waktu")
        ChipRow { for ((d, t) in RACE_DIST) RunChip(t, s.raceDist == d, { vm.setRaceX(d, s.raceGoal) }) }
        var goalTxt by rememberSaveable { mutableStateOf(s.raceGoal) }
        OutlinedTextField(
            value = goalTxt, onValueChange = { goalTxt = it; vm.setRaceX(s.raceDist, it) }, singleLine = true,
            label = { Text("Target, mis. 1:05:00 atau 55:00") }, modifier = Modifier.fillMaxWidth(),
        )

        LabelText("Pola run-walk")
        var runTxt by rememberSaveable { mutableStateOf(rwT(s.rw.run)) }
        var walkTxt by rememberSaveable { mutableStateOf(rwT(s.rw.walk)) }
        fun apply(on: Boolean, rt: String, wt: String) = vm.setRunWalk(on, parseRw(rt, 225), parseRw(wt, 30))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = s.rw.on, onCheckedChange = { apply(it, runTxt, walkTxt) })
            Text("Pakai run-walk di semua tahap", fontSize = 14.sp, color = c.tx)
        }
        OutlinedTextField(
            value = runTxt, onValueChange = { runTxt = it; apply(s.rw.on, it, walkTxt) }, singleLine = true,
            label = { Text("Durasi lari, mm:ss (3:45)") }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = walkTxt, onValueChange = { walkTxt = it; apply(s.rw.on, runTxt, it) }, singleLine = true,
            label = { Text("Durasi jalan, mm:ss (0:30)") }, modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun rwT(n: Int) = "${n / 60}:${(n % 60).toString().padStart(2, '0')}"

/** pt() di setRw: "m:ss" valid bila detik < 60 dan total >= 10 detik; selain itu bawaan. */
private fun parseRw(v: String, d: Int): Int {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(v.trim()) ?: return d
    val t = m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    return if (m.groupValues[2].toInt() < 60 && t >= 10) t else d
}

@Composable
private fun PlanRow(dow: Int, name: String, ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    val p = ui.settings.plan[dow]
    var open by remember { mutableStateOf(false) }
    var km by rememberSaveable(dow) { mutableStateOf(if ((p?.km ?: 0.0) > 0) fmtKm(p!!.km) else "") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(name, fontWeight = FontWeight.Bold, color = c.tx, modifier = Modifier.width(36.dp))
        Box(Modifier.weight(1f)) {
            OutlineBtn(PLAN_TYPES.firstOrNull { it.first == (p?.t ?: "auto") }?.second ?: "Auto", { open = true })
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                for ((v, l) in PLAN_TYPES) DropdownMenuItem(text = { Text(l) }, onClick = { open = false; vm.setPlanType(dow, v) })
            }
        }
        OutlinedTextField(
            value = km, onValueChange = { km = it; vm.setPlanKm(dow, it.replace(',', '.').toDoubleOrNull() ?: 0.0) },
            singleLine = true, placeholder = { Text("km") }, modifier = Modifier.width(90.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

private fun fmtKm(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

/** Peringatan rencana (renderPlanUI): long > 30% volume, batas naik 10%, quality dan long berdekatan. */
private fun planWarnings(snap: Snapshot): String {
    val plan = snap.settings.plan
    val order = PLAN_DOW
    val tot = snap.planWeekKm()
    val lk = plan.values.filter { it.t == "long" }.maxOfOrNull { it.km } ?: 0.0
    val msgs = ArrayList<String>()
    if (tot > 0 && lk > 0.3 * tot) msgs.add("Long ${fmtKm(lk)} km = ${Math.round(lk / tot * 100)}% volume minggu. Batas ~30%. Kurangi long atau tambah easy.")
    val today = Instant.ofEpochMilli(snap.nowMs).atZone(snap.zone).toLocalDate()
    val wk = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val lastKm = snap.acts.filter {
        val d = Instant.ofEpochMilli(it.start).atZone(snap.zone).toLocalDate()
        it.kind == com.runnow.domain.ActKind.RUN && d >= wk.minusDays(7) && d < wk
    }.sumOf { it.km }
    if (lastKm > 0) {
        val cap = Math.round(lastKm * 1.1)
        msgs.add("Minggu lalu ${Math.round(lastKm)} km. Batas naik aman ~10%: $cap km minggu ini." + if (tot > cap * 1.05) " Rencanamu ${fmtKm(tot)} km melewati batas itu." else "")
    }
    for ((i, d) in order.withIndex()) {
        val nx = order[(i + 1) % 7]
        val a = plan[d.first]?.t
        val b = plan[nx.first]?.t
        if ((a == "quality" && b == "long") || (a == "long" && b == "quality")) msgs.add("${d.second} dan ${nx.second}: quality dan long berdekatan (<48 jam).")
    }
    return msgs.joinToString(" ")
}

/** Pengingat harian dari rencana minggu, dijadwalkan lewat PlanNotifier (AlarmManager). */
@Composable
private fun ReminderCard(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ask() {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var timeTxt by rememberSaveable { mutableStateOf(ui.remindTime) }
    RunCard {
        LabelText("Notifikasi")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = ui.remindOn, onCheckedChange = { vm.setRemind(it, ui.remindRest, ui.remindTime); if (it) ask() })
            Text("Notifikasi harian sesi lari", fontSize = 14.sp, color = c.tx)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = ui.remindRest, onCheckedChange = { vm.setRemind(ui.remindOn, it, ui.remindTime) })
            Text("Ingatkan juga di hari istirahat", fontSize = 14.sp, color = c.tx)
        }
        OutlinedTextField(
            value = timeTxt,
            onValueChange = {
                timeTxt = it
                if (runCatching { LocalTime.parse(it) }.isSuccess && Regex("^\\d{2}:\\d{2}$").matches(it)) vm.setRemind(ui.remindOn, ui.remindRest, it)
            },
            singleLine = true, label = { Text("Jam notifikasi (HH:mm)") }, modifier = Modifier.fillMaxWidth(),
        )
        Muted("Pengingat dibuat dari hari berjenis Easy, Quality, Long, atau Rest di rencana minggu. Hari Auto tidak diingatkan.")
    }
}

private val TIPS = mapOf(
    1 to "Jalan cepat 20–35 mnt (akhir pekan lebih lama). Naik ke run-walk bila estimasi ≥ 7,9 METs.",
    2 to "Run-walk White Plan fase I (~30 mnt). Naik ke tahap 3 bila estimasi ≥ 9,2 METs.",
    3 to "Easy dibatasi ~70% kapasitas. Planner penuh bila Max lebih tinggi atau pace easy melambat.",
    4 to "Planner terstruktur normal. Pace 6:00 terasa santai (~65%) butuh Max ≈ 14–15,5 METs.",
)
private val REFS = listOf("Jalan cepat 10:40" to 4.8, "Run-walk" to 6.0, "Pace 8:15–8:30" to 7.8, "Pace 7:15–7:30" to 8.5, "Pace 6:00" to 9.3)

/** Mode lanjutan: kapasitas, tahap, beban, tes Cooper, referensi intensitas, uji estimator. */
@Composable
private fun AdvancedCard(ui: UiState, snap: Snapshot) {
    val c = RunTheme.colors
    val a = ui.result!!.athlete
    val scope = rememberCoroutineScope()
    var cooper by rememberSaveable { mutableStateOf("") }
    var bt by remember { mutableStateOf<BacktestResult?>(null) }
    var btBusy by remember { mutableStateOf(false) }
    RunCard {
        LabelText("Mode lanjutan")
        KvRow("Max METs", a.mets?.let { f1(it) + " (≈ VO₂max " + Math.round(it * 3.5) + ")" } ?: "—")
        KvRow("Kategori", a.mets?.let { band(it) } ?: "—")
        KvRow("Tahap", a.stageName.ifBlank { "—" })
        KvRow("CTL / ATL / TSB", listOf(a.ctl, a.atl, a.tsb).joinToString(" / ") { v -> v?.let { (Math.round(it * 10) / 10.0).toString() } ?: "—" })
        KvRow("LTHR · LTPace", (a.lthr?.let { "$it bpm" } ?: "—") + " · " + (a.ltPace?.let { "$it /km" } ?: "—"))
        Insight(
            if (a.stage != 0) "T${a.stage}. ${TIPS[a.stage] ?: ""}" + (if (a.est.src.isNotBlank()) " Sumber: ${a.est.src} (keyakinan ${a.est.conf})." else "")
            else "Belum cukup data untuk membaca kapasitas.",
        )
        if (a.mets == null || a.est.conf == "rendah") {
            Muted("Keyakinan rendah: estimasi dari data tipis. Isi usia/detak, atau lakukan tes 12 menit di bawah.")
        }
        LabelText("Tes lapangan 12 menit (Cooper)")
        Muted("Lari sekuat mampu 12 menit di lintasan datar, lalu isi jarak totalnya. Hasilnya pembanding untuk estimasi di atas.")
        NumField("Jarak (meter)", cooper, placeholder = "mis. 2200", onChange = { cooper = it })
        val d = cooper.toDoubleOrNull() ?: 0.0
        KvRow("Estimasi Max METs", if (d in 800.0..4500.0) { val vo2 = (d - 504.9) / 44.73; f1(vo2 / 3.5) + " (≈ VO₂max " + Math.round(vo2) + ")" } else "—")
        LabelText("Referensi intensitas")
        if (a.mets != null) {
            for ((n, m) in REFS) {
                val pct = m / a.mets!!
                val col = if (pct <= 0.68) c.primary else if (pct <= 0.85) c.yellow else c.red
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(n, fontSize = 13.sp, color = c.lightGrey)
                    Text("${f1(m)} METs · ${Math.round(pct * 100)}% · ${durText(pct)}", fontSize = 13.sp, color = col)
                }
            }
        } else Muted("Butuh estimasi Max METs dulu.")
        OutlineBtn(if (btBusy) "Menghitung…" else "Uji estimator Max METs", {
            btBusy = true
            scope.launch {
                bt = withContext(Dispatchers.Default) { runCatching { snap.runBacktest() }.getOrNull() }
                btBusy = false
            }
        }, enabled = !btBusy)
        bt?.let { b ->
            Muted(b.subtitle)
            val st = b.stats
            if (st == null) Muted("Belum cukup data uji (butuh minimal 10 lari teruji).")
            else for ((l, v) in st) KvRow(l, v)
            for ((l, v) in b.recent) KvRow(l, v)
        }
    }
}
