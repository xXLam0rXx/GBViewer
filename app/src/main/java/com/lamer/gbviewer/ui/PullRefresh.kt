package com.lamer.gbviewer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * «Потянуть вниз, чтобы обновить». Работает, когда содержимое прокручено в самый верх.
 * [onRefresh] выполняется, пока на экране крутится индикатор.
 */
@Composable
fun PullRefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { 72.dp.toPx() }
    val indicatorPx = with(density) { 40.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (refreshing || pull <= 0f || available.y >= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -pull)
                pull += consumed
                return Offset(0f, consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (refreshing || source != NestedScrollSource.Drag || available.y <= 0f) return Offset.Zero
                pull = (pull + available.y * 0.5f).coerceAtMost(thresholdPx * 1.6f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (refreshing) return Velocity.Zero
                if (pull >= thresholdPx) {
                    refreshing = true
                    pull = thresholdPx
                    scope.launch {
                        try {
                            currentOnRefresh()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // ошибка уже показана самим экраном
                        } finally {
                            refreshing = false
                            pull = 0f
                        }
                    }
                } else {
                    pull = 0f
                }
                return Velocity.Zero
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        content()
        if (refreshing || pull > 0f) {
            Surface(
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, (pull - indicatorPx).roundToInt()) }
                    .size(40.dp)
                    .graphicsLayer {
                        alpha = if (refreshing) 1f else (pull / thresholdPx).coerceIn(0f, 1f)
                    },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}
