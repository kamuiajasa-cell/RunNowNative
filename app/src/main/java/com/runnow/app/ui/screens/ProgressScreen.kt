package com.runnow.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.ui.AppViewModel
import com.runnow.app.ui.RunTheme
import com.runnow.app.ui.UiState
import com.runnow.domain.FitPoint
import com.runnow.domain.WeekBucket
import com.runnow.domain.fitPoints
import com.runnow.domain.fitSummary
import com.runnow.domain.fmt1c
import com.runnow.domain.hasRuns
import com.runnow.domain.mixOf
import com.runnow.domain.planAdherence
import com.runnow.domain.progressWeeks
import com.runnow.domain.thinData
import com.runnow.domain.volumeInsight
import com.runnow.domain.weekDeltaPct
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val ID = Locale("id", "ID")
private fun dm(d: LocalDate) = d.format(DateTimeFormatter.ofPattern("d MMM", ID))

/**
 * Layar Progres (renderProgress). Sudah ada: volume mingguan, komposisi sesi, rencana minggu ini, kebugaran (CTL/ATL).
 * Belum dipindahkan: kartu Prediksi waktu lomba dan tren pace easy.
 */
@Composable
fun ProgressScreen(ui: UiState, vm: AppViewModel) {
    val c = RunTheme.colors
    var range by rememberSaveable { mutableStateOf(8) }
    val snap = ui.snap
    ScreenFrame("Progres") {
        ChipRow {
            for ((n, t) in listOf(8 to "8 minggu", 26 to "6 bulan", 52 to "1 tahun")) RunChip(t, range == n, { range = n })
        }
        if (snap == null || !snap.hasRuns()) {
            RunCard {
                Text("Belum ada data lari", fontWeight = FontWeight.SemiBold, color = c.tx)
                Muted("Sinkronkan Intervals.icu atau jam, lalu lari pertama akan mengisi volume, kebugaran, dan prediksi di sini.")
            }
            return@ScreenFrame
        }
        val weeks = remember(snap, range) { snap.progressWeeks(range) }
        val active = weeks.count { it.n >= 1 }
        if (thinData(weeks, range)) {
            RunCard {
                Text("Data masih tipis di rentang ini", fontWeight = FontWeight.SemiBold, color = c.tx)
                Muted("Hanya $active dari $range minggu punya lari. Grafik tetap ditampilkan; prediksi dan tren akan lebih stabil setelah ${max(4, Math.ceil(range * 0.4).toInt())} minggu aktif.")
            }
        }
        val totKm = weeks.sumOf { it.km }
        StatRow(
            listOf(
                fmt1c(totKm) to "km total",
                weeks.sumOf { it.n }.toString() to "lari",
                fmt1c(totKm / max(1, range)) to "km/minggu",
                "$active/$range" to "minggu aktif",
            ),
        )

        // Volume mingguan
        RunCard {
            val last = weeks.last()
            val dlt = weekDeltaPct(weeks)
            Text("Volume mingguan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
            Muted("Minggu ini ${fmt1c(last.km)} km" + if (dlt != null) " · ${if (dlt >= 0) "+" else ""}$dlt% dari minggu lalu" else "")
            var sel by remember(weeks.size) { mutableIntStateOf(weeks.size - 1) }
            BarChart(weeks, sel) { sel = it }
            val w = weeks[sel.coerceIn(0, weeks.size - 1)]
            Text(
                "${dm(w.start)} – ${dm(w.start.plusDays(6))} · ${fmt1c(w.km)} km · ${w.n} lari" +
                    if (w.km > 0) " · easy ${fmt1c(w.easy)} · quality ${fmt1c(w.qual)} · long ${fmt1c(w.long)}" else "",
                fontSize = 12.sp, color = c.lightGrey,
            )
            Insight(volumeInsight(weeks, range))
        }

        // Komposisi sesi
        mixOf(weeks)?.let { m ->
            RunCard {
                Text("Komposisi sesi", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                Muted("Proporsi jarak di rentang terpilih (deteksi otomatis + konfirmasi manual)")
                Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    if (m.easyPct > 0) Box(Modifier.weight(m.easyPct.toFloat()).height(10.dp).background(c.primary))
                    if (m.qualPct > 0) Box(Modifier.weight(m.qualPct.toFloat()).height(10.dp).background(c.yellow))
                    if (m.longPct > 0) Box(Modifier.weight(m.longPct.toFloat()).height(10.dp).background(androidx.compose.ui.graphics.Color(0xFFA78BFA)))
                }
                KvRow("Easy", "${m.easyPct}% · ${fmt1c(m.easyKm)} km")
                KvRow("Quality", "${m.qualPct}% · ${fmt1c(m.qualKm)} km")
                KvRow("Long", "${m.longPct}% · ${fmt1c(m.longKm)} km")
                Insight(m.insight)
            }
        }

        // Rencana minggu ini
        snap.planAdherence()?.let { p ->
            RunCard {
                Text("Rencana minggu ini", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                KvRow("Volume rencana", "${fmt1c(p.planKm)} km")
                KvRow("Sudah dijalankan", "${fmt1c(p.doneKm)} km" + (p.pctKm?.let { " · $it%" } ?: ""))
                KvRow("Hari terencana", "${p.doneN}/${p.planN}" + (p.pctN?.let { " · $it%" } ?: ""))
                Insight(p.insight)
            }
        }

        // Kebugaran dan kelelahan
        val pts = remember(snap, range) { snap.fitPoints(range) }
        val fit = fitSummary(pts)
        if (fit != null) {
            RunCard {
                Text("Kebugaran & kelelahan", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx)
                Muted(
                    "CTL ${one(fit.ctl)} (${if (fit.dCtl >= 0) "+" else ""}${one(fit.dCtl)} sejak awal) · TSB ${one(fit.tsb)}, ${fit.label}",
                )
                var sel by remember(pts.size) { mutableIntStateOf(pts.size - 1) }
                LineChart(pts, sel) { sel = it }
                val p = pts[sel.coerceIn(0, pts.size - 1)]
                Text("${dm(p.day)} · CTL ${one(p.ctl)} · ATL ${one(p.atl)} · TSB ${one(p.ctl - p.atl)}", fontSize = 12.sp, color = c.lightGrey)
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    Text("● Kebugaran (CTL)", fontSize = 11.sp, color = c.primary)
                    Text("● Kelelahan (ATL)", fontSize = 11.sp, color = c.yellow)
                }
                Insight(fit.insight)
            }
        }
    }
}

private fun one(x: Double) = (Math.round(x * 10) / 10.0).toString()

@Composable
private fun BarChart(weeks: List<WeekBucket>, sel: Int, onSel: (Int) -> Unit) {
    val c = RunTheme.colors
    val mx = max(10.0, weeks.maxOf { it.km })
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .pointerInput(weeks.size) {
                detectTapGestures { off -> onSel(((off.x / size.width) * weeks.size).toInt().coerceIn(0, weeks.size - 1)) }
            },
    ) {
        val bw = size.width / weeks.size
        weeks.forEachIndexed { i, w ->
            val h = max(if (w.km > 0) 2f else 0f, (w.km / mx * size.height).toFloat())
            val a = if (i == sel) 1f else if (w.km > 0) 0.55f else 0.2f
            drawRoundRect(
                color = c.primary.copy(alpha = a),
                topLeft = Offset(i * bw + bw * 0.12f, size.height - h),
                size = Size(bw * 0.76f, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun LineChart(pts: List<FitPoint>, sel: Int, onSel: (Int) -> Unit) {
    val c = RunTheme.colors
    val lo = pts.minOf { minOf(it.ctl, it.atl) }
    val hi = pts.maxOf { maxOf(it.ctl, it.atl) }
    val rg = max(1.0, hi - lo)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(110.dp)
            .pointerInput(pts.size) {
                detectTapGestures { off -> onSel(((off.x / size.width) * pts.size).toInt().coerceIn(0, pts.size - 1)) }
            },
    ) {
        fun x(i: Int) = if (pts.size > 1) i.toFloat() / (pts.size - 1) * size.width else size.width / 2
        fun y(v: Double) = (8.dp.toPx() + (1 - (v - lo) / rg) * (size.height - 16.dp.toPx())).toFloat()
        fun path(f: (FitPoint) -> Double) = Path().also { p ->
            pts.forEachIndexed { i, pt -> if (i == 0) p.moveTo(x(i), y(f(pt))) else p.lineTo(x(i), y(f(pt))) }
        }
        drawPath(path { it.atl }, c.yellow.copy(alpha = 0.85f), style = Stroke(2.dp.toPx()))
        drawPath(path { it.ctl }, c.primary, style = Stroke(2.5f.dp.toPx()))
        val s = sel.coerceIn(0, pts.size - 1)
        drawLine(c.grey, Offset(x(s), 0f), Offset(x(s), size.height), strokeWidth = 1.dp.toPx())
        drawCircle(c.primary, 4.dp.toPx(), Offset(x(s), y(pts[s].ctl)))
    }
}
