package com.d4vram.psychologger

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import android.text.InputType
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.d4vram.psychologger.ui.screens.LockScreen
import com.d4vram.psychologger.ui.screens.SettingsScreen
import com.d4vram.psychologger.ui.screens.AdvancedSettingsScreen
import com.d4vram.psychologger.ui.screens.ProfileSettingsScreen
import com.d4vram.psychologger.ui.screens.PinSetupScreen
import com.d4vram.psychologger.ui.screens.PinEntryScreen
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var capturePhotoLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickPhotoLauncher: ActivityResultLauncher<String>
    private lateinit var importBackupLauncher: ActivityResultLauncher<Array<String>>
    private var pendingPhotoResult: ((PhotoResult) -> Unit)? = null
    private var pendingPhotoFile: File? = null
    private val photoManager by lazy { PhotoManager(this) }
    private val backupManager by lazy { BackupManager(this) }
    private lateinit var appLockManager: AppLockManager
    var webAppInterface: WebAppInterface? = null
        private set

    // --- Persistencia de Tema ---
    private val uiPrefs by lazy { getSharedPreferences("ui_settings", MODE_PRIVATE) }
    private var _isSoftTheme = mutableStateOf(false)
    val isSoftTheme: State<Boolean> get() = _isSoftTheme

    var webView: WebView? = null
        private set

    // State para mostrar ProfileSettingsScreen (accesible desde el bridge)
    private val _showProfileSettingsState = mutableStateOf(false)
    val showProfileSettingsState: State<Boolean> get() = _showProfileSettingsState

    fun showProfileSettingsFromBridge() {
        _showProfileSettingsState.value = true
    }

    fun hideProfileSettings() {
        _showProfileSettingsState.value = false
    }

    data class PhotoResult(
        val success: Boolean,
        val filename: String? = null,
        val error: String? = null
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Inicializar el gestor de bloqueo de aplicación
        appLockManager = AppLockManager(this)
        
        // Inicializar tema
        _isSoftTheme.value = uiPrefs.getBoolean("is_soft_theme", false)

        // Programar backups automáticos cada 12 horas
        schedulePeriodicBackups()

        // Deja que Compose gestione los insets (barra de estado / teclado)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val results = if (result.data?.data != null && result.resultCode == RESULT_OK) {
                arrayOf(result.data!!.data!!)
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

        capturePhotoLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            val callback = pendingPhotoResult
            val file = pendingPhotoFile
            pendingPhotoResult = null
            pendingPhotoFile = null

            if (success && file != null && file.exists()) {
                callback?.invoke(PhotoResult(success = true, filename = file.name))
            } else {
                file?.takeIf { it.exists() }?.delete()
                callback?.invoke(
                    PhotoResult(
                        success = false,
                        error = if (success) "No se pudo guardar la foto" else null
                    )
                )
            }
        }

        pickPhotoLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            val callback = pendingPhotoResult
            pendingPhotoResult = null

            if (uri != null) {
                try {
                    val copiedFile = photoManager.copyUriToPhoto(uri)
                    callback?.invoke(PhotoResult(success = true, filename = copiedFile.name))
                } catch (e: Exception) {
                    callback?.invoke(
                        PhotoResult(
                            success = false,
                            error = e.message ?: "Error al importar imagen"
                        )
                    )
                }
            } else {
                callback?.invoke(PhotoResult(success = false, error = null))
            }
        }

        importBackupLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Algunos proveedores no permiten persistir el permiso; ignorar
                }

                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        backupManager.restoreBackup(uri)
                    }
                    
                    if (result.needsPassword) {
                        showRestorePasswordDialog(uri)
                    } else {
                        handleRestoreResult(result)
                    }
                }
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Importación cancelada",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        setContent {
            PsychoLoggerTheme {
                // Estado local opcional por si quieres forzar cubierta visual
                var forceLocked by remember { mutableStateOf(false) }

                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                // --- ÚNICO bloqueo en render: si debe estar bloqueada, bloquea ---
                LaunchedEffect(Unit) {
                    if (appLockManager.shouldAppBeLocked()) {
                        appLockManager.lockApp()
                        // forceLocked = true // (opcional si quieres usarlo como cubierta visual adicional)
                    }
                }

                // Observa ciclo de vida para gestionar autolock y cancelar jobs
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> appLockManager.onAppBackgrounded()
                            Lifecycle.Event.ON_RESUME -> appLockManager.onAppForegrounded()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                // Estado de pantallas
                var showSettings by remember { mutableStateOf(false) }
                var showAdvancedSettings by remember { mutableStateOf(false) }
                val showProfileSettings by showProfileSettingsState  // Observa el estado de clase
                var showPinSetup by remember { mutableStateOf(false) }
                var showPinEntry by remember { mutableStateOf(false) }

                // Observa el estado de bloqueo y configuración
                val isAppLocked by appLockManager.isAppLocked.collectAsState()
                val isAppLockEnabled by appLockManager.isAppLockEnabled.collectAsState()
                val autoLockDelay by appLockManager.autoLockDelay.collectAsState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                ) {
                    // --- LOCKSCREEN + auto-prompt UNA VEZ al mostrarse ---
                    if ((isAppLockEnabled && isAppLocked) || forceLocked) {

                        // Auto-lanzar el prompt una sola vez cuando aparece LockScreen.
                        // Si el usuario cancela o hay error, NO reintentamos solos:
                        LaunchedEffect(isAppLocked) {
                            if (isAppLocked && appLockManager.isBiometricAvailable() && appLockManager.needsAuth() && appLockManager.canShowPromptNow()) {
                                appLockManager.showBiometricPrompt(
                                    activity = this@MainActivity,
                                    onSuccess = {
                                        // AppLockManager ya hace unlockApp() internamente
                                        forceLocked = false
                                        Toast.makeText(context, "✅ Aplicación desbloqueada", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        // No reintentamos automáticamente; el usuario puede pulsar botones en LockScreen
                                        // Puedes mostrar un aviso si quieres:
                                        // Toast.makeText(context, "🔒 Bloqueada por inactividad", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }

                        LockScreen(
                            onUnlockWithBiometric = {
                                if (appLockManager.isBiometricAvailable() && appLockManager.canShowPromptNow()) {
                                    appLockManager.showBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = {
                                            // AppLockManager ya hace unlockApp()
                                            forceLocked = false
                                            Toast.makeText(context, "✅ Aplicación desbloqueada", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else if (!appLockManager.isBiometricAvailable()) {
                                    Toast.makeText(context, "❌ Biometría no disponible", Toast.LENGTH_LONG).show()
                                }
                                // Si no canShowPromptNow(), simplemente no hace nada (evita spam)
                            },
                            onUnlockWithPin = {
                                if (appLockManager.hasPinSet()) {
                                    showPinEntry = true
                                } else {
                                    Toast.makeText(context, "🔢 Primero debes configurar un PIN en Ajustes", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    } else {
                        // --- Contenido principal de la aplicación ---
                        Box(modifier = Modifier.fillMaxSize()) {
                            WebViewScreen(
                                context = context,
                                isSoftTheme = isSoftTheme.value,
                                onFileChooser = { callback ->
                                    filePathCallback = callback
                                    this@MainActivity.openFileChooser()
                                },
                                onWebViewReady = { webView ->
                                    this@MainActivity.webView = webView
                                }
                            )

                            // Botón de configuración flotante
                            FloatingActionButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Configuración"
                                )
                            }
                        }
                    }

                    // --- Pantalla de ajustes ---
                    if (showSettings) {
                        SettingsScreen(
                            isAppLockEnabled = isAppLockEnabled,
                            onAppLockToggle = { enabled ->
                                appLockManager.setAppLockEnabled(enabled)
                                if (enabled) {
                                    // Recomendación: bloquear en el acto para dejar claro el nuevo estado
                                    appLockManager.lockApp()
                                    Toast.makeText(context, "🔒 Bloqueo de aplicación activado", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "🔓 Bloqueo de aplicación desactivado", Toast.LENGTH_SHORT).show()
                                }
                            },
                            isSoftTheme = isSoftTheme.value,
                            onThemeToggle = { soft ->
                                toggleTheme(soft)
                            },
                            onAdvancedSettings = { showAdvancedSettings = true },
                            onPinSetup = { showPinSetup = true },
                            onClose = { showSettings = false }
                        )
                    }

                    // --- Ajustes avanzados ---
                    if (showAdvancedSettings) {
                        AdvancedSettingsScreen(
                            autoLockDelay = autoLockDelay,
                            onAutoLockDelayChange = { delay ->
                                appLockManager.setAutoLockDelay(delay)
                                Toast.makeText(context, "⏰ Tiempo de bloqueo actualizado", Toast.LENGTH_SHORT).show()
                            },
                            onBack = { showAdvancedSettings = false }
                        )
                    }

                    // --- Perfil y Backups ---
                    if (showProfileSettings) {
                        ProfileSettingsScreen(
                            nickname = "Psiconauta", 
                            onNicknameChange = { /* Guardar localmente si fuera necesario */ },
                            isDarkTheme = true,
                            onThemeToggle = { /* Cambiar */ },
                            isNotificationsEnabled = true,
                            onNotificationsToggle = { /* Cambiar */ },
                            isBiometricEnabled = isAppLockEnabled,
                            onBiometricToggle = { enabled -> 
                                appLockManager.setAppLockEnabled(enabled)
                            },
                            onBack = { hideProfileSettings() },
                            onExportData = {
                                getLocalStorageData()
                            },
                            onImportData = { csv ->
                                webAppInterface?.processFileContent(csv, "import_manual.csv")
                            },
                            onClearData = {
                                executeJavaScript("localStorage.clear(); location.reload();")
                            }
                        )
                    }

                    // --- Configuración de PIN ---
                    if (showPinSetup) {
                        PinSetupScreen(
                            onPinSet = { pin ->
                                appLockManager.setPin(pin)
                                showPinSetup = false
                                Toast.makeText(context, "🔢 PIN configurado correctamente", Toast.LENGTH_SHORT).show()
                            },
                            onCancel = { showPinSetup = false }
                        )
                    }

                    // --- Entrada de PIN ---
                    if (showPinEntry) {
                        PinEntryScreen(
                            onPinCorrect = { pin ->
                                if (appLockManager.verifyPin(pin)) {
                                    appLockManager.unlockApp()
                                    forceLocked = false
                                    showPinEntry = false
                                    Toast.makeText(context, "✅ PIN correcto", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "❌ PIN incorrecto", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onBackToBiometric = { showPinEntry = false }
                        )
                    }
                }
            }
        }
    }

    private fun toggleTheme(soft: Boolean) {
        _isSoftTheme.value = soft
        uiPrefs.edit().putBoolean("is_soft_theme", soft).apply()
        
        // Notificar al WebView
        val themeName = if (soft) "soft" else "original"
        executeJavaScript("if (typeof setAppTheme === 'function') { setAppTheme('$themeName'); }")
    }

    // ==== SELECTOR DE ARCHIVOS (MÉTODO DE LA ACTIVITY) ====
    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "text/csv",
                    "text/comma-separated-values",
                    "application/csv",
                    "text/plain"
                )
            )
        }

        try {
            val chooser = Intent.createChooser(intent, "Seleccionar archivo CSV")
            fileChooserLauncher.launch(chooser)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            Toast.makeText(this, "Error al abrir selector: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPhotoFromCamera(onResult: (PhotoResult) -> Unit) {
        if (pendingPhotoResult != null) {
            onResult(PhotoResult(success = false, error = "Ya hay una operación de imagen en curso"))
            return
        }

        try {
            val file = photoManager.createPhotoFile()
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            pendingPhotoFile = file
            pendingPhotoResult = onResult
            capturePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            pendingPhotoFile = null
            pendingPhotoResult = null
            onResult(PhotoResult(success = false, error = e.message ?: "No se pudo iniciar la cámara"))
        }
    }

    fun requestPhotoFromGallery(onResult: (PhotoResult) -> Unit) {
        if (pendingPhotoResult != null) {
            onResult(PhotoResult(success = false, error = "Ya hay una operación de imagen en curso"))
            return
        }

        try {
            pendingPhotoResult = onResult
            pickPhotoLauncher.launch("image/*")
        } catch (e: Exception) {
            pendingPhotoResult = null
            onResult(PhotoResult(success = false, error = e.message ?: "No se pudo abrir la galería"))
        }
    }

    fun executeJavaScript(script: String) {
        runOnUiThread {
            webView?.evaluateJavascript(script) { result ->
                println("JavaScript ejecutado. Resultado: $result")
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // ⚠️ FIX CRÍTICO: Resetear estado biométrico al volver del background
        appLockManager.resetBiometricState()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Liberar recursos de audio
        webAppInterface?.release()
        webView = null
    }

    /**
     * Programa backups automáticos cada 12 horas usando WorkManager
     *
     * CONCEPTO: WorkManager Periodic Work
     * - PeriodicWorkRequest ejecuta tareas periódicamente
     * - ExistingPeriodicWorkPolicy.KEEP no duplica el trabajo
     * - Constraints garantiza que solo ejecute en condiciones óptimas
     */
    private fun schedulePeriodicBackups() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresBatteryNotLow(true)  // Solo si batería > 15%
            .build()

        val backupWork = androidx.work.PeriodicWorkRequestBuilder<BackupWorker>(
            12, java.util.concurrent.TimeUnit.HOURS  // Cada 12 horas
        )
            .setConstraints(constraints)
            .build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            BackupWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,  // No reemplazar si ya existe
            backupWork
        )

        android.util.Log.d("MainActivity", "Backups automáticos programados cada 12 horas")
    }

    fun setWebAppInterface(instance: WebAppInterface) {
        webAppInterface = instance
    }

    fun startBackupImportFlow() {
        importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    }

    suspend fun getLocalStorageData(): String = withContext(Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val script = """
                (function() {
                    return JSON.stringify({
                        substances: JSON.parse(localStorage.getItem('substances') || '[]'),
                        entries: JSON.parse(localStorage.getItem('entries') || '[]'),
                        userProfile: JSON.parse(localStorage.getItem('userProfile') || '{}'),
                        customUnits: JSON.parse(localStorage.getItem('psychologger_custom_units') || '{}'),
                        customSets: JSON.parse(localStorage.getItem('psychologger_custom_sets') || '[]'),
                        customSettings: JSON.parse(localStorage.getItem('psychologger_custom_settings') || '[]')
                    });
                })()
            """.trimIndent()
            
            webView?.evaluateJavascript(script) { result ->
                val rawJson = result?.let { 
                    if (it.startsWith('"') && it.endsWith('"')) {
                        org.json.JSONTokener(it).nextValue().toString()
                    } else it
                } ?: "{}"
                continuation.resume(rawJson) { }
            }
        }
    }

    private fun handleRestoreResult(result: BackupManager.RestoreResult) {
        if (result.success && result.dataJson != null) {
            val payloadQuoted = JSONObject.quote(result.dataJson)
            val infoJson = JSONObject().apply {
                put("audios", result.restoredAudios)
                put("photos", result.restoredPhotos)
                result.message?.let { put("message", it) }
            }.toString()
            val infoQuoted = JSONObject.quote(infoJson)

            executeJavaScript(
                """
                    if (window.onBackupRestored) {
                        window.onBackupRestored($payloadQuoted, $infoQuoted);
                    }
                """.trimIndent()
            )

            Toast.makeText(
                this@MainActivity,
                "✅ Backup importado correctamente",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val errorMessage = result.message ?: "No se pudo importar el backup"
            executeJavaScript(
                """
                    if (window.onBackupRestoreError) {
                        window.onBackupRestoreError(${JSONObject.quote(errorMessage)});
                    }
                """.trimIndent()
            )
            Toast.makeText(
                this@MainActivity,
                "❌ $errorMessage",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showRestorePasswordDialog(uri: Uri) {
        runOnUiThread {
            val input = EditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                hint = "Contraseña del Backup"
            }
            
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("🔒 Backup Cifrado")
                .setMessage("Introduce la contraseña para restaurar este backup:")
                .setView(input)
                .setPositiveButton("RESTAURAR") { _, _ ->
                    val password = input.text.toString()
                    lifecycleScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            backupManager.restoreBackup(uri, password)
                        }
                        handleRestoreResult(result)
                    }
                }
                .setNegativeButton("CANCELAR", null)
                .show()
        }
    }
}

// ==== INTERFAZ ANDROID-JS ====
class WebAppInterface(private val context: Context, private val activity: MainActivity) {
    var onProfileRequest: (() -> Unit)? = null

    // === GESTIÓN DE AUDIO ===
    private val audioRecorder = AudioRecorder(context)
    private val audioPlayer = AudioPlayer()

    // === GESTIÓN DE FOTOS ===
    private val photoManager = PhotoManager(context)

    // === GESTIÓN DE BACKUPS ===
    private val backupManager = BackupManager(context)

    // Launcher para solicitar permisos de audio
    private var recordAudioPermissionLauncher: ActivityResultLauncher<String>? = null

    init {
        // Configurar callbacks del reproductor
        audioPlayer.setProgressCallback { progress, currentMs, totalMs ->
            // Notificar a JavaScript sobre el progreso
            activity.executeJavaScript("""
                if (window.onAudioProgressUpdate) {
                    window.onAudioProgressUpdate($progress, $currentMs, $totalMs);
                }
            """.trimIndent())
        }

        audioPlayer.setCompletionCallback {
            // Notificar a JavaScript que terminó la reproducción
            activity.executeJavaScript("""
                if (window.onAudioCompleted) {
                    window.onAudioCompleted();
                }
            """.trimIndent())
        }

        audioPlayer.setErrorCallback { errorMessage ->
            // Notificar a JavaScript sobre errores
            activity.executeJavaScript("""
                if (window.onAudioError) {
                    window.onAudioError("$errorMessage");
                }
            """.trimIndent())
        }
    }

    private data class SubstanceCsv(
        val id: String,
        val name: String,
        val color: String,
        val emoji: String?,
        val createdAt: String,
        val updatedAt: String?
    )

    private data class EntryCsv(
        val id: String,
        val substance: String,
        val dose: Double,
        val unit: String,
        val date: String,
        val set: String?,
        val setting: String?,
        val notes: String?,
        val createdAt: String?,
        val updatedAt: String?
    )

    private fun normaliseLine(raw: String): String =
        raw.trim().removePrefix("\uFEFF")

    private fun isHeaderLine(line: String): Boolean {
        val upper = line.uppercase(Locale.getDefault())
        return upper.startsWith("ID,") ||
                upper.startsWith("ID;") ||
                upper.startsWith("CAMPO,") ||
                upper.startsWith("CAMPO;")
    }

    private fun parseCsvLine(rawLine: String): List<String> {
        if (rawLine.isEmpty()) return emptyList()

        val line = normaliseLine(rawLine)
        if (line.isEmpty()) return emptyList()

        val delimiter = detectDelimiter(line)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var quoteChar = '"'
        var i = 0

        fun flush() {
            result.add(current.toString())
            current.setLength(0)
        }

        while (i < line.length) {
            val ch = line[i]
            when {
                (ch == '"' || ch == '\'') && (!inQuotes) -> {
                    inQuotes = true
                    quoteChar = ch
                }
                ch == quoteChar && inQuotes -> {
                    val nextChar = line.getOrNull(i + 1)
                    if (nextChar == quoteChar) {
                        current.append(quoteChar)
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                ch == delimiter && !inQuotes -> {
                    flush()
                }
                else -> current.append(ch)
            }
            i++
        }

        flush()
        return result
    }

    private fun detectDelimiter(line: String): Char {
        val semicolons = line.count { it == ';' }
        val commas = line.count { it == ',' }
        return when {
            semicolons > 0 && semicolons >= commas -> ';'
            commas > 0 -> ','
            else -> ';'
        }
    }

    private fun parseSubstance(columns: List<String>): SubstanceCsv? {
        if (columns.size < 3) return null

        val id = columns.getOrNull(0)?.trim().orEmpty().ifBlank {
            "${System.currentTimeMillis()}-${kotlin.random.Random.nextInt(1000, 9999)}"
        }
        val name = columns.getOrNull(1)?.trim('"', '\'').orEmpty()
        val color = columns.getOrNull(2)?.trim('"', '\'').orEmpty()
        if (name.isEmpty()) return null

        val thirdColumn = columns.getOrNull(3)?.trim('"', '\'')
        val (emoji, createdAtIndex) = if (isLikelyDate(thirdColumn)) {
            null to 3
        } else {
            (thirdColumn?.ifBlank { null }) to 4
        }

        val createdAtRaw = columns.getOrNull(createdAtIndex)?.trim('"', '\'')
        val updatedAtRaw = columns.getOrNull(createdAtIndex + 1)?.trim('"', '\'')

        return SubstanceCsv(
            id = id,
            name = name,
            color = color,
            emoji = emoji,
            createdAt = createdAtRaw?.takeUnless { it.isBlank() } ?: Date().toInstantString(),
            updatedAt = updatedAtRaw?.takeUnless { it.isBlank() }
        )
    }

    private fun parseEntry(columns: List<String>): EntryCsv? {
        if (columns.size < 5) return null

        val id = columns.getOrNull(0)?.trim().orEmpty().ifBlank {
            System.currentTimeMillis().toString()
        }
        val substance = columns.getOrNull(1)?.trim('"', '\'').orEmpty()
        if (substance.isEmpty()) return null

        val doseValue = columns.getOrNull(2)?.trim()?.replace(',', '.') ?: "0"
        val dose = doseValue.toDoubleOrNull() ?: 0.0

        val unit = columns.getOrNull(3)?.trim('"', '\'').orEmpty()
        val date = columns.getOrNull(4)?.trim('"', '\'').orEmpty()

        val set = columns.getOrNull(5)?.trim('"', '\'')?.takeUnless { it.isBlank() }
        val setting = columns.getOrNull(6)?.trim('"', '\'')?.takeUnless { it.isBlank() }
        val notes = columns.getOrNull(7)?.trim('"', '\'')?.takeUnless { it.isBlank() }
        val createdAt = columns.getOrNull(8)?.trim('"', '\'')?.takeUnless { it.isBlank() }
        val updatedAt = columns.getOrNull(9)?.trim('"', '\'')?.takeUnless { it.isBlank() }

        return EntryCsv(
            id = id,
            substance = substance,
            dose = dose,
            unit = unit,
            date = date,
            set = set,
            setting = setting,
            notes = notes,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun isLikelyDate(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val normalized = value.trim()
        return normalized.contains("-") || normalized.contains("T") || normalized.contains(":")
    }

    private fun Date.toInstantString(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(this)

    @JavascriptInterface
    fun downloadCSV(csvContent: String, filename: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val finalFilename = if (filename.isBlank()) "bitacora_psiconáutica_$timestamp.csv" else filename

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - usar MediaStore
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalFilename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let { fileUri ->
                    resolver.openOutputStream(fileUri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "✅ CSV exportado: $finalFilename", Toast.LENGTH_LONG).show()

                    // Después de exportar exitosamente, abrir ShareSheet automáticamente
                    activity.runOnUiThread {
                        try {
                            val shareFile = File(context.cacheDir, finalFilename)
                            shareFile.writeText(csvContent)

                            val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                putExtra(Intent.EXTRA_SUBJECT, "Bitácora Psiconáutica")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val chooser = Intent.createChooser(shareIntent, "🚀 Compartir CSV")
                            activity.startActivity(chooser)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } ?: run {
                    Toast.makeText(context, "❌ Error al crear archivo", Toast.LENGTH_LONG).show()
                }
            } else {
                // Android 9 y anteriores - método tradicional
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, finalFilename)

                FileOutputStream(file).use { fos ->
                    fos.write(csvContent.toByteArray(Charsets.UTF_8))
                }

                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(file)
                }
                context.sendBroadcast(intent)

                Toast.makeText(context, "✅ CSV exportado: $finalFilename", Toast.LENGTH_LONG).show()

                // Después de exportar exitosamente, abrir ShareSheet automáticamente
                activity.runOnUiThread {
                    try {
                        val shareFile = File(context.cacheDir, finalFilename)
                        shareFile.writeText(csvContent)

                        val shareUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, shareUri)
                            putExtra(Intent.EXTRA_SUBJECT, "Bitácora Psiconáutica")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val chooser = Intent.createChooser(shareIntent, "🚀 Compartir CSV")
                        activity.startActivity(chooser)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    @JavascriptInterface
    fun shareCSV(csvContent: String, filename: String = "") {
        // Ejecutar en el hilo principal de la Activity
        activity.runOnUiThread {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val finalFilename = if (filename.isBlank()) "bitacora_psiconáutica_$timestamp.csv" else filename

                // Crear archivo temporal en cache
                val file = File(context.cacheDir, finalFilename)
                file.writeText(csvContent)

                // Obtener URI del archivo usando FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                // Crear Intent para compartir
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Bitácora Psiconáutica - $timestamp")
                    putExtra(Intent.EXTRA_TEXT, "📊 Mis datos exportados de PsychoLogger 🧠✨\n\nArchivo: $finalFilename")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Crear chooser y mostrarlo desde la Activity
                val chooserIntent = Intent.createChooser(shareIntent, "🚀 Compartir mi bitácora psiconáutica")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(chooserIntent)

                Toast.makeText(context, "✅ ShareSheet abierto", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun processFileContent(content: String, filename: String) {
        try {
            if (content.isBlank()) {
                Toast.makeText(context, "❌ El archivo está vacío", Toast.LENGTH_LONG).show()
                return
            }

            val lines = content
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .split("\n")
            var substanceCount = 0
            var entryCount = 0
            var currentSection = ""

            for (line in lines) {
                val trimmedLine = normaliseLine(line)
                if (trimmedLine.isEmpty()) continue

                val upper = trimmedLine.uppercase(Locale.getDefault())
                when {
                    upper == "SUSTANCIAS" -> { currentSection = "substances"; continue }
                    upper == "REGISTROS"  -> { currentSection = "entries"; continue }
                    upper == "PERFIL"     -> { currentSection = "profile"; continue }
                }

                if (isHeaderLine(trimmedLine)) continue

                val columns = parseCsvLine(trimmedLine)
                when (currentSection) {
                    "substances" -> if (parseSubstance(columns) != null) substanceCount++
                    "entries" -> if (parseEntry(columns) != null) entryCount++
                }
            }

            if (substanceCount == 0 && entryCount == 0) {
                Toast.makeText(context, "❌ No se encontraron datos válidos en el archivo", Toast.LENGTH_LONG).show()
                return
            }

            showImportDialog(content, substanceCount, entryCount)

        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al procesar archivo: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun showImportDialog(csvContent: String, substancesCount: Int, entriesCount: Int) {
        activity.runOnUiThread {
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("📂 Importar Datos CSV")
                .setMessage(
                    """
                    📊 Datos encontrados en el archivo:
                    • $substancesCount sustancias
                    • $entriesCount registros
                    
                    ⚠️ ¿Qué deseas hacer?
                    """.trimIndent()
                )
                .setPositiveButton("➕ AÑADIR") { _, _ -> importCSVData(csvContent, false) }
                .setNegativeButton("🔄 REEMPLAZAR") { _, _ ->
                    val confirmBuilder = android.app.AlertDialog.Builder(context)
                        .setTitle("⚠️ Confirmar Reemplazo")
                        .setMessage("¿Estás seguro de que quieres BORRAR todos los datos actuales?")
                        .setPositiveButton("SÍ, REEMPLAZAR") { _, _ -> importCSVData(csvContent, true) }
                        .setNegativeButton("CANCELAR") { dialog, _ -> dialog.dismiss() }
                    confirmBuilder.show()
                }
                .setNeutralButton("CANCELAR") { dialog, _ -> dialog.dismiss() }

            builder.show()
        }
    }

    private fun importCSVData(csvContent: String, replaceAll: Boolean) {
        try {
            activity.runOnUiThread {
                Toast.makeText(context, "🔄 Procesando importación...", Toast.LENGTH_SHORT).show()

                val webView = activity.webView ?: run {
                    Toast.makeText(context, "❌ Error: WebView no disponible", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }

                val lines = csvContent
                    .replace("\r\n", "\n")
                    .replace("\r", "\n")
                    .split("\n")
                var currentSection = ""
                val substancesToImport = mutableListOf<SubstanceCsv>()
                val entriesToImport    = mutableListOf<EntryCsv>()

                for (line in lines) {
                    val trimmedLine = normaliseLine(line)
                    if (trimmedLine.isEmpty()) continue

                    val upper = trimmedLine.uppercase(Locale.getDefault())
                    when {
                        upper == "SUSTANCIAS" -> { currentSection = "substances"; continue }
                        upper == "REGISTROS"  -> { currentSection = "entries"; continue }
                        upper == "PERFIL"     -> { currentSection = "profile"; continue }
                    }

                    if (isHeaderLine(trimmedLine)) continue

                    val columns = parseCsvLine(trimmedLine)

                    when (currentSection) {
                        "substances" -> parseSubstance(columns)?.let { substancesToImport += it }
                        "entries" -> parseEntry(columns)?.let { entriesToImport += it }
                    }
                }

                val jsScript = buildString {
                    appendLine("try {")
                    appendLine("  console.log('Iniciando importación...');")
                    if (replaceAll) {
                        appendLine("  substances = [];")
                        appendLine("  entries = [];")
                        appendLine("  userProfile = userProfile || {};")
                    }
                    appendLine("  var importedSub = 0; var importedEnt = 0;")

                    substancesToImport.forEach { s ->
                        val subJson = JSONObject().apply {
                            put("id", s.id)
                            put("name", s.name)
                            put("color", s.color)
                            s.emoji?.let { put("emoji", it) }
                            put("createdAt", s.createdAt)
                            s.updatedAt?.let { put("updatedAt", it) }
                        }
                        val subJsonString = subJson.toString()
                        val lowerName = s.name.lowercase(Locale.getDefault())
                        val lowerNameLiteral = JSONObject.quote(lowerName)
                        appendLine("""
                            (function(){
                              var newSub = ${subJsonString};
                              var shouldInsert = true;
                              if (!${replaceAll}) {
                                shouldInsert = !(substances || []).some(function(ex){ return ((ex.name || '')).toLowerCase() === ${lowerNameLiteral}; });
                              }
                              if (shouldInsert) { substances.push(newSub); importedSub++; }
                            })();
                        """.trimIndent())
                    }

                    entriesToImport.forEach { e ->
                        val entryJson = JSONObject().apply {
                            put("id", e.id)
                            put("substance", e.substance)
                            put("dose", e.dose)
                            put("unit", e.unit)
                            put("date", e.date)
                            put("set", e.set ?: "")
                            put("setting", e.setting ?: "")
                            put("notes", e.notes ?: "")
                            put("createdAt", e.createdAt ?: "")
                            put("updatedAt", e.updatedAt ?: "")
                        }
                        val entryJsonString = entryJson.toString()
                        appendLine("""
                            (function(){
                              entries.push(${entryJsonString});
                              importedEnt++;
                            })();
                        """.trimIndent())
                    }

                    appendLine("""
                        localStorage.setItem('substances', JSON.stringify(substances));
                        localStorage.setItem('entries', JSON.stringify(entries));
                        localStorage.setItem('userProfile', JSON.stringify(userProfile || {}));

                        if (typeof refreshAfterImport === 'function') {
                            refreshAfterImport();
                        } else if (typeof syncDataFromStorage === 'function') {
                            syncDataFromStorage();
                            if (typeof rebuildSuggestionsFromEntries === 'function') {
                                rebuildSuggestionsFromEntries();
                            }
                            renderSubstanceList(); generateCalendar(); renderStats();
                        } else {
                            window.substances = substances;
                            window.entries = entries;
                            renderSubstanceList(); generateCalendar(); renderStats();
                        }

                        Android.showToast('✅ Importado: ' + importedSub + ' sustancias, ' + importedEnt + ' registros');
                    """.trimIndent())
                    appendLine("} catch (error) { console.error(error); Android.showToast('❌ Error: ' + error.message); }")
                }

                webView.evaluateJavascript(jsScript, null)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    // ========================================
    // === MÉTODOS DE GRABACIÓN DE AUDIO ===
    // ========================================

    /**
     * Inicia la grabación de audio
     *
     * IMPORTANTE: Este método verifica permisos automáticamente.
     * Si no hay permisos, muestra un Toast pidiendo que el usuario los otorgue manualmente.
     *
     * @return Ruta del archivo donde se está grabando (o mensaje de error)
     *
     * CONCEPTO: @JavascriptInterface
     * Esta anotación hace que el método sea accesible desde JavaScript:
     * JavaScript: Android.startRecording()
     * Kotlin: este método se ejecuta
     */
    @JavascriptInterface
    fun startRecording(): String {
        return try {
            // Verificar permiso de audio
            if (!hasRecordAudioPermission()) {
                Toast.makeText(context, "⚠️ Se requiere permiso de micrófono. Ve a Ajustes → Permisos", Toast.LENGTH_LONG).show()
                return "ERROR: Sin permiso de micrófono"
            }

            // Iniciar grabación
            val file = audioRecorder.startRecording()

            // Retornar el nombre del archivo (JavaScript lo guardará)
            file.name
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al grabar: ${e.message}", Toast.LENGTH_SHORT).show()
            "ERROR: ${e.message}"
        }
    }

    /**
     * Detiene la grabación actual
     *
     * @return JSON con información del resultado: {"filename": "...", "duration": 15000}
     *
     * CONCEPTO: Retornar JSON desde Kotlin a JavaScript
     * JavaScript puede parsear esto con JSON.parse()
     */
    @JavascriptInterface
    fun stopRecording(): String {
        return try {
            val result = audioRecorder.stopRecording()

            // Construir JSON manualmente (simple y claro)
            """{"filename": "${result.file.name}", "duration": ${result.durationMillis}}"""
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al detener grabación: ${e.message}", Toast.LENGTH_SHORT).show()
            """{"error": "${e.message}"}"""
        }
    }

    /**
     * Cancela la grabación actual sin guardar
     */
    @JavascriptInterface
    fun cancelRecording() {
        try {
            audioRecorder.cancelRecording()
            Toast.makeText(context, "🗑️ Grabación cancelada", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al cancelar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Verifica si hay una grabación en curso
     *
     * @return "true" o "false" (como String porque JavaScript lo espera así)
     *
     * CONCEPTO: JavaScript solo recibe Strings desde @JavascriptInterface
     * Debemos retornar "true"/"false" como String, no Boolean
     */
    @JavascriptInterface
    fun isRecording(): String {
        return audioRecorder.isRecording().toString()
    }

    /**
     * Obtiene la duración actual de la grabación en milisegundos
     *
     * ÚTIL PARA: Mostrar timer "00:15" en la UI durante grabación
     */
    @JavascriptInterface
    fun getRecordingDuration(): String {
        return audioRecorder.getCurrentDuration().toString()
    }

    // ========================================
    // === MÉTODOS DE REPRODUCCIÓN DE AUDIO ===
    // ========================================

    /**
     * Reproduce un archivo de audio
     *
     * @param filename Nombre del archivo (ej: "audio_123.m4a")
     * @return "OK" si se inició correctamente, "ERROR: ..." si falló
     *
     * CONCEPTO: Rutas de archivos
     * JavaScript solo conoce el NOMBRE del archivo.
     * Kotlin reconstruye la ruta completa en filesDir/audio_notes/
     */
    @JavascriptInterface
    fun playAudio(filename: String): String {
        return try {
            // Reconstruir ruta completa
            val audioNotesDir = File(context.filesDir, "audio_notes")
            val audioFile = File(audioNotesDir, filename)

            if (!audioFile.exists()) {
                Toast.makeText(context, "❌ Archivo de audio no encontrado", Toast.LENGTH_SHORT).show()
                return "ERROR: Archivo no existe"
            }

            // Reproducir
            val success = audioPlayer.play(audioFile)
            if (success) "OK" else "ERROR: No se pudo reproducir"
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al reproducir: ${e.message}", Toast.LENGTH_SHORT).show()
            "ERROR: ${e.message}"
        }
    }

    /**
     * Pausa la reproducción actual
     */
    @JavascriptInterface
    fun pauseAudio() {
        audioPlayer.pause()
    }

    /**
     * Reanuda la reproducción
     */
    @JavascriptInterface
    fun resumeAudio() {
        audioPlayer.resume()
    }

    /**
     * Detiene completamente la reproducción
     */
    @JavascriptInterface
    fun stopAudio() {
        audioPlayer.stop()
    }

    /**
     * Verifica si hay audio reproduciéndose
     *
     * @return "true" o "false" como String
     */
    @JavascriptInterface
    fun isPlayingAudio(): String {
        return audioPlayer.isPlaying().toString()
    }

    /**
     * Elimina un archivo de audio del almacenamiento
     *
     * @param filename Nombre del archivo a eliminar
     * @return "OK" si se eliminó, "ERROR: ..." si falló
     *
     * ÚTIL PARA: Cuando el usuario elimina una entrada con audio adjunto
     */
    @JavascriptInterface
    fun deleteAudio(filename: String): String {
        return try {
            val audioNotesDir = File(context.filesDir, "audio_notes")
            val audioFile = File(audioNotesDir, filename)

            if (audioFile.exists()) {
                audioFile.delete()
                "OK"
            } else {
                "ERROR: Archivo no existe"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Comparte un archivo de audio vía ShareSheet
     *
     * @param filename Nombre del archivo a compartir
     *
     * CONCEPTO: FileProvider
     * No podemos compartir archivos de filesDir directamente (privados).
     * Usamos FileProvider para crear URIs temporales compartibles.
     */
    @JavascriptInterface
    fun shareAudio(filename: String) {
        activity.runOnUiThread {
            try {
                val audioNotesDir = File(context.filesDir, "audio_notes")
                val audioFile = File(audioNotesDir, filename)

                if (!audioFile.exists()) {
                    Toast.makeText(context, "❌ Archivo no encontrado", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                // Crear URI compartible con FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    audioFile
                )

                // Intent de compartir
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Nota de voz - PsychoLogger")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "🎤 Compartir nota de voz")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(chooser)

            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // ========================================
    // === MÉTODOS DE FOTOS ===
    // ========================================

    @JavascriptInterface
    fun capturePhoto() {
        activity.runOnUiThread {
            if (!hasCameraPermission()) {
                Toast.makeText(context, "⚠️ Se requiere permiso de cámara. Ve a Ajustes → Permisos", Toast.LENGTH_LONG).show()
                val message = JSONObject.quote("Permiso de cámara denegado")
                activity.executeJavaScript(
                    """
                        if (window.onPhotoCaptureError) {
                            window.onPhotoCaptureError($message);
                        }
                    """.trimIndent()
                )
                return@runOnUiThread
            }

            activity.requestPhotoFromCamera { result ->
                when {
                    result.success && !result.filename.isNullOrBlank() -> {
                        val quoted = JSONObject.quote(result.filename)
                        activity.executeJavaScript(
                            """
                                if (window.onPhotoCaptured) {
                                    window.onPhotoCaptured($quoted);
                                }
                            """.trimIndent()
                        )
                    }
                    !result.error.isNullOrBlank() -> {
                        val message = JSONObject.quote(result.error)
                        activity.executeJavaScript(
                            """
                                if (window.onPhotoCaptureError) {
                                    window.onPhotoCaptureError($message);
                                }
                            """.trimIndent()
                        )
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun pickPhotoFromGallery() {
        activity.runOnUiThread {
            activity.requestPhotoFromGallery { result ->
                when {
                    result.success && !result.filename.isNullOrBlank() -> {
                        val quoted = JSONObject.quote(result.filename)
                        activity.executeJavaScript(
                            """
                                if (window.onPhotoCaptured) {
                                    window.onPhotoCaptured($quoted);
                                }
                            """.trimIndent()
                        )
                    }
                    !result.error.isNullOrBlank() -> {
                        val message = JSONObject.quote(result.error)
                        activity.executeJavaScript(
                            """
                                if (window.onPhotoCaptureError) {
                                    window.onPhotoCaptureError($message);
                                }
                            """.trimIndent()
                        )
                    }
                }
            }
        }
    }

    @JavascriptInterface
    fun deletePhoto(filename: String): String {
        return try {
            if (filename.isBlank()) {
                "ERROR: Nombre de archivo inválido"
            } else if (photoManager.deletePhoto(filename)) {
                "OK"
            } else {
                "ERROR: Archivo no encontrado"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    @JavascriptInterface
    fun getPhotoPreview(filename: String): String {
        return try {
            photoManager.getPreviewDataUrl(filename) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun viewPhoto(filename: String) {
        activity.runOnUiThread {
            try {
                val uri = photoManager.getShareUri(filename)
                if (uri == null) {
                    Toast.makeText(context, "❌ Imagen no encontrada", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                activity.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "❌ No se encontró app para abrir imágenes", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error al abrir imagen: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun sharePhoto(filename: String) {
        activity.runOnUiThread {
            try {
                val uri = photoManager.getShareUri(filename)
                if (uri == null) {
                    Toast.makeText(context, "❌ Imagen no encontrada", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Foto - PsychoLogger")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "📷 Compartir foto")
                activity.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error al compartir foto: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========================================
    // === MÉTODOS DE BACKUP Y EXPORTACIÓN ===
    // ========================================

    /**
     * Crear backup manual (botón en Ajustes)
     *
     * @param localStorageJson JSON string con todo el localStorage
     * @return "OK" si se creó correctamente, "ERROR: ..." si falló
     */
    @JavascriptInterface
    fun cacheLocalStorageSnapshot(localStorageJson: String) {
        try {
            backupManager.saveLocalStorageSnapshot(localStorageJson)
        } catch (e: Exception) {
            android.util.Log.e("WebAppInterface", "Error al actualizar snapshot de backup", e)
        }
    }

    /**
     * Crear backup manual (botón en Ajustes)
     *
     * @param localStorageJson JSON string con todo el localStorage
     * @return "OK" si se creó correctamente, "ERROR: ..." si falló
     */
    @JavascriptInterface
    fun createManualBackup(localStorageJson: String): String {
        return try {
            val backupFile = backupManager.createBackupWithData(
                localStorageData = localStorageJson,
                password = null,
                includeMedia = true
            )

            if (backupFile != null) {
                val backupPath = backupFile.absolutePath
                Toast.makeText(
                    context,
                    "✅ Backup guardado en:\n$backupPath",
                    Toast.LENGTH_LONG
                ).show()
                "OK:${backupFile.name}"
            } else {
                Toast.makeText(context, "❌ Error al crear backup", Toast.LENGTH_SHORT).show()
                "ERROR: No se pudo crear el backup"
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
            "ERROR: ${e.message}"
        }
    }

    @JavascriptInterface
    fun importBackup() {
        activity.runOnUiThread {
            Toast.makeText(context, "📥 Selecciona un archivo ZIP de backup", Toast.LENGTH_SHORT).show()
            activity.startBackupImportFlow()
        }
    }

    @JavascriptInterface
    fun getLastBackupInfo(): String {
        return try {
            val metadata = backupManager.getLastBackupMetadata() ?: return ""
            JSONObject().apply {
                put("type", metadata.type)
                put("timestamp", metadata.timestamp)
                put("filename", metadata.filename)
                put("absolutePath", metadata.absolutePath)
                put("formattedDate", metadata.formattedDate)
            }.toString()
        } catch (e: Exception) {
            ""
        }
    }
    @JavascriptInterface
    fun createComprehensiveBackup(localStorageData: String, password: String?, includeMedia: Boolean) {
        activity.lifecycleScope.launch {
            val backupFile = withContext(Dispatchers.IO) {
                backupManager.createBackupWithData(
                    localStorageData = localStorageData,
                    password = password?.takeIf { it.isNotBlank() },
                    includeMedia = includeMedia
                )
            }

            if (backupFile != null) {
                activity.runOnUiThread {
                    Toast.makeText(context, "✅ Backup creado exitosamente", Toast.LENGTH_SHORT).show()
                }
                // Notificar éxito a JS si es necesario
                activity.executeJavaScript("if(window.onBackupCreated) { window.onBackupCreated('${backupFile.name}'); }")
            } else {
                activity.runOnUiThread {
                    Toast.makeText(context, "❌ Error al crear backup", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @JavascriptInterface
    fun showProfileSettings() {
        activity.runOnUiThread {
            onProfileRequest?.invoke()
        }
    }

    fun shareBackup(filename: String) {
        activity.runOnUiThread {
            try {
                val backupDir = backupManager.getBackupsDirectory()
                val backupFile = File(backupDir, filename)

                if (!backupFile.exists()) {
                    Toast.makeText(context, "❌ El archivo de backup no existe", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                val shareUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    backupFile
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Backup PsychoLogger: $filename")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(shareIntent, "🚀 Compartir Backup")
                activity.startActivity(chooser)

            } catch (e: Exception) {
                android.util.Log.e("WebAppInterface", "Error al compartir backup", e)
                Toast.makeText(context, "❌ Error al compartir: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @JavascriptInterface
    fun getBackupsDirectory(): String {
        return backupManager.getBackupsDirectory().absolutePath
    }

    /**
     * Exportar todos los audios en un ZIP (sin cifrar)
     *
     * @return "OK" si se exportó, "ERROR: ..." si falló
     */
    @JavascriptInterface
    fun exportAudiosZip(): String {
        return try {
            val zipFile = backupManager.exportAudioZip(password = null)

            if (zipFile != null) {
                // Compartir vía ShareSheet
                activity.runOnUiThread {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Notas de voz - PsychoLogger")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(shareIntent, "📦 Exportar audios")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(chooser)
                }

                "OK"
            } else {
                "ERROR: No se pudo crear el ZIP"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    @JavascriptInterface
    fun exportPhotosZip(): String {
        return try {
            val zipFile = backupManager.exportPhotosZip()

            if (zipFile != null) {
                activity.runOnUiThread {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Fotos - PsychoLogger")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(shareIntent, "📸 Exportar fotos")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(chooser)
                }

                "OK"
            } else {
                "ERROR: No se pudo crear el ZIP de fotos"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Exportar audios en ZIP cifrado con contraseña
     *
     * @param password Contraseña para cifrar (AES-256)
     * @return "OK" si se exportó, "ERROR: ..." si falló
     */
    @JavascriptInterface
    fun exportAudiosZipEncrypted(password: String): String {
        return try {
            if (password.length < 8) {
                Toast.makeText(
                    context,
                    "⚠️ La contraseña debe tener al menos 8 caracteres",
                    Toast.LENGTH_SHORT
                ).show()
                return "ERROR: Contraseña muy corta"
            }

            val zipFile = backupManager.exportAudioZip(password = password)

            if (zipFile != null) {
                // Compartir vía ShareSheet
                activity.runOnUiThread {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "Notas de voz cifradas - PsychoLogger")
                        putExtra(Intent.EXTRA_TEXT, "⚠️ Este archivo está cifrado con AES-256.\nGuarda la contraseña en un lugar seguro.")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    val chooser = Intent.createChooser(shareIntent, "🔒 Exportar audios cifrados")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(chooser)
                }

                Toast.makeText(
                    context,
                    "✅ ZIP cifrado creado. Guarda la contraseña!",
                    Toast.LENGTH_LONG
                ).show()

                "OK"
            } else {
                "ERROR: No se pudo crear el ZIP cifrado"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Listar backups disponibles
     *
     * @return JSON array con la lista de backups
     */
    @JavascriptInterface
    fun listBackups(): String {
        return try {
            val backups = backupManager.listBackups()
            val jsonArray = org.json.JSONArray()

            backups.forEach { backup ->
                val jsonObj = org.json.JSONObject().apply {
                    put("name", backup.file.name)
                    put("date", backup.formattedDate)
                    put("size", backup.formattedSize)
                    put("timestamp", backup.timestamp)
                }
                jsonArray.put(jsonObj)
            }

            jsonArray.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    // ========================================
    // === MÉTODOS AUXILIARES ===
    // ========================================

    /**
     * Verifica si la app tiene permiso de grabación de audio
     *
     * CONCEPTO: Runtime Permissions
     * En Android 6.0+ los permisos peligrosos deben verificarse en tiempo de ejecución.
     */
    private fun hasRecordAudioPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Libera recursos al destruir la interfaz
     *
     * IMPORTANTE: Llamar esto en onDestroy() de MainActivity
     */
    fun release() {
        audioRecorder.cancelRecording()
        audioPlayer.release()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    context: Context,
    isSoftTheme: Boolean,
    onFileChooser: (ValueCallback<Array<Uri>>) -> Unit,
    onWebViewReady: (WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        factory = { _ ->
            WebView(context).apply {
                onWebViewReady(this)

                // Asegura foco para que el IME (teclado) actúe correctamente
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url.toString()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            return try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                true
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "No se puede abrir: $url",
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // Aplicar tema persistido
                        val themeName = if (isSoftTheme) "soft" else "original"
                        view?.evaluateJavascript("if (typeof setAppTheme === 'function') { setAppTheme('$themeName'); }", null)

                        view?.evaluateJavascript(
                            """
                            // Función de exportación
                            window.exportToCSV = function() {
                                try {
                                    let csvContent = "";
                                    csvContent += "SUSTANCIAS\n";
                                    csvContent += "ID,Nombre,Color,Emoji,Fecha_Creacion,Fecha_Actualizacion\n";
                                    if (typeof substances !== 'undefined') {
                                        substances.forEach(substance => {
                                            var emoji = substance.emoji || (typeof getSubstanceEmoji === 'function' ? getSubstanceEmoji(substance.name) : '💊');
                                            csvContent += substance.id + ',"' + substance.name + '","' + substance.color + '","' + emoji + '","' + substance.createdAt + '","' + (substance.updatedAt || '') + '"\n';
                                        });
                                    }
                                    csvContent += "\nREGISTROS\n";
                                    csvContent += "ID,Sustancia,Dosis,Unidad,Fecha_Hora,Set,Setting,Notas,Fecha_Creacion,Fecha_Actualizacion\n";
                                    if (typeof entries !== 'undefined') {
                                        entries.forEach(entry => {
                                            csvContent += entry.id + ',"' + entry.substance + '",' + entry.dose + ',"' + entry.unit + '","' + entry.date + '","' + (entry.set || '') + '","' + (entry.setting || '') + '","' + (entry.notes || '') + '","' + entry.createdAt + '","' + (entry.updatedAt || '') + '"\n';
                                        });
                                    }
                                    const today = new Date().toISOString().split('T')[0];
                                    const filename = 'bitacora_psiconáutica_' + today + '.csv';
                                    Android.downloadCSV(csvContent, filename);
                                } catch (error) {
                                    console.error('Error en exportToCSV:', error);
                                    Android.showToast('❌ Error al exportar: ' + error.message);
                                }
                            };

                            // Función de importación que lee archivo directamente
                            window.importFromCSV = function(input) {
                                const file = input.files[0];
                                if (!file) return;
                                Android.showToast('📁 Leyendo archivo: ' + file.name);
                                const reader = new FileReader();
                                reader.onload = function(e) {
                                    try {
                                        const content = e.target.result || '';
                                        if (!content.length) {
                                            Android.showToast('❌ El archivo está vacío');
                                            return;
                                        }
                                        Android.processFileContent(content, file.name);
                                    } catch (error) {
                                        console.error('Error leyendo archivo:', error);
                                        Android.showToast('❌ Error al leer archivo: ' + error.message);
                                    }
                                };
                                reader.onerror = function(error) {
                                    console.error('FileReader error:', error);
                                    Android.showToast('❌ Error al leer archivo');
                                };
                                reader.readAsText(file, 'UTF-8');
                                input.value = '';
                            };

                            console.log('Funciones CSV configuradas');
                        """.trimIndent(), null
                        )
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        return if (filePathCallback != null) {
                            onFileChooser(filePathCallback)
                            true
                        } else {
                            false
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            println("WebView Console [${it.messageLevel()}]: ${it.message()}")
                        }
                        return true
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    javaScriptCanOpenWindowsAutomatically = true

                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = true
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = true
                }

                val activity = context as MainActivity
                val interfaceInstance = WebAppInterface(context, activity)
                activity.setWebAppInterface(interfaceInstance)
                // Assign callback immediately after setting the interface
                interfaceInstance.onProfileRequest = {
                    activity.runOnUiThread {
                        activity.showProfileSettingsFromBridge()
                    }
                }
                addJavascriptInterface(interfaceInstance, "Android")

                setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                    try {
                        val request = DownloadManager.Request(Uri.parse(url))
                        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                        if (mimetype.contains("csv") && !fileName.endsWith(".csv")) {
                            fileName = fileName.substringBeforeLast(".") + ".csv"
                        }
                        request.setMimeType(mimetype)
                        request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_DOWNLOADS,
                            fileName
                        )
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setAllowedOverMetered(true)
                        request.setAllowedOverRoaming(true)
                        val downloadManager =
                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)
                        Toast.makeText(context, "Descargando $fileName...", Toast.LENGTH_LONG)
                            .show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "Error al descargar: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                loadUrl("file:///android_asset/index.html")
            }
        }
    )}
