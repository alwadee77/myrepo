package com.ess.portal

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleUtil {

    fun applyLocale(context: Context, langCode: String): Context {
        val locale = getLocale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun getLocale(langCode: String): Locale {
        if (langCode.startsWith("ar")) return Locale("ar")
        return Locale("en")
    }
}
