package com.runnow.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pengingat latihan. index.html menghitung daftarnya; di sini hanya dijadwalkan lewat AlarmManager, jadi tetap
 * berbunyi walau halaman tidak terbuka. Daftar disimpan supaya bisa dijadwalkan ulang setelah HP restart.
 */
object PlanNotifier {
    private const val CHANNEL = "rencana"
    private const val PREFS = "plan"
    private const val KEY_JSON = "items"
    private const val KEY_COUNT = "count"
    private const val MAX_ITEMS = 60

    private class Item(val at: Long, val title: String, val body: String, val big: String)

    private fun parse(json: String): List<Item> {
        val arr = try { JSONArray(json) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val at = o.optLong("at", 0L)
            if (at <= 0L) null else Item(at, o.optString("title"), o.optString("body"), o.optString("big", o.optString("body")))
        }
    }

    private fun intent(ctx: Context) = Intent(ctx, PlanReceiver::class.java)

    private fun pending(ctx: Context, index: Int, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(ctx, index, intent(ctx), flags or PendingIntent.FLAG_IMMUTABLE)

    /** Ganti seluruh jadwal dengan [json]. */
    fun set(ctx: Context, json: String) {
        val app = ctx.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alarms = app.getSystemService(AlarmManager::class.java)
        for (i in 0 until prefs.getInt(KEY_COUNT, 0)) {
            pending(app, i, PendingIntent.FLAG_NO_CREATE)?.let { alarms.cancel(it); it.cancel() }
        }
        val now = System.currentTimeMillis()
        val items = parse(json).filter { it.at > now }.sortedBy { it.at }.take(MAX_ITEMS)
        val saved = JSONArray().also { a ->
            items.forEach { a.put(JSONObject().put("at", it.at).put("title", it.title).put("body", it.body).put("big", it.big)) }
        }
        prefs.edit().putString(KEY_JSON, saved.toString()).putInt(KEY_COUNT, items.size).apply()
        items.forEachIndexed { i, item -> schedule(app, alarms, i, item) }
    }

    /** Setelah HP restart: jadwalkan ulang yang belum lewat. */
    fun reschedule(ctx: Context) {
        val prefs = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        set(ctx, prefs.getString(KEY_JSON, "[]") ?: "[]")
    }

    private fun schedule(ctx: Context, alarms: AlarmManager, index: Int, item: Item) {
        val i = intent(ctx)
            .putExtra("title", item.title).putExtra("body", item.body).putExtra("big", item.big).putExtra("at", item.at)
        val pi = PendingIntent.getBroadcast(ctx, index, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pi)
    }

    fun show(ctx: Context, id: Int, title: String, body: String, big: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pengingat latihan", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = android.app.Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(big.ifBlank { body }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Tanpa izin notifikasi (Android 13+) panggilan ini diam saja.
        nm.notify(id, n)
    }
}

class PlanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val at = intent.getLongExtra("at", 0L)
        PlanNotifier.show(
            context,
            (at % 1_000_000_000L).toInt(),
            intent.getStringExtra("title").orEmpty(),
            intent.getStringExtra("body").orEmpty(),
            intent.getStringExtra("big").orEmpty(),
        )
    }
}

class PlanBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PlanNotifier.reschedule(context)
    }
}
