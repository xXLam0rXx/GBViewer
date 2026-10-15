package com.lamer.gbviewer.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import com.lamer.gbviewer.appLanguages
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.lamer.gbviewer.app
import com.lamer.gbviewer.data.AppLock
import com.lamer.gbviewer.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun dirSize(dir: File): Long =
    dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val gb = context.app
    val settings = gb.settings
    val lock = gb.lock

    val theme by settings.theme.collectAsState()
    val language by settings.language.collectAsState()
    val accent by settings.accentColor.collectAsState()
    var showAccentDialog by remember { mutableStateOf(false) }
    val blacklist by settings.blacklist.collectAsState()
    val hideCopyright by settings.hideCopyright.collectAsState()
    val hideMetadata by settings.hideMetadata.collectAsState()
    val searchColumns by settings.searchColumns.collectAsState()
    val favoritesColumns by settings.favoritesColumns.collectAsState()
    val alwaysShuffle by settings.alwaysShuffleFavorites.collectAsState()
    val autoplay by settings.autoplayVideo.collectAsState()
    val videoSound by settings.videoSound.collectAsState()
    val showComments by settings.showComments.collectAsState()
    val swipePosts by settings.swipePosts.collectAsState()
    val hasPin by lock.hasPin.collectAsState()
    val fingerprint by lock.fingerprintEnabled.collectAsState()
    val favorites by gb.favorites.items.collectAsState()

    var apiKey by remember { mutableStateOf(settings.apiKey.value) }
    var userId by remember { mutableStateOf(settings.userId.value) }
    var blText by rememberSaveable { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var favBytes by remember { mutableLongStateOf(0L) }
    var cacheBytes by remember { mutableLongStateOf(0L) }

    val suggest: suspend (String) -> List<String> = { q -> gb.api.suggestTags(q) }

    // Вес избранного и кэша (пересчитывается при изменении избранного).
    LaunchedEffect(favorites) {
        withContext(Dispatchers.IO) {
            favBytes = gb.favorites.sizeBytes()
            cacheBytes = dirSize(context.cacheDir)
        }
    }

    // ----- резервная копия -----
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, context.getString(R.string.backup_creating), Toast.LENGTH_SHORT).show()
            gb.appScope.launch {
                val result = runCatching {
                    val out = context.contentResolver.openOutputStream(uri)
                        ?: throw IOException(context.getString(R.string.err_cant_open_file))
                    gb.favorites.exportTo(out)
                }
                Toast.makeText(
                    context,
                    result.fold(
                        { context.getString(R.string.backup_saved) },
                        { context.getString(R.string.error_fmt, it.message ?: "") },
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) importUri = uri }

    fun runImport(uri: Uri, replace: Boolean) {
        Toast.makeText(context, context.getString(R.string.backup_loading), Toast.LENGTH_SHORT).show()
        gb.appScope.launch {
            val result = runCatching {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException(context.getString(R.string.err_cant_open_file))
                gb.favorites.importFrom(input, replace)
            }
            Toast.makeText(
                context,
                result.fold(
                    { context.getString(R.string.backup_loaded_fmt, it) },
                    { context.getString(R.string.error_fmt, it.message ?: "") },
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ----- API -----
        Text(stringResource(R.string.settings_api_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.settings_api_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it; settings.setApiKey(it.trim()) },
            label = { Text("API key") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = userId,
            onValueChange = { userId = it; settings.setUserId(it.trim()) },
            label = { Text("User ID") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        // ----- тема -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        var languageMenu by remember { mutableStateOf(false) }
        val currentLanguage = appLanguages.firstOrNull { it.code == language }?.name ?: language
        Box {
            OutlinedButton(
                onClick = { languageMenu = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(currentLanguage, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(
                expanded = languageMenu,
                onDismissRequest = { languageMenu = false },
            ) {
                appLanguages.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.name) },
                        onClick = {
                            settings.setLanguage(lang.code)
                            languageMenu = false
                        },
                        trailingIcon = {
                            if (lang.code == language) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.theme_title), style = MaterialTheme.typography.titleMedium)
        ThemeMode.values().forEach { mode ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.setTheme(mode) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = theme == mode, onClick = { settings.setTheme(mode) })
                Text(stringResource(mode.titleRes))
            }
        }

        // ----- цвет акцента -----
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showAccentDialog = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.accent_title), modifier = Modifier.weight(1f))
            Text(
                if (accent == null) stringResource(R.string.accent_default) else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(end = 12.dp),
            )
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent?.let { Color(it) } ?: MaterialTheme.colorScheme.primary)
            )
        }

        // ----- отображение -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.search_columns_fmt, searchColumns))
        Slider(
            value = searchColumns.toFloat(),
            onValueChange = { settings.setSearchColumns(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
        Text(stringResource(R.string.fav_columns_fmt, favoritesColumns))
        Slider(
            value = favoritesColumns.toFloat(),
            onValueChange = { settings.setFavoritesColumns(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
        )
        SwitchRow(stringResource(R.string.always_shuffle), alwaysShuffle) { settings.setAlwaysShuffleFavorites(it) }
        SwitchRow(stringResource(R.string.swipe_posts), swipePosts) { settings.setSwipePosts(it) }
        // Gelbooru отключил API комментариев ("Disabled due to abuse"):
        // SwitchRow("Показывать комментарии", showComments) { settings.setShowComments(it) }
        SwitchRow(stringResource(R.string.hide_copyright), hideCopyright) { settings.setHideCopyright(it) }
        SwitchRow(stringResource(R.string.hide_metadata), hideMetadata) { settings.setHideMetadata(it) }

        // ----- видео -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.video_title), style = MaterialTheme.typography.titleMedium)
        SwitchRow(stringResource(R.string.autoplay), autoplay) { settings.setAutoplayVideo(it) }
        SwitchRow(stringResource(R.string.video_sound), videoSound) { settings.setVideoSound(it) }

        // ----- защита -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.protection_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (hasPin) R.string.pin_set_status else R.string.pin_not_set),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showPinDialog = true }) {
                Text(stringResource(if (hasPin) R.string.pin_change else R.string.pin_create))
            }
            if (hasPin) {
                TextButton(onClick = { lock.clearPin() }) { Text(stringResource(R.string.remove)) }
            }
        }
        SwitchRow(stringResource(R.string.fingerprint_login), fingerprint) { enable ->
            if (!enable) {
                lock.setFingerprint(false)
            } else if (!biometricAvailable(context)) {
                Toast.makeText(
                    context,
                    context.getString(R.string.fingerprint_not_set),
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                val activity = context.findFragmentActivity()
                if (activity != null) {
                    showBiometricPrompt(
                        activity = activity,
                        title = context.getString(R.string.fingerprint_confirm_title),
                        negativeText = context.getString(R.string.cancel),
                        onSuccess = { lock.setFingerprint(true) },
                        onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    )
                }
            }
        }

        // ----- резервная копия -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val name = "gbviewer_backup_" +
                    SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".zip"
                lock.skipNextLock = true
                exportLauncher.launch(name)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_create)) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                lock.skipNextLock = true
                importLauncher.launch(arrayOf("*/*"))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_load)) }

        // ----- чёрный список -----
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.blacklist_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.blacklist_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        TagInput(
            label = stringResource(R.string.blacklist_add_label),
            tags = blacklist,
            onTagsChange = { settings.setBlacklist(it) },
            text = blText,
            onTextChange = { blText = it },
            suggest = suggest,
        )

        // ----- информация -----
        Spacer(Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.info_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        InfoRow(stringResource(R.string.info_fav_size), Formatter.formatShortFileSize(context, favBytes))
        InfoRow(stringResource(R.string.info_fav_count), favorites.size.toString())
        InfoRow(stringResource(R.string.info_cache), Formatter.formatShortFileSize(context, cacheBytes))
        Spacer(Modifier.height(48.dp))
    }

    if (showAccentDialog) {
        AccentColorDialog(
            current = accent,
            defaultColor = MaterialTheme.colorScheme.primary,
            onApply = {
                settings.setAccentColor(it)
                showAccentDialog = false
            },
            onReset = {
                settings.setAccentColor(null)
                showAccentDialog = false
            },
            onDismiss = { showAccentDialog = false },
        )
    }

    if (showPinDialog) {
        SetPinDialog(lock = lock, onDismiss = { showPinDialog = false })
    }

    val pending = importUri
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.backup_dialog_title)) },
            text = {
                Text(stringResource(R.string.backup_dialog_text))
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { runImport(pending, replace = false); importUri = null }) {
                        Text(stringResource(R.string.backup_add))
                    }
                    TextButton(onClick = { runImport(pending, replace = true); importUri = null }) {
                        Text(stringResource(R.string.backup_replace))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { importUri = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Задание нового PIN-кода: ввести дважды. */
@Composable
private fun SetPinDialog(lock: AppLock, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp)) {
            PinPad(
                title = stringResource(if (first == null) R.string.pin_new else R.string.pin_repeat),
                error = error,
                onComplete = { pin ->
                    val f = first
                    if (f == null) {
                        first = pin
                        error = null
                    } else if (f == pin) {
                        lock.setPin(pin)
                        onDismiss()
                    } else {
                        first = null
                        error = context.getString(R.string.pin_mismatch)
                    }
                },
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}
