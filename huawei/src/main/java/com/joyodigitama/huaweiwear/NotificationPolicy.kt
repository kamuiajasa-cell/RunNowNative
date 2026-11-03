package com.joyodigitama.huaweiwear

import android.app.Notification
import android.content.pm.ApplicationInfo

/** Which posted notifications are worth sending to the watch. Pure functions so they can be unit tested. */
internal object NotificationPolicy {
  /** Notification flags GB also treats as "not a message": ongoing and foreground-service entries. */
  private const val ONGOING_MASK = Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE

  /** Why a notification is dropped from its flags, category and package alone, before any text is read. Null when it passes. */
  fun skipReasonEarly(flags: Int, category: String?, sourcePackage: String, ownPackage: String): String? = when {
    sourcePackage == ownPackage -> "own app"
    flags and ONGOING_MASK != 0 -> "ongoing"
    flags and Notification.FLAG_GROUP_SUMMARY != 0 -> "group summary"
    flags and Notification.FLAG_LOCAL_ONLY != 0 -> "local only"
    // VoIP call notifications are taken before this filter by VoipCallNotifications; whatever call is left (dialer, ongoing) stays off the watch.
    category == Notification.CATEGORY_CALL -> "call (not forwarded)"
    category == Notification.CATEGORY_TRANSPORT -> "media controls"
    category == Notification.CATEGORY_PROGRESS -> "progress"
    else -> null
  }

  /** Why a notification is not forwarded, or null when it passes the content filters. */
  fun skipReason(
    flags: Int,
    category: String?,
    sourcePackage: String,
    ownPackage: String,
    title: String?,
    body: String?,
  ): String? = skipReasonEarly(flags, category, sourcePackage, ownPackage)
    ?: if (title.isNullOrBlank() && body.isNullOrBlank()) "empty" else null

  /** An explicit per-app choice wins; otherwise every app is on except system apps. */
  fun appAllowed(explicitChoice: Boolean?, isSystemApp: Boolean): Boolean = explicitChoice ?: !isSystemApp

  /** A preinstalled app that the user has not updated from a store. */
  fun isSystemApp(appFlags: Int): Boolean =
    appFlags and ApplicationInfo.FLAG_SYSTEM != 0 && appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
}
