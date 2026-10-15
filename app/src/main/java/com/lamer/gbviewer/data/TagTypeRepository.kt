package com.lamer.gbviewer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Типы тегов (artist / character / metadata / обычный). Запоминаются на устройстве,
 * поэтому порядок тегов работает и без интернета для уже виденных тегов.
 * Порядок: artist, copyright, character, metadata, остальные.
 */
class TagTypeRepository(context: Context, private val api: GelbooruApi) {
    private val file = File(context.filesDir, "tag_types.json")
    private val cache = ConcurrentHashMap<String, Int>()

    init {
        try {
            if (file.exists()) {
                val o = JSONObject(file.readText())
                o.keys().forEach { cache[it] = o.optInt(it) }
            }
        } catch (e: Exception) {
            // повреждённый кэш не страшен: загрузим заново
        }
    }

    /** Докачивает типы для тегов, которых ещё нет в кэше. */
    suspend fun load(tags: List<String>) {
        val missing = tags.filter { !cache.containsKey(it) }
        if (missing.isEmpty()) return
        val result = api.tagTypes(missing)
        result.forEach { (name, type) -> cache[name] = type }
        missing.forEach { cache.putIfAbsent(it, 0) }
        withContext(Dispatchers.IO) {
            synchronized(file) {
                try {
                    file.writeText(JSONObject(cache.toMap()).toString())
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun rank(type: Int?): Int = when (type) {
        1 -> 0 // artist
        3 -> 1 // copyright
        4 -> 2 // character
        5 -> 3 // metadata
        else -> 4 // обычные теги
    }

    fun typeOf(tag: String): Int? = cache[tag]

    /** Сортировка по типу + скрытие copyright / metadata по настройкам. */
    fun prepare(tags: List<String>, hideCopyright: Boolean, hideMetadata: Boolean): List<String> =
        sorted(tags).filter {
            val t = cache[it]
            !(hideCopyright && t == 3) && !(hideMetadata && t == 5)
        }

    /** Сортирует по типу; внутри одного типа сохраняется исходный порядок. */
    fun sorted(tags: List<String>): List<String> = tags.sortedBy { rank(cache[it]) }
}
