package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lamer.gbviewer.app
import com.lamer.gbviewer.data.MediaKind
import com.lamer.gbviewer.data.filterFavorites
import com.lamer.gbviewer.data.orderFavorites

/**
 * Полноэкранный просмотр избранного «как в галерее»: свайп между картинками, приближение.
 * Кнопка «Теги» открывает нижнюю треть экрана со списком тегов; нажатие на тег копирует его.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteViewerScreen(startId: Long, onBack: () -> Unit, onTagClick: (String) -> Unit) {
    val context = LocalContext.current
    val gb = context.app
    val items by gb.favorites.items.collectAsState()
    val filter by gb.favoritesFilter.collectAsState()
    val shuffleSeed by gb.favoritesShuffleSeed.collectAsState()
    val list = remember(items, filter, shuffleSeed) {
        orderFavorites(filterFavorites(items, filter), shuffleSeed)
    }
    val currentList by rememberUpdatedState(list)

    val startIndex = remember { list.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { currentList.size }

    var zoomed by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val panelHeight = (LocalConfiguration.current.screenHeightDp / 3).dp

    // Если всё удалили — возвращаемся в избранное.
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty()) onBack() }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            key = { index -> currentList.getOrNull(index)?.id ?: index },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = currentList.getOrNull(page)
            if (item != null) {
                if (item.kind == MediaKind.VIDEO) {
                    VideoPlayer(
                        model = gb.favorites.file(item),
                        active = page == pagerState.currentPage,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableImage(
                        model = gb.favorites.file(item),
                        onZoomChanged = { z -> if (page == pagerState.currentPage) zoomed = z },
                    )
                }
            }
        }

        BackButton(onBack, Modifier.align(Alignment.TopStart).padding(8.dp))

        IconButton(
            onClick = { confirmDelete = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove_from_favorites))
        }

        Button(
            onClick = { showTags = !showTags },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (showTags) panelHeight + 12.dp else 16.dp),
        ) {
            Text(stringResource(R.string.tags))
        }

        if (showTags) {
            val currentItem = currentList.getOrNull(pagerState.currentPage)
            var typesVersion by remember(currentItem?.id) { mutableIntStateOf(0) }
            LaunchedEffect(currentItem?.id) {
                try {
                    gb.tagTypes.load(currentItem?.tags.orEmpty())
                    typesVersion++
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // без интернета оставляем исходный порядок
                }
            }
            val hideCopyright by gb.settings.hideCopyright.collectAsState()
            val hideMetadata by gb.settings.hideMetadata.collectAsState()
            val tags = remember(currentItem?.id, typesVersion, hideCopyright, hideMetadata) {
                gb.tagTypes.prepare(currentItem?.tags.orEmpty(), hideCopyright, hideMetadata)
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(panelHeight),
            ) {
                Column {
                    Text(
                        stringResource(R.string.viewer_tags_hint_fmt, tags.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    HorizontalDivider()
                    LazyColumn {
                        items(tags) { tag ->
                            Text(
                                text = tag,
                                color = tagColor(gb.tagTypes.typeOf(tag)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onTagClick(tag) },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            copyToClipboard(context, tag)
                                        },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.remove_fav_title)) },
            text = { Text(stringResource(R.string.file_will_be_deleted)) },
            confirmButton = {
                TextButton(onClick = {
                    currentList.getOrNull(pagerState.currentPage)?.let { gb.favorites.remove(it.id) }
                    confirmDelete = false
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
