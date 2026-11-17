package com.runnow.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.runnow.app.data.RunSenseGraph
import com.runnow.data.RunSenseRepository
import com.runnow.domain.ProcessResult
import com.runnow.domain.Settings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

enum class Phase { LOADING, SETUP, READY }

/** Semua yang dibaca layar. [result] = hasil processData (null bila belum ada data atau gagal dihitung). */
data class UiState(
    val phase: Phase = Phase.LOADING,
    val result: ProcessResult? = null,
    val busy: Boolean = false,
    val message: String = "",
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val isDemo: Boolean = false,
    /** state.src: "" | "watch". */
    val source: String = "",
    val hasKey: Boolean = false,
    /** Naik tiap data berubah, supaya layar yang membaca repo langsung bisa ikut tersegarkan. */
    val rev: Int = 0,
)

/**
 * Satu-satunya pemegang [RunSenseRepository]. Repository tidak aman lintas-thread, jadi semua panggilannya
 * lewat satu thread ([io]). Perubahan data (`onChange`) menghitung ulang [ProcessResult] dan mengisi [ui].
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = UiPrefs(app)
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "runnow-repo") }
    private val io: CoroutineDispatcher = executor.asCoroutineDispatcher()

    val repo: RunSenseRepository = RunSenseGraph.create(app)

    private val _ui = MutableStateFlow(UiState(theme = prefs.theme))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /**
     * Pengaturan pengguna (rencana mingguan, race, gaya, panas). BELUM disimpan: persistensinya ikut Langkah 10
     * (layar Saya). Sampai itu, nilainya bawaan [Settings].
     */
    @Volatile
    var settings: Settings = Settings()
        private set

    private var refreshing = false
    private var started = false

    init {
        repo.onChange = { refresh() }
        viewModelScope.launch(io) {
            repo.restore()
            refresh()
            if (!started) {
                started = true
                autoSync()
            }
        }
    }

    // ---------- status ----------

    private fun refresh() {
        if (refreshing) return // process() bisa menulis profil -> onChange lagi
        refreshing = true
        try {
            val key = repo.apiKey
            val hasKey = !key.isNullOrEmpty()
            val hasData = repo.data.acts.isNotEmpty() || repo.data.wellness.isNotEmpty()
            val phase = if (!hasKey && repo.source != "watch" && !hasData) Phase.SETUP else Phase.READY
            val result = if (phase == Phase.READY) runCatching { repo.process(settings) }.getOrNull() else null
            _ui.update {
                it.copy(
                    phase = phase, result = result, isDemo = repo.isDemo,
                    source = repo.source, hasKey = hasKey, rev = it.rev + 1,
                )
            }
        } finally {
            refreshing = false
        }
    }

    private fun say(msg: String) = _ui.update { it.copy(message = msg) }

    private fun busy(on: Boolean) = _ui.update { it.copy(busy = on) }

    // ---------- tindakan ----------

    private suspend fun autoSync() {
        syncAll(silent = true)
    }

    /** sync() Intervals.icu (bila ada kunci) lalu pullWatch(), lalu putaran cocokkan cuaca (seperti HTML, jeda 800 ms). */
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

    /** Masuk dengan API key Intervals.icu, lalu sinkron. */
    fun connect(key: String) {
        val k = key.trim()
        if (k.isEmpty()) {
            say("Isi API key dulu")
            return
        }
        viewModelScope.launch(io) {
            repo.login(k)
            syncAll(silent = false)
        }
    }

    /** enterWatchMode() di HTML: hanya data jam. */
    fun enterWatchMode() {
        viewModelScope.launch(io) {
            repo.setSource("watch")
            busy(true)
            say("Ambil data jam…")
            try {
                val s = repo.pullWatch()
                if (s == null || s.wellness + s.workouts == 0) say("Belum ada data jam. Sambungkan jam di tab Jam, lalu coba lagi.")
            } finally {
                busy(false)
                refresh()
            }
        }
    }

    fun logout() {
        viewModelScope.launch(io) { repo.logout() }
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
