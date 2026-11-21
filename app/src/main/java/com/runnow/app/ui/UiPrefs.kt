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

    /** Pengingat harian dari rencana minggu. Bawaan menyala, jam 05:30 (sama dengan index.html). */
    var remindOn: Boolean
        get() = sp.getBoolean("remind_on", true)
        set(v) {
            sp.edit().putBoolean("remind_on", v).apply()
        }

    var remindRest: Boolean
        get() = sp.getBoolean("remind_rest", true)
        set(v) {
            sp.edit().putBoolean("remind_rest", v).apply()
        }

    /** "HH:mm". */
    var remindTime: String
        get() = sp.getString("remind_time", null) ?: "05:30"
        set(v) {
            sp.edit().putString("remind_time", v).apply()
        }
}
