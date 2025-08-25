package com.d4vram.psychologger

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import java.security.MessageDigest

class AppLockManager(private val context: Context) {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "app_lock_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val _isAppLockEnabled = MutableStateFlow(
        encryptedPrefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    )
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()
    
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()
    
    private val _autoLockDelay = MutableStateFlow(
        encryptedPrefs.getInt(KEY_AUTO_LOCK_DELAY, 0)
    )
    val autoLockDelay: StateFlow<Int> = _autoLockDelay.asStateFlow()
    
    private var lastBackgroundTime: Long = 0
    private var autoLockJob: kotlinx.coroutines.Job? = null
    
    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"
        private const val KEY_PIN_HASH = "pin_hash"
    }
    
    fun setAppLockEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _isAppLockEnabled.value = enabled
        
        if (!enabled) {
            _isAppLocked.value = false
        }
    }
    
    fun lockApp() {
        if (_isAppLockEnabled.value) {
            _isAppLocked.value = true
        }
    }
    
    fun unlockApp() {
        _isAppLocked.value = false
    }
    
    fun setAutoLockDelay(delaySeconds: Int) {
        encryptedPrefs.edit().putInt(KEY_AUTO_LOCK_DELAY, delaySeconds).apply()
        _autoLockDelay.value = delaySeconds
    }
    
    fun onAppBackgrounded() {
        if (_isAppLockEnabled.value && _autoLockDelay.value > 0) {
            lastBackgroundTime = System.currentTimeMillis()
            // Programar el bloqueo automático
            autoLockJob?.cancel()
            autoLockJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                kotlinx.coroutines.delay(_autoLockDelay.value * 1000L)
                if (System.currentTimeMillis() - lastBackgroundTime >= _autoLockDelay.value * 1000L) {
                    lockApp()
                }
            }
        } else if (_isAppLockEnabled.value && _autoLockDelay.value == 0) {
            // Bloqueo inmediato
            lockApp()
        }
    }
    
    fun onAppForegrounded() {
        autoLockJob?.cancel()
        if (_isAppLockEnabled.value && _isAppLocked.value) {
            // La app está bloqueada, no hacer nada
        }
    }
    
    // Métodos para gestión del PIN
    fun setPin(pin: String) {
        val hashedPin = hashPin(pin)
        encryptedPrefs.edit().putString(KEY_PIN_HASH, hashedPin).apply()
    }
    
    fun verifyPin(pin: String): Boolean {
        val storedHash = encryptedPrefs.getString(KEY_PIN_HASH, null)
        return storedHash != null && storedHash == hashPin(pin)
    }
    
    fun hasPinSet(): Boolean {
        return encryptedPrefs.getString(KEY_PIN_HASH, null) != null
    }
    
    private fun hashPin(pin: String): String {
        return android.util.Base64.encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(pin.toByteArray()),
            android.util.Base64.NO_WRAP
        )
    }
    
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
    
    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError("Error de autenticación: $errString")
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Autenticación fallida. Inténtalo de nuevo.")
                }
            })
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("🔒 Desbloquear Aplicación")
            .setSubtitle("Usa tu huella dactilar para acceder")
            .setNegativeButtonText("Cancelar")
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
}
