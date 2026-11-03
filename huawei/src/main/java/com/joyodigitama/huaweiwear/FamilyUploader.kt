package com.joyodigitama.huaweiwear

import android.content.Context
import android.os.PowerManager
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** What the server's answer to the ingest means for the phone: 401 and 403 mean the token is revoked or no link is active. */
internal fun familyOutcomeForStatus(status: Int): FamilyOutcome = when (status) {
  200 -> FamilyOutcome.SENT
  401, 403 -> FamilyOutcome.REJECTED
  else -> FamilyOutcome.FAILED
}

/**
 * Whether the upload goes out now: always when [force]d (the app asked) or when the watch synced since the last accepted
 * upload, otherwise at most every [FamilyUploader.MIN_INTERVAL_MS].
 */
internal fun familyShouldPost(force: Boolean, nowMs: Long, postedAtMs: Long, postedWatchSyncAt: Long?, watchSyncAt: Long?): Boolean =
  force || watchSyncAt != postedWatchSyncAt || nowMs - postedAtMs >= FamilyUploader.MIN_INTERVAL_MS

/**
 * Family monitoring, monitored side: builds the [FamilySummary] from the sync database and `PUT`s it to the cloud
 * Worker with the device token the app stored ([FamilySettings]). Runs from JS ([upload] forced) and after every
 * background sync ([uploadAfterSync]), so the watcher's feed moves while JoyoFit is closed. One upload at a time.
 */
internal class FamilyUploader(
  private val context: Context,
  private val settings: FamilySettings,
  private val source: FamilySource,
  /** The watch battery as last read in this process, null before the first read. */
  private val battery: () -> Int?,
  private val events: WearEvents,
  private val scope: CoroutineScope,
) {
  private val mutex = Mutex()

  fun configure(config: FamilyConfig?) = settings.configure(config)

  fun status(): Map<String, Any?> {
    val config = settings.config
    return mapOf(
      "configured" to (config != null),
      "enabled" to (config?.enabled ?: false),
      "last" to settings.last?.toJs(),
      "lastPayload" to settings.lastPayload,
    )
  }

  /** After a background sync (BackgroundController): fire and forget; a failure is logged and never reaches the sync. */
  fun uploadAfterSync() {
    scope.launch(Dispatchers.IO) { upload(force = false) }
  }

  /**
   * Never throws: every failure is a `failed` result and a log line, so a sync is never broken by it. A `skipped` result
   * (disabled, no config, nothing to send, throttled) is returned but not stored: it made no attempt.
   */
  suspend fun upload(force: Boolean): FamilyUploadResult = withContext(Dispatchers.IO) {
    mutex.withLock {
      try {
        post(force)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log(LogDirection.ERROR, "Family upload failed: ${e.message ?: e.javaClass.simpleName}")
        FamilyUploadResult(FamilyOutcome.FAILED, null, System.currentTimeMillis()).also(settings::recordAttempt)
      }
    }
  }

  private fun post(force: Boolean): FamilyUploadResult {
    val nowMs = System.currentTimeMillis()
    val config = settings.config
    if (config == null || !config.enabled) return FamilyUploadResult(FamilyOutcome.SKIPPED, null, nowMs)
    val payload = FamilySummary.build(nowMs, ZoneId.systemDefault(), config.scopes, source, battery())
    if (payload.isEmpty || !familyShouldPost(force, nowMs, settings.postedAt, settings.postedWatchSyncAt, payload.watchSyncAt)) {
      return FamilyUploadResult(FamilyOutcome.SKIPPED, null, nowMs)
    }
    if (!isOnline(context)) {
      log(LogDirection.ERROR, "Family upload failed: no network")
      return FamilyUploadResult(FamilyOutcome.FAILED, null, nowMs).also(settings::recordAttempt)
    }
    val body = payload.toJson()
    val wakeLock = context.getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "joyofit:family")
      .apply { setReferenceCounted(false) }
    // Doze: the background sync's wake lock is gone by the time the request runs.
    val status = try {
      wakeLock.acquire(WAKE_LOCK_MS)
      put(config, body)
    } catch (e: IOException) {
      log(LogDirection.ERROR, "Family upload failed: ${e.message ?: e.javaClass.simpleName}")
      null
    } finally {
      if (wakeLock.isHeld) wakeLock.release()
    }
    val result = FamilyUploadResult(status?.let(::familyOutcomeForStatus) ?: FamilyOutcome.FAILED, status, System.currentTimeMillis())
    when (result.outcome) {
      FamilyOutcome.SENT -> {
        settings.recordSent(result, payload, body)
        log(LogDirection.INFO, "Family upload sent")
      }
      FamilyOutcome.REJECTED -> {
        settings.recordAttempt(result)
        settings.clearConfig()
        log(LogDirection.ERROR, "Family upload rejected [HTTP $status]: token revoked or no active link, config cleared")
      }
      else -> {
        settings.recordAttempt(result)
        if (status != null) log(LogDirection.ERROR, "Family upload failed [HTTP $status]")
      }
    }
    return result
  }

  private fun put(config: FamilyConfig, body: String): Int {
    val connection = URL(config.baseUrl + INGEST_PATH).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "PUT"
      connection.connectTimeout = TIMEOUT_MS
      connection.readTimeout = TIMEOUT_MS
      connection.doOutput = true
      connection.setRequestProperty("Authorization", "Bearer ${config.token}")
      connection.setRequestProperty("Content-Type", "application/json")
      connection.setRequestProperty("User-Agent", "JoyoFit")
      connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      return connection.responseCode
    } finally {
      connection.disconnect()
    }
  }

  private fun log(direction: LogDirection, message: String) {
    events.log(direction, null, null, null, message)
  }

  companion object {
    /** Without a new watch sync, an upload waits this long after the last accepted one. */
    const val MIN_INTERVAL_MS = 10 * 60_000L
    private const val INGEST_PATH = "/v1/family/ingest"
    private const val TIMEOUT_MS = 15_000
    private const val WAKE_LOCK_MS = 60_000L
  }
}
