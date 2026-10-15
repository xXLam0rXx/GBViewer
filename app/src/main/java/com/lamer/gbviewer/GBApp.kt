package com.lamer.gbviewer
import androidx.annotation.StringRes

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import com.lamer.gbviewer.data.AppLock
import com.lamer.gbviewer.data.FavoritesRepository
import com.lamer.gbviewer.data.GelbooruApi
import com.lamer.gbviewer.data.Post
import com.lamer.gbviewer.data.SettingsStore
import com.lamer.gbviewer.data.TagTypeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import kotlin.random.Random
import java.util.concurrent.ConcurrentHashMap

class GBApp : Application(), ImageLoaderFactory {

    lateinit var okHttp: OkHttpClient
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var api: GelbooruApi
        private set
    lateinit var favorites: FavoritesRepository
        private set
    lateinit var tagTypes: TagTypeRepository
        private set
    lateinit var lock: AppLock
        private set

    /** Посты, уже загруженные в поиске: чтобы открыть страницу поста без повторного запроса. */
    val postCache = ConcurrentHashMap<Long, Post>()

    /** Текущий фильтр на экране избранного (общий для сетки и полноэкранного просмотра). */
    val favoritesFilter = MutableStateFlow<List<String>>(emptyList())

    /** Seed перемешивания избранного: null — обычный порядок. */
    val favoritesShuffleSeed = MutableStateFlow<Long?>(null)

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://gelbooru.com/")
                        .build()
                )
            }
            .build()
        api = GelbooruApi(this, okHttp, settings)
        tagTypes = TagTypeRepository(this, api)
        lock = AppLock(this)
        if (settings.alwaysShuffleFavorites.value) favoritesShuffleSeed.value = Random.nextLong()
        favorites = FavoritesRepository(this, okHttp, appScope)
    }

    /** Строка на выбранном в настройках языке (для кода вне интерфейса). */
    fun str(@StringRes id: Int, vararg args: Any): String =
        localizedResources(this, settings.language.value).getString(id, *args)

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .crossfade(true)
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            .build()

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}

val Context.app: GBApp get() = applicationContext as GBApp
