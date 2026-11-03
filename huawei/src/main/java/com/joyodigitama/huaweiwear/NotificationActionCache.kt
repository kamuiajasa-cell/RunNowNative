package com.joyodigitama.huaweiwear

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.LinkedHashMap

internal data class CachedReplyAction(
  val key: String,
  val notificationId: Int,
  val packageName: String,
  val actionIntent: PendingIntent? = null,
  val remoteInputs: Array<RemoteInput>? = null,
  val timestamp: Long = System.currentTimeMillis(),
)

internal class NotificationActionCache(private val maxEntries: Int = 30) {
  private val lock = Any()
  private val idToKey = HashMap<Int, String>()
  private val cache = object : LinkedHashMap<String, CachedReplyAction>(maxEntries, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedReplyAction>?): Boolean {
      val evict = size > maxEntries
      if (evict && eldest != null) {
        idToKey.remove(eldest.value.notificationId)
      }
      return evict
    }
  }

  val size: Int get() = synchronized(lock) { cache.size }
  fun extractAndStore(sbn: StatusBarNotification, notificationId: Int): CachedReplyAction? {
    val notification = sbn.notification ?: return null
    val action = findReplyAction(notification) ?: return null
    val remoteInputs = action.remoteInputs ?: return null
    val intent = action.actionIntent ?: return null
    if (remoteInputs.isEmpty()) return null

    val sbnKey = sbn.key
    val primaryKey = if (!sbnKey.isNullOrEmpty()) sbnKey else "0|${sbn.packageName}|$notificationId||0"

    val cached = CachedReplyAction(
      key = primaryKey,
      notificationId = notificationId,
      packageName = sbn.packageName,
      actionIntent = intent,
      remoteInputs = remoteInputs,
    )

    synchronized(lock) {
      val existing = cache.put(primaryKey, cached)
      if (existing != null && existing.notificationId != notificationId) {
        idToKey.remove(existing.notificationId)
      }
      idToKey[notificationId] = primaryKey
    }
    return cached
  }

  fun store(action: CachedReplyAction) = synchronized(lock) {
    val existing = cache.put(action.key, action)
    if (existing != null && existing.notificationId != action.notificationId) {
      idToKey.remove(existing.notificationId)
    }
    idToKey[action.notificationId] = action.key
  }

  fun find(key: String, notificationId: Int? = null): CachedReplyAction? = synchronized(lock) {
    cleanExpired()
    val trimmedKey = key.trim()

    // 1. Direct exact primary key match (e.g. "0|com.whatsapp|42|null|10001")
    if (trimmedKey.isNotEmpty()) {
      cache[trimmedKey]?.let { return it }
    }

    // 2. Exact notification ID match (from reply packet subId integer)
    if (notificationId != null && notificationId != 0) {
      idToKey[notificationId]?.let { cache[it]?.let { return it } }
    }

    return null
  }

  fun remove(key: String): CachedReplyAction? = synchronized(lock) {
    val removed = cache.remove(key)
    if (removed != null) {
      idToKey.remove(removed.notificationId)
    }
    removed
  }

  fun clear() = synchronized(lock) {
    cache.clear()
    idToKey.clear()
  }

  private fun cleanExpired(ttlMs: Long = 3600_000L) {
    val cutoff = System.currentTimeMillis() - ttlMs
    val iterator = cache.values.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (entry.timestamp < cutoff) {
        idToKey.remove(entry.notificationId)
        iterator.remove()
      }
    }
  }
  companion object {
    fun findReplyAction(notification: Notification): Notification.Action? {
      // 1. Standard notification actions
      notification.actions?.firstOrNull { action ->
        action.remoteInputs != null && action.remoteInputs.isNotEmpty()
      }?.let { return it }

      // 2. Notification.WearableExtender actions (for wear-specific remote inputs)
      try {
        val wearableExtender = Notification.WearableExtender(notification)
        wearableExtender.actions?.firstOrNull { action ->
          action.remoteInputs != null && action.remoteInputs.isNotEmpty()
        }?.let { return it }
      } catch (_: Throwable) {
        // WearableExtender might throw on custom vendor OS flavors
      }

      return null
    }

    fun sendReply(
      context: Context,
      action: CachedReplyAction,
      replyText: String,
    ) {
      val intent = action.actionIntent
        ?: throw IllegalStateException("Missing PendingIntent for notification action")
      val inputs = action.remoteInputs?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("Missing RemoteInput for notification action")

      val sendIntent = Intent()
      val bundle = Bundle()
      for (input in inputs) {
        bundle.putCharSequence(input.resultKey, replyText)
      }
      RemoteInput.addResultsToIntent(inputs, sendIntent, bundle)
      intent.send(context, 0, sendIntent)
    }
  }
}
