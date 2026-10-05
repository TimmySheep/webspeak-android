package com.echosixhiya.webspeak.android.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/** Applies the selected per-app locale to non-Activity contexts such as the foreground service. */
object AppLocaleContext {
    fun applicationLanguageTag(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager == null) {
                ""
            } else {
                val current = localeManager.applicationLocales.toLanguageTags()
                if (current.isNotBlank()) {
                    val preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    if (preferences.contains(KEY_LANGUAGE_TAG)) preferences.edit().remove(KEY_LANGUAGE_TAG).apply()
                    current
                } else {
                    val preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                    val legacyTag = preferences.getString(KEY_LANGUAGE_TAG, "").orEmpty()
                    if (legacyTag.isBlank()) {
                        ""
                    } else {
                        localeManager.applicationLocales = LocaleList.forLanguageTags(legacyTag)
                        preferences.edit().remove(KEY_LANGUAGE_TAG).apply()
                        localeManager.applicationLocales.toLanguageTags()
                    }
                }
            }
        } else {
            context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getString(KEY_LANGUAGE_TAG, "").orEmpty()
        }
    }

    fun setApplicationLanguage(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireNotNull(context.getSystemService(LocaleManager::class.java)).applicationLocales = LocaleList.forLanguageTags(languageTag)
            val preferences = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            if (preferences.contains(KEY_LANGUAGE_TAG)) preferences.edit().remove(KEY_LANGUAGE_TAG).apply()
        } else {
            context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putString(KEY_LANGUAGE_TAG, languageTag).apply()
            (context as? Activity)?.recreate()
        }
    }

    fun wrap(context: Context): Context {
        val requestedLanguageTags = applicationLanguageTag(context)
        val languageTags = if (requestedLanguageTags.isBlank()) {
            val systemLanguage = context.resources.configuration.locales[0].language.lowercase()
            if (systemLanguage in SUPPORTED_LANGUAGES) return context else DEFAULT_LANGUAGE_TAG
        } else {
            requestedLanguageTags
        }

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList.forLanguageTags(languageTags))
        return context.createConfigurationContext(configuration)
    }

    private const val PREFERENCES = "webspeak_language_preferences"
    private const val KEY_LANGUAGE_TAG = "application_language_tag"
    private const val DEFAULT_LANGUAGE_TAG = "zh-CN"
    private val SUPPORTED_LANGUAGES = setOf("zh", "en", "fr", "de", "ja", "es")
}
