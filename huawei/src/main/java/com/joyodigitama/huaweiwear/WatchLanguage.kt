// Derived from Gadgetbridge (AGPL-3.0-or-later; see NOTICE): service/devices/huawei/requests/SetLanguageSettingRequest,
// devices/huawei/packets/LocaleConfig; the trigger and the tag follow Huawei Health's LanguageUtil / LanguageChangedReceiver.
package com.joyodigitama.huaweiwear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.LocaleConfig

/** SetLanguageSetting (service 0C, command 01): tells the watch the phone's language, only when the watch lists the command. */
internal object LanguageSender {
  fun supported(): Boolean = DeviceCaps.supports(LocaleConfig.id.toInt(), LocaleConfig.SetLanguageSetting.id.toInt())

  /** The phone's first language, as Huawei Health reads it (`Resources.getConfiguration()`, so a per-app language counts). */
  fun phoneLocale(context: Context): Locale {
    val locales = context.resources.configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales.get(0)
  }

  /**
   * Sends what [WatchLocale.resolve] makes of [pinned] and the phone language; true when the watch acknowledged. JoyoFit has no unit
   * setting and shows kilometres, so the unit tag is always metric.
   */
  suspend fun send(session: HuaweiSession, events: WearEvents, pinned: String?, phone: Locale): Boolean {
    if (!supported()) {
      events.log(LogDirection.INFO, null, null, null, "LanguageSetting: the watch does not list 0C/01, not sent")
      return false
    }
    val setting = WatchLocale.resolve(pinned, phone, DeviceCaps.expandCapability(WatchLocale.SCRIPT_CAPABILITY))
    val request = LocaleConfig.SetLanguageSetting(
      session.params,
      setting.tag.toByteArray(Charsets.UTF_8),
      LocaleConfig.MeasurementSystem.metric,
      setting.script?.toByteArray(Charsets.UTF_8),
    )
    session.request(request, Timeouts.INIT_MS, "LanguageSetting")
    events.log(
      LogDirection.INFO, null, null, null,
      "LanguageSetting: ${setting.tag}${setting.script?.let { " script $it" } ?: ""}, metric (${if (pinned == null) "phone language" else "app language $pinned"})",
    )
    return true
  }
}

/**
 * The watch's language follows JoyoFit's language (the "Bahasa" choice in Settings, when pinned) or the phone's. It is sent at every
 * connect by [HuaweiInitializer]; this class sends it again while connected when either changes, as Huawei Health does on
 * `ACTION_LOCALE_CHANGED`.
 */
internal class WatchLanguage(
  private val context: Context,
  private val settings: WearSettings,
  private val link: WearLink,
  private val events: WearEvents,
  private val scope: CoroutineScope,
) {
  private val receiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != Intent.ACTION_LOCALE_CHANGED) return
      // A pinned app language does not move with the phone.
      if (settings.watchLanguage == null) scope.launch { resend("the phone language changed") }
    }
  }

  init {
    val filter = IntentFilter(Intent.ACTION_LOCALE_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      context.registerReceiver(receiver, filter)
    }
  }

  /** The app language the watch follows; null follows the phone. Returns true when the connected watch took it. */
  suspend fun setPinned(language: String?): Boolean {
    val value = language?.trim()?.takeIf { it.isNotEmpty() }
    if (value != null && !PINNED.matches(value)) throw WearException(ErrorCodes.INVALID_ARGUMENT, "'$language' is not a language tag")
    if (settings.watchLanguage == value) return false
    settings.watchLanguage = value
    return resend("the app language changed")
  }

  /** Sends the language to the connected watch; false when none is connected or the watch did not take it. */
  suspend fun resend(reason: String): Boolean {
    val handle = link.handle() ?: return false
    return try {
      handle.operationLock.withLock {
        LanguageSender.send(handle.session, events, settings.watchLanguage, LanguageSender.phoneLocale(context))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      events.log(LogDirection.ERROR, null, null, null, "LanguageSetting not sent ($reason): ${e.message ?: e.javaClass.simpleName}")
      false
    }
  }

  private companion object {
    /** `id`, `en`, `en-US`, `zh-Hant-TW`: what `Locale.forLanguageTag` can read. */
    val PINNED = Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,2}")
  }
}
