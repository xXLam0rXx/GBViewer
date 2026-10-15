package com.lamer.gbviewer.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.lamer.gbviewer.app
import java.io.File

/**
 * Видеоплеер (ExoPlayer) со стандартными элементами управления.
 * [model] — ссылка (String) или локальный файл (File). Видео зациклено.
 * [active] = false ставит видео на паузу (например, когда страница пейджера не текущая).
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    model: Any,
    modifier: Modifier = Modifier,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val okHttp = context.app.okHttp
    val autoPlay = context.app.settings.autoplayVideo.value
    val soundOn = context.app.settings.videoSound.value

    val player = remember(model) {
        val dataSourceFactory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttp))
        ExoPlayer.Builder(context)
            .setSeekForwardIncrementMs(5_000)
            .setSeekBackIncrementMs(5_000)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                val uri = if (model is File) Uri.fromFile(model) else Uri.parse(model.toString())
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (soundOn) 1f else 0f
                playWhenReady = active && autoPlay
                prepare()
            }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(active, player) {
        if (!active) player.playWhenReady = false
        else if (autoPlay) player.playWhenReady = true
    }

    // Пауза при сворачивании приложения.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(context.findFragmentActivity() ?: ctx).apply {
                this.player = player
                useController = true
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
