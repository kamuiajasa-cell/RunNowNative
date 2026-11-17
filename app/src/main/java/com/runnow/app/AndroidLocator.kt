package com.runnow.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.runnow.data.Locator
import com.runnow.domain.GeoLoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lokasi terakhir yang diketahui sistem (tanpa menyalakan GPS). Null bila izin lokasi belum diberikan atau belum ada
 * lokasi. Meminta izin adalah urusan layar (Fase C). BELUM DIUJI di perangkat.
 */
class AndroidLocator(private val context: Context) : Locator {
    @SuppressLint("MissingPermission")
    override suspend fun lastLocation(): GeoLoc? =
        withContext(Dispatchers.IO) {
            val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!coarse && !fine) return@withContext null
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@withContext null
            var best: Location? = null
            for (p in listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)) {
                try {
                    val l = lm.getLastKnownLocation(p) ?: continue
                    val b = best
                    if (b == null || l.time > b.time) best = l
                } catch (e: SecurityException) {
                } catch (e: IllegalArgumentException) {
                }
            }
            best?.let { GeoLoc(it.latitude, it.longitude, t = it.time) }
        }
}
