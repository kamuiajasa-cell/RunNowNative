package com.joyodigitama.huaweiwear

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager

/** One VoIP call notification: what the watch shows and the notification's own answer/decline actions. */
internal class VoipCall(
  val key: String,
  val appName: String,
  val caller: String,
  val answer: PendingIntent?,
  val decline: PendingIntent?,
)

/**
 * Reads `CATEGORY_CALL` notifications from VoIP apps and hands them to the [CallForwarder], so a
 * WhatsApp, Telegram, Signal, Meet or LINE call rings the watch like a phone call. The rules are in
 * [VoipCallPolicy]:
 * - the system dialer is ignored here (its calls come from `PhoneStateReceiver`, so no double ring);
 * - only an incoming call rings; an ongoing one ("Ongoing voice call") never does;
 * - the per-app choice in Settings applies (an app the user switched off does not ring);
 * - the switch for phone calls ("Show incoming calls on the watch") also gates VoIP calls, but the
 *   all-notifications switch does not.
 */
internal class VoipCallNotifications(
  context: Context,
  private val settings: WearSettings,
  private val calls: CallForwarder,
) {
  private val appContext = context.applicationContext
  private val ownPackage = appContext.packageName

  /** True when [sbn] is a call notification and was handled here, so it is not also forwarded as an ordinary one. */
  fun onPosted(sbn: StatusBarNotification): Boolean {
    val notification = sbn.notification ?: return false
    val callType = callType(notification)
    if (!VoipCallPolicy.isCall(notification.category, callType)) return false
    val packageName = sbn.packageName
    if (packageName == ownPackage) return false
    if (VoipCallPolicy.isDialer(packageName, defaultDialer())) return true

    val extras = notification.extras
    val actions = notification.actions?.toList().orEmpty()
    val titles = actions.map { it.title }
    val incoming = VoipCallPolicy.isIncoming(callType, notification.flags, notification.fullScreenIntent != null, titles)
    val info = try {
      appContext.packageManager.getApplicationInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
      null
    }
    // An answered call (no longer incoming) must still end the ring, so only a ring is subject to the app choice.
    if (incoming && !NotificationPolicy.appAllowed(settings.appChoice(packageName), info != null && NotificationPolicy.isSystemApp(info.flags))) return true

    val appName = info?.let { appContext.packageManager.getApplicationLabel(it).toString() }?.takeIf { it.isNotBlank() } ?: packageName
    val found = VoipCallPolicy.findActions(titles, callStyleIncoming = callType == VoipCallPolicy.CALL_TYPE_INCOMING)
    val caller = VoipCallPolicy.callerName(
      personName = personName(notification),
      title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let(::sanitize),
      appName = appName,
    )
    calls.onVoipPosted(
      VoipCall(
        key = sbn.key ?: "$packageName:${sbn.id}",
        appName = appName,
        caller = caller,
        answer = pending(extras, Notification.EXTRA_ANSWER_INTENT) ?: found.answer?.let { actions[it].actionIntent },
        decline = pending(extras, Notification.EXTRA_DECLINE_INTENT) ?: found.decline?.let { actions[it].actionIntent },
      ),
      incoming,
    )
    return true
  }

  fun onRemoved(sbn: StatusBarNotification) {
    calls.onVoipRemoved(sbn.key ?: "${sbn.packageName}:${sbn.id}")
  }

  private fun defaultDialer(): String? = appContext.getSystemService(TelecomManager::class.java)?.defaultDialerPackage

  /** `Notification.CallStyle`'s call type, API 31+; null for a notification without CallStyle. */
  private fun callType(notification: Notification): Int? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val extras = notification.extras ?: return null
    return if (extras.containsKey(Notification.EXTRA_CALL_TYPE)) extras.getInt(Notification.EXTRA_CALL_TYPE) else null
  }

  private fun personName(notification: Notification): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    @Suppress("DEPRECATION")
    val person = notification.extras?.getParcelable<android.app.Person>(Notification.EXTRA_CALL_PERSON)
    return person?.name?.toString()?.let(::sanitize)
  }

  private fun pending(extras: android.os.Bundle?, key: String): PendingIntent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || extras == null) return null
    @Suppress("DEPRECATION")
    return extras.getParcelable<PendingIntent>(key)
  }

  private fun sanitize(text: String): String = text.filter { !Character.isISOControl(it) || it == '\n' }
}
