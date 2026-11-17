package com.runnow.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Pilihan tema: ikuti sistem, atau paksa gelap/terang (setara data-theme di runsense.html). */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Warna dari variabel CSS di runsense.html (`:root` dan `:root[data-theme="light"]`), nama sama:
 * --primary, --primary-dim, --bg, --card, --grey, --light-grey, --tx, --yellow, --red,
 * --nav-border, --card-border, --el. Layar Fase C memakai ini lewat [RunTheme.colors].
 */
@Immutable
class RunColors(
    val isDark: Boolean,
    val primary: Color,
    val primaryDim: Color,
    val bg: Color,
    val card: Color,
    val grey: Color,
    val lightGrey: Color,
    val tx: Color,
    val yellow: Color,
    val red: Color,
    val navBorder: Color,
    val cardBorder: Color,
    val el: Color,
)

val DarkRunColors = RunColors(
    isDark = true,
    primary = Color(0xFF00C853),
    primaryDim = Color(0x2600C853), // rgba(0,200,83,.15)
    bg = Color(0xFF0A0A0A),
    card = Color(0xFF1E1E1E),
    grey = Color(0xFF9E9E9E),
    lightGrey = Color(0xFFE0E0E0),
    tx = Color(0xFFFAFAFA),
    yellow = Color(0xFFFFB300),
    red = Color(0xFFFF5252),
    navBorder = Color(0x12FFFFFF), // rgba(255,255,255,.07)
    cardBorder = Color(0x0DFFFFFF), // .05
    el = Color(0x0AFFFFFF), // .04
)

val LightRunColors = RunColors(
    isDark = false,
    primary = Color(0xFF00A844),
    primaryDim = Color(0x1F00A844), // rgba(0,168,68,.12)
    bg = Color(0xFFF4F5F0),
    card = Color(0xFFFFFFFF),
    grey = Color(0xFF6B7369),
    lightGrey = Color(0xFF3D453C),
    tx = Color(0xFF1A1F1A),
    yellow = Color(0xFFC48900),
    red = Color(0xFFD32F2F),
    navBorder = Color(0x14000000), // rgba(0,0,0,.08)
    cardBorder = Color(0x0F000000), // .06
    el = Color(0x0A000000), // .04
)

val LocalRunColors = staticCompositionLocalOf { DarkRunColors }

object RunTheme {
    val colors: RunColors
        @Composable
        @ReadOnlyComposable
        get() = LocalRunColors.current
}

@Composable
fun RunNowTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val c = if (dark) DarkRunColors else LightRunColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.primary, onPrimary = Color.Black,
            background = c.bg, onBackground = c.tx,
            surface = c.card, onSurface = c.tx,
            surfaceVariant = c.card, onSurfaceVariant = c.grey,
            error = c.red, outline = c.navBorder, outlineVariant = c.navBorder,
        )
    } else {
        lightColorScheme(
            primary = c.primary, onPrimary = Color.White,
            background = c.bg, onBackground = c.tx,
            surface = c.card, onSurface = c.tx,
            surfaceVariant = c.card, onSurfaceVariant = c.grey,
            error = c.red, outline = c.navBorder, outlineVariant = c.navBorder,
        )
    }
    // Ikon status bar/navigasi mengikuti tema pilihan (bukan hanya tema sistem).
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalRunColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
