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
    
    companion object {
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
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
