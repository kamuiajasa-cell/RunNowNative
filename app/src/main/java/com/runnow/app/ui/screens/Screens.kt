package com.runnow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.MainActivity
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.ThemeMode
import com.runnow.app.ui.UiState
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

// Layar sementara: cukup untuk membuktikan data repository -> ViewModel -> UI tersambung.
// Diganti HomeScreen.kt (Langkah 8), HistoryScreen/ProgressScreen (9), MeScreen (10), SetupScreen (11).

@Composable
fun HomeScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    ScreenFrame("Hari Ini") {
        val r = ui.result
        if (r == null) {
            EmptyNote()
        } else {
            val rec = r.recommendation
            RunCard {
                Text(rec.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                if (rec.duration.isNotBlank()) Muted(rec.duration)
                Text(rec.reason, fontSize = 14.sp, color = c.lightGrey)
            }
            RunCard {
                Muted("Kesiapan")
                Text("${r.athlete.score ?: "-"} · ${r.athlete.formLabel}", fontSize = 16.sp, color = c.tx)
            }
        }
        if (ui.message.isNotBlank()) Muted(ui.message)
    }
}

@Composable
fun HistoryScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    ScreenFrame("Riwayat") {
        val items = ui.result?.activities.orEmpty()
        if (items.isEmpty()) EmptyNote()
        for (a in items.take(25)) {
            RunCard {
                Text(a.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                Muted(a.date + " · " + a.meta)
            }
        }
    }
}

@Composable
fun ProgressScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    ScreenFrame("Progres") {
        val r = ui.result
        if (r == null) {
            EmptyNote()
        } else {
            RunCard {
                Muted("Tahap")
                Text(r.athlete.stageName, fontSize = 16.sp, color = c.tx)
            }
            RunCard {
                Muted("CTL · ATL · TSB")
                fun f(x: Double?) = if (x == null) "-" else (Math.round(x * 10) / 10.0).toString()
                Text("${f(r.athlete.ctl)} · ${f(r.athlete.atl)} · ${f(r.athlete.tsb)}", fontSize = 16.sp, color = c.tx)
            }
            RunCard {
                Muted("Estimasi target")
                Text(r.goalEst.current, fontSize = 15.sp, color = c.tx)
                Muted(r.goalEst.projection)
            }
        }
    }
}

@Composable
fun MeScreen(ui: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    ScreenFrame("Saya") {
        RunCard {
            Muted("Tema")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((mode, label) in listOf(ThemeMode.SYSTEM to "Sistem", ThemeMode.DARK to "Gelap", ThemeMode.LIGHT to "Terang")) {
                    if (ui.theme == mode) {
                        Button(onClick = { vm.setTheme(mode) }) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { vm.setTheme(mode) }) { Text(label) }
                    }
                }
            }
        }
        RunCard {
            Muted(if (ui.isDemo) "Mode demo" else if (ui.source == "watch") "Sumber: data jam" else "Sumber: Intervals.icu")
            Button(onClick = { vm.syncNow() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.busy) "Menyinkronkan…" else "Sinkron sekarang")
            }
            if (ui.message.isNotBlank()) Muted(ui.message)
        }
        RunCard {
            Muted("Versi lama (WebView) masih ada sampai Langkah 12.")
            OutlinedButton(
                onClick = { ctx.startActivity(Intent(ctx, MainActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Buka versi lama") }
        }
        OutlinedButton(
            onClick = { vm.logout() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RunTheme.colors.red),
        ) { Text("Reset data & logout") }
    }
}

/** Gerbang minimal: API key atau data jam saja. Mode demo dan onboarding lengkap menyusul di Langkah 11. */
@Composable
fun SetupScreen(ui: UiState, vm: AppViewModel) {
    var key by rememberSaveable { mutableStateOf("") }
    ScreenFrame("Run Now") {
        RunCard {
            Muted("Masuk dengan API key Intervals.icu")
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                singleLine = true,
                label = { Text("API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { vm.connect(key) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.busy) "Menyambung…" else "Sambungkan")
            }
        }
        OutlinedButton(onClick = { vm.enterWatchMode() }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Pakai data jam saja")
        }
        if (ui.message.isNotBlank()) Muted(ui.message)
    }
}
