package com.runnow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.ui.RunTheme
import com.runnow.domain.RunReview

/** Kerangka layar: judul + isi yang bisa digulir (.page-title + .content di HTML). */
@Composable
fun ScreenFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title.isNotEmpty()) Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunTheme.colors.tx)
        content()
    }
}

/** Kartu dengan latar --card dan garis --card-border. Bila [onClick] diisi, seluruh kartu bisa diketuk. */
@Composable
fun RunCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = RunTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val m = if (onClick != null) modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick) else modifier.fillMaxWidth()
    Card(
        modifier = m, shape = shape,
        colors = CardDefaults.cardColors(containerColor = c.card), border = BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) = Text(text, modifier = modifier, fontSize = 13.sp, color = RunTheme.colors.grey)

@Composable
fun LabelText(text: String) = Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RunTheme.colors.grey)

@Composable
fun EmptyNote(text: String = "Belum ada data. Sinkronkan dari tab Saya atau sambungkan jam di tab Jam.") = RunCard { Muted(text) }

/** Baris label di kiri, nilai tebal di kanan (.row di HTML). */
@Composable
fun KvRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = RunTheme.colors.grey, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RunTheme.colors.tx)
    }
}

/** Chip pilihan (.chip / .fchip / .ty-chip). */
@Composable
fun RunChip(label: String, selected: Boolean, onClick: () -> Unit, warn: Boolean = false) {
    val c = RunTheme.colors
    val accent = if (warn) c.yellow else c.primary
    val bg = if (selected) accent.copy(alpha = 0.15f) else c.el
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (selected) accent else c.lightGrey)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun PrimaryBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = RunTheme.colors
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = if (c.isDark) Color.Black else Color.White),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun OutlineBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val c = RunTheme.colors
    OutlinedButton(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.navBorder),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) c.red else c.tx),
    ) { Text(text) }
}

/** Ringkasan angka (.hist-sum): nilai tebal di atas, keterangan kecil di bawah. */
@Composable
fun StatRow(items: List<Pair<String, String>>) {
    val c = RunTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for ((v, l) in items) StatCell(v, l)
    }
}

@Composable
private fun RowScope.StatCell(v: String, l: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunTheme.colors.tx)
        Text(l, fontSize = 11.sp, color = RunTheme.colors.grey)
    }
}

@Composable
fun Insight(text: String) {
    val c = RunTheme.colors
    Text(
        text, fontSize = 13.sp, color = c.lightGrey,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.el)
            .padding(10.dp),
    )
}

/** Ulasan lari (renderReviewHTML di HTML): ringkasan lalu bagian-bagian bercatatan hijau/kuning/merah. */
@Composable
fun ReviewBlock(rev: RunReview) {
    val c = RunTheme.colors
    rev.headline?.let { Text(it, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.tx) }
    if (rev.summary.isNotBlank()) Muted(rev.summary)
    for (sec in rev.sections) {
        LabelText(sec.title)
        for (n in sec.notes) {
            val col = when (n.k) {
                "g" -> c.primary
                "y" -> c.yellow
                "r" -> c.red
                else -> c.grey
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(50)).background(col))
                Text(n.text, fontSize = 13.sp, color = c.lightGrey)
            }
        }
    }
}
