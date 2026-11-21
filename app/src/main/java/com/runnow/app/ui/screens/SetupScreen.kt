package com.runnow.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.WatchScreen
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.UiState
import com.runnow.data.SettingsStore

/**
 * Layar awal (screen-setup di runsense.html): API key Intervals.icu, target, gaya coaching, Coba Demo, dan jalur
 * "data jam saja". Muncul hanya selama belum ada kunci, sumber jam, atau data.
 */
@Composable
fun SetupScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    var key by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf(ui.settings.goal) }
    var style by rememberSaveable { mutableStateOf(ui.settings.style) }
    var showWatch by rememberSaveable { mutableStateOf(false) }

    if (showWatch) {
        BackHandler { showWatch = false }
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showWatch = false }, modifier = Modifier.padding(start = 8.dp, top = 8.dp)) {
                Text("← Kembali", color = c.primary)
            }
            WatchScreen()
        }
        return
    }

    ScreenFrame("") {
        Text("Run Now", fontSize = 32.sp, fontWeight = FontWeight.Black, color = c.primary)
        Muted("Coach lari otomatis · Intervals.icu")
        Muted(if (ui.busy) "Menghubungkan…" else "Koneksi langsung ke Intervals.icu (tanpa server lokal)")

        RunCard {
            LabelText("API Key Intervals.icu")
            OutlinedTextField(
                value = key, onValueChange = { key = it }, singleLine = true,
                placeholder = { Text("Paste API key di sini") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Muted("Ambil di Intervals.icu → Settings → Developer Settings → API key. Key hanya disimpan di perangkat ini.")
        }

        RunCard {
            LabelText("Target utama")
            ChipRow { for (g in SettingsStore.GOALS) RunChip(g, goal == g, { goal = g }) }
            LabelText("Gaya coaching")
            for ((k, t, sub) in listOf(
                Triple("moderate", "Moderat", "Seimbang (disarankan)"),
                Triple("conservative", "Konservatif", "Lebih banyak recovery"),
                Triple("aggressive", "Agresif", "Lebih banyak quality session"),
            )) {
                RunCard(onClick = { style = k }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(t, fontWeight = FontWeight.SemiBold, color = c.tx)
                            Muted(sub)
                        }
                        if (style == k) Text("✓", color = c.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        PrimaryBtn(if (ui.busy) "Menghubungkan…" else "Hubungkan & Mulai", { vm.connect(key, goal, style) }, enabled = !ui.busy)
        OutlineBtn("Coba Demo", { vm.enterDemo(goal, style) }, enabled = !ui.busy)

        RunCard {
            LabelText("Dari jam")
            OutlineBtn("Atur jam", { showWatch = true })
            OutlineBtn("Pakai data jam saja", { vm.enterWatchMode(goal, style) }, enabled = !ui.busy)
            Muted("Tanpa Intervals.icu: tidak ada CTL/TSB dan workout tidak bisa dikirim ke kalender Intervals. Workout tetap bisa dikirim langsung ke jam.")
        }
        if (ui.message.isNotBlank() && !ui.busy) Text(ui.message, fontSize = 13.sp, color = c.red)
    }
}
