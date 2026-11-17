package com.runnow.data

import com.runnow.domain.GeoLoc

/** Kegagalan memanggil Intervals.icu. [kind] membedakan penolakan API key, kode HTTP, dan jaringan. */
class IcuException(val kind: Kind, val status: Int, message: String) : Exception(message) {
    enum class Kind { AUTH, HTTP, NETWORK }
}

/** Respons HTTP mentah. */
class HttpResponse(val status: Int, val body: String)

/** Klien HTTP GET. Implementasi Android memanggilnya di thread IO; melempar IOException bila jaringan gagal. */
interface HttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

/** Intervals.icu. [path] relatif terhadap https://intervals.icu/api/v1/ (mis. "athlete/0"). */
interface IcuApi {
    suspend fun get(path: String): JsonValue
}

/** Cuaca. Semua fungsi mengembalikan null bila gagal (bukan melempar), seperti wxGet di HTML. */
interface WeatherApi {
    suspend fun forecast(lat: Double, lon: Double): JsonValue?
    suspend fun archive(lat: Double, lon: Double, day: String): JsonValue?
    suspend fun geocode(name: String): GeoLoc?
    suspend fun currentTemp(lat: Double, lon: Double): Double?
}

/** Penyimpanan kunci-nilai (setara localStorage). Android: berkas di filesDir. */
interface KeyValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

fun interface Clock {
    fun now(): Long
}

/** Profil dari jam (hanya yang sudah dikonfigurasi): getHeartRateZonesStatus dan getUserProfile di HTML. */
data class WatchProfile(val maxHr: Int = 0, val restingHr: Int = 0, val birthYear: Int = 0)

/** Sumber data jam (modul huawei). Implementasi Android memanggil WatchData/WatchImport langsung. */
interface WatchSource {
    /** Isi `{wellness, workouts}` (null bila belum ada database jam). Dipanggil di thread IO. */
    suspend fun watchData(): WatchImportData?

    fun profile(): WatchProfile?
}

/** Lokasi perangkat terakhir (null bila izin ditolak atau tidak tersedia). */
interface Locator {
    suspend fun lastLocation(): GeoLoc?
}
