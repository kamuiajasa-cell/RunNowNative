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
import androidx.compose.foundation.verticalScroll
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
            onboardingDone = getSharedPreferences("native_ui", MODE_PRIVATE).getBoolean("onboarding_done", false),
            onFinishOnboarding = { getSharedPreferences("native_ui", MODE_PRIVATE).edit().putBoolean("onboarding_done", true).apply() },
            onPair = { startActivity(Intent(this, PairingActivity::class.java)) },
            onSettings = { startActivity(Intent(this, PairingActivity::class.java)) },
            onSaveProfile = { age, rest, max, goal -> getSharedPreferences("native_athlete", MODE_PRIVATE).edit().putInt("age", age).putFloat("resting_hr", rest.toFloat()).putFloat("max_hr", max.toFloat()).putString("goal", goal).apply(); NativeSettingsStore(this).save(NativeSettingsStore(this).load().copy(goal = goal)) },
            onSaveSettings = { settings -> NativeSettingsStore(this).save(settings) },
            onExport = { acts ->
                val csv = buildString {
                    appendLine("tanggal,jenis,nama,jarak_km,durasi_menit,pace_menit_per_km,hr_rata_rata,hr_maks,beban")
                    acts.sortedBy { it.start }.forEach { act ->
                        val date = Instant.ofEpochMilli(act.start).atZone(ZoneId.systemDefault()).toLocalDate()
                        val kind = when (act.kind) { ActKind.RUN -> "lari"; ActKind.WALK -> "jalan"; else -> "lainnya" }
                        val pace = if (act.km > 0) String.format(Locale.US, "%.2f", act.min / act.km) else ""
                        val safeName = act.name.replace("\"", "\"\"")
                        appendLine("$date,$kind,\"$safeName\",${act.km},${act.min},$pace,${act.avgHr},${act.maxHr},${act.load}")
                    }
                }
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_TEXT, csv); putExtra(Intent.EXTRA_SUBJECT, "RunNow - riwayat aktivitas") }
                startActivity(Intent.createChooser(send, "Bagikan ekspor aktivitas"))
            },
            loadClimate = { location -> NativeDataSource(this).loadClimate(location) },
            loadActivityPoints = { act -> NativeDataSource(this).loadActivityPoints(act) },
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
        val nativeSettings = NativeSettingsStore(activity).load()
        val ltParts = nativeSettings.athlete.ltPace.split(":")
        val ltPaceSeconds = if (ltParts.size == 2) (ltParts[0].toIntOrNull() ?: 0) * 60 + (ltParts[1].toIntOrNull() ?: 0) else 0
        val profile = Profile(
            rest = restingHr, max = maxHr, age = age, restManual = restingHr > 0,
            lthrC = nativeSettings.athlete.lthr, ltpC = ltPaceSeconds,
        )
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
        var snap = data.snapshot.copy(settings = nativeSettings.copy(goal = goal))
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

     suspend fun loadActivityPoints(act: Act): List<WatchWorkoutPoint> = withContext(Dispatchers.IO) {
        val id = act.icuId.orEmpty()
        if (id.startsWith("hw")) {
            val workoutId = id.removePrefix("hw").toLongOrNull() ?: return@withContext emptyList()
            return@withContext WatchData(activity).workoutPoints(workoutId)
        }
        val key = com.runnow.data.IntervalsCredentialStore(activity).read()
        if (key.isNullOrBlank() || id.isBlank()) return@withContext emptyList()
        val stream = com.runnow.data.IntervalsIcuClient(key).activityStream(id) ?: return@withContext emptyList()
        stream.time.indices.mapNotNull { i ->
            val t = stream.time[i]
            if (!t.isFinite()) return@mapNotNull null
            val speed = if (i > 0) {
                val dt = stream.time[i] - stream.time[i - 1]
                val dd = stream.distance[i] - stream.distance[i - 1]
                (dd / dt).takeIf { dt > 0 && it.isFinite() && it in 0.0..15.0 }
            } else null
            WatchWorkoutPoint(
                tsSec = act.start / 1000L + t.toLong(),
                lat = null, lon = null, alt = null,
                hr = stream.heartrate?.getOrNull(i)?.takeIf { it.isFinite() && it > 0 }?.toInt(),
                speed = speed, cadence = null,
            )
        }
    }

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
private fun RunNowNativeApp(onboardingDone: Boolean, onFinishOnboarding: () -> Unit, onPair: () -> Unit, onSettings: () -> Unit, onExport: (List<Act>) -> Unit, onSaveProfile: (Int, Double, Double, String) -> Unit, onSaveSettings: (Settings) -> Unit, loadClimate: suspend (ClimateLocation) -> WeatherBundle, loadActivityPoints: suspend (Act) -> List<WatchWorkoutPoint>, load: suspend () -> NativeState, syncIntervals: suspend (String) -> NativeState) {
    var onboardingComplete by remember { mutableStateOf(onboardingDone) }
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
        if (!onboardingComplete) {
            OnboardingScreen(onPair = { onPair() }, onContinue = { onFinishOnboarding(); onboardingComplete = true })
            return@MaterialTheme
        }
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
                    1 -> HistoryScreen(s, loadActivityPoints)
                    2 -> AnalysisScreen(s)
                    else -> ProfileScreen(s, onPair, onSettings, onSaveSettings, loadClimate, onExport = { onExport(s?.acts.orEmpty()) }, onSyncIntervals = { key ->
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
private fun OnboardingScreen(onPair: () -> Unit, onContinue: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("Lari dengan ritmemu", "Data dari perangkat", "Coach yang memahami konteks")
    val descriptions = listOf(
        "RunNow membantu membaca latihan, pemulihan, dan konsistensi. Fokusnya kesehatan dan progres yang bisa diulang, bukan memaksa pace setiap hari.",
        "Riwayat dan wellness dibaca dari Huawei Watch. Intervals.icu bisa ditambahkan sebagai sumber latihan. Aplikasi tidak merekam GPS dari ponsel pada alur ini.",
        "Rekomendasi menggabungkan riwayat latihan, beban, sinyal tubuh, dan cuaca bila tersedia. Angka yang tidak ada akan ditampilkan kosong, bukan ditebak."
    )
    Column(Modifier.fillMaxSize().background(Surface).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("RUN NOW", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(Modifier.weight(1f).height(4.dp).background(if (index <= page) Accent else Color(0xFFE0E5DE), RoundedCornerShape(8.dp)))
                }
            }
            Text("0${page + 1} / 03", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(titles[page], color = Ink, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text(descriptions[page], color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
            SurfaceCard {
                Text(when(page) { 0 -> "HARI INI · RIWAYAT · ANALISIS · PROFIL"; 1 -> "HUAWEI WATCH + INTERVALS.ICU"; else -> "DATA ASLI · PRIVASI · KONTEKS" }, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(when(page) { 0 -> "Satu tempat untuk melihat kondisi dan arah latihan."; 1 -> "Sinkronisasi pertama dilakukan setelah jam terhubung."; else -> "Coach memberi arahan, bukan diagnosis medis." }, color = Muted, fontSize = 12.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page < 2) {
                Button(onClick = { page++ }, modifier = Modifier.fillMaxWidth()) { Text("Lanjut") }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Lewati pengenalan") }
            } else {
                Button(onClick = { onPair(); onContinue() }, modifier = Modifier.fillMaxWidth()) { Text("Hubungkan Huawei Watch") }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Masuk tanpa menghubungkan sekarang") }
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
                s?.result?.let { result ->
                    Text(coachLine(result.recommendation, result.athlete.formLabel), color = Muted, fontSize = 13.sp)
                }
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
                val weekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                val week = s?.acts.orEmpty().filter {
                    val date = Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate()
                    !date.isBefore(weekStart) && date.isBefore(weekStart.plusDays(7))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Metric("Jarak", String.format(Locale.US, "%.1f km", week.sumOf { it.km }))
                    Metric("Sesi", week.size.toString())
                    Metric("Durasi", "${week.sumOf { it.min }.toInt() / 60} jam")
                }
            }
        }
        item {
            SurfaceCard {
                Text("AKTIVITAS TERAKHIR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                val last = s?.acts?.maxByOrNull { it.start }
                if (last == null) Text("Belum ada aktivitas tersimpan.", color = Muted)
                else ActivityRow(last)
            }
        }
        item { DataStatus(s) }
    }
}

@Composable
private fun HistoryScreen(s: NativeState?, loadActivityPoints: suspend (Act) -> List<WatchWorkoutPoint>) {
    var period by remember { mutableStateOf("Bulan ini") }
    var selected by remember { mutableStateOf<Act?>(null) }
    val today = LocalDate.now()
    val all = s?.acts.orEmpty()
    val filtered = all.filter { act ->
        val date = Instant.ofEpochMilli(act.start).atZone(ZoneId.systemDefault()).toLocalDate()
        when (period) {
            "Minggu ini" -> !date.isBefore(today.with(java.time.DayOfWeek.MONDAY))
            "Bulan ini" -> date.year == today.year && date.month == today.month
            else -> true
        }
    }
    val km = filtered.sumOf { it.km }
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
                    Metric("Durasi", "${filtered.sumOf { it.min }.toInt() / 60}j ${filtered.sumOf { it.min }.toInt() % 60}m")
                }
            }
        }
        if (filtered.isEmpty()) item {
            SurfaceCard {
                Text("Belum ada aktivitas pada periode ini", fontWeight = FontWeight.SemiBold)
                Text("Aktivitas akan muncul setelah disinkronkan dari jam atau Intervals.icu.", color = Muted, fontSize = 12.sp)
            }
        }
        items(filtered, key = { it.lid }) { act ->
            SurfaceCard {
                ActivityRow(act)
                TextButton(onClick = { selected = act }) { Text("Lihat analisis aktivitas") }
            }
        }
    }
    selected?.let { act ->
        val review = s?.reviews?.get(act.start)
        val climate = s?.climateContexts?.get(act.start)
        val reserveRest = s?.result?.profile?.rest ?: 0.0
        val reserveMax = s?.result?.profile?.max?.takeIf { it > 0 } ?: s?.age?.takeIf { it > 0 }?.let { 208.0 - 0.7 * it } ?: 0.0
        var activityPoints by remember(act.lid) { mutableStateOf<List<WatchWorkoutPoint>>(emptyList()) }
        var activityLoading by remember(act.lid) { mutableStateOf(false) }
        var activityError by remember(act.lid) { mutableStateOf<String?>(null) }
        LaunchedEffect(act.lid) {
            activityLoading = true
            try { activityPoints = loadActivityPoints(act) } catch (e: Exception) { activityError = e.message ?: "Gagal membaca stream" }
            activityLoading = false
        }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(act.name.ifBlank { if (act.kind == ActKind.RUN) "Lari" else if (act.kind == ActKind.WALK) "Jalan" else "Aktivitas" }) },
            text = {
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityRow(act)
                    ActivityStreamSummary(activityPoints, activityLoading, activityError, reserveRest, reserveMax)
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
                        s?.climateAdvice?.get(act.start)?.let { Text(it.message, fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun ActivityStreamSummary(points: List<WatchWorkoutPoint>, loading: Boolean, error: String?, restingHr: Double, maxHr: Double) {
    Divider()
    Text("REKAMAN AKTIVITAS", fontWeight = FontWeight.SemiBold)
    if (loading) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Membaca stream aktivitas…", color = Muted, fontSize = 12.sp)
        }
    } else if (points.isEmpty()) {
        Text(error ?: "Sumber data tidak menyediakan titik stream untuk sesi ini. Ringkasan aktivitas tetap tersedia.", color = Muted, fontSize = 12.sp)
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
            if (restingHr > 0 && maxHr > restingHr) HeartRateZones(hr, restingHr, maxHr)
        } else Text("Rekaman detak jantung tidak cukup untuk membuat grafik.", color = Muted, fontSize = 12.sp)
        PaceStreamChart(points)
        averagePaceSecondsPerKm(points)?.let { seconds ->
            Metric("Pace stream rata-rata", String.format(Locale.US, "%d:%02d/km", seconds / 60, seconds % 60))
        }
        val splits = streamPaceSplits(points)
        if (splits.isNotEmpty()) {
            Divider()
            Text("SPLIT PER KM", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            splits.forEachIndexed { index, seconds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("KM ${index + 1}", fontSize = 12.sp)
                    Text(String.format(Locale.US, "%d:%02d /km", seconds / 60, seconds % 60), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text("Split dihitung dari speed atau GPS yang valid; jeda dan lonjakan data diabaikan.", color = Muted, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("Cadence rata-rata", cadence.takeIf { it.isNotEmpty() }?.let { "${it.average().toInt()} spm" } ?: "—")
            Metric("Elevasi", if (altitude.size >= 2) "${altitude.minOrNull()!!.toInt()}–${altitude.maxOrNull()!!.toInt()} m" else "—")
        }
        Text("Grafik memakai sampel HR asli yang tersimpan; titik kosong tidak diinterpolasi.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun HeartRateZones(values: List<Int>, restingHr: Double, maxHr: Double) {
    val zones = IntArray(5)
    values.forEach { hr ->
        val reserve = (hr - restingHr) / (maxHr - restingHr)
        val index = when { reserve < .60 -> 0; reserve < .70 -> 1; reserve < .80 -> 2; reserve < .90 -> 3; else -> 4 }
        zones[index]++
    }
    val total = zones.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("DISTRIBUSI ZONA HRR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        zones.forEachIndexed { index, count ->
            val pct = count * 100f / total
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Z${index + 1}", modifier = Modifier.width(22.dp), fontSize = 11.sp, color = Muted)
                Box(Modifier.weight(1f).height(7.dp).background(Color(0xFFE8ECE6), RoundedCornerShape(8.dp))) {
                    Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(Ink, RoundedCornerShape(8.dp)))
                }
                Text("${pct.toInt()}%", modifier = Modifier.width(34.dp), fontSize = 11.sp, color = Ink)
            }
        }
        Text("Persentase memakai HRR dari RHR dan HRmax profil. Sampel HR yang tidak valid diabaikan.", color = Muted, fontSize = 10.sp)
    }
}

private fun averagePaceSecondsPerKm(points: List<WatchWorkoutPoint>): Int? {
    var distanceMeters = 0.0
    var durationSeconds = 0.0
    points.zipWithNext().forEach { (a, b) ->
        val dt = b.tsSec - a.tsSec
        val speed = (b.speed ?: a.speed)?.takeIf { it.isFinite() && it in 0.5..12.0 }
        if (dt in 1..20 && speed != null) {
            distanceMeters += speed * dt
            durationSeconds += dt
        }
    }
    if (distanceMeters <= 0.0 || durationSeconds <= 0.0) return null
    return (durationSeconds / distanceMeters * 1000.0).toInt().takeIf { it in 120..1200 }
}
private fun streamPaceSplits(points: List<WatchWorkoutPoint>): List<Int> {
    if (points.size < 2) return emptyList()
    fun distance(a: WatchWorkoutPoint, b: WatchWorkoutPoint): Double {
        val lat1 = Math.toRadians(a.lat!!); val lat2 = Math.toRadians(b.lat!!)
        val dLat = lat2 - lat1; val dLon = Math.toRadians(b.lon!! - a.lon!!)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2).let { it * it }
        return 6371000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
    }
    val first = points.first().tsSec
    var cumulative = 0.0
    var previous = points.first()
    val splits = mutableListOf<Int>()
    var nextKm = 1000.0
    var lastBoundaryElapsed = 0.0
    for (point in points.drop(1)) {
        val dt = point.tsSec - previous.tsSec
        val gpsSegment = if (previous.lat != null && previous.lon != null && point.lat != null && point.lon != null) distance(previous, point) else null
        val speedSegment = (point.speed ?: previous.speed)?.takeIf { it.isFinite() && it in 0.5..12.0 }?.times(dt.toDouble())
        val segment = speedSegment ?: gpsSegment ?: 0.0
        if (dt in 1..20 && segment in 0.5..(dt * 12.0)) {
            val before = cumulative
            cumulative += segment
            while (cumulative >= nextKm) {
                val fraction = ((nextKm - before) / segment).coerceIn(0.0, 1.0)
                val elapsed = (previous.tsSec - first) + dt * fraction
                val splitSeconds = (elapsed - lastBoundaryElapsed).toInt()
                if (splitSeconds in 120..1200) splits.add(splitSeconds)
                lastBoundaryElapsed = elapsed
                nextKm += 1000.0
            }
        }
        previous = point
    }
    return splits
}

@Composable
private fun PaceStreamChart(points: List<WatchWorkoutPoint>) {
    val samples = points.mapNotNull { p ->
        p.speed?.takeIf { it in 0.5..12.0 }?.let { p.tsSec to (60.0 / it).toFloat() }?.takeIf { it.second in 2.5f..20f }
    }
    if (samples.size < 2) return
    val min = samples.minOf { it.second }.coerceAtLeast(2.5f)
    val max = samples.maxOf { it.second }.coerceAtMost(20f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("PACE · MENIT/KM", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxWidth().height(100.dp)) {
            val span = (max - min).coerceAtLeast(.1f)
            val left = 8f; val top = 8f; val width = size.width - 16f; val height = size.height - 16f
            for (i in 0..3) {
                val y = top + height * i / 3f
                drawLine(Color(0xFFE6EBE4), Offset(left, y), Offset(left + width, y), 1.dp.toPx())
            }
            val first = samples.first().first
            val duration = (samples.last().first - first).coerceAtLeast(1L).toFloat()
            val path = androidx.compose.ui.graphics.Path()
            samples.forEachIndexed { index, sample ->
                val x = left + width * ((sample.first - first) / duration)
                val y = top + height * ((sample.second - min) / span)
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
private fun HeartRateStreamChart(points: List<WatchWorkoutPoint>) {
    val samples = points.mapNotNull { p -> p.hr?.takeIf { it in 30..240 }?.let { p.tsSec to it.toFloat() } }
    if (samples.size < 2) return
    val min = (samples.minOf { it.second } - 5f).coerceAtLeast(30f)
    val max = (samples.maxOf { it.second } + 5f).coerceAtMost(240f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("DETAK JANTUNG · BPM", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
    val sections = listOf("Ikhtisar", "Tren", "Beban", "Performa", "Wellness")
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
    val months = (5 downTo 0).map { back ->
        val month = LocalDate.now().withDayOfMonth(1).minusMonths(back.toLong())
        val items = runs.filter { act ->
            val d = Instant.ofEpochMilli(act.start).atZone(ZoneId.systemDefault()).toLocalDate()
            d.year == month.year && d.month == month.month
        }
        val distance = items.sumOf { it.km }
        val minutes = items.sumOf { it.min }
        val hrSamples = items.filter { it.avgHr > 0 }
        val avgHr = hrSamples.takeIf { it.isNotEmpty() }?.let { list -> list.sumOf { it.avgHr * it.min } / list.sumOf { it.min }.coerceAtLeast(1.0) }
        Triple(month, items, Triple(distance, minutes, avgHr))
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
            "Tren" -> {
                item {
                    SurfaceCard {
                        Text("TREN 6 BULAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        val maxKm = months.maxOfOrNull { it.third.first }?.coerceAtLeast(1.0) ?: 1.0
                        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            months.forEach { (month, items, metrics) ->
                                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                    Text(String.format(Locale.US, "%.0f", metrics.first), fontSize = 9.sp, color = Muted)
                                    Box(Modifier.fillMaxWidth().height((metrics.first / maxKm * 100.0).coerceAtLeast(4.0).dp).background(if (month.month == LocalDate.now().month && month.year == LocalDate.now().year) Accent else Ink, RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp)))
                                    Text(month.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale("id")), fontSize = 9.sp, color = Muted)
                                }
                            }
                        }
                        Text("Jarak lari per bulan (km). Bulan berjalan belum lengkap.", color = Muted, fontSize = 11.sp)
                    }
                }
                item {
                    SurfaceCard {
                        Text("RINCIAN BULANAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        months.asReversed().forEach { (month, items, metrics) ->
                            val pace = if (metrics.first > 0) (metrics.second / metrics.first * 60.0).toInt() else 0
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale("id")) + " " + month.year, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(String.format(Locale.US, "%.1f km · %d sesi", metrics.first, items.size), fontSize = 12.sp)
                                }
                                Text((if (pace > 0) String.format(Locale.US, "Pace rata-rata %d:%02d/km", pace / 60, pace % 60) else "Belum ada pace") +
                                    (metrics.third?.let { " · HR rata-rata ${it.toInt()} bpm" } ?: ""), color = Muted, fontSize = 11.sp)
                            }
                            Divider(color = Surface)
                        }
                    }
                }
                item {
                    SurfaceCard {
                        Text("KONSISTENSI MINGGUAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            weeks.forEachIndexed { i, value ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.fillMaxWidth().height((value * 4 + 4).coerceAtMost(110f).dp).background(if (i == 7) Accent else Ink, RoundedCornerShape(4.dp)))
                                    Text("${i + 1}", color = Muted, fontSize = 9.sp)
                                }
                            }
                        }
                        Text("Delapan minggu terakhir, Senin–Minggu. Minggu berjalan belum lengkap.", color = Muted, fontSize = 11.sp)
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
    onExport: () -> Unit,
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
                        Button(onClick = onExport, enabled = !s?.acts.isNullOrEmpty()) { Text("Ekspor riwayat aktivitas · CSV") }
                        Text("Ekspor berisi ringkasan aktivitas, bukan rute GPS atau stream mentah.", color = Muted, fontSize = 11.sp)
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
        Text(when { s?.synced == true -> "Database Huawei tersedia"; !s?.acts.isNullOrEmpty() -> "Riwayat aktivitas tersimpan"; else -> "Menunggu sinkronisasi pertama" }, fontWeight = FontWeight.SemiBold)
        Text(s?.overview?.lastSampleSec?.let { "Sampel terakhir: " + DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("id")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochSecond(it)) } ?: "Belum ada sampel harian",
            color = Muted, fontSize = 12.sp)
        s?.error?.takeIf { it.isNotBlank() }?.let {
            Text("Catatan pembacaan database Huawei: $it", color = Color(0xFFB3261E), fontSize = 11.sp)
        }
        s?.error?.let { Text("Catatan pembacaan: $it", color = Color(0xFFB3261E), fontSize = 12.sp) }
    }
}

@Composable
private fun ActivityRow(act: Act) {
    val date = Instant.ofEpochMilli(act.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm", Locale("id")))
    val pace = if (act.km > 0 && act.min > 0) {
        val sec = (act.min * 60 / act.km).toInt()
        String.format(Locale.US, "%d:%02d/km", sec / 60, sec % 60)
    } else "—"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(act.name.ifBlank { when(act.kind) { ActKind.RUN -> "Lari"; ActKind.WALK -> "Jalan"; else -> "Aktivitas" } }, fontWeight = FontWeight.SemiBold)
            Text(date + " · " + if (act.icuId?.startsWith("hw") == true) "Huawei" else "Intervals.icu", color = Muted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(String.format(Locale.US, "%.2f km", act.km), fontWeight = FontWeight.Bold)
            Text("$pace · ${act.min.toInt()} mnt", color = Muted, fontSize = 11.sp)
        }
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
