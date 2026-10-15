package com.lamer.gbviewer.ui
import androidx.compose.ui.res.stringResource
import com.lamer.gbviewer.R

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lamer.gbviewer.data.AppLock

/** Цифровая клавиатура с 4 точками. Когда введены 4 цифры, вызывается [onComplete]. */
@Composable
fun PinPad(
    title: String,
    error: String?,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    var pin by remember { mutableStateOf("") }
    LaunchedEffect(pin) {
        if (pin.length == 4) {
            onComplete(pin)
            pin = ""
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = error ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "<"),
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                row.forEach { key ->
                    PinKey(key) {
                        when (key) {
                            "<" -> pin = pin.dropLast(1)
                            "" -> Unit
                            else -> if (pin.length < 4) pin += key
                        }
                    }
                }
            }
        }
        footer()
    }
}

@Composable
private fun PinKey(key: String, onClick: () -> Unit) {
    if (key.isEmpty()) {
        Spacer(Modifier.size(72.dp))
        return
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (key == "<") "⌫" else key,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * Экран блокировки поверх всего приложения.
 * Отпечаток запрашивается сразу при появлении экрана (если включён), PIN вводится на клавиатуре.
 */
@Composable
fun LockScreen(lock: AppLock) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val hasPin by lock.hasPin.collectAsState()
    val fingerprint by lock.fingerprintEnabled.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var prompting by remember { mutableStateOf(false) }
    var cancelled by remember { mutableStateOf(false) }

    fun startBiometric() {
        if (activity == null || prompting || cancelled || !fingerprint) return
        prompting = true
        showBiometricPrompt(
            activity = activity,
            title = context.getString(R.string.biometric_title_login),
            negativeText = context.getString(R.string.cancel),
            onSuccess = {
                prompting = false
                lock.unlock()
            },
            onError = { message ->
                prompting = false
                cancelled = true
                error = if (hasPin) null else message
            },
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, fingerprint) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancelled = false
            if (event == Lifecycle.Event.ON_RESUME) startBiometric()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler { activity?.moveTaskToBack(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hasPin) {
                PinPad(
                    title = stringResource(R.string.pin_enter),
                    error = error,
                    onComplete = { pin ->
                        if (lock.checkPin(pin)) lock.unlock() else error = context.getString(R.string.pin_wrong)
                    },
                    footer = {
                        if (fingerprint) {
                            TextButton(onClick = {
                                cancelled = false
                                startBiometric()
                            }) { Text(stringResource(R.string.unlock_fingerprint)) }
                        }
                    },
                )
            } else {
                Text(stringResource(R.string.app_locked), style = MaterialTheme.typography.titleLarge)
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = {
                    cancelled = false
                    error = null
                    startBiometric()
                }) { Text(stringResource(R.string.unlock_fingerprint)) }
            }
        }
    }
}
