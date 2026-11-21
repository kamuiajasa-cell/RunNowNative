package com.runnow.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runnow.app.PlanNotifier
import com.runnow.app.data.FileKeyValueStore
import com.runnow.app.data.RunSenseGraph
import com.runnow.data.DemoData
import com.runnow.data.RunSenseRepository
import com.runnow.data.SettingsStore
import com.runnow.data.snapshot
import com.runnow.domain.Act
import com.runnow.domain.LegFlag
import com.runnow.domain.PlanDay
import com.runnow.domain.ProcessResult
import com.runnow.domain.Profile
import com.runnow.domain.RunWalkSettings
import com.runnow.domain.Settings
import com.runnow.domain.Snapshot
import com.runnow.domain.withProcessed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executors

enum class Phase { LOADING, SETUP, READY }

/** Semua yang dibaca layar. [result] = hasil processData, [snap] = Snapshot terproses untuk fungsi :domain (review, tipe, dll). */
data class UiState(
    val phase: Phase = Phase.LOADING,
    val result: ProcessResult? = null,
    val snap: Snapshot? = null,
    val settings: Settings = Settings(),
    /** Hari yang ditandai istirahat ("yyyy-MM-dd") atau "". */
    val restDay: String = "",
    val advanced: Boolean = false,
    val remindOn: Boolean = true,
    val remindRest: Boolean = true,
    val remindTime: String = "05:30",
    val busy: Boolean = false,
    val message: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val isDemo: Boolean = false,
    /** state.src: "" | "watch". */
    val source: String = "",
    val hasKey: Boolean = false,
    val rev: Int = 0,
)

/**
 * Satu-satunya pemegang [RunSenseRepository] dan [SettingsStore]. Repository tidak aman lintas-thread, jadi semua
 * panggilannya lewat satu thread ([io]). Perubahan data (`onChange`) menghitung ulang hasil dan mengisi [ui].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UiPrefs(app)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "runnow-repo") }
    private val io: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val zone: ZoneId = ZoneId.systemDefault()

    val repo: RunSenseRepository = RunSenseGraph.create(app)
    private val settingsStore = SettingsStore(FileKeyValueStore(File(app.filesDir, "runsense")))

    private val _ui = MutableStateFlow(
        UiState(theme = prefs.theme, remindOn = prefs.remindOn, remindRest = prefs.remindRest, remindTime = prefs.remindTime),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var settings: Settings = Settings()
    private var restDay: String = ""
    private var refreshing = false
    private var started = false

    init {
        repo.onChange = { refresh() }
        viewModelScope.launch(io) {
            settings = settingsStore.load()
            restDay = settingsStore.restDay
            repo.restore()
            refresh()
            if (!repo.isDemo) scheduleReminders()
            if (!started) {
                started = true
                syncAll(silent = true)
            }
        }
    }

    // ---------- status ----------

    private fun todayStr(): String = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate().toString()

    private fun refresh() {
        if (refreshing) return // process() bisa menulis profil -> onChange lagi
        refreshing = true
        try {
            val key = repo.apiKey
            val hasKey = !key.isNullOrEmpty()
            val hasData = repo.data.acts.isNotEmpty() || repo.data.wellness.isNotEmpty()
            val phase = if (!hasKey && repo.source != "watch" && !hasData) Phase.SETUP else Phase.READY
            var result: ProcessResult? = null
            var snap: Snapshot? = null
            if (phase == Phase.READY) {
                try {
                    result = repo.process(settings)
                    snap = repo.data.snapshot(System.currentTimeMillis(), zone, settings).withProcessed(result)
                } catch (e: Exception) {
                    result = null
                    snap = null
                }
            }
            _ui.update {
                it.copy(
                    phase = phase, result = result, snap = snap, settings = settings, restDay = restDay,
                    advanced = settingsStore.advanced, isDemo = repo.isDemo, source = repo.source,
                    hasKey = hasKey, rev = it.rev + 1,
                )
            }
        } finally {
            refreshing = false
        }
    }

    private fun say(msg: String) = _ui.update { it.copy(message = msg) }

    private fun busy(on: Boolean) = _ui.update { it.copy(busy = on) }

    // ---------- sinkron ----------

    /** sync() Intervals.icu, pullWatch(), autoHeat(), lalu putaran cocokkan cuaca (jeda 800 ms, seperti HTML). */
    private suspend fun syncAll(silent: Boolean) {
        busy(true)
        try {
            val key = repo.apiKey
            if (!key.isNullOrEmpty() && key != "demo") {
                val r = repo.sync(silent)
                if (r.message.isNotEmpty()) say(r.message)
            }
            if (repo.source == "watch" || !repo.apiKey.isNullOrEmpty()) {
                val s = repo.pullWatch()
                if (s != null && s.wellness + s.workouts > 0) say("Jam: ${s.workouts} latihan, ${s.wellness} hari wellness")
            }
            if (repo.autoHeat() && settings.heat != todayStr()) change { it.copy(heat = todayStr()) }
            var guard = 0
            while (guard++ < 20 && repo.climateMatch() > 0) delay(800)
        } catch (e: Exception) {
            say("Gagal: " + (e.message ?: e.toString()))
        } finally {
            busy(false)
            refresh()
        }
    }

    fun syncNow() {
        viewModelScope.launch(io) { syncAll(silent = false) }
    }

    fun connect(key: String, goal: String, style: String) {
        val k = key.trim()
        if (k.isEmpty()) {
            say("API Key wajib diisi")
            return
        }
        viewModelScope.launch(io) {
            change { it.copy(goal = goal, style = style) }
            repo.login(k)
            syncAll(silent = false)
            if (repo.data.acts.isEmpty() && repo.data.wellness.isEmpty()) {
                // Gagal sinkron: seperti connect() di HTML, kunci dibuang agar kembali ke layar setup.
                val msg = _ui.value.message.ifEmpty { "Gagal sinkron. Cek API key." }
                repo.logout()
                say(msg)
            }
        }
    }

    fun enterWatchMode(goal: String, style: String) {
        viewModelScope.launch(io) {
            change { it.copy(goal = goal, style = style) }
            repo.setSource("watch")
            busy(true)
            say("Ambil data jam…")
            try {
                val s = repo.pullWatch()
                if (s == null || s.wellness + s.workouts == 0) say("Belum ada data jam. Sambungkan jam lewat tombol Atur jam, lalu coba lagi.")
            } finally {
                busy(false)
                refresh()
            }
        }
    }

    /** enterDemo() di HTML: data contoh hanya di memori. */
    fun enterDemo(goal: String, style: String) {
        viewModelScope.launch(io) {
            val d = DemoData.create(System.currentTimeMillis(), zone, goal, style)
            settings = d.settings // tidak disimpan: mode demo
            repo.enterDemo(d.data)
        }
    }

    fun logout() {
        viewModelScope.launch(io) {
            val wasDemo = repo.isDemo
            repo.logout()
            if (wasDemo) settings = settingsStore.load() // buang rencana contoh
            refresh()
        }
    }

    // ---------- pengaturan ----------

    private fun change(f: (Settings) -> Settings) {
        settings = f(settings)
        if (!repo.isDemo) {
            settingsStore.save(settings)
            scheduleReminders()
        }
        refresh()
    }

    /** Jadwalkan ulang pengingat dari rencana minggu. Mati = kosongkan jadwal. Tidak dijalankan di mode demo. */
    private fun scheduleReminders() {
        val ctx = getApplication<Application>()
        val json = if (prefs.remindOn) {
            val t = runCatching { LocalTime.parse(prefs.remindTime) }.getOrDefault(LocalTime.of(5, 30))
            PlanReminders.build(settings, System.currentTimeMillis(), zone, t, prefs.remindRest)
        } else "[]"
        runCatching { PlanNotifier.set(ctx, json) }
    }

    fun setRemind(on: Boolean, rest: Boolean, time: String) {
        viewModelScope.launch(io) {
            prefs.remindOn = on
            prefs.remindRest = rest
            prefs.remindTime = time
            _ui.update { it.copy(remindOn = on, remindRest = rest, remindTime = time) }
            if (!repo.isDemo) scheduleReminders()
        }
    }

    private fun edit(f: (Settings) -> Settings) {
        viewModelScope.launch(io) { change(f) }
    }

    fun setGoal(g: String) = edit { it.copy(goal = g) }

    fun setStyle(s: String) = edit { it.copy(style = s) }

    /** setFlag() di HTML: 0 = OK, 1 = was-was, 2 = sakit. */
    fun setFlag(lv: Int) = edit { it.copy(flag = if (lv == 1 || lv == 2) LegFlag(lv, System.currentTimeMillis()) else null) }

    fun toggleHeat() = edit { it.copy(heat = if (it.heat == todayStr()) "" else todayStr()) }

    fun setPlanType(dow: Int, t: String) = edit { s ->
        val cur = s.plan[dow] ?: PlanDay("auto", 0.0)
        s.copy(plan = s.plan + (dow to cur.copy(t = t)))
    }

    fun setPlanKm(dow: Int, km: Double) = edit { s ->
        val cur = s.plan[dow] ?: PlanDay("auto", 0.0)
        s.copy(plan = s.plan + (dow to cur.copy(km = km.coerceIn(0.0, 60.0))))
    }

    fun setRace(date: String) = edit { it.copy(race = date) }

    fun setRaceX(dist: Double, goal: String) = edit { it.copy(raceDist = dist, raceGoal = goal.trim()) }

    fun setRunWalk(on: Boolean, run: Int, walk: Int) = edit { it.copy(rw = RunWalkSettings(on, run, walk)) }

    /** markRest() di HTML. */
    fun markRest(on: Boolean) {
        viewModelScope.launch(io) {
            restDay = if (on) todayStr() else ""
            settingsStore.restDay = restDay
            refresh()
        }
    }

    fun setAdvanced(on: Boolean) {
        viewModelScope.launch(io) {
            settingsStore.advanced = on
            refresh()
        }
    }

    /** saveProfile() di HTML. Nilai di luar rentang dibuang; RHR kosong = otomatis (rata-rata 28 hari). */
    fun saveProfile(age: String, rest: String, max: String, lthr: String, ltp: String) {
        viewModelScope.launch(io) {
            val p = repo.data.profile
            val a = (age.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 90.0).toInt()
            val rv = (rest.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 120.0)
            val mx = (max.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 230.0)
            val lv = lthr.toDoubleOrNull() ?: 0.0
            val m = Regex("^(\\d{1,2})[:,](\\d{2})$").find(ltp.trim())
            val lsec = if (m != null) m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt() else 0
            val ltpOk = m != null && m.groupValues[2].toInt() < 60 && lsec in 120..1200
            val np: Profile = p.copy(
                age = a,
                rest = if (rv > 0) rv else p.rest,
                restManual = rv > 0,
                max = mx,
                lthrC = if (lv in 100.0..220.0) Math.round(lv).toInt() else 0,
                ltpC = if (ltpOk) lsec else 0,
            )
            repo.setProfile(np)
        }
    }

    fun setSessionType(ms: Long, type: String) {
        viewModelScope.launch(io) { repo.setSessionType(ms, type) }
    }

    fun ensureStream(a: Act) {
        viewModelScope.launch(io) { repo.ensureStream(a) }
    }

    fun setTheme(mode: ThemeMode) {
        prefs.theme = mode
        _ui.update { it.copy(theme = mode) }
    }

    fun consumeMessage() = say("")

    override fun onCleared() {
        repo.onChange = null
        executor.shutdown()
        super.onCleared()
    }
}
