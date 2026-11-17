package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ClimCtx
import com.runnow.domain.GeoLoc
import com.runnow.domain.GpsPick
import com.runnow.domain.Profile
import com.runnow.domain.Settings
import com.runnow.domain.Snapshot
import com.runnow.domain.StreamSummary
import com.runnow.domain.Wellness
import com.runnow.domain.WxData
import java.time.ZoneId

/**
 * Isi `state` di HTML yang disimpan ke penyimpanan (KEY_STORE "runsense_auto_v2"): lari, wellness, jenis sesi
 * yang dikonfirmasi, ringkasan stream, profil tubuh, dan waktu sinkron. Tidak berubah di tempat; tiap operasi
 * mengembalikan salinan baru.
 */
data class AppData(
    val profile: Profile = Profile(),
    val acts: List<Act> = emptyList(),
    val wellness: List<Wellness> = emptyList(),
    /** state.vx: jenis sesi yang dikonfirmasi, kunci = waktu mulai (ms). */
    val vx: Map<Long, String> = emptyMap(),
    /** state.sx: ringkasan stream per icuId; urutan masuk dipertahankan (yang tertua dibuang lebih dulu). */
    val sx: Map<String, StreamSummary> = emptyMap(),
    val icuLast: Long = 0L,
    val icuName: String = "",
    val hwLast: Long = 0L,
)

/** Snapshot untuk modul :domain. [settings] dari pengaturan pengguna (Fase C). */
fun AppData.snapshot(nowMs: Long, zone: ZoneId, settings: Settings = Settings()): Snapshot =
    Snapshot(profile, acts, wellness, vx, nowMs, zone, settings, sx)

/** CL di HTML (kunci "runsense_clim"): lokasi, konteks cuaca per lari, cache cuaca, dan GPS ringkas per lari. */
data class ClimateState(
    val loc: GeoLoc? = null,
    val ctx: Map<Long, ClimCtx> = emptyMap(),
    val wx: Map<String, WxData> = emptyMap(),
    val gps: Map<Long, GpsPick> = emptyMap(),
)

/** Satu hari wellness dari jam. Bilangan yang bukan angka positif diperlakukan "kosong" (tidak menimpa). */
data class WatchWellnessIn(
    val id: String,
    val restingHR: Double? = null,
    val hrv: Double? = null,
    /** True bila hrvSrc == "spot". */
    val hrvSpot: Boolean = false,
    val sleepSecs: Double? = null,
    val sleepScore: Double? = null,
    val steps: Double? = null,
    val spO2: Double? = null,
    val stress: Double? = null,
    val stressMax: Double? = null,
    val stressNight: Double? = null,
)

/** Satu latihan dari jam. [pts] = [detik, hr, m/s, tinggi, kadens, lat, lon]; null bila tidak dikirim. */
data class WatchWorkoutIn(
    val start: Double,
    val kind: String?,
    val durSec: Double,
    val distM: Double = 0.0,
    val avgHr: Double = 0.0,
    val maxHr: Double = 0.0,
    val elevUp: Double = 0.0,
    val name: String = "",
    val iid: String? = null,
    val pts: List<DoubleArray>? = null,
)

/** `nativeImport` di HTML: `{wellness, workouts}`. */
data class WatchImportData(
    val wellness: List<WatchWellnessIn> = emptyList(),
    val workouts: List<WatchWorkoutIn> = emptyList(),
)
