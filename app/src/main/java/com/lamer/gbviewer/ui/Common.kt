package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lamer.gbviewer.data.MediaKind

/** Полупрозрачная кнопка «назад» поверх полноэкранной картинки. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        modifier = modifier,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
        ),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

/** Копирует текст (например, тег) в буфер обмена и показывает уведомление. */
fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("tag", text))
    Toast.makeText(context, context.getString(R.string.copied_fmt, text), Toast.LENGTH_SHORT).show()
}

/** Полупрозрачная метка «GIF» / «VID» в углу превью. Для обычных картинок ничего не рисует. */
@Composable
fun MediaBadge(kind: MediaKind, modifier: Modifier = Modifier) {
    if (kind == MediaKind.IMAGE) return
    Text(
        text = if (kind == MediaKind.GIF) "GIF" else "VID",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Сердечко на превью постов, которые уже в избранном. */
@Composable
fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .padding(4.dp),
    ) {
        Icon(
            Icons.Filled.Favorite,
            contentDescription = stringResource(R.string.in_favorites),
            tint = Color(0xFFFF4D6D),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Цвет тега по его типу: artist — красный, character — зелёный, copyright — маджента, metadata — оранжевый. */
fun tagColor(type: Int?): Color = when (type) {
    1 -> Color(0xFFE53935)
    3 -> Color(0xFFD63DD6)
    4 -> Color(0xFF43A047)
    5 -> Color(0xFFFB8C00)
    else -> Color.Unspecified
}
