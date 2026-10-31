package com.example.ethos.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String?) { SYSTEM(null), GERMAN("de"), ENGLISH("en") }

/**
 * In-app language. Kept in plain SharedPreferences instead of DataStore because it is needed
 * synchronously before the first screen exists (attachBaseContext).
 */
object AppLocale {
    private const val PREFS = "app_locale"
    private const val KEY = "language"

    fun get(context: Context): AppLanguage =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
            ?: AppLanguage.SYSTEM

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).commit()
    }

    /**
     * [base] with the chosen language applied. Also sets the default locale, which the date
     * formatters use. With SYSTEM, [base] is returned unchanged.
     */
    fun wrap(base: Context): Context {
        val tag = get(base).tag
        if (tag == null) {
            Locale.setDefault(base.resources.configuration.locales[0])
            return base
        }
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}

/** Context for texts outside of activities (notifications, view models, exports). */
fun Context.localized(): Context = AppLocale.wrap(this)
