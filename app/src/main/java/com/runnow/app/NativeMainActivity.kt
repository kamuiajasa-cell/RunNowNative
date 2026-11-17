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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joyodigitama.huaweiwear.*
import com.runnow.domain.*
import com.runnow.data.ClimateLocation
import com.runnow.data.OpenMeteoProvider
import com.runnow.data.WeatherBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val Ink = Color(0xFF17221F)
internal val Muted = Color(0xFF718078)
internal val Accent = Color(0xFFB8F36B)
internal val Surface = Color(0xFFF5F7F2)
internal val Card = Color(0xFFFFFFFF)

class NativeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RunNowNativeApp(
            onPair = { startActivity(Intent(this, PairingActivity::class.java)) },
            onSettings = { startActivity(Intent(this, PairingActivity::class.java)) },
            onSaveProfile = { age, rest, max, goal -> getSharedPreferences("native_athlete", MODE_PRIVATE).edit().putInt("age", age).putFloat("resting_hr", rest.toFloat()).putFloat("max_hr", max.toFloat()).putString("goal", goal).apply(); NativeSettingsStore(this).save(NativeSettingsStore(this).load().copy(goal = goal)) },
            onSaveSettings = { settings -> NativeSettingsStore(this).save(settings) },
            loadClimate = { location -> NativeDataSource(this).loadClimate(location) },
            loadWorkoutPoints = { id -> NativeDataSource(this).loadWorkoutPoints(id) },
            load = { NativeDataSource(this).load() },
            syncIntervals = { key -> NativeDataSource(this).syncIntervals(key) },
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
    val settings: Settings,
    val intervalsConnected: Boolean,
    val climateContexts: Map<Long, ClimateContext>,
    val climateProfile: PersonalClimateProfile,
    val savedClimateLocation: ClimateLocation?,
    val climateAdvice: Map<Long, ClimateAdvice>,
    val climateComparisons: Map<Long, ClimateComparison>,
)

private class NativeDataSource(private val activity: android.content.Context) {
    suspend fun load(): NativeState = withContext(Dispatchers.IO) {
        val watch = WatchData(activity)
        val overview = watch.overview()
        val workouts = watch.workouts(250).filter { it.complete }
        val days = watch.days(35)
        val sleep = watch.sleepNights(14)
        val hrv = watch.hrv(35)
        val prefs = activity.getSharedPreferences("native_athlete", android.content.Context.MODE_PRIVATE)
        val age = prefs.getInt("age", 0)
        val restingHr = prefs.getFloat("resting_hr", 0f).toDouble()
        val maxHr = prefs.getFloat("max_hr", 0f).toDouble()
        val goal = prefs.getString("goal", "5K") ?: "5K"
        val profile = Profile(rest = restingHr, max = maxHr, age = age, restManual = restingHr > 0)
        val repository = com.runnow.data.RunNowRepository(activity)
        val data = repository.load(profile = profile)
        val climateRepository = com.runnow.data.ClimateDataRepository(activity)
        val intervalsKey = com.runnow.data.IntervalsCredentialStore(activity).read()
        runCatching {
            climateRepository.matchRecentRuns(data.snapshot, gps = { act ->
                val sourceId = act.icuId.orEmpty()
                if (sourceId.startsWith("hw")) {
                    val workoutId = sourceId.removePrefix("hw").toLongOrNull() ?: return@matchRecentRuns null
                    val workout = workouts.firstOrNull { it.id == workoutId } ?: return@matchRecentRuns null
                    watch.workoutPoints(workoutId).mapNotNull { point ->
                        val lat = point.lat
                        val lon = point.lon
                        if (lat == null || lon == null) null
                        else com.runnow.data.ActivityGpsPoint((point.tsSec - workout.startSec).toDouble(), lat, lon)
                    }
                } else if (!intervalsKey.isNullOrBlank() && sourceId.isNotBlank()) {
                    com.runnow.data.IntervalsIcuClient(intervalsKey).activityGps(sourceId, act.min * 60.0)
                } else null
            }, limit = 1)
        }
        val climateContexts = climateRepository.contexts()
        val climateProfile = data.snapshot.personalClimateProfile(climateContexts)
        val climateComparisons = data.snapshot.personalClimateComparisons(climateContexts, climateProfile)
        val climateAdviceByStart = data.snapshot.acts.mapNotNull { act ->
            val context = climateContexts[act.start] ?: return@mapNotNull null
            val guess = data.snapshot.detectType(act).type
            val tag = when (guess) { "thr", "vo2" -> "QUALITY"; "long" -> "LONG"; else -> "EASY" }
            val comparison = climateComparisons[act.start]
            act.start to climateAdvice(context.conditions, climateProfile, tag, false,
                comparison?.deltaHr, comparison?.count ?: 0, comparison?.confidence)
        }.toMap()
        var snap = data.snapshot.copy(settings = NativeSettingsStore(activity).load().copy(goal = goal))
        val acts = snap.acts
        val wellness = snap.wellness
        val process = if (acts.isNotEmpty() || wellness.isNotEmpty()) snap.processData() else null
        if (process != null) snap = snap.withProcessed(process)
        val reviews = acts.filter { it.kind == ActKind.RUN }.associate { it.start to snap.reviewRun(it) }
        NativeState(
            overview, workouts, acts, days, sleep, hrv, process, reviews,
            data.watchAvailable, data.watchError, age, restingHr, maxHr, goal, snap.settings,
            intervalsKey != null, climateContexts, climateProfile, climateRepository.savedLocation(), climateAdviceByStart, climateComparisons,
        )
    }

    suspend fun loadWorkoutPoints(workoutId: Long): List<WatchWorkoutPoint> = withContext(Dispatchers.IO) { WatchData(activity).workoutPoints(workoutId) }

    suspend fun loadClimate(location: ClimateLocation): WeatherBundle = withContext(Dispatchers.IO) {
        com.runnow.data.ClimateDataRepository(activity).saveLocation(location)
        OpenMeteoProvider(activity).forecast(location, pastDays = 1, forecastDays = 3)
    }

    suspend fun syncIntervals(apiKey: String): NativeState = withContext(Dispatchers.IO) {
        val prefs = activity.getSharedPreferences("native_athlete", android.content.Context.MODE_PRIVATE)
        val rest = prefs.getFloat("resting_hr", 0f).toDouble()
        val profile = Profile(
            rest = rest, max = prefs.getFloat("max_hr", 0f).toDouble(),
            age = prefs.getInt("age", 0), restManual = rest > 0,
        )
        com.runnow.data.IntervalsCredentialStore(activity).save(apiKey)
        com.runnow.data.RunNowRepository(activity).syncIntervals(apiKey, profile)
        load()
    }
}

@Composable
private fun RunNowNativeApp(onPair: () -> Unit, onSettings: () -> Unit, onSaveProfile: (Int, Double, Double, String) -> Unit, onSaveSettings: (Settings) -> Unit, loadClimate: suspend (ClimateLocation) -> WeatherBundle, loadWorkoutPoints: suspend (Long) -> List<WatchWorkoutPoint>, load: suspend () -> NativeState, syncIntervals: suspend (String) -> NativeState) {
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
                        Text(if (busy) "Memuat…" else "Perbarui", color = Ink)
                    }
                }
                if (failure != null) Text(failure!!, Modifier.padding(horizontal = 22.dp), color = Color(0xFFB3261E))
                val s = state
                if (busy && s == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else when (tab) {
                    0 -> TodayScreen(s, onPair) { updated -> onSaveSettings(updated); state = state?.copy(settings = updated) }
                    1 -> HistoryScreen(s, loadWorkoutPoints)
                    2 -> AnalysisScreen(s)
                    else -> ProfileScreen(s, onPair, onSettings, onSaveSettings, loadClimate, onSyncIntervals = { key ->
                        scope.launch {
                            busy = true
                            try { state = syncIntervals(key); failure = null }
                            catch (e: Exception) { failure = e.message ?: "Sinkronisasi Intervals gagal" }
                            busy = false
                        }
                    }) { age, rest, max, goal -> onSaveProfile(age, rest, max, goal); scope.launch { refresh() } }
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(s: NativeState?, onPair: () -> Unit, onSaveSettings: (Settings) -> Unit) {
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
                Text("SINYAL TUBUH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val flag = s?.settings?.flag
                Text(when (flag?.lv) { 2 -> "Kaki ditandai sakit"; 1 -> "Kaki perlu diwaspadai"; else -> "Kaki terasa baik" }, fontWeight = FontWeight.SemiBold)
                Text("Tandai kondisi kaki agar coach menyesuaikan beban. Ini bukan diagnosis medis.", color = Muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = flag == null, onClick = { onSaveSettings((s?.settings ?: Settings()).copy(flag = null)) }, label = { Text("Baik") })
                    FilterChip(selected = flag?.lv == 1, onClick = { onSaveSettings((s?.settings ?: Settings()).copy(flag = LegFlag(1, System.currentTimeMillis()))) }, label = { Text("Waspada") })
                    FilterChip(selected = flag?.lv == 2, onClick = { onSaveSettings((s?.settings ?: Settings()).copy(flag = LegFlag(2, System.currentTimeMillis()))) }, label = { Text("Sakit") })
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
private fun HistoryScreen(s: NativeState?, loadWorkoutPoints: suspend (Long) -> List<WatchWorkoutPoint>) {
    var period by remember { mutableStateOf("Bulan ini") }
    var selected by remember { mutableStateOf<WatchWorkout?>(null) }
    var stream by remember(selected?.id) { mutableStateOf<List<WatchWorkoutPoint>>(emptyList()) }
    var streamLoading by remember(selected?.id) { mutableStateOf(false) }
    var streamError by remember(selected?.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(selected?.id) {
        val id = selected?.id ?: return@LaunchedEffect
        streamLoading = true
        streamError = null
        try { stream = loadWorkoutPoints(id) } catch (e: Exception) { streamError = e.message ?: "Gagal membaca rekaman aktivitas" }
        streamLoading = false
    }
    val today = LocalDate.now()
    val all = s?.workouts.orEmpty()
    val filtered = all.filter { w ->
        val date = Instant.ofEpochSecond(w.startSec).atZone(ZoneId.systemDefault()).toLocalDate()
        when (period) {
            "Minggu ini" -> !date.isBefore(today.with(java.time.DayOfWeek.MONDAY))
            "Bulan ini" -> date.year == today.year && date.month == today.month
            else -> true
        }
    }
    val km = filtered.sumOf { it.distanceM } / 1000.0
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Minggu ini", "Bulan ini", "Semua").forEach { label ->
                    FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) })
                }
            }
        }
        item {
            SurfaceCard {
                Text("RINGKASAN · $period".uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Jarak", String.format(Locale.US, "%.1f km", km))
                    Metric("Aktivitas", filtered.size.toString())
                    Metric("Durasi", "${filtered.sumOf { it.durationSec } / 3600}j ${(filtered.sumOf { it.durationSec } % 3600) / 60}m")
                }
            }
        }
        if (filtered.isEmpty()) item {
            SurfaceCard {
                Text("Belum ada aktivitas pada periode ini", fontWeight = FontWeight.SemiBold)
                Text("Aktivitas akan muncul setelah disinkronkan dari jam atau Intervals.icu.", color = Muted, fontSize = 12.sp)
            }
        }
        items(filtered, key = { it.id }) { w ->
            SurfaceCard {
                WorkoutRow(w)
                TextButton(onClick = { selected = w }) { Text("Lihat analisis aktivitas") }
            }
        }
    }
    selected?.let { w ->
        val act = s?.acts?.firstOrNull { it.icuId == "hw${w.id}" || it.icuId == w.id.toString() }
        val review = act?.let { s?.reviews?.get(it.start) }
        val climate = act?.let { s?.climateContexts?.get(it.start) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(w.sportName.ifBlank { "Detail aktivitas" }) },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WorkoutRow(w)
                    ActivityStreamSummary(w, stream, streamLoading, streamError)
                    review?.let {
                        Divider()
                        Text(it.headline ?: "Analisis aktivitas", fontWeight = FontWeight.SemiBold)
                        Text(it.summary, color = Muted, fontSize = 12.sp)
                        it.sections.forEach { section ->
                            Text(section.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            section.notes.forEach { note -> Text("• ${note.text}", color = Muted, fontSize = 12.sp) }
                        }
                    }
                    climate?.let { ctx ->
                        Divider()
                        Text("Cuaca saat lari", fontWeight = FontWeight.SemiBold)
                        Text("${ctx.conditions.temperatureC?.let { "${it.toInt()}°C" } ?: "Tidak tersedia"} · terasa ${ctx.conditions.apparentTemperatureC?.let { "${it.toInt()}°C" } ?: "—"} · RH ${ctx.conditions.humidityPct?.let { "${it.toInt()}%" } ?: "—"}", fontSize = 12.sp)
                        s?.climateAdvice?.get(act!!.start)?.let { Text(it.message, fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun ActivityStreamSummary(w: WatchWorkout, points: List<WatchWorkoutPoint>, loading: Boolean, error: String?) {
    Divider()
    Text("REKAMAN AKTIVITAS", fontWeight = FontWeight.SemiBold)
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Membaca stream dari jam…", color = Muted, fontSize = 12.sp)
        }
    } else if (points.isEmpty()) {
        Text(error ?: "Jam tidak menyediakan titik stream untuk sesi ini. Ringkasan aktivitas tetap tersedia.", color = Muted, fontSize = 12.sp)
    } else {
        val hr = points.mapNotNull { it.hr?.takeIf { v -> v in 30..240 } }
        val cadence = points.mapNotNull { it.cadence?.takeIf { v -> v > 0 } }
        val altitude = points.mapNotNull { it.alt }
        val gps = points.count { it.lat != null && it.lon != null }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Titik", points.size.toString())
            Metric("HR terekam", hr.size.toString())
            Metric("GPS", gps.toString())
        }
        if (hr.size >= 2) {
            val avg = hr.average()
            Metric("HR rata-rata / rentang", "${avg.toInt()} bpm · ${hr.minOrNull()}–${hr.maxOrNull()}")
            HeartRateStreamChart(points)
        } else Text("Rekaman detak jantung tidak cukup untuk membuat grafik.", color = Muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Cadence rata-rata", cadence.takeIf { it.isNotEmpty() }?.let { "${it.average().toInt()} spm" } ?: "—")
            Metric("Elevasi", if (altitude.size >= 2) "${altitude.minOrNull()!!.toInt()}–${altitude.maxOrNull()!!.toInt()} m" else "—")
        }
        Text("Grafik memakai sampel HR asli yang tersimpan; titik kosong tidak diinterpolasi.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun HeartRateStreamChart(points: List<WatchWorkoutPoint>) {
    val samples = points.mapNotNull { p -> p.hr?.takeIf { it in 30..240 }?.let { p.tsSec to it.toFloat() } }
    if (samples.size < 2) return
    val min = (samples.minOf { it.second } - 5f).coerceAtLeast(30f)
    val max = (samples.maxOf { it.second } + 5f).coerceAtMost(240f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DETak JANTUNG · BPM", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(112.dp)) {
            val span = (max - min).coerceAtLeast(1f)
            val left = 8f
            val top = 8f
            val width = size.width - 16f
            val height = size.height - 16f
            for (i in 0..3) {
                val y = top + height * i / 3f
                drawLine(Color(0xFFE6EBE4), Offset(left, y), Offset(left + width, y), 1.dp.toPx())
            }
            val first = samples.first().first
            val duration = (samples.last().first - first).coerceAtLeast(1L).toFloat()
            val path = androidx.compose.ui.graphics.Path()
            samples.forEachIndexed { index, sample ->
                val x = left + width * ((sample.first - first) / duration)
                val y = top + height * (1f - (sample.second - min) / span)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = Ink, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mulai", color = Muted, fontSize = 10.sp)
            Text("Selesai", color = Muted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun AnalysisScreen(s: NativeState?) {
    var section by remember { mutableStateOf("Ikhtisar") }
    val sections = listOf("Ikhtisar", "Beban", "Performa", "Wellness")
    val runs = s?.acts.orEmpty().filter { it.kind == ActKind.RUN }
    val athlete = s?.result?.athlete
    val totalKm = runs.sumOf { it.km }
    val totalMinutes = runs.sumOf { it.min }
    val weeks = (7 downTo 0).map { back ->
        val start = LocalDate.now().minusWeeks(back.toLong())
        val weekStart = start.with(java.time.DayOfWeek.MONDAY)
        runs.filter { act ->
            val d = Instant.ofEpochMilli(act.start).atZone(ZoneId.systemDefault()).toLocalDate()
            !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7))
        }.sumOf { it.km }.toFloat()
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                sections.forEach { label ->
                    FilterChip(selected = section == label, onClick = { section = label }, label = { Text(label) })
                }
            }
        }
        when (section) {
            "Ikhtisar" -> {
                item {
                    SurfaceCard {
                        Text("RINGKASAN PERFORMA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("Jarak total", String.format(Locale.US, "%.1f km", totalKm))
                            Metric("Sesi lari", runs.size.toString())
                            Metric("Waktu", "${totalMinutes.toInt()} mnt")
                        }
                        Text("Data seluruh riwayat yang tersimpan · bukan hanya periode 8 minggu.", color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    SurfaceCard {
                        Text("KONSISTENSI 8 MINGGU", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                            weeks.forEachIndexed { i, value ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.width(24.dp).height((8 + value.coerceAtMost(30f) * 3).dp).background(if (i == 7) Accent else Ink, RoundedCornerShape(5.dp)))
                                    Text("${i + 1}", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                        Text(String.format(Locale.US, "Rata-rata %.1f km per minggu", weeks.average()), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            "Beban" -> {
                item {
                    SurfaceCard {
                        Text("BEBAN LATIHAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("CTL", athlete?.ctl?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
                            Metric("ATL", athlete?.atl?.let { String.format(Locale.US, "%.0f", it) } ?: "—")
                            Metric("TSB", athlete?.tsb?.let { String.format(Locale.US, "%+.0f", it) } ?: "—")
                        }
                        Text("CTL menggambarkan beban jangka panjang; ATL beban jangka pendek; TSB selisih CTL dan ATL. Ini estimasi model, bukan pengukuran jaringan tubuh.", color = Muted, fontSize = 12.sp)
                    }
                }
                item {
                    SurfaceCard {
                        Text("ARAHAN COACH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(s?.result?.recommendation?.title ?: "Belum cukup data", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(s?.result?.recommendation?.reason ?: "Sinkronkan beberapa minggu aktivitas untuk membangun estimasi beban.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            "Performa" -> {
                item {
                    SurfaceCard {
                        Text("KAPASITAS & AMBANG", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("LTHR", athlete?.lthr?.let { "$it bpm" } ?: "—")
                            Metric("LTPace", athlete?.ltPace?.let { "$it/km" } ?: "—")
                            Metric("Tahap", athlete?.stageName ?: "—")
                        }
                        Text("Ambang ditampilkan jika mesin dapat memperkirakannya dari data yang tersedia.", color = Muted, fontSize = 12.sp)
                    }
                }
                item {
                    SurfaceCard {
                        Text("REKOR AKTIVITAS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val best5k = runs.filter { it.km >= 4.8 && it.km <= 5.3 && it.min > 0 }.minByOrNull { it.min }
                        val best10k = runs.filter { it.km >= 9.7 && it.km <= 10.5 && it.min > 0 }.minByOrNull { it.min }
                        Metric("5K terdekat", best5k?.let { "${it.min.toInt()} menit · ${String.format(Locale.US, "%.2f km", it.km)}" } ?: "Belum ada data")
                        Metric("10K terdekat", best10k?.let { "${it.min.toInt()} menit · ${String.format(Locale.US, "%.2f km", it.km)}" } ?: "Belum ada data")
                        Text("Ini sesi dengan jarak terdekat pada arsip, bukan rekor resmi hasil pemotongan split.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            else -> {
                item {
                    SurfaceCard {
                        Text("WELLNESS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val day = s?.days?.firstOrNull()
                        val night = s?.sleep?.firstOrNull()
                        val hrv = s?.hrv?.firstOrNull { it.source.equals("night", true) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("RHR", day?.restingHr?.let { "$it bpm" } ?: "—")
                            Metric("HRV", hrv?.let { String.format(Locale.US, "%.0f ms", it.rmssd) } ?: "—")
                            Metric("Tidur", night?.let { "${it.asleepSec / 3600}j ${(it.asleepSec % 3600) / 60}m" } ?: "—")
                        }
                        Text("Nilai kosong berarti sumber belum menyediakan data; tidak dianggap nol.", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    s: NativeState?,
    onPair: () -> Unit,
    onSettings: () -> Unit,
    onSaveSettings: (Settings) -> Unit,
    loadClimate: suspend (ClimateLocation) -> WeatherBundle,
    onSyncIntervals: (String) -> Unit,
    onSaveProfile: (Int, Double, Double, String) -> Unit,
) {
    var page by remember { mutableStateOf("Menu") }
    var age by remember(s?.age) { mutableStateOf(if ((s?.age ?: 0) > 0) s!!.age.toString() else "") }
    var rest by remember(s?.restingHr) { mutableStateOf(if ((s?.restingHr ?: 0.0) > 0) s!!.restingHr.toInt().toString() else "") }
    var max by remember(s?.maxHr) { mutableStateOf(if ((s?.maxHr ?: 0.0) > 0) s!!.maxHr.toInt().toString() else "") }
    var goal by remember(s?.goal) { mutableStateOf(s?.goal ?: "5K") }
    var intervalsKey by remember { mutableStateOf("") }
    var settings by remember(s?.settings) { mutableStateOf(s?.settings ?: Settings()) }
    var runSecs by remember(settings.rw) { mutableStateOf(settings.rw.run.toString()) }
    var walkSecs by remember(settings.rw) { mutableStateOf(settings.rw.walk.toString()) }
    var lthr by remember(settings.athlete) { mutableStateOf(settings.athlete.lthr.takeIf { it > 0 }?.toString() ?: "") }
    var ltPace by remember(settings.athlete) { mutableStateOf(settings.athlete.ltPace) }
    val pages = listOf(
        "Data atlet" to "Usia, RHR, dan HRmax",
        "Ambang & zona" to "LTHR dan LTPace",
        "Sasaran" to "Target dan lomba",
        "Rencana mingguan" to "Jadwal sesi latihan",
        "Pendampingan" to "Gaya coach dan sinyal kaki",
        "Lari-jalan" to "Interval lari dan jalan",
        "Perangkat & sinkronisasi" to "Huawei Watch dan Intervals.icu",
        "Cuaca & lokasi" to "Prakiraan dan waktu latihan",
        "Data & privasi" to "Penyimpanan dan sumber data",
    )
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (page != "Menu") TextButton(onClick = { page = "Menu" }) { Text("‹ Profil") }
                Text(if (page == "Menu") "PROFIL ATLET" else page.uppercase(), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (page == "Menu") {
            item {
                SurfaceCard {
                    Text("RunNow", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Target ${s?.goal ?: "5K"} · ${if (s?.intervalsConnected == true) "Intervals.icu terhubung" else "Belum ada koneksi Intervals.icu"}", color = Muted, fontSize = 12.sp)
                    Text("Atur parameter atlet dan cara coach menyusun latihan.", color = Muted, fontSize = 12.sp)
                }
            }
            items(pages) { (title, subtitle) ->
                SurfaceCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, fontWeight = FontWeight.SemiBold)
                            Text(subtitle, color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { page = title }) { Text("Buka ›") }
                    }
                }
            }
        } else when (page) {
            "Data atlet" -> {
                item {
                    SurfaceCard {
                        Text("PARAMETER DASAR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = age, onValueChange = { age = it.filter(Char::isDigit).take(3) }, label = { Text("Usia (tahun)") }, singleLine = true)
                        OutlinedTextField(value = rest, onValueChange = { rest = it.filter(Char::isDigit).take(3) }, label = { Text("RHR manual (bpm, opsional)") }, singleLine = true)
                        OutlinedTextField(value = max, onValueChange = { max = it.filter(Char::isDigit).take(3) }, label = { Text("HRmax (bpm, opsional)") }, singleLine = true)
                        Text("Kosongkan nilai yang tidak diketahui. Nilai kosong tidak diperlakukan sebagai nol oleh antarmuka.", color = Muted, fontSize = 11.sp)
                        Button(onClick = { onSaveProfile(age.toIntOrNull() ?: 0, rest.toDoubleOrNull() ?: 0.0, max.toDoubleOrNull() ?: 0.0, goal) }) { Text("Simpan data atlet") }
                    }
                }
            }
            "Ambang & zona" -> {
                item {
                    SurfaceCard {
                        Text("AMBANG LATIHAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        OutlinedTextField(value = lthr, onValueChange = { lthr = it.filter(Char::isDigit).take(3) }, label = { Text("LTHR manual (bpm)") }, singleLine = true)
                        OutlinedTextField(value = ltPace, onValueChange = { ltPace = it.take(12) }, label = { Text("LTPace (mm:ss/km)") }, singleLine = true)
                        Text("Isi hanya jika memiliki hasil tes atau estimasi yang cukup tepercaya. Kosongkan untuk memakai estimasi otomatis.", color = Muted, fontSize = 11.sp)
                        Button(onClick = {
                            settings = settings.copy(athlete = AthleteSettings(lthr.toIntOrNull() ?: 0, ltPace.trim()))
                            onSaveSettings(settings)
                        }) { Text("Simpan ambang") }
                    }
                }
            }
            "Sasaran" -> {
                item {
                    SurfaceCard {
                        Text("TARGET UTAMA", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("5K", "10K", "Half", "Marathon", "Umum").forEach { item ->
                                FilterChip(selected = goal == item, onClick = { goal = item }, label = { Text(item) })
                            }
                        }
                        OutlinedTextField(value = settings.race, onValueChange = { settings = settings.copy(race = it.take(10)) }, label = { Text("Tanggal lomba (YYYY-MM-DD, opsional)") }, singleLine = true)
                        OutlinedTextField(value = settings.raceGoal, onValueChange = { settings = settings.copy(raceGoal = it.take(8)) }, label = { Text("Target waktu (opsional)") }, singleLine = true)
                        Button(onClick = {
                            settings = settings.copy(goal = goal)
                            onSaveProfile(age.toIntOrNull() ?: 0, rest.toDoubleOrNull() ?: 0.0, max.toDoubleOrNull() ?: 0.0, goal)
                            onSaveSettings(settings)
                        }) { Text("Simpan sasaran") }
                    }
                }
            }
            "Rencana mingguan" -> {
                item {
                    SurfaceCard {
                        Text("JADWAL MINGGUAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf(1 to "Senin", 2 to "Selasa", 3 to "Rabu", 4 to "Kamis", 5 to "Jumat", 6 to "Sabtu", 0 to "Minggu").forEach { (day, label) ->
                            val selected = settings.plan[day] ?: PlanDay("auto", 0.0)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf("auto", "easy", "quality", "long", "rest").forEach { type ->
                                        FilterChip(selected = selected.t == type, onClick = {
                                            settings = settings.copy(plan = settings.plan + (day to selected.copy(t = type)))
                                        }, label = { Text(when(type) { "auto" -> "Auto"; "easy" -> "Santai"; "quality" -> "Kualitas"; "long" -> "Jauh"; else -> "Rest" }) })
                                    }
                                }
                            }
                            Divider(color = Surface)
                        }
                        Button(onClick = { onSaveSettings(settings) }) { Text("Simpan rencana") }
                    }
                }
            }
            "Pendampingan" -> {
                item {
                    SurfaceCard {
                        Text("GAYA COACH", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf("conservative" to "Konservatif", "moderate" to "Moderat", "aggressive" to "Agresif").forEach { (value, label) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = settings.style == value, onClick = { settings = settings.copy(style = value) })
                                Column {
                                    Text(label, fontWeight = FontWeight.SemiBold)
                                    Text(when(value) { "conservative" -> "Lebih mengutamakan pemulihan dan kenaikan beban bertahap."; "moderate" -> "Menyeimbangkan stimulus dan pemulihan."; else -> "Lebih menantang, perlu toleransi beban dan pemulihan yang baik." }, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                        Button(onClick = { onSaveSettings(settings) }) { Text("Simpan preferensi") }
                    }
                }
            }
            "Lari-jalan" -> {
                item {
                    SurfaceCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Pola lari-jalan", fontWeight = FontWeight.SemiBold)
                                Text("Coach dapat menyusun sesi interval lari dan jalan.", color = Muted, fontSize = 11.sp)
                            }
                            Switch(checked = settings.rw.on, onCheckedChange = { settings = settings.copy(rw = settings.rw.copy(on = it)) })
                        }
                        OutlinedTextField(value = runSecs, onValueChange = { runSecs = it.filter(Char::isDigit).take(4) }, label = { Text("Durasi lari (detik)") }, singleLine = true)
                        OutlinedTextField(value = walkSecs, onValueChange = { walkSecs = it.filter(Char::isDigit).take(4) }, label = { Text("Durasi jalan (detik)") }, singleLine = true)
                        Button(onClick = {
                            val run = (runSecs.toIntOrNull() ?: 225).coerceIn(30, 1800)
                            val walk = (walkSecs.toIntOrNull() ?: 30).coerceIn(10, 600)
                            settings = settings.copy(rw = RunWalkSettings(settings.rw.on, run, walk))
                            onSaveSettings(settings)
                        }) { Text("Simpan pola") }
                    }
                }
            }
            "Perangkat & sinkronisasi" -> {
                item {
                    SurfaceCard {
                        Text(if (s?.synced == true) "Huawei Watch terdeteksi" else "Jam belum tersinkron", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${s?.overview?.workouts ?: 0} aktivitas · ${s?.overview?.hrSamples ?: 0} sampel HR · ${s?.overview?.hrvReadings ?: 0} pengukuran HRV", color = Muted, fontSize = 12.sp)
                        Button(onClick = onPair) { Text("Kelola koneksi jam") }
                    }
                }
                item {
                    SurfaceCard {
                        Text("INTERVALS.ICU", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(if (s?.intervalsConnected == true) "Akun terhubung" else "Hubungkan sumber latihan", fontWeight = FontWeight.Bold)
                        Text("Kunci API disimpan terenkripsi di Android Keystore. Masukkan kunci baru hanya untuk mengganti.", color = Muted, fontSize = 11.sp)
                        OutlinedTextField(value = intervalsKey, onValueChange = { intervalsKey = it }, label = { Text("API key Intervals.icu") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Button(onClick = { if (intervalsKey.isNotBlank()) { val key = intervalsKey; intervalsKey = ""; onSyncIntervals(key) } }, enabled = intervalsKey.isNotBlank()) { Text("Simpan & sinkronkan") }
                    }
                }
                item {
                    SurfaceCard {
                        Text("PERANGKAT TERDETEKSI", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (s?.overview?.devices.isNullOrEmpty()) Text("Belum ada perangkat tersimpan.", color = Muted)
                        s?.overview?.devices.orEmpty().forEach { Text("${it.name} · ${it.model ?: "Huawei"}", fontWeight = FontWeight.SemiBold) }
                        TextButton(onClick = onSettings) { Text("Pengaturan perangkat dan aplikasi") }
                    }
                }
            }
            "Cuaca & lokasi" -> item { ClimateLocationScreen(s?.climateProfile, s?.savedClimateLocation, loadClimate) }
            "Data & privasi" -> {
                item {
                    SurfaceCard {
                        Text("PENYIMPANAN LOKAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Aktivitas, ringkasan stream, wellness, dan konteks cuaca disimpan di perangkat agar riwayat tidak bertambah ganda saat sinkronisasi diulang.", fontSize = 12.sp)
                        Text("API key Intervals.icu disimpan terenkripsi menggunakan Android Keystore.", color = Muted, fontSize = 12.sp)
                        Text("Sumber: Huawei Watch dan Intervals.icu. RunNow tidak merekam aktivitas GPS dari ponsel pada alur ini.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
            else -> item { Text("Bagian belum tersedia.", color = Muted) }
        }
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
internal fun SurfaceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(22.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
