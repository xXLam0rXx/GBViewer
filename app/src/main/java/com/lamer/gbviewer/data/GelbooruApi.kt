package com.lamer.gbviewer.data
import com.lamer.gbviewer.GBApp
import com.lamer.gbviewer.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class GelbooruApi(
    private val app: GBApp,
    private val client: OkHttpClient,
    private val settings: SettingsStore,
) {
    private val base = "https://gelbooru.com/index.php".toHttpUrl()

    private fun builder(section: String): HttpUrl.Builder {
        val b = base.newBuilder()
            .addQueryParameter("page", "dapi")
            .addQueryParameter("s", section)
            .addQueryParameter("q", "index")
            .addQueryParameter("json", "1")
        val key = settings.apiKey.value.trim()
        val uid = settings.userId.value.trim()
        if (key.isNotEmpty() && uid.isNotEmpty()) {
            b.addQueryParameter("api_key", key)
            b.addQueryParameter("user_id", uid)
        }
        return b
    }

    private suspend fun getJson(url: HttpUrl): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty().trim()
            if (!resp.isSuccessful) {
                throw IOException(
                    when (resp.code) {
                        401, 403 -> app.str(R.string.err_access_denied)
                        429 -> app.str(R.string.err_too_many)
                        else -> app.str(R.string.err_server_fmt, resp.code)
                    }
                )
            }
            val json = try {
                if (body.startsWith("[")) JSONObject() else JSONObject(body)
            } catch (e: JSONException) {
                throw IOException(
                    app.str(R.string.err_unexpected)
                )
            }
            val attrs = json.optJSONObject("response")?.optJSONObject("@attributes")
            if (attrs != null && attrs.optString("success") == "false") {
                throw IOException(
                    attrs.optString("reason").ifBlank { app.str(R.string.err_rejected) }
                )
            }
            json
        }
    }

    /** Достаёт список объектов по ключу (API может вернуть массив, один объект или ничего). */
    private fun JSONObject.objects(key: String): List<JSONObject> =
        when (val v = opt(key)) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optJSONObject(it) }
            is JSONObject -> listOf(v)
            else -> emptyList()
        }

    private fun JSONObject.toPost(): Post = Post(
        id = optLong("id"),
        tags = unescapeHtml(optString("tags")).split(' ').filter { it.isNotBlank() },
        fileUrl = optString("file_url"),
        sampleUrl = optString("sample_url"),
        previewUrl = optString("preview_url"),
        width = optInt("width"),
        height = optInt("height"),
        rating = optString("rating"),
        score = optInt("score"),
        createdAt = optString("created_at"),
    )

    suspend fun searchPosts(tags: String, pid: Int, limit: Int): List<Post> {
        val url = builder("post")
            .addQueryParameter("tags", tags)
            .addQueryParameter("pid", pid.toString())
            .addQueryParameter("limit", limit.toString())
            .build()
        return getJson(url).objects("post").map { it.toPost() }.filter { it.id != 0L }
    }

    suspend fun getPost(id: Long): Post? {
        val url = builder("post")
            .addQueryParameter("id", id.toString())
            .build()
        return getJson(url).objects("post").firstOrNull()?.toPost()
    }

    suspend fun getComments(postId: Long): List<Comment> {
        val url = builder("comment")
            .addQueryParameter("post_id", postId.toString())
            .build()
        return getJson(url).objects("comment").map { c ->
            Comment(
                creator = unescapeHtml(c.optString("creator")).ifBlank { app.str(R.string.anonymous) },
                body = cleanCommentBody(c.optString("body")),
                createdAt = c.optString("created_at"),
            )
        }.filter { it.body.isNotBlank() }
    }

    /** Типы тегов: имя -> число (0 обычный, 1 artist, 3 copyright, 4 character, 5 metadata). */
    suspend fun tagTypes(names: List<String>): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (chunk in names.chunked(40)) {
            val url = builder("tag")
                .addQueryParameter("names", chunk.joinToString(" "))
                .addQueryParameter("limit", "100")
                .build()
            getJson(url).objects("tag").forEach {
                out[unescapeHtml(it.optString("name"))] = it.optInt("type")
            }
        }
        return out
    }

    /** Автодополнение: теги, начинающиеся с [prefix], по убыванию популярности. */
    suspend fun suggestTags(prefix: String): List<String> {
        val p = prefix.trim().removePrefix("-").replace('*', '%')
        if (p.isEmpty()) return emptyList()
        val url = builder("tag")
            .addQueryParameter("name_pattern", "$p%")
            .addQueryParameter("orderby", "count")
            .addQueryParameter("order", "DESC")
            .addQueryParameter("limit", "10")
            .build()
        return getJson(url).objects("tag")
            .map { unescapeHtml(it.optString("name")) }
            .filter { it.isNotBlank() }
    }
}

fun unescapeHtml(s: String): String {
    if (!s.contains('&')) return s
    return s
        .replace(Regex("&#(\\d+);")) { m ->
            val cp = m.groupValues[1].toIntOrNull()
            if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
        }
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

fun cleanCommentBody(raw: String): String =
    unescapeHtml(raw)
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .trim()
