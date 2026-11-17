package com.runnow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.data.ClimateLocation
import com.runnow.data.WeatherBundle
import com.runnow.domain.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ClimateLocationScreen(
    profile: PersonalClimateProfile?,
    savedLocation: ClimateLocation?,
    onFetch: suspend (ClimateLocation) -> WeatherBundle,
) {
    var name by remember(savedLocation) { mutableStateOf(savedLocation?.name.orEmpty()) }
    var latitude by remember(savedLocation) { mutableStateOf(savedLocation?.latitude?.toString().orEmpty()) }
    var longitude by remember(savedLocation) { mutableStateOf(savedLocation?.longitude?.toString().orEmpty()) }
    var bundle by remember { mutableStateOf<WeatherBundle?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val localProfile = profile ?: PersonalClimateProfile(false, 0, 0, explanation = "Profil suhu personal belum tersedia.")
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            SurfaceCard {
                Text("LOKASI LATIHAN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Prakiraan cuaca untuk memilih waktu latihan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Masukkan koordinat lokasi yang biasa dipakai berlari. Data cuaca berasal dari Open-Meteo, bukan sensor jam.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama lokasi (opsional)") }, singleLine = true)
                OutlinedTextField(value = latitude, onValueChange = { latitude = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(12) }, label = { Text("Latitude") }, singleLine = true)
                OutlinedTextField(value = longitude, onValueChange = { longitude = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' }.take(12) }, label = { Text("Longitude") }, singleLine = true)
                Button(
                    enabled = !loading && latitude.toDoubleOrNull() in -90.0..90.0 && longitude.toDoubleOrNull() in -180.0..180.0,
                    onClick = {
                        val loc = ClimateLocation(latitude.toDouble(), longitude.toDouble(), name.ifBlank { null }, true)
                        scope.launch {
                            loading = true
                            error = null
                            try { bundle = onFetch(loc) }
                            catch (e: Exception) { error = e.message ?: "Prakiraan cuaca tidak dapat dimuat" }
                            finally { loading = false }
                        }
                    },
                ) { Text(if (loading) "Memuat prakiraan…" else "Simpan lokasi & perbarui cuaca") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }
        bundle?.let { weather ->
            val now = System.currentTimeMillis()
            val current = weather.hourly.minByOrNull { kotlin.math.abs(it.first - now) }?.second
            if (current != null) {
                val advice = climateAdvice(current, localProfile, "EASY", false)
                item {
                    SurfaceCard {
                        Text("KONDISI TERDEKAT", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(advice.status.label, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${current.temperatureC?.let { "${it.toInt()}°C" } ?: "—"} · terasa ${current.apparentTemperatureC?.let { "${it.toInt()}°C" } ?: "—"} · RH ${current.humidityPct?.let { "${it.toInt()}%" } ?: "—"}", fontWeight = FontWeight.SemiBold)
                        Text(advice.message, color = Muted, fontSize = 12.sp)
                        advice.stopWarning?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        Text("Diperbarui ${DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale("id")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(weather.fetchedAtMs))} · ${weather.source}", color = Muted, fontSize = 10.sp)
                    }
                }
                val slots = recommendClimateSlots(now, 45, weather.hourly, current, localProfile)
                item {
                    SurfaceCard {
                        Text("WAKTU LATIHAN YANG LEBIH BAIK", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        if (slots.isEmpty()) Text("Belum ada slot prakiraan yang sesuai dalam 40 jam mendatang.", color = Muted, fontSize = 12.sp)
                        slots.forEach { slot ->
                            val time = DateTimeFormatter.ofPattern("EEE, HH:mm", Locale("id")).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(slot.startMs))
                            Text(time, fontWeight = FontWeight.Bold)
                            Text(slot.explanation.joinToString(" · "), color = Muted, fontSize = 12.sp)
                            Text(if (slot.betterThanNow) "Lebih disarankan dibanding kondisi terdekat" else "Alternatif berdasarkan prakiraan", fontSize = 11.sp)
                            Divider()
                        }
                    }
                }
            }
        }
        item {
            SurfaceCard {
                Text("AMBANG PERSONAL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(localProfile.explanation, color = Muted, fontSize = 12.sp)
                if (localProfile.available) Text("Nyaman hingga sekitar ${localProfile.comfortableBelowC ?: "—"}°C · perhatian panas di atas ${localProfile.heavyAboveC ?: "—"}°C", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}
