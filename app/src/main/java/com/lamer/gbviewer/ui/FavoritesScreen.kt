package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lamer.gbviewer.app
import com.lamer.gbviewer.data.filterFavorites
import com.lamer.gbviewer.data.orderFavorites
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(onOpen: (Long) -> Unit) {
    val gb = LocalContext.current.app
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val items by gb.favorites.items.collectAsState()
    val filter by gb.favoritesFilter.collectAsState()
    val columns by gb.settings.favoritesColumns.collectAsState()
    val shuffleSeed by gb.favoritesShuffleSeed.collectAsState()
    val alwaysShuffle by gb.settings.alwaysShuffleFavorites.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    val gridState = rememberLazyGridState()

    // Выбранные посты (режим выбора включается долгим нажатием). Поиск выбор не сбрасывает.
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }

    val filtered = remember(items, filter, shuffleSeed) {
        orderFavorites(filterFavorites(items, filter), shuffleSeed)
    }
    val tagCounts = remember(items) { items.flatMap { it.tags }.groupingBy { it }.eachCount() }

    // Автодополнение по тегам, которые уже есть в избранном.
    val suggest: suspend (String) -> List<String> = { q ->
        val p = q.trim().removePrefix("-")
        tagCounts.entries
            .filter { it.key.startsWith(p, ignoreCase = true) }
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }

    val showUp by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 88.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    TagInput(
                        label = stringResource(R.string.fav_search_label),
                        tags = filter,
                        onTagsChange = {
                            gb.favoritesFilter.value = it
                            if (alwaysShuffle) {
                                gb.favoritesShuffleSeed.value = Random.nextLong()
                                scope.launch { gridState.scrollToItem(0) }
                            }
                        },
                        text = text,
                        onTextChange = { text = it },
                        suggest = suggest,
                    )
                }
            }
            items(filtered, key = { it.id }) { item ->
                val isSelected = item.id in selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = {
                                if (selecting) {
                                    selected = if (isSelected) selected - item.id else selected + item.id
                                } else {
                                    onOpen(item.id)
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selected = if (isSelected) selected - item.id else selected + item.id
                            },
                        ),
                ) {
                    AsyncImage(
                        model = gb.favorites.gridModel(item),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(Modifier.align(Alignment.TopEnd)) { MediaBadge(item.kind) }
                    if (isSelected) {
                        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.33f)))
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = stringResource(R.string.selected_desc),
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.Center).size(40.dp),
                        )
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(if (items.isEmpty()) R.string.fav_empty else R.string.nothing_found),
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showUp,
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        ) {
            FloatingActionButton(onClick = { scope.launch { gridState.animateScrollToItem(0) } }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.scroll_to_top))
            }
        }

        AnimatedVisibility(
            visible = !selecting && items.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            FloatingActionButton(onClick = {
                gb.favoritesShuffleSeed.value = Random.nextLong()
                scope.launch { gridState.scrollToItem(0) }
            }) {
                Text("🎲", fontSize = 24.sp)
            }
        }

        AnimatedVisibility(
            visible = selecting,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = { confirmDelete = true },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_selected_desc))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_selected_title_fmt, selected.size)) },
            text = { Text(stringResource(R.string.files_will_be_deleted)) },
            confirmButton = {
                TextButton(onClick = {
                    gb.favorites.removeMany(selected)
                    selected = emptySet()
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
