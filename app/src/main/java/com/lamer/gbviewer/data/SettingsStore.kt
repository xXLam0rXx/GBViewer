package com.lamer.gbviewer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val apiKey = MutableStateFlow(prefs.getString("api_key", "") ?: "")
    val userId = MutableStateFlow(prefs.getString("user_id", "") ?: "")
    val theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString("theme", "DARK") ?: "DARK") }
            .getOrDefault(ThemeMode.DARK)
    )
    val blacklist = MutableStateFlow(
        (prefs.getString("blacklist", "") ?: "").split(' ').filter { it.isNotBlank() }
    )

    val hideCopyright = MutableStateFlow(prefs.getBoolean("hide_copyright", false))
    val hideMetadata = MutableStateFlow(prefs.getBoolean("hide_metadata", false))

    fun setHideCopyright(value: Boolean) {
        hideCopyright.value = value
        prefs.edit().putBoolean("hide_copyright", value).apply()
    }

    fun setHideMetadata(value: Boolean) {
        hideMetadata.value = value
        prefs.edit().putBoolean("hide_metadata", value).apply()
    }

    val searchColumns = MutableStateFlow(prefs.getInt("search_columns", 2))
    val favoritesColumns = MutableStateFlow(prefs.getInt("favorites_columns", 2))
    val autoplayVideo = MutableStateFlow(prefs.getBoolean("autoplay_video", true))
    val videoSound = MutableStateFlow(prefs.getBoolean("video_sound", true))
    val showComments = MutableStateFlow(prefs.getBoolean("show_comments", true))
    val swipePosts = MutableStateFlow(prefs.getBoolean("swipe_posts", true))

    fun setSearchColumns(v: Int) {
        searchColumns.value = v
        prefs.edit().putInt("search_columns", v).apply()
    }

    fun setFavoritesColumns(v: Int) {
        favoritesColumns.value = v
        prefs.edit().putInt("favorites_columns", v).apply()
    }

    fun setAutoplayVideo(v: Boolean) {
        autoplayVideo.value = v
        prefs.edit().putBoolean("autoplay_video", v).apply()
    }

    fun setVideoSound(v: Boolean) {
        videoSound.value = v
        prefs.edit().putBoolean("video_sound", v).apply()
    }

    fun setShowComments(v: Boolean) {
        showComments.value = v
        prefs.edit().putBoolean("show_comments", v).apply()
    }

    fun setSwipePosts(v: Boolean) {
        swipePosts.value = v
        prefs.edit().putBoolean("swipe_posts", v).apply()
    }

    val alwaysShuffleFavorites = MutableStateFlow(prefs.getBoolean("always_shuffle_favorites", false))

    fun setAlwaysShuffleFavorites(v: Boolean) {
        alwaysShuffleFavorites.value = v
        prefs.edit().putBoolean("always_shuffle_favorites", v).apply()
    }

    /** Язык интерфейса: "en" (по умолчанию) или "ru". */
    val language = MutableStateFlow(prefs.getString("language", "en") ?: "en")

    fun setLanguage(code: String) {
        language.value = code
        prefs.edit().putString("language", code).apply()
    }

    /** Цвет акцента (ARGB). null — стандартный цвет темы. */
    val accentColor = MutableStateFlow<Int?>(
        if (prefs.contains("accent_color")) prefs.getInt("accent_color", 0) else null
    )

    fun setAccentColor(argb: Int?) {
        accentColor.value = argb
        if (argb == null) prefs.edit().remove("accent_color").apply()
        else prefs.edit().putInt("accent_color", argb).apply()
    }

    fun setApiKey(value: String) {
        apiKey.value = value
        prefs.edit().putString("api_key", value).apply()
    }

    fun setUserId(value: String) {
        userId.value = value
        prefs.edit().putString("user_id", value).apply()
    }

    fun setTheme(mode: ThemeMode) {
        theme.value = mode
        prefs.edit().putString("theme", mode.name).apply()
    }

    fun setBlacklist(tags: List<String>) {
        blacklist.value = tags
        prefs.edit().putString("blacklist", tags.joinToString(" ")).apply()
    }
}
