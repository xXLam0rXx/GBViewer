package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lamer.gbviewer.app
import com.lamer.gbviewer.data.Comment
import com.lamer.gbviewer.data.MediaKind
import kotlinx.coroutines.CancellationException

/**
 * Страница поста. Если включено «Листать посты», страницы переключаются свайпом влево/вправо
 * по результатам поиска, из которого открыт пост.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PostScreen(
    startId: Long,
    searchVm: SearchViewModel?,
    onBack: () -> Unit,
    onTagClick: (String) -> Unit,
    onOpenFull: (Long) -> Unit,
) {
    val gb = LocalContext.current.app
    val swipe by gb.settings.swipePosts.collectAsState()

    val feed: List<Long> =
        if (swipe && searchVm != null && searchVm.posts.any { it.id == startId }) {
            searchVm.posts.map { it.id }
        } else {
            listOf(startId)
        }
    val feedState by rememberUpdatedState(feed)
    val startIndex = remember { feed.indexOf(startId).coerceAtLeast(0) }
    val pagerState = rememberPagerState(initialPage = startIndex) { feedState.size }

    // Подгружаем следующие результаты поиска, когда листаем к концу списка.
    LaunchedEffect(pagerState.currentPage, feed.size, swipe) {
        if (swipe && searchVm != null &&
            pagerState.currentPage >= feed.size - 4 &&
            !searchVm.endReached && searchVm.error == null
        ) {
            searchVm.loadMore()
        }
    }

    val currentId = feed.getOrNull(pagerState.currentPage) ?: startId
    val dateText = gb.postCache[currentId]?.publishedDate.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("#$currentId")
                        Spacer(Modifier.weight(1f))
                        Text(dateText, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            key = { index -> feedState.getOrNull(index) ?: index },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) { page ->
            val id = feedState.getOrNull(page)
            if (id != null) {
                PostPage(
                    postId = id,
                    active = page == pagerState.currentPage,
                    onTagClick = onTagClick,
                    onOpenFull = onOpenFull,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun PostPage(
    postId: Long,
    active: Boolean,
    onTagClick: (String) -> Unit,
    onOpenFull: (Long) -> Unit,
) {
    val context = LocalContext.current
    val gb = context.app
    val haptic = LocalHapticFeedback.current
    val favorites by gb.favorites.items.collectAsState()
    val busy by gb.favorites.busy.collectAsState()
    val showComments = false // Gelbooru отключил API комментариев ("Disabled due to abuse")
    val hideCopyright by gb.settings.hideCopyright.collectAsState()
    val hideMetadata by gb.settings.hideMetadata.collectAsState()

    var post by remember(postId) { mutableStateOf(gb.postCache[postId]) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    var comments by remember(postId) { mutableStateOf<List<Comment>>(emptyList()) }
    var typesVersion by remember(postId) { mutableIntStateOf(0) }

    suspend fun reload(force: Boolean) {
        if (post == null || force) {
            try {
                val loaded = gb.api.getPost(postId)
                if (loaded != null) {
                    gb.postCache[postId] = loaded
                    post = loaded
                    error = null
                } else if (post == null) {
                    error = context.getString(R.string.post_not_found)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (post == null) error = e.message ?: context.getString(R.string.network_error)
            }
        }
        val p = post ?: return
        try {
            gb.tagTypes.load(p.tags)
            typesVersion++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // без интернета оставляем исходный порядок
        }
        if (showComments) {
            try {
                comments = gb.api.getComments(postId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // комментарии необязательны
            }
        }
    }

    LaunchedEffect(postId) { reload(false) }

    // Потянуть вниз — перезагрузить пост и комментарии.
    PullRefreshBox(onRefresh = { reload(true) }, modifier = Modifier.fillMaxSize()) {
        val p = post
        if (p == null) {
            Box(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                val message = error
                if (message != null) Text(message, color = MaterialTheme.colorScheme.error)
                else CircularProgressIndicator()
            }
        } else {
            val isFavorite = favorites.any { it.id == p.id }
            val isBusy = p.id in busy
            val visibleTags = remember(p.id, typesVersion, hideCopyright, hideMetadata) {
                gb.tagTypes.prepare(p.tags, hideCopyright, hideMetadata)
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (p.kind == MediaKind.VIDEO) {
                    val videoRatio =
                        if (p.width > 0 && p.height > 0) p.width.toFloat() / p.height else 16f / 9f
                    VideoPlayer(
                        model = p.fileUrl,
                        active = active,
                        modifier = Modifier.fillMaxWidth().aspectRatio(videoRatio),
                    )
                    OutlinedButton(
                        onClick = { onOpenFull(p.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.fullscreen_btn)) }
                } else {
                    val ratioModifier =
                        if (p.width > 0 && p.height > 0) Modifier.aspectRatio(p.width.toFloat() / p.height)
                        else Modifier.heightIn(min = 200.dp)
                    AsyncImage(
                        // у GIF берём оригинал, чтобы она анимировалась
                        model = if (p.kind == MediaKind.GIF) p.fileUrl else p.displayUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(ratioModifier)
                            .clickable { onOpenFull(p.id) },
                    )
                }

                Button(
                    onClick = {
                        if (isFavorite) gb.favorites.remove(p.id) else gb.favorites.add(p)
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            isBusy -> stringResource(R.string.saving)
                            isFavorite -> stringResource(R.string.remove_from_favorites)
                            else -> stringResource(R.string.add_to_favorites)
                        }
                    )
                }

                // Комментарии: блок показывается, только если они есть и включены в настройках.
                if (showComments && comments.isNotEmpty()) {
                    Text(
                        stringResource(R.string.comments_fmt, comments.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    HorizontalDivider()
                    comments.forEach { c ->
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.creator,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    commentDate(c.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(c.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    stringResource(R.string.tags_fmt, visibleTags.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                HorizontalDivider()
                visibleTags.forEach { tag ->
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

private fun commentDate(raw: String): String {
    val s = raw.trim()
    return if (Regex("^\\d{4}-\\d{2}-\\d{2}.*").matches(s)) s.take(16) else s
}

@Composable
fun FullscreenScreen(postId: Long, onBack: () -> Unit) {
    val gb = LocalContext.current.app
    val post = gb.postCache[postId]
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (post != null) {
            if (post.kind == MediaKind.VIDEO) {
                VideoPlayer(model = post.fileUrl, modifier = Modifier.fillMaxSize())
            } else {
                ZoomableImage(model = post.fileUrl)
            }
        } else {
            Text(
                stringResource(R.string.image_unavailable),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        BackButton(onBack, Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}
