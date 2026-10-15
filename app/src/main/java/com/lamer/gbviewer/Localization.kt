package com.lamer.gbviewer

import android.content.res.Configuration
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import java.util.Locale

/** Ресурсы на выбранном языке приложения (не зависят от языка системы). */
fun localizedResources(base: Context, lang: String): Resources {
    val cfg = Configuration(base.resources.configuration)
    cfg.setLocale(Locale.forLanguageTag(lang))
    return base.createConfigurationContext(cfg).resources
}

/** Контекст, который отдаёт строки на выбранном языке; всё остальное берёт у Activity. */
class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}

data class AppLanguage(val code: String, val name: String)

/**
 * Языки приложения (показываются в выпадающем списке в настройках).
 * Чтобы добавить язык: допишите его сюда и создайте файл res/values-<код>/strings.xml с переводом.
 * Название пишется на самом языке.
 */
val appLanguages = listOf(
    AppLanguage("en", "English"),
    AppLanguage("ru", "Русский"),
    AppLanguage("de", "Deutsch"),
    AppLanguage("ko", "한국어"),
    AppLanguage("ja", "日本語"),
    AppLanguage("zh", "中文"),
)
