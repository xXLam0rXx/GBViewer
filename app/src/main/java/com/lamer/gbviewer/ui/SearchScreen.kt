package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lamer.gbviewer.GBApp
import com.lamer.gbviewer.app
import com.lamer.gbviewer.data.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SearchViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {
    private val gb = app as GBApp

    var included by mutableStateOf<List<String>>(emptyList())
    var excluded by mutableStateOf<List<String>>(emptyList())
    val posts = mutableStateListOf<Post>()
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var endReached by mutableStateOf(false)

    private var page = 0
    private var gen = 0
    private val seen = HashSet<Long>()
    private var job: Job? = null

    init {
        handle.get<String>("tag")?.takeIf { it.isNotBlank() }?.let { included = listOf(it) }
        search()
    }

    fun search() {
        job?.cancel()
        gen++
        posts.clear()
        seen.clear()
        page = 0
        endReached = false
        error = null
        loading = false
        loadMore()
    }

    fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        val g = gen
        val query = (included + excluded.map { "-" + it.removePrefix("-") }).joinToString(" ")
        job = viewModelScope.launch {
            try {
                var added = 0
                // Если вся страница отфильтрована чёрным списком — сразу грузим следующую.
                while (added == 0 && !endReached) {
                    val raw = gb.api.searchPosts(query, page, PAGE_SIZE)
                    if (g != gen) return@launch
                    page++
                    if (raw.size < PAGE_SIZE) endReached = true
                    val blacklist = gb.settings.blacklist.value
                    val fresh = raw.filter { p ->
                        p.isSupported && p.tags.none { it in blacklist } && seen.add(p.id)
                    }
                    fresh.forEach { gb.postCache[it.id] = it }
                    posts.addAll(fresh)
                    added = fresh.size
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (g == gen) error = e.message ?: gb.str(R.string.network_error)
            } finally {
                if (g == gen) loading = false
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 40
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    onOpenPost: (Long) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val context = LocalContext.current
    val gb = context.app
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var includeText by rememberSaveable { mutableStateOf("") }
    var excludeText by rememberSaveable { mutableStateOf("") }

    val columns by gb.settings.searchColumns.collectAsState()
    val favorites by gb.favorites.items.collectAsState()
    val favIds = remember(favorites) { favorites.map { it.id }.toHashSet() }

    val suggest: suspend (String) -> List<String> = { q -> gb.api.suggestTags(q) }

    fun doSearch() {
        vm.included = (vm.included + splitTags(includeText)).distinct()
        vm.excluded = (vm.excluded + splitTags(excludeText).map { it.removePrefix("-") }).distinct()
        includeText = ""
        excludeText = ""
        focusManager.clearFocus()
        vm.search()
    }

    // Бесконечная прокрутка: подгружаем следующую страницу, когда дошли почти до конца.
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, vm.posts.size, vm.loading) {
        if (nearEnd && !vm.endReached && vm.error == null) vm.loadMore()
    }

    val showUp by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
    }

    // Потянуть вниз — перезагрузить поиск.
    PullRefreshBox(
        onRefresh = {
            vm.search()
            snapshotFlow { vm.loading }.first { !it }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    TagInput(
                        label = stringResource(R.string.tags),
                        tags = vm.included,
                        onTagsChange = { vm.included = it },
                        text = includeText,
                        onTextChange = { includeText = it },
                        suggest = suggest,
                        onDone = { doSearch() },
                    )
                    Spacer(Modifier.height(8.dp))
                    TagInput(
                        label = stringResource(R.string.exclude_tags),
                        tags = vm.excluded,
                        onTagsChange = { vm.excluded = it },
                        text = excludeText,
                        onTextChange = { excludeText = it },
                        suggest = suggest,
                        onDone = { doSearch() },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { doSearch() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.search_btn))
                    }
                }
            }

            items(vm.posts, key = { it.id }) { post ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .combinedClickable(
                            onClick = { onOpenPost(post.id) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (post.id in favIds) {
                                    Toast.makeText(context, context.getString(R.string.already_in_favorites), Toast.LENGTH_SHORT).show()
                                } else {
                                    gb.favorites.add(post)
                                    Toast.makeText(context, context.getString(R.string.added_to_favorites), Toast.LENGTH_SHORT).show()
                                }
                            },
                        ),
                ) {
                    AsyncImage(
                        model = post.gridUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Row(Modifier.align(Alignment.TopEnd)) {
                        MediaBadge(post.kind)
                        if (post.id in favIds) FavoriteBadge()
                    }
                }
            }

            if (vm.loading) {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            vm.error?.let { message ->
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.loadMore() }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            if (!vm.loading && vm.error == null && vm.posts.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.nothing_found),
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
    }
}
