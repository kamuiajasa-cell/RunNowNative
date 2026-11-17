package com.runnow.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runnow.app.ui.RunTheme

/** Kerangka layar: judul + isi yang bisa digulir. Dipakai Home/History/Progress/Me sampai diganti Langkah 8-10. */
@Composable
fun ScreenFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunTheme.colors.tx)
        content()
    }
}

/** Kartu dengan latar --card dan garis --card-border. */
@Composable
fun RunCard(content: @Composable ColumnScope.() -> Unit) {
    val c = RunTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        border = BorderStroke(1.dp, c.cardBorder),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun Muted(text: String) = Text(text, fontSize = 13.sp, color = RunTheme.colors.grey)

@Composable
fun EmptyNote() = RunCard { Muted("Belum ada data. Sinkronkan dari tab Saya atau sambungkan jam di tab Jam.") }
