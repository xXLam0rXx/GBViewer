package com.lamer.gbviewer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Блокировка приложения: PIN-код (4 цифры) и/или отпечаток пальца.
 * Для входа достаточно любого из включённых способов.
 */
class AppLock(context: Context) {
    private val prefs = context.getSharedPreferences("lock", Context.MODE_PRIVATE)

    val hasPin = MutableStateFlow(prefs.contains("pin_hash"))
    val fingerprintEnabled = MutableStateFlow(prefs.getBoolean("fingerprint", false))

    /** true — приложение сейчас закрыто экраном блокировки. При запуске заблокировано, если защита включена. */
    val locked = MutableStateFlow(hasPin.value || fingerprintEnabled.value)

    /** Ставится перед системными окнами (выбор файла и т.п.), чтобы они не вызывали блокировку. */
    @Volatile
    var skipNextLock = false

    val isEnabled: Boolean get() = hasPin.value || fingerprintEnabled.value

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        val saved = prefs.getString("pin_hash", null) ?: return false
        return hash(salt, pin) == saved
    }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
        hasPin.value = true
    }

    fun clearPin() {
        prefs.edit().remove("pin_salt").remove("pin_hash").apply()
        hasPin.value = false
        if (!fingerprintEnabled.value) locked.value = false
    }

    fun setFingerprint(enabled: Boolean) {
        prefs.edit().putBoolean("fingerprint", enabled).apply()
        fingerprintEnabled.value = enabled
        if (!isEnabled) locked.value = false
    }

    /** Вызывается, когда приложение ушло с экрана (свернули, выключили экран). */
    fun onAppStopped() {
        if (skipNextLock) {
            skipNextLock = false
            return
        }
        if (isEnabled) locked.value = true
    }

    fun unlock() {
        locked.value = false
    }
}
