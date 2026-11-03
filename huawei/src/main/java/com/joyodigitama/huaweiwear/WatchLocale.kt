// The tag follows Huawei Health 17.0.8.300's LanguageUtil (service 0x0C command 0x01) and Gadgetbridge's SetLanguageSettingRequest.
package com.joyodigitama.huaweiwear

import java.util.Locale

/**
 * The language the watch is told about: the `xx-YY` tag and, for watches with ExpandCapability 57, the script. Huawei Health sends
 * the phone's language at every connect and on every system language change; a watch that was never told one keeps its factory
 * locale (UNVERIFIED on a watch: the spoken workout prompts in Chinese on a reset watch are the symptom this answers).
 */
internal object WatchLocale {
  /** ExpandCapability bit that makes Huawei Health add the script tag (`LanguageUtil`: `egs.e(device, 57)`). */
  const val SCRIPT_CAPABILITY = 57

  /** What goes into one SetLanguageSetting: [tag] e.g. `in-ID`, [script] e.g. `Latn` or null (tag left out). */
  data class Setting(val tag: String, val script: String?)

  /**
   * Huawei Health runs the language through `CommonUtil.c(Locale)`: Java's old codes, so Indonesian is `in` (not `id`), Hebrew `iw`
   * and Yiddish `ji`. Android's own `Locale.getLanguage()` already answers that way; the mapping keeps the tag the same on a JVM that
   * does not (the unit tests) and for the `id` the JS side sends.
   */
  fun legacyLanguage(language: String): String = when (val lower = language.trim().lowercase(Locale.ROOT)) {
    "id" -> "in"
    "he" -> "iw"
    "yi" -> "ji"
    else -> lower
  }

  /** For a pinned language that carries no country (`id`, `en`): the country the watch's own table has for it. */
  private val defaultCountry = mapOf("in" to "ID", "en" to "US", "zh" to "CN", "ms" to "MY")

  /**
   * `xx-YY` for [locale]. Without ExpandCapability 57 Huawei Health's `CommonUtil.ab()` writes traditional Chinese outside Taiwan and
   * Hong Kong as `zh-CHT`; with it the script goes into its own tag instead.
   */
  fun tag(locale: Locale, scriptCapable: Boolean): String {
    val language = legacyLanguage(locale.language).ifEmpty { "en" }
    val country = locale.country.uppercase(Locale.ROOT).ifEmpty { defaultCountry[language] ?: language.uppercase(Locale.ROOT) }
    if (language == "zh" && !scriptCapable) {
      val script = locale.script
      if (script.isNotEmpty() && !script.equals("Hans", ignoreCase = true) && country != "TW" && country != "HK") return "zh-CHT"
    }
    return "$language-$country"
  }

  /**
   * The setting to send: the language JoyoFit was pinned to in Settings ([pinned], a BCP-47 tag such as `id` or `en-US`), else the
   * phone's [phone] locale (the app's "Otomatis").
   */
  fun resolve(pinned: String?, phone: Locale, scriptCapable: Boolean): Setting {
    val locale = pinnedLocale(pinned) ?: phone
    val script = if (scriptCapable) locale.script.takeIf { it.isNotEmpty() } else null
    return Setting(tag(locale, scriptCapable), script)
  }

  private fun pinnedLocale(pinned: String?): Locale? {
    val text = pinned?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    return Locale.forLanguageTag(text).takeIf { it.language.isNotEmpty() }
  }
}
