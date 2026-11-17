package com.runnow.app.ui

import android.content.Context

/** Pilihan tampilan kecil (tema). Data lari dan kunci API tetap di penyimpanan :data. */
class UiPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("runnow_ui", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        set(v) {
            sp.edit().putString("theme", v.name).apply()
        }
}
