package com.joyodigitama.huaweiwear

import android.app.Notification

/**
 * Pure decisions for VoIP calls (WhatsApp, Telegram, Signal, Meet, LINE ...) that reach the phone as
 * `CATEGORY_CALL` notifications instead of telephony broadcasts. No GB counterpart: GB's notification
 * listener only ever forwards these as ordinary notifications.
 */
internal object VoipCallPolicy {
  /** The call-type values of `Notification.CallStyle` (API 31+), also the `EXTRA_CALL_TYPE` extra. */
  const val CALL_TYPE_INCOMING = Notification.CallStyle.CALL_TYPE_INCOMING
  const val CALL_TYPE_ONGOING = Notification.CallStyle.CALL_TYPE_ONGOING
  const val CALL_TYPE_SCREENING = Notification.CallStyle.CALL_TYPE_SCREENING

  /** Dialer and telecom packages: their calls reach the watch through `PhoneStateReceiver`, so they must not ring twice. */
  private val SYSTEM_CALL_PACKAGES = setOf(
    "com.android.dialer",
    "com.google.android.dialer",
    "com.android.incallui",
    "com.samsung.android.incallui",
    "com.android.server.telecom",
    "com.android.phone",
    "com.android.contacts",
    "com.google.android.contacts",
  )

  private val DECLINE_WORDS = listOf("decline", "reject", "dismiss", "hang up", "hangup", "end call", "ignore", "tolak", "tutup", "akhiri", "abaikan")
  private val ANSWER_WORDS = listOf("answer", "accept", "pick up", "jawab", "terima", "angkat")

  /** A call notification: the call category, or a CallStyle with a call type. */
  fun isCall(category: String?, callType: Int?): Boolean = category == Notification.CATEGORY_CALL || callType != null

  /** The system dialer path (the default dialer, or a known dialer/telecom package). */
  fun isDialer(packageName: String, defaultDialer: String?): Boolean =
    packageName == defaultDialer || packageName in SYSTEM_CALL_PACKAGES

  /**
   * Whether a call notification is a ringing incoming call rather than an ongoing one.
   *
   * - A CallStyle call type is authoritative: only `CALL_TYPE_INCOMING` rings (ongoing and screening do not).
   * - Without CallStyle, an incoming call posts a full-screen intent (the incoming-call screen) or an
   *   insistent alert (repeating ring), or offers both an answer and a decline action. "Ongoing voice call"
   *   notifications have none of these: they are `FLAG_ONGOING_EVENT` with a single hang-up action.
   *
   * `FLAG_ONGOING_EVENT` alone decides nothing: many apps set it on the incoming-call notification too.
   */
  fun isIncoming(callType: Int?, flags: Int, hasFullScreenIntent: Boolean, actionTitles: List<CharSequence?>): Boolean = when {
    callType != null -> callType == CALL_TYPE_INCOMING
    hasFullScreenIntent -> true
    flags and Notification.FLAG_INSISTENT != 0 -> true
    else -> findActions(actionTitles, callStyleIncoming = false).let { it.answer != null && it.decline != null }
  }

  /** The name the watch shows: the CallStyle person, else the title, else the app name. */
  fun callerName(personName: String?, title: String?, appName: String): String =
    listOf(personName, title).firstOrNull { !it.isNullOrBlank() }?.trim() ?: appName

  /** Indexes into the notification's actions. */
  data class Actions(val answer: Int?, val decline: Int?)

  /**
   * Which actions answer and decline. Titles are matched first. A CallStyle incoming notification lists
   * the decline action first and the answer action last, which covers the app-supplied titles this
   * word list does not know.
   */
  fun findActions(titles: List<CharSequence?>, callStyleIncoming: Boolean): Actions {
    fun find(words: List<String>): Int? = titles.indexOfFirst { title ->
      val text = title?.toString()?.lowercase() ?: return@indexOfFirst false
      words.any { it in text }
    }.takeIf { it >= 0 }
    var decline = find(DECLINE_WORDS)
    var answer = find(ANSWER_WORDS)
    if (callStyleIncoming && titles.size >= 2) {
      if (decline == null && answer != 0) decline = 0
      if (answer == null && decline != titles.size - 1) answer = titles.size - 1
    }
    return Actions(answer, decline)
  }

  /** What the call announcement has to do after one notification event. */
  enum class Step {
    /** An incoming call started ringing: send the call packet. */
    RING,

    /** The ringing call became an ongoing one (answered on the phone): send the start-call packet. */
    ANSWERED,

    /** The ringing call is gone (declined, missed, ended): send the stop packet. */
    STOP,

    /** Nothing to send. */
    NONE,
  }
}

/**
 * The VoIP calls that are ringing on the watch, by notification key, each with the [A] needed to answer
 * or decline it. Apps repost the same key while ringing, so a repeat is [VoipCallPolicy.Step.NONE]; a
 * key that stops being incoming (answered) or is removed ends the ring. With two calls ringing at once
 * the watch shows one, and it is stopped when either is removed.
 */
internal class VoipCallTracker<A> {
  private val lock = Any()
  private val ringing = LinkedHashMap<String, A>()

  /** A call notification was posted or updated. */
  fun onPosted(key: String, incoming: Boolean, payload: A): VoipCallPolicy.Step = synchronized(lock) {
    if (incoming) {
      val wasRinging = ringing.containsKey(key)
      ringing[key] = payload
      if (wasRinging) VoipCallPolicy.Step.NONE else VoipCallPolicy.Step.RING
    } else if (ringing.remove(key) != null) {
      VoipCallPolicy.Step.ANSWERED
    } else {
      VoipCallPolicy.Step.NONE
    }
  }

  /** The notification was removed. */
  fun onRemoved(key: String): VoipCallPolicy.Step = synchronized(lock) {
    if (ringing.remove(key) != null) VoipCallPolicy.Step.STOP else VoipCallPolicy.Step.NONE
  }

  /** The most recent ringing call, for the watch's answer/reject buttons. */
  fun current(): A? = synchronized(lock) { ringing.values.lastOrNull() }
}
