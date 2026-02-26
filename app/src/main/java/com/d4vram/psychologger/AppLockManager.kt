package com.d4vram.psychologger

import android.content.Context
import android.os.SystemClock
import com.d4vram.psychologger.R
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/**
 * AppLockManager - Gestión de bloqueo con biometría / PIN del sistema
 *
 * Regla única y determinista:
 *   needsAuth = appLockEnabled && (lastUnlockTime == 0L || now - lastUnlockTime >= autoLockDelayMs) || isAppLocked
 *
 * Recomendación de uso desde Activity:
 *   - En onResume(): if (appLockManager.needsAuth()) { appLockManager.showBiometricPrompt(...) }
 *   - Llamar a appLockManager.onAppBackgrounded() / onAppForegrounded() en los eventos de lifecycle apropiados si usas auto-lock diferido.
 */
class AppLockManager(private val context: Context) {

    // --- Almacenamiento seguro ---
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

    // --- State ---
    private val _isAppLockEnabled = MutableStateFlow(
        encryptedPrefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
    )
    val isAppLockEnabled: StateFlow<Boolean> = _isAppLockEnabled.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    private val _autoLockDelay = MutableStateFlow(
        encryptedPrefs.getInt(KEY_AUTO_LOCK_DELAY, 0) // segundos (0 = inmediato)
    )
    val autoLockDelay: StateFlow<Int> = _autoLockDelay.asStateFlow()

    // --- Control interno ---
    private var lastBackgroundTime: Long = 0L
    private var autoLockJob: Job? = null

    // Evita mostrar múltiples diálogos si llaman varias veces seguido.
    @Volatile
    private var isPromptShowing: Boolean = false
    
    // Debounce para evitar relanzar prompt en bucle si el usuario minimiza/maximiza rápido
    private var lastPromptAt = 0L
    private val MIN_PROMPT_INTERVAL_MS = 800L

    companion object {
        private const val KEY_APP_LOCK_ENABLED   = "app_lock_enabled"
        private const val KEY_AUTO_LOCK_DELAY    = "auto_lock_delay"   // en segundos
        private const val KEY_PIN_HASH           = "pin_hash"
        private const val KEY_LAST_UNLOCK_TIME   = "last_unlock_time"
        private const val KEY_APP_INITIALIZED    = "app_initialized"
    }

    // -------------------------------
    // Control de estado biométrico
    // -------------------------------
    
    /**
     * Resetea el estado del prompt biométrico.
     * Útil cuando la app vuelve del background para evitar estado "colgado".
     */
    fun resetBiometricState() {
        isPromptShowing = false
    }
    
    /**
     * Marca que se va a mostrar un prompt y registra el timestamp.
     */
    private fun markPromptShown() {
        isPromptShowing = true
        lastPromptAt = SystemClock.elapsedRealtime()
    }
    
    /**
     * Verifica si se puede mostrar un prompt ahora (evita spam).
     */
    fun canShowPromptNow(): Boolean {
        return !isPromptShowing && 
               (SystemClock.elapsedRealtime() - lastPromptAt) > MIN_PROMPT_INTERVAL_MS
    }
    
    // -------------------------------
    // Configuración
    // -------------------------------

    fun setAppLockEnabled(enabled: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _isAppLockEnabled.value = enabled
        if (!enabled) {
            _isAppLocked.value = false
        } else {
            // Marca la app como inicializada a partir de ahora
            encryptedPrefs.edit().putBoolean(KEY_APP_INITIALIZED, true).apply()
        }
    }

    fun setAutoLockDelay(delaySeconds: Int) {
        val safe = delaySeconds.coerceAtLeast(0)
        encryptedPrefs.edit().putInt(KEY_AUTO_LOCK_DELAY, safe).apply()
        _autoLockDelay.value = safe
    }

    // -------------------------------
    // Lógica unificada de bloqueo
    // -------------------------------

    /**
     * Política determinista del bloqueo.
     * - Si el bloqueo no está habilitado -> false
     * - Si nunca se ha desbloqueado -> true
     * - Si el tiempo desde el último unlock >= delay -> true
     * - Si isAppLocked ya está en true -> true
     */
    fun needsAuth(now: Long = System.currentTimeMillis()): Boolean {
        val enabled = encryptedPrefs.getBoolean(KEY_APP_LOCK_ENABLED, false)
        if (!enabled) return false

        if (_isAppLocked.value) return true

        val last = encryptedPrefs.getLong(KEY_LAST_UNLOCK_TIME, 0L)
        val delaySec = encryptedPrefs.getInt(KEY_AUTO_LOCK_DELAY, 0).coerceAtLeast(0)
        val delayMs = delaySec * 1000L

        return (last == 0L) || (now - last >= delayMs)
    }

    /**
     * Compat: inicializa estado al arrancar.
     * Devuelve true si debe iniciar bloqueada, false si no.
     */
    fun initializeAppLock(): Boolean {
        val lock = needsAuth()
        _isAppLocked.value = lock
        encryptedPrefs.edit().putBoolean(KEY_APP_INITIALIZED, true).apply()
        return lock
    }

    /**
     * Compat: consulta si debe estar bloqueada (redirige a needsAuth()).
     */
    fun shouldAppBeLocked(): Boolean = needsAuth()

    /**
     * Compat: ejecuta seguridad en arranque (solo aplica needsAuth).
     */
    fun enforceSecurityOnStartup(): Boolean {
        val lock = needsAuth()
        if (lock) _isAppLocked.value = true
        return lock
    }

    // -------------------------------
    // Transiciones foreground/background
    // -------------------------------

    fun onAppBackgrounded() {
        if (_isAppLockEnabled.value) {
            lastBackgroundTime = System.currentTimeMillis()

            autoLockJob?.cancel()
            val delaySec = _autoLockDelay.value.coerceAtLeast(0)

            if (delaySec == 0) {
                // Bloqueo inmediato al ir a background
                lockApp()
            } else {
                // Programa el bloqueo tras el delay
                autoLockJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(delaySec * 1000L)
                    // Solo bloquea si realmente ha pasado el tiempo fuera
                    val passed = System.currentTimeMillis() - lastBackgroundTime
                    if (passed >= delaySec * 1000L) {
                        lockApp()
                    }
                }
            }
        }
    }

    fun onAppForegrounded() {
        // Cancelar cualquier bloqueo programado
        autoLockJob?.cancel()
        // Si ya está bloqueada, el UI decidirá mostrar prompt
    }

    // -------------------------------
    // Estado de bloqueo
    // -------------------------------

    fun lockApp() {
        if (_isAppLockEnabled.value) {
            _isAppLocked.value = true
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
        // Registrar el tiempo de desbloqueo
        encryptedPrefs.edit()
            .putLong(KEY_LAST_UNLOCK_TIME, System.currentTimeMillis())
            .putBoolean(KEY_APP_INITIALIZED, true)
            .apply()
    }

    // -------------------------------
    // PIN (opcional, almacenamiento seguro)
    // -------------------------------

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
        val sha = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return Base64.encodeToString(sha, Base64.NO_WRAP)
    }

    // -------------------------------
    // Biometría
    // -------------------------------

    fun isBiometricAvailable(): Boolean {
        val bm = BiometricManager.from(context)
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        return when (bm.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Muestra el prompt biométrico (con fallback a credencial del dispositivo).
     * onSuccess: desbloquea y notifica
     * onError: no desbloquea y devuelve el motivo
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!canShowPromptNow()) return
        markPromptShown()

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isPromptShowing = false
                    unlockApp()
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Intento no reconocido (dedo erróneo, etc.)
                    // No desbloquea, pero dejamos que el sistema siga mostrando el prompt
                    // Si quieres avisar, puedes notificar:
                    // onError("Autenticación fallida, inténtalo de nuevo.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    isPromptShowing = false
                    // No desbloquea, propaga el error
                    onError(errString.toString())
                }
            }
        )

        // Biometría + credencial del dispositivo (PIN/patrón del sistema) como fallback
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}
