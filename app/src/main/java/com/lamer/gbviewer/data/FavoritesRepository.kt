package com.lamer.gbviewer.data
import com.lamer.gbviewer.R
import com.lamer.gbviewer.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Избранное хранится во внутренней памяти приложения (filesDir/favorites):
 *   <id>.<ext>  — сама картинка (оригинал)
 *   <id>.json   — метаданные (теги, размеры, дата добавления)
 * Внутренняя папка приложения не индексируется галереей.
 */
class FavoritesRepository(
    private val context: Context,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val dir = File(context.filesDir, "favorites").also { it.mkdirs() }

    private val _items = MutableStateFlow<List<FavoriteItem>>(emptyList())
    val items: StateFlow<List<FavoriteItem>> = _items

    /** id постов, которые сейчас скачиваются. */
    private val _busy = MutableStateFlow<Set<Long>>(emptySet())
    val busy: StateFlow<Set<Long>> = _busy

    init {
        scope.launch(Dispatchers.IO) {
            val loaded = readAll()
            _items.update { current ->
                (current + loaded).distinctBy { it.id }.sortedByDescending { it.addedAt }
            }
        }
    }

    fun file(item: FavoriteItem): File = File(dir, "${item.id}.${item.ext}")

    fun thumbFile(item: FavoriteItem): File = File(dir, "${item.id}.thumb")

    /** Что показывать в сетке: для картинок — сам файл, для GIF/видео — сохранённое превью. */
    fun gridModel(item: FavoriteItem): File {
        if (item.kind == MediaKind.IMAGE) return file(item)
        val thumb = thumbFile(item)
        return if (thumb.exists() || item.kind == MediaKind.VIDEO) thumb else file(item)
    }

    fun isFavorite(id: Long): Boolean = _items.value.any { it.id == id }

    private fun readAll(): List<FavoriteItem> {
        val jsons = dir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return jsons.mapNotNull { f ->
            try {
                val o = JSONObject(f.readText())
                val tags = o.optJSONArray("tags")
                val item = FavoriteItem(
                    id = o.getLong("id"),
                    ext = o.getString("ext"),
                    tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
                    addedAt = o.optLong("addedAt"),
                    width = o.optInt("width"),
                    height = o.optInt("height"),
                )
                if (file(item).exists()) item else null
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.addedAt }
    }

    fun add(post: Post) {
        if (isFavorite(post.id) || post.id in _busy.value) return
        _busy.update { it + post.id }
        scope.launch {
            try {
                val item = withContext(Dispatchers.IO) { download(post) }
                _items.update { (listOf(item) + it).distinctBy { i -> i.id }.sortedByDescending { i -> i.addedAt } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.app.str(
                        R.string.save_failed_fmt,
                        e.message ?: context.app.str(R.string.err_network_short)
                    ),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                _busy.update { it - post.id }
            }
        }
    }

    private fun download(post: Post): FavoriteItem {
        val ext = post.extension.ifBlank { "jpg" }
        val tmp = File(dir, "${post.id}.tmp")
        val target = File(dir, "${post.id}.$ext")
        try {
            val request = Request.Builder().url(post.fileUrl).build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException(context.app.str(R.string.err_empty_response))
                body.byteStream().use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException(context.app.str(R.string.err_write_failed))
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
        val item = FavoriteItem(
            id = post.id,
            ext = ext,
            tags = post.tags,
            addedAt = System.currentTimeMillis(),
            width = post.width,
            height = post.height,
        )
        val json = JSONObject()
            .put("id", item.id)
            .put("ext", item.ext)
            .put("tags", JSONArray(item.tags))
            .put("addedAt", item.addedAt)
            .put("width", item.width)
            .put("height", item.height)
        File(dir, "${post.id}.json").writeText(json.toString())
        if (item.kind != MediaKind.IMAGE && post.previewUrl.isNotBlank()) {
            val thumb = File(dir, "${post.id}.thumb")
            try {
                fetchTo(post.previewUrl, thumb)
            } catch (e: Exception) {
                thumb.delete()
            }
        }
        return item
    }

    private fun fetchTo(url: String, target: File) {
        val tmp = File(target.path + ".tmp")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException(context.app.str(R.string.err_empty_response))
                body.byteStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } finally {
            tmp.delete()
        }
    }

    fun removeMany(ids: Set<Long>) {
        val removed = _items.value.filter { it.id in ids }
        _items.update { list -> list.filterNot { it.id in ids } }
        scope.launch(Dispatchers.IO) {
            removed.forEach {
                file(it).delete()
                thumbFile(it).delete()
                File(dir, "${it.id}.json").delete()
            }
        }
    }

    /** Общий вес папки избранного в байтах. */
    fun sizeBytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Резервная копия: один zip-файл со всем содержимым папки избранного. */
    suspend fun exportTo(out: OutputStream) = withContext(Dispatchers.IO) {
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.setLevel(0) // картинки и видео уже сжаты
            dir.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".tmp") }
                ?.forEach { f ->
                    zip.putNextEntry(ZipEntry(f.name))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    /**
     * Загрузка резервной копии. replace = true — заменить текущее избранное,
     * false — добавить к текущему (посты, которые уже есть, пропускаются).
     * Возвращает число загруженных постов.
     */
    suspend fun importFrom(input: InputStream, replace: Boolean): Int = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "import_tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = File(entry.name).name
                    if (!entry.isDirectory && NAME_REGEX.matches(name)) {
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }
            val files = tmp.listFiles().orEmpty()
            if (files.none { it.extension == "json" }) {
                throw IOException(context.app.str(R.string.err_not_backup))
            }
            val existing = if (replace) emptySet() else _items.value.map { it.id }.toSet()
            if (replace) {
                dir.listFiles()?.forEach { if (it.isFile) it.delete() }
            }
            var imported = 0
            for (f in files) {
                val id = f.name.substringBefore('.').toLongOrNull() ?: continue
                if (id in existing) continue
                val target = File(dir, f.name)
                if (!f.renameTo(target)) {
                    f.copyTo(target, overwrite = true)
                    f.delete()
                }
                if (target.extension == "json") imported++
            }
            _items.value = readAll()
            imported
        } finally {
            tmp.deleteRecursively()
        }
    }

    fun remove(id: Long) {
        val item = _items.value.firstOrNull { it.id == id }
        _items.update { list -> list.filterNot { it.id == id } }
        scope.launch(Dispatchers.IO) {
            if (item != null) file(item).delete()
            File(dir, "$id.json").delete()
            File(dir, "$id.thumb").delete()
        }
    }
}

private val NAME_REGEX = Regex("^\\d+\\.[A-Za-z0-9]{1,5}$")

/** Подходит ли шаблон (точный тег или с * как подстановкой) хотя бы к одному тегу. */
private fun matchesAny(tags: List<String>, pattern: String): Boolean {
    if (!pattern.contains('*')) return tags.contains(pattern)
    val regex = Regex(pattern.split('*').joinToString(".*") { Regex.escape(it) })
    return tags.any { regex.matches(it) }
}

/** Фильтр избранного: "tag" — должен быть, "-tag" — не должен быть. */
fun filterFavorites(items: List<FavoriteItem>, filter: List<String>): List<FavoriteItem> {
    if (filter.isEmpty()) return items
    val positive = filter.filter { !it.startsWith("-") }
    val negative = filter.filter { it.startsWith("-") }.map { it.removePrefix("-") }.filter { it.isNotEmpty() }
    return items.filter { item ->
        positive.all { matchesAny(item.tags, it) } && negative.none { matchesAny(item.tags, it) }
    }
}

private fun mix(x: Long): Long {
    var z = x + -7046029254386353131L
    z = (z xor (z ushr 30)) * -4658895280553007687L
    z = (z xor (z ushr 27)) * -7723592293110705685L
    return z xor (z ushr 31)
}

/**
 * Порядок показа избранного. seed == null — обычный (новые сверху);
 * иначе перемешанный. Перемешивание привязано к id поста, поэтому при удалении
 * или фильтрации остальные посты не «прыгают» с места на место.
 */
fun orderFavorites(list: List<FavoriteItem>, seed: Long?): List<FavoriteItem> =
    if (seed == null) list else list.sortedBy { mix(it.id xor seed) }
