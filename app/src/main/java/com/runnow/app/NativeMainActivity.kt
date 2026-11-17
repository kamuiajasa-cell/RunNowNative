package com.runnow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyodigitama.huaweiwear.*
import com.runnow.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Ink = Color(0xFF17221F)
private val Muted = Color(0xFF718078)
private val Accent = Color(0xFFB8F36B)
private val Surface = Color(0xFFF5F7F2)
private val Card = Color(0xFFFFFFFF)

class NativeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RunNowNativeApp(
            onPair = { startActivity(Intent(this, PairingActivity::class.java)) },
            onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onSaveProfile = { age, rest, max, goal -> getSharedPreferences("native_athlete", MODE_PRIVATE).edit().putInt("age", age).putFloat("resting_hr", rest.toFloat()).putFloat("max_hr", max.toFloat()).putString("goal", goal).apply() },
            load = { NativeDataSource(this).load() },
        ) }
    }
}

private data class NativeState(
    val overview: WatchDataOverview,
    val workouts: List<WatchWorkout>,
    val acts: List<Act>,
    val days: List<WatchDay>,
    val sleep: List<WatchSleepNight>,
    val hrv: List<WatchHrv>,
    val result: ProcessResult?,
    val reviews: Map<Long, RunReview>,
    val synced: Boolean,
    val error: String?,
    val age: Int,
    val restingHr: Double,
    val maxHr: Double,
    val goal: String,
)

private class NativeDataSource(private val activity: android.content.Context) {
    suspend fun load(): NativeState = withContext(Dispatchers.IO) {
        val watch = WatchData(activity)
        val overview = watch.overview()
        val workouts = watch.workouts(250).filter { it.complete }
        val days = watch.days(35)
        val sleep = watch.sleepNights(14)
        val hrv = watch.hrv(35)
        val zone = ZoneId.systemDefault()
        val acts = workouts.map { w ->
            val kind = when {
                w.sportType in setOf(1, 5, 11) || w.sportName.contains("run", true) || w.sportName.contains("lari", true) -> ActKind.RUN
                w.sportType in setOf(2, 13, 14) || w.sportName.contains("walk", true) || w.sportName.contains("jalan", true) -> ActKind.WALK
                else -> ActKind.OTHER
            }
            Act(
                start = w.startSec * 1000L, kind = kind,
                min = w.durationSec / 60.0, km = w.distanceM / 1000.0,
                avgHr = (w.avgHr ?: 0).toDouble(), lid = "huawei:${w.id}",
                name = w.sportName, maxHr = (w.maxHr ?: 0).toDouble(),
                icuId = w.id.toString(),
            )
        }
        val wellness = days.map { d ->
            val night = sleep.firstOrNull { Instant.ofEpochSecond(it.startSec).atZone(zone).toLocalDate().toString() == d.date }
            val hv = hrv.firstOrNull { Instant.ofEpochSecond(it.startSec).atZone(zone).toLocalDate().toString() == d.date && it.source.equals("night", true) }
            Wellness(
                id = d.date, restingHR = (d.restingHr ?: 0).toDouble(), hrv = hv?.rmssd ?: 0.0,
                hrvSrc = hv?.source.orEmpty(), hrvSpot = hv?.source?.equals("spot", true) == true,
                sleepSecs = (night?.asleepSec ?: 0).toDouble(), sleepScore = (night?.score ?: 0).toDouble(),
                steps = d.steps.toDouble(), spO2 = (d.spo2Avg ?: 0).toDouble(),
            )
        }.sortedBy { it.id }
        val streamSummaries = workouts.take(35).mapNotNull { workout ->
            val points = watch.workoutPoints(workout.id).filter { it.lat != null && it.lon != null }
            if (points.size < 30) return@mapNotNull null
            val times = ArrayList<Double>(points.size)
            val distances = ArrayList<Double>(points.size)
            val heartRates = ArrayList<Double>(points.size)
            var distance = 0.0
            var previous: WatchWorkoutPoint? = null
            val start = points.first().tsSec
            points.forEach { point ->
                val prev = previous
                if (prev != null) {
                    val lat1 = Math.toRadians(prev.lat!!)
                    val lat2 = Math.toRadians(point.lat!!)
                    val dLat = lat2 - lat1
                    val dLon = Math.toRadians(point.lon!! - prev.lon!!)
                    val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                        kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
                    val segment = 6371000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
                    if (segment < 100.0) distance += segment
                }
                times += (point.tsSec - start).toDouble()
                distances += distance
                heartRates += (point.hr ?: 0).toDouble()
                previous = point
            }
            val raw = StreamData(times, distances, heartRates.takeIf { values -> values.any { it > 0 } })
            val summary = Snapshot(profile = Profile(), acts = emptyList(), wellness = emptyList(), nowMs = System.currentTimeMillis(), zone = zone)
                .summarizeStream(raw, minN = 30)
            summary?.let { workout.id.toString() to it }
        }.toMap()
        val prefs = activity.getSharedPreferences("native_athlete", android.content.Context.MODE_PRIVATE)
        val age = prefs.getInt("age", 0)
        val restingHr = prefs.getFloat("resting_hr", 0f).toDouble()
        val maxHr = prefs.getFloat("max_hr", 0f).toDouble()
        val goal = prefs.getString("goal", "5K") ?: "5K"
        val profile = Profile(rest = restingHr, max = maxHr, age = age, restManual = restingHr > 0)
        var snap = Snapshot(profile, acts, wellness, nowMs = System.currentTimeMillis(), zone = zone, settings = Settings(goal = goal), streams = streamSummaries)
        val process = if (acts.isNotEmpty() || wellness.isNotEmpty()) snap.processData() else null
        if (process != null) {
            snap = snap.withProcessed(process)
        }
        val reviews = acts.filter { it.kind == ActKind.RUN }.associate { it.start to snap.reviewRun(it) }
        NativeState(overview, workouts, acts, days, sleep, hrv, process, reviews, watch.available(), watch.lastError, age, restingHr, maxHr, goal)
    }
}

@Composable
private fun RunNowNativeApp(onPair: () -> Unit, onSettings: () -> Unit, onSaveProfile: (Int, Double, Double, String) -> Unit, load: suspend () -> NativeState) {
    var tab by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<NativeState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        busy = true
        try { state = load(); failure = null } catch (e: Exception) { failure = e.message ?: "Gagal membaca data jam" }
        busy = false
    }
    LaunchedEffect(Unit) { refresh() }
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Color.White, secondary = Accent,
        background = Surface, surface = Card, onSurface = Ink,
    )) {
        Scaffold(
            containerColor = Surface,
            bottomBar = {
                NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                    listOf("Hari Ini", "Riwayat", "Analisis", "Profil").forEachIndexed { i, label ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i },
                            icon = { Text(listOf("◉", "▤", "⌁", "○")[i], fontSize = 19.sp) },
                            label = { Text(label, fontSize = 11.sp) })
                    }
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("RUN NOW", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(listOf("Hari Ini", "Riwayat Aktivitas", "Analisis Latihan", "Profil Atlet")[tab],
                            fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    TextButton(onClick = { scope.launch { refresh() } }) {
                        Text(if (busy) "Memuat…" else "Sinkron", color = Ink)
                    }
                }
                if (failure != null) Text(failure!!, Modifier.padding(horizontal = 22.dp), color = Color(0xFFB3261E))
                val s = state
                if (busy && s == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else when (tab) {
                    0 -> TodayScreen(s, onPair)
                    1 -> HistoryScreen(s)
                    2 -> AnalysisScreen(s)
                    else -> ProfileScreen(s, onPair, onSettings) { age, rest, max, goal -> onSaveProfile(age, rest, max, goal); scope.launch { refresh() } }
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(s: NativeState?, onPair: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SurfaceCard {
                Text("KONDISI HARI INI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val ready = s?.result?.recommendation
                Text(ready?.score?.toString() ?: "—", fontSize = 54.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(when { ready == null -> "Menunggu data latihan"; ready.score >= 75 -> "Kesiapan baik"; ready.score >= 55 -> "Jaga intensitas"; else -> "Prioritaskan pemulihan" },
                    color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(if (ready == null) "Sinkronkan jam untuk membaca kondisi dan riwayat latihan." else ready.reason,
                    color = Muted, fontSize = 13.sp)
            }
        }
        item {
            SurfaceCard {
                Text("ARAHAN COACH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(s?.result?.recommendation?.title ?: "Belum ada rekomendasi", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(s?.result?.recommendation?.duration ?: "Data latihan diperlukan", color = Muted)
                Text(s?.result?.recommendation?.pace ?: "—", color = Muted, fontSize = 13.sp)
                Text(s?.result?.recommendation?.hr ?: "", color = Muted, fontSize = 13.sp)
                if (s?.result != null) s.result.recommendation.steps.forEach { (a,b) ->
                    Text("• $a — $b", fontSize = 13.sp, color = Ink)
                }
            }
        }
        item {
            SurfaceCard {
                Text("MINGGU BERJALAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val week = s?.workouts.orEmpty().filter {
                    val d = Instant.ofEpochSecond(it.startSec).atZone(ZoneId.systemDefault()).dayOfWeek.value
                    val daysAgo = (java.time.LocalDate.now().dayOfWeek.value - d + 7) % 7
                    daysAgo < 7 && Instant.ofEpochSecond(it.startSec).atZone(ZoneId.systemDefault()).toLocalDate().isAfter(java.time.LocalDate.now().minusDays(7))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Metric("Jarak", String.format(Locale.US, "%.1f km", week.sumOf { it.distanceM } / 1000.0))
                    Metric("Sesi", week.size.toString())
                    Metric("Durasi", "${week.sumOf { it.durationSec } / 3600} jam")
                }
            }
        }
        item {
            SurfaceCard {
                Text("AKTIVITAS TERAKHIR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val last = s?.workouts?.firstOrNull()
                if (last == null) Text("Belum ada aktivitas dari jam.", color = Muted)
                else WorkoutRow(last)
            }
        }
        item { DataStatus(s) }
    }
}

@Composable
private fun HistoryScreen(s: NativeState?) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SurfaceCard {
                Text("RINGKASAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Metric("Total", String.format(Locale.US, "%.1f km", s?.workouts.orEmpty().sumOf { it.distanceM } / 1000.0))
                    Metric("Aktivitas", s?.workouts?.size?.toString() ?: "0")
                    Metric("Menit", (s?.workouts.orEmpty().sumOf { it.durationSec } / 60).toString())
                }
            }
        }
        items(s?.workouts.orEmpty(), key = { it.id }) { w ->
            SurfaceCard {
                WorkoutRow(w)
                val act = s?.acts?.firstOrNull { it.icuId == w.id.toString() }
                val review = s?.reviews?.get(act?.start)
                if (review != null && review.headline != null) {
                    Divider(color = Surface)
                    Text(review.headline, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(review.summary, color = Muted, fontSize = 12.sp)
                    review.sections.take(2).forEach { section ->
                        Text(section.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        section.notes.take(2).forEach { Text("• ${it.text}", color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisScreen(s: NativeState?) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SurfaceCard {
                Text("BEBAN LATIHAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val a = s?.result?.athlete
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Metric("CTL", a?.ctl?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
                    Metric("ATL", a?.atl?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
                    Metric("TSB", a?.tsb?.let { String.format(Locale.US, "%+.0f", it) } ?: "—")
                }
                Text("CTL = kebugaran jangka panjang · ATL = beban akut · TSB = keseimbangan beban.", color = Muted, fontSize = 12.sp)
            }
        }
        item {
            SurfaceCard {
                Text("KAPASITAS & AMBANG", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val a = s?.result?.athlete
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Metric("LTHR", a?.lthr?.let { "$it bpm" } ?: "—")
                    Metric("LTPace", a?.ltPace?.let { "$it/km" } ?: "—")
                }
                Text("Tahap adaptasi: ${a?.stageName ?: "Belum terukur"}", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            SurfaceCard {
                Text("JARAK 7 HARI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val values = (6 downTo 0).map { back ->
                    val date = java.time.LocalDate.now().minusDays(back.toLong())
                    s?.workouts.orEmpty().filter {
                        Instant.ofEpochSecond(it.startSec).atZone(ZoneId.systemDefault()).toLocalDate() == date
                    }.sumOf { it.distanceM } / 1000f
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    values.forEachIndexed { i, v ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(28.dp).height((12 + v.coerceAtMost(12f) * 8).dp).background(if (i == 6) Accent else Ink, RoundedCornerShape(6.dp)))
                            Text(listOf("S","S","R","K","J","S","M")[i].toString(), fontSize = 10.sp, color = Muted)
                        }
                    }
                }
                Text(String.format(Locale.US, "Total %.1f km", values.sum()), fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            SurfaceCard {
                Text("WELLNESS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                val day = s?.days?.firstOrNull()
                val night = s?.sleep?.firstOrNull()
                val hrv = s?.hrv?.firstOrNull { it.source.equals("night", true) }
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Metric("RHR", day?.restingHr?.let { "$it bpm" } ?: "—")
                    Metric("HRV", hrv?.let { String.format(Locale.US, "%.0f ms", it.rmssd) } ?: "—")
                    Metric("Tidur", night?.let { "${it.asleepSec / 3600}j ${(it.asleepSec % 3600) / 60}m" } ?: "—")
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(s: NativeState?, onPair: () -> Unit, onSettings: () -> Unit, onSaveProfile: (Int, Double, Double, String) -> Unit) {
    var age by remember(s?.age) { mutableStateOf(if ((s?.age ?: 0) > 0) s!!.age.toString() else "") }
    var rest by remember(s?.restingHr) { mutableStateOf(if ((s?.restingHr ?: 0.0) > 0) s!!.restingHr.toInt().toString() else "") }
    var max by remember(s?.maxHr) { mutableStateOf(if ((s?.maxHr ?: 0.0) > 0) s!!.maxHr.toInt().toString() else "") }
    var goal by remember(s?.goal) { mutableStateOf(s?.goal ?: "5K") }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SurfaceCard {
                Text("PROFIL ATLET", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Kalibrasi analisis", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Isi hanya nilai yang kamu ketahui. Kosongkan RHR atau HRmax bila belum yakin.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) }, label = { Text("Usia (tahun)") }, singleLine = true)
                OutlinedTextField(value = rest, onValueChange = { rest = it.filter(Char::isDigit).take(3) }, label = { Text("RHR manual (bpm, opsional)") }, singleLine = true)
                OutlinedTextField(value = max, onValueChange = { max = it.filter(Char::isDigit).take(3) }, label = { Text("HRmax (bpm, opsional)") }, singleLine = true)
                Text("Target utama", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("5K", "10K", "Half", "Marathon", "Umum").forEach { item ->
                        FilterChip(selected = goal == item, onClick = { goal = item }, label = { Text(item) })
                    }
                }
                Button(onClick = { onSaveProfile(age.toIntOrNull() ?: 0, rest.toDoubleOrNull() ?: 0.0, max.toDoubleOrNull() ?: 0.0, goal) },
                    colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Simpan profil") }
            }
        }
        item {
            SurfaceCard {
                Text("SUMBER DATA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (s?.synced == true) "Huawei Watch terdeteksi" else "Jam belum tersinkron", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${s?.overview?.workouts ?: 0} aktivitas · ${s?.overview?.hrSamples ?: 0} sampel HR · ${s?.overview?.hrvReadings ?: 0} pengukuran HRV",
                    color = Muted, fontSize = 13.sp)
                Button(onClick = onPair, colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("Kelola koneksi jam") }
            }
        }
        item {
            SurfaceCard {
                Text("PERANGKAT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                if (s?.overview?.devices.isNullOrEmpty()) Text("Belum ada perangkat tersimpan.", color = Muted)
                s?.overview?.devices.orEmpty().forEach { Text("${it.name} · ${it.model ?: "Huawei"}", fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = onSettings) { Text("Pengaturan perangkat dan aplikasi") }
            }
        }
        item { DataStatus(s) }
    }
}

@Composable
private fun DataStatus(s: NativeState?) {
    SurfaceCard {
        Text("STATUS DATA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(if (s?.synced == true) "Database jam tersedia" else "Menunggu sinkronisasi pertama", fontWeight = FontWeight.SemiBold)
        Text(s?.overview?.lastSampleSec?.let { "Sampel terakhir: " + DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("id")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(it)) } ?: "Belum ada sampel harian",
            color = Muted, fontSize = 12.sp)
        s?.error?.let { Text("Catatan pembacaan: $it", color = Color(0xFFB3261E), fontSize = 12.sp) }
    }
}

@Composable
private fun WorkoutRow(w: WatchWorkout) {
    Text(w.sportName.ifBlank { "Aktivitas" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale("id")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(w.startSec)),
        color = Muted, fontSize = 11.sp)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("Jarak", String.format(Locale.US, "%.2f km", w.distanceM / 1000.0))
        Metric("Durasi", "${w.durationSec / 60} mnt")
        Metric("Pace", if (w.distanceM > 0) {
            val sec = w.durationSec.toDouble() / (w.distanceM / 1000.0)
            String.format(Locale.US, "%d:%02d", sec.toInt()/60, sec.toInt()%60)
        } else "—")
        Metric("HR", w.avgHr?.let { "$it" } ?: "—")
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Muted, fontSize = 11.sp)
        Text(value, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
