package com.runnow.app.data

import com.runnow.data.KeyValueStore
import java.io.File
import java.io.IOException

/**
 * Penyimpanan kunci-nilai berupa berkas teks di [dir] (padanan localStorage). Satu kunci = satu berkas JSON;
 * tulis lewat berkas sementara lalu diganti nama supaya tidak ada berkas setengah tertulis bila aplikasi dimatikan.
 * BELUM DIUJI di perangkat (modul :data hanya dites dengan penyimpanan palsu).
 */
class FileKeyValueStore(private val dir: File) : KeyValueStore {
    init {
        dir.mkdirs()
    }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    @Synchronized
    override fun read(key: String): String? =
        try {
            file(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            null
        }

    @Synchronized
    override fun write(key: String, value: String) {
        val f = file(key)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(value, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }

    @Synchronized
    override fun remove(key: String) {
        file(key).delete()
    }
}
