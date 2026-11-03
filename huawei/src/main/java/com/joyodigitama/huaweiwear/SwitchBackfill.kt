package com.joyodigitama.huaweiwear

import android.content.SharedPreferences
import java.util.Locale

/**
 * Which watches pull their whole history on the next activity sync, because the person moved the link to them from another watch
 * (docs/multi-watch.md, section 7).
 *
 * `device.last_sync` is one cursor per watch, so a watch that comes back after another one was linked asks only for what is newer than
 * its own last sync. Whatever the other watch wrote over its minutes in the meantime (an older build overwrote them outright, a build
 * with the merge triggers still lets an unattributed row be replaced) was never asked for again. A switch therefore sets the mark; the
 * next sync that stores its steps and sleep clears it.
 *
 * Only a connect the person made counts ([WearLink.connect] with `remember`): the background loop reconnecting the watch it follows,
 * and a visit to another watch, are not switches. The marks sit in the settings file, so they survive the process and a failed sync
 * leaves the mark for the next one.
 */
internal class SwitchBackfill(private val prefs: SharedPreferences) {
  /** A connect to [address] that made it the remembered watch: a switch when [previous], the watch remembered until now, is another one. */
  fun noteConnect(previous: String?, address: String) {
    if (previous != null && !previous.equals(address, ignoreCase = true)) set(address, true)
  }

  fun pending(address: String): Boolean = prefs.getBoolean(key(address), false)

  /** The steps and sleep of [address] were pulled from the beginning (or the watch was forgotten). */
  fun done(address: String) = set(address, false)

  /**
   * The `last_sync` an activity sync of [address] computes its window from: null (everything the watch holds) when the caller asked for
   * the full history or a switch is pending, else what [stored] reads.
   */
  inline fun windowBase(address: String, fullHistory: Boolean, stored: () -> Long?): Long? = if (fullHistory || pending(address)) null else stored()

  private fun set(address: String, pending: Boolean) {
    prefs.edit().apply { if (pending) putBoolean(key(address), true) else remove(key(address)) }.apply()
  }

  private fun key(address: String) = KEY_PREFIX + address.trim().uppercase(Locale.ROOT)

  private companion object {
    const val KEY_PREFIX = "backfill_after_switch_"
  }
}
