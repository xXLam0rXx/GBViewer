package com.lamer.gbviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lamer.gbviewer.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Цветовой круг: угол — оттенок, расстояние от центра — насыщенность.
 * Яркость задаётся отдельно ([value]).
 */
@Composable
private fun ColorWheel(
    hue: Float,
    sat: Float,
    value: Float,
    onChange: (hue: Float, sat: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val selected = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
    val hueColors = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    }
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                fun update(p: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = p.x - cx
                    val dy = p.y - cy
                    var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (h < 0f) h += 360f
                    val s = (sqrt(dx * dx + dy * dy) / (size.width / 2f)).coerceIn(0f, 1f)
                    currentOnChange(h, s)
                }
                val down = awaitFirstDown(requireUnconsumed = false)
                update(down.position)
                down.consume()
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach {
                        if (it.pressed) {
                            update(it.position)
                            it.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        val r = size.minDimension / 2f
        drawCircle(brush = Brush.sweepGradient(hueColors, center), radius = r, center = center)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center, radius = r),
            radius = r,
            center = center,
        )
        drawCircle(color = Color.Black.copy(alpha = 1f - value), radius = r, center = center)

        val rad = hue * PI.toFloat() / 180f
        val pos = Offset(center.x + cos(rad) * r * sat, center.y + sin(rad) * r * sat)
        drawCircle(color = selected, radius = 10.dp.toPx(), center = pos)
        drawCircle(color = Color.White, radius = 12.dp.toPx(), center = pos, style = Stroke(width = 3.dp.toPx()))
    }
}

/** Диалог выбора цвета акцента: цветовой круг + ползунок яркости. */
@Composable
fun AccentColorDialog(
    current: Int?,
    defaultColor: Color,
    onApply: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(current ?: defaultColor.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2].coerceIn(0.2f, 1f)) }
    val chosen = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.accent_pick_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                ColorWheel(
                    hue = hue,
                    sat = sat,
                    value = value,
                    onChange = { h, s ->
                        hue = h
                        sat = s
                    },
                    modifier = Modifier.size(240.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.accent_brightness), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = 0.2f..1f,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(chosen))
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.accent_reset)) }
                    Row {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = { onApply(chosen) }) { Text(stringResource(R.string.accent_apply)) }
                    }
                }
            }
        }
    }
}
