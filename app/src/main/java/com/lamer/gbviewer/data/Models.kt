package com.lamer.gbviewer.data
import androidx.annotation.StringRes
import com.lamer.gbviewer.R

import java.text.SimpleDateFormat
import java.util.Locale

data class Post(
    val id: Long,
    val tags: List<String>,
    val fileUrl: String,
    val sampleUrl: String,
    val previewUrl: String,
    val width: Int,
    val height: Int,
    val rating: String,
    val score: Int,
    val createdAt: String = "",
) {
    /** Дата публикации в виде дд.мм.гггг (пусто, если не удалось разобрать). */
    val publishedDate: String
        get() = try {
            val src = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)
            val date = src.parse(createdAt.trim().replace(Regex("\\s+"), " "))
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date!!)
        } catch (e: Exception) {
            ""
        }

    /** Что показывать на странице поста: сэмпл (если есть), иначе оригинал. */
    val displayUrl: String get() = sampleUrl.ifBlank { fileUrl }

    /** Что показывать в сетке поиска. */
    val gridUrl: String get() = previewUrl.ifBlank { displayUrl }

    val extension: String
        get() = fileUrl.substringBefore('?').substringAfterLast('.', "").lowercase()

    /** Картинки, GIF и видео. */
    val isSupported: Boolean
        get() = fileUrl.isNotBlank() && extension in SUPPORTED_EXTENSIONS

    val kind: MediaKind get() = mediaKindOf(extension)

    companion object {
        val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "mp4", "webm", "mkv")
    }
}

data class FavoriteItem(
    val id: Long,
    val ext: String,
    val tags: List<String>,
    val addedAt: Long,
    val width: Int,
    val height: Int,
) {
    val kind: MediaKind get() = mediaKindOf(ext)
}

enum class MediaKind { IMAGE, GIF, VIDEO }

fun mediaKindOf(ext: String): MediaKind = when (ext.lowercase()) {
    "gif" -> MediaKind.GIF
    "mp4", "webm", "mkv" -> MediaKind.VIDEO
    else -> MediaKind.IMAGE
}

enum class ThemeMode(@StringRes val titleRes: Int) {
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
    AMOLED(R.string.theme_amoled),
}

data class Comment(
    val creator: String,
    val body: String,
    val createdAt: String,
)
