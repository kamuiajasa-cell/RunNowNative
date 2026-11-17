package com.runnow.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Lima tujuan di bottom nav. Empat pertama sama dengan runsense.html (Hari Ini, Riwayat, Progres, Saya) + Jam. */
enum class Tab(val route: String, val label: String, val path: String) {
    HOME("home", "Hari Ini", "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"),
    HISTORY(
        "history", "Riwayat",
        "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8H12z",
    ),
    PROGRESS("progress", "Progres", "M3 17l6-6 4 4 8-8v4h2V4h-7v2h4l-7 7-4-4-8 8z"),
    WATCH(
        "watch", "Jam",
        "M20 12c0-1.77-.77-3.37-2-4.46V2H6v5.54C4.77 8.63 4 10.23 4 12s.77 3.37 2 4.46V22h12v-5.54c1.23-1.09 2-2.69 2-4.46zM6 12c0-3.31 2.69-6 6-6s6 2.69 6 6-2.69 6-6 6-6-2.69-6-6z",
    ),
    ME("me", "Saya", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm0 2c-3.33 0-8 1.67-8 5v1h16v-1c0-3.33-4.67-5-8-5z"),
    ;

    /** Ikon dibangun dari path SVG yang sama dengan nav di HTML (tanpa dependensi material-icons). */
    val icon: ImageVector by lazy {
        ImageVector.Builder(
            name = "tab_$route",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }
}
