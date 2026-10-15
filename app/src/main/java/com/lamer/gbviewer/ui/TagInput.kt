package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

fun splitTags(text: String): List<String> =
    text.split(' ', ',', '\n').map { it.trim() }.filter { it.isNotEmpty() }

/** Выпадающий список располагается прямо под полем ввода (или над ним, если снизу нет места). */
private object BelowAnchorPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        var y = anchorBounds.bottom
        if (y + popupContentSize.height > windowSize.height) {
            y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(anchorBounds.left, y)
    }
}

/**
 * Поле ввода тегов: чипы уже выбранных тегов + текстовое поле с автодополнением.
 * Тег добавляется по пробелу / запятой / кнопке «Готово» / нажатию на подсказку.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagInput(
    label: String,
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    text: String,
    onTextChange: (String) -> Unit,
    suggest: suspend (String) -> List<String>,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    var fieldWidthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val currentSuggest by rememberUpdatedState(suggest)

    LaunchedEffect(text) {
        val q = text.trim()
        if (q.removePrefix("-").isEmpty()) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = try {
            currentSuggest(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun commit(newTags: List<String>) {
        if (newTags.isNotEmpty()) onTagsChange((tags + newTags).distinct())
    }

    fun handleInput(value: String) {
        if (value.none { it == ' ' || it == ',' || it == '\n' }) {
            onTextChange(value)
            return
        }
        val parts = splitTags(value)
        val endsWithSeparator = value.last().let { it == ' ' || it == ',' || it == '\n' }
        if (endsWithSeparator) {
            commit(parts)
            onTextChange("")
        } else {
            commit(parts.dropLast(1))
            onTextChange(parts.lastOrNull().orEmpty())
        }
    }

    Column(modifier) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagsChange(tags - tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove), modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Box(Modifier.fillMaxWidth().onSizeChanged { fieldWidthPx = it.width }) {
            OutlinedTextField(
                value = text,
                onValueChange = { handleInput(it) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) {
                        commit(splitTags(text))
                        onTextChange("")
                    } else {
                        onDone()
                    }
                }),
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
            if (focused && suggestions.isNotEmpty()) {
                val negative = text.trim().startsWith("-")
                Popup(
                    popupPositionProvider = BelowAnchorPositionProvider,
                    properties = PopupProperties(focusable = false),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 8.dp,
                        tonalElevation = 3.dp,
                        modifier = Modifier
                            .width(with(density) { fieldWidthPx.toDp() })
                            .heightIn(max = 320.dp),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            suggestions.forEach { s ->
                                val value = (if (negative) "-" else "") + s
                                Text(
                                    text = value,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            commit(listOf(value))
                                            onTextChange("")
                                            suggestions = emptyList()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
