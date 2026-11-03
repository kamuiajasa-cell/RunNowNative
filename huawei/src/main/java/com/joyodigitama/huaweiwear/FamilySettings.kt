package com.joyodigitama.huaweiwear

import android.content.Context
import android.content.SharedPreferences

/** `FamilyConfig` in `HuaweiWearFamily.types.ts`. */
internal data class FamilyConfig(
  /** Cloud Worker origin without a trailing slash. */
  val baseUrl: String,
  /** The revocable per-device bearer token. */
  val token: String,
  val scopes: FamilyScopes,
  /** False while every link is paused or none is active: nothing is uploaded. */
  val enabled: Boolean,
)

/** Wire names match `FamilyUploadResult.outcome` in `HuaweiWearFamily.types.ts`. */
internal enum class FamilyOutcome(val wire: String) {
  SENT("sent"),
  SKIPPED("skipped"),
  REJECTED("rejected"),
  FAILED("failed");

  companion object {
    fun fromWire(value: String): FamilyOutcome? = entries.firstOrNull { it.wire == value }
  }
}

/** `FamilyUploadResult` in `HuaweiWearFamily.types.ts`; [at] is unix ms. */
internal class FamilyUploadResult(val outcome: FamilyOutcome, val status: Int?, val at: Long) {
  fun toJs(): Map<String, Any?> = mapOf("outcome" to outcome.wire, "status" to status, "at" to at)
}

/**
 * Family monitoring state that must survive without JS running: the config the app pushed (so a background sync can
 * upload alone), the result of the last attempt, the exact last body, and what the throttle compares. Same
 * SharedPreferences file as [WearSettings], under `family_` keys.
 */
internal class FamilySettings(context: Context) {
  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  /** Null until the app configures it, and again after it clears the config or the server rejects the token. */
  val config: FamilyConfig?
    get() {
      val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
      val token = prefs.getString(KEY_TOKEN, null) ?: return null
      return FamilyConfig(
        baseUrl = baseUrl,
        token = token,
        scopes = FamilyScopes(
          sync = prefs.getBoolean(KEY_SCOPE_SYNC, false),
          steps = prefs.getBoolean(KEY_SCOPE_STEPS, false),
          sleep = prefs.getBoolean(KEY_SCOPE_SLEEP, false),
          restingHr = prefs.getBoolean(KEY_SCOPE_RESTING_HR, false),
          spo2 = prefs.getBoolean(KEY_SCOPE_SPO2, false),
        ),
        enabled = prefs.getBoolean(KEY_ENABLED, false),
      )
    }

  /** The result of the last attempt that made one (never a `skipped`). */
  val last: FamilyUploadResult?
    get() {
      val outcome = FamilyOutcome.fromWire(prefs.getString(KEY_LAST_OUTCOME, null) ?: return null) ?: return null
      val status = prefs.getInt(KEY_LAST_STATUS, NO_STATUS)
      return FamilyUploadResult(outcome, status.takeIf { it != NO_STATUS }, prefs.getLong(KEY_LAST_AT, 0L))
    }

  /** The exact JSON body of the last accepted upload. */
  val lastPayload: String? get() = prefs.getString(KEY_LAST_PAYLOAD, null)

  /** Unix ms of the last accepted upload; 0 before the first. */
  val postedAt: Long get() = prefs.getLong(KEY_POSTED_AT, 0L)

  /** `watchSyncAt` (unix ms) that last accepted upload carried. */
  val postedWatchSyncAt: Long?
    get() = prefs.getLong(KEY_POSTED_WATCH_SYNC, NO_SYNC).takeIf { it != NO_SYNC }

  /** Stores [config], or clears it and everything kept for it when null. A different server or token restarts the throttle. */
  fun configure(config: FamilyConfig?) {
    if (config == null) {
      prefs.edit().apply { prefs.all.keys.filter { it.startsWith(PREFIX) }.forEach { remove(it) } }.apply()
      return
    }
    val previous = this.config
    prefs.edit().apply {
      putString(KEY_BASE_URL, config.baseUrl)
      putString(KEY_TOKEN, config.token)
      putBoolean(KEY_ENABLED, config.enabled)
      putBoolean(KEY_SCOPE_SYNC, config.scopes.sync)
      putBoolean(KEY_SCOPE_STEPS, config.scopes.steps)
      putBoolean(KEY_SCOPE_SLEEP, config.scopes.sleep)
      putBoolean(KEY_SCOPE_RESTING_HR, config.scopes.restingHr)
      putBoolean(KEY_SCOPE_SPO2, config.scopes.spo2)
      if (previous == null || previous.baseUrl != config.baseUrl || previous.token != config.token) {
        THROTTLE_KEYS.forEach { remove(it) }
      }
    }.apply()
  }

  /** The server revoked the token: the config goes, the last result and body stay for the app to show. */
  fun clearConfig() {
    prefs.edit().apply { (CONFIG_KEYS + THROTTLE_KEYS).forEach { remove(it) } }.apply()
  }

  fun recordAttempt(result: FamilyUploadResult) {
    prefs.edit().apply { putResult(result) }.apply()
  }

  /** The server accepted [payload]: the throttle restarts from it. */
  fun recordSent(result: FamilyUploadResult, payload: FamilyPayload, json: String) {
    prefs.edit().apply {
      putResult(result)
      putString(KEY_LAST_PAYLOAD, json)
      putLong(KEY_POSTED_AT, result.at)
      payload.watchSyncAt?.let { putLong(KEY_POSTED_WATCH_SYNC, it) } ?: remove(KEY_POSTED_WATCH_SYNC)
    }.apply()
  }

  private fun SharedPreferences.Editor.putResult(result: FamilyUploadResult) {
    putString(KEY_LAST_OUTCOME, result.outcome.wire)
    result.status?.let { putInt(KEY_LAST_STATUS, it) } ?: remove(KEY_LAST_STATUS)
    putLong(KEY_LAST_AT, result.at)
  }

  private companion object {
    /** Same file as [WearSettings]. */
    const val PREFS_NAME = "joyofit_settings"
    const val PREFIX = "family_"
    const val KEY_BASE_URL = "family_base_url"
    const val KEY_TOKEN = "family_token"
    const val KEY_ENABLED = "family_enabled"
    const val KEY_SCOPE_SYNC = "family_scope_sync"
    const val KEY_SCOPE_STEPS = "family_scope_steps"
    const val KEY_SCOPE_SLEEP = "family_scope_sleep"
    const val KEY_SCOPE_RESTING_HR = "family_scope_resting_hr"
    const val KEY_SCOPE_SPO2 = "family_scope_spo2"
    const val KEY_LAST_OUTCOME = "family_last_outcome"
    const val KEY_LAST_STATUS = "family_last_status"
    const val KEY_LAST_AT = "family_last_at"
    const val KEY_LAST_PAYLOAD = "family_last_payload"
    const val KEY_POSTED_AT = "family_posted_at"
    const val KEY_POSTED_WATCH_SYNC = "family_posted_watch_sync"
    const val NO_STATUS = -1
    const val NO_SYNC = -1L
    val CONFIG_KEYS = listOf(
      KEY_BASE_URL, KEY_TOKEN, KEY_ENABLED, KEY_SCOPE_SYNC, KEY_SCOPE_STEPS, KEY_SCOPE_SLEEP, KEY_SCOPE_RESTING_HR, KEY_SCOPE_SPO2,
    )
    val THROTTLE_KEYS = listOf(KEY_POSTED_AT, KEY_POSTED_WATCH_SYNC)
  }
}
