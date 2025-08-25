package com.d4vram.psychologger

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.d4vram.psychologger.ui.screens.LockScreen
import com.d4vram.psychologger.ui.screens.SettingsScreen
import com.d4vram.psychologger.ui.screens.AdvancedSettingsScreen
import com.d4vram.psychologger.ui.screens.PinSetupScreen
import com.d4vram.psychologger.ui.screens.PinEntryScreen
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : FragmentActivity() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var appLockManager: AppLockManager

    var webView: WebView? = null
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Inicializar el gestor de bloqueo de aplicación
        appLockManager = AppLockManager(this)
        
        // Inicializar el estado de bloqueo de forma SÍNCRONA
        appLockManager.initializeAppLock()
        
        // VERIFICACIÓN CRÍTICA DE SEGURIDAD ANTES de cualquier renderizado
        val isSecurityEnforced = appLockManager.enforceSecurityOnStartup()
        
        // Si la app debe estar bloqueada, forzar el estado inmediatamente
        if (isSecurityEnforced) {
            appLockManager.lockApp()
        }
        
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

        setContent {
            PsychoLoggerTheme {
                // Estado local para garantizar bloqueo inmediato
                var forceLocked by remember { mutableStateOf(isSecurityEnforced) }
                
                // Verificación adicional de seguridad al renderizar
                LaunchedEffect(Unit) {
                    if (appLockManager.shouldAppBeLocked() && !forceLocked) {
                        forceLocked = true
                        appLockManager.lockApp()
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .imePadding()
                ) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    
                    // Observar el ciclo de vida para bloquear la app cuando pase a segundo plano
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_PAUSE -> {
                                    appLockManager.onAppBackgrounded()
                                }
                                Lifecycle.Event.ON_RESUME -> {
                                    appLockManager.onAppForegrounded()
                                }
                                else -> {}
                            }
                        }
                        
                        lifecycleOwner.lifecycle.addObserver(observer)
                        
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }
                    
                    // Estado para mostrar/ocultar pantallas
                    var showSettings by remember { mutableStateOf(false) }
                    var showAdvancedSettings by remember { mutableStateOf(false) }
                    var showPinSetup by remember { mutableStateOf(false) }
                    var showPinEntry by remember { mutableStateOf(false) }
                    
                    // Observar el estado de bloqueo
                    val isAppLocked by appLockManager.isAppLocked.collectAsState()
                    val isAppLockEnabled by appLockManager.isAppLockEnabled.collectAsState()
                    val autoLockDelay by appLockManager.autoLockDelay.collectAsState()
                    
                    // Verificación adicional de seguridad al renderizar
                    LaunchedEffect(Unit) {
                        if (appLockManager.shouldAppBeLocked() && !isAppLocked) {
                            appLockManager.lockApp()
                        }
                    }
                    
                    // Mostrar pantalla de bloqueo si está habilitado y bloqueado
                    if ((isAppLockEnabled && isAppLocked) || forceLocked) {
                        LockScreen(
                            onUnlockWithBiometric = {
                                if (appLockManager.isBiometricAvailable()) {
                                    appLockManager.showBiometricPrompt(
                                        activity = this@MainActivity,
                                        onSuccess = {
                                            appLockManager.unlockApp()
                                            forceLocked = false // Resetear estado local
                                            Toast.makeText(context, "✅ Aplicación desbloqueada", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                } else {
                                    Toast.makeText(context, "❌ Biometría no disponible", Toast.LENGTH_LONG).show()
                                }
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
                        // Contenido principal de la aplicación
                        Box(modifier = Modifier.fillMaxSize()) {
                            WebViewScreen(
                                context = context,
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
                    
                    // Mostrar pantalla de ajustes si está solicitada
                    if (showSettings) {
                        SettingsScreen(
                            isAppLockEnabled = isAppLockEnabled,
                            onAppLockToggle = { enabled ->
                                appLockManager.setAppLockEnabled(enabled)
                                if (enabled) {
                                    Toast.makeText(context, "🔒 Bloqueo de aplicación activado", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "🔓 Bloqueo de aplicación desactivado", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onAdvancedSettings = { showAdvancedSettings = true },
                            onPinSetup = { showPinSetup = true },
                            onClose = { showSettings = false }
                        )
                    }
                    
                    // Mostrar pantalla de configuraciones avanzadas
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
                    
                    // Mostrar pantalla de configuración de PIN
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
                    
                    // Mostrar pantalla de entrada de PIN
                    if (showPinEntry) {
                        PinEntryScreen(
                            onPinCorrect = { pin ->
                                if (appLockManager.verifyPin(pin)) {
                                    appLockManager.unlockApp()
                                    forceLocked = false // Resetear estado local
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

    fun executeJavaScript(script: String) {
        runOnUiThread {
            webView?.evaluateJavascript(script) { result ->
                println("JavaScript ejecutado. Resultado: $result")
            }
        }
    }
}

// ==== INTERFAZ ANDROID-JS ====
class WebAppInterface(private val context: Context, private val activity: MainActivity) {

    @JavascriptInterface
    fun downloadCSV(csvContent: String, filename: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val finalFilename = if (filename.isBlank()) "bitacora_psiconáutica_$timestamp.csv" else filename

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
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Error al exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
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

            val lines = content.split("\n")
            var substanceCount = 0
            var entryCount = 0
            var currentSection = ""

            for (line in lines) {
                val trimmedLine = line.trim()
                when {
                    trimmedLine == "SUSTANCIAS" -> { currentSection = "substances"; continue }
                    trimmedLine == "REGISTROS"  -> { currentSection = "entries"; continue }
                    trimmedLine == "PERFIL"     -> { currentSection = "profile"; continue }
                    trimmedLine.isEmpty() ||
                            trimmedLine.startsWith("ID,") ||
                            trimmedLine.startsWith("Campo,") -> continue

                    currentSection == "substances" && trimmedLine.isNotEmpty() -> substanceCount++
                    currentSection == "entries"    && trimmedLine.isNotEmpty() -> entryCount++
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

                val lines = csvContent.split("\n")
                var currentSection = ""
                val substancesToImport = mutableListOf<Map<String, String>>()
                val entriesToImport    = mutableListOf<Map<String, String>>()

                for (line in lines) {
                    val trimmedLine = line.trim()
                    when {
                        trimmedLine == "SUSTANCIAS" -> { currentSection = "substances"; continue }
                        trimmedLine == "REGISTROS"  -> { currentSection = "entries"; continue }
                        trimmedLine.isEmpty() || trimmedLine.startsWith("ID,") -> continue

                        currentSection == "substances" -> {
                            val parts = trimmedLine.split(",")
                            if (parts.size >= 4) {
                                substancesToImport += mapOf(
                                    "id"        to (parts[0].toIntOrNull()?.toString()
                                        ?: "${System.currentTimeMillis()}-${kotlin.random.Random.nextInt(1000, 9999)}"),
                                    "name"      to parts[1].replace("\"", ""),
                                    "color"     to parts[2].replace("\"", ""),
                                    "createdAt" to parts[3].replace("\"", "")
                                )
                            }
                        }

                        currentSection == "entries" -> {
                            val parts = trimmedLine.split(",")
                            if (parts.size >= 7) {
                                entriesToImport += mapOf(
                                    "id"        to (parts[0].toIntOrNull()?.toString()
                                        ?: System.currentTimeMillis().toString()),
                                    "substance" to parts[1].replace("\"", ""),
                                    "dose"      to parts[2],
                                    "unit"      to parts[3].replace("\"", ""),
                                    "date"      to parts[4].replace("\"", ""),
                                    "notes"     to (if (parts.size > 5) parts[5].replace("\"", "") else ""),
                                    "createdAt" to (if (parts.size > 6) parts[6].replace("\"", "") else "")
                                )
                            }
                        }
                    }
                }

                val jsScript = buildString {
                    appendLine("try {")
                    appendLine("  console.log('Iniciando importación...');")
                    if (replaceAll) {
                        appendLine("  substances = getDefaultSubstances();")
                        appendLine("  entries = [];")
                        appendLine("  userProfile = {};")
                    }
                    appendLine("  var importedSub = 0; var importedEnt = 0;")

                    substancesToImport.forEach { s ->
                        appendLine("""
                            (function(){
                              var newSub = { id: ${s["id"]}, name: "${s["name"]}", color: "${s["color"]}", createdAt: "${s["createdAt"]}" };
                              var exists = (substances || []).some(function(ex){ return ex.name.toLowerCase() === newSub.name.toLowerCase(); });
                              if (!exists) { substances.push(newSub); importedSub++; }
                            })();
                        """.trimIndent())
                    }

                    entriesToImport.forEach { e ->
                        appendLine("""
                            (function(){
                              entries.push({
                                id: ${e["id"]},
                                substance: "${e["substance"]}",
                                dose: ${e["dose"]},
                                unit: "${e["unit"]}",
                                date: "${e["date"]}",
                                notes: "${e["notes"]}",
                                createdAt: "${e["createdAt"]}"
                              });
                              importedEnt++;
                            })();
                        """.trimIndent())
                    }

                    appendLine("""
                        localStorage.setItem('substances', JSON.stringify(substances));
                        localStorage.setItem('entries', JSON.stringify(entries));
                        renderSubstanceList(); generateCalendar(); renderStats();
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    context: Context,
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
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            return try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se puede abrir: $url", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("""
                            // Función de exportación
                            window.exportToCSV = function() {
                                try {
                                    let csvContent = "";
                                    csvContent += "SUSTANCIAS\n";
                                    csvContent += "ID,Nombre,Color,Fecha_Creacion\n";
                                    if (typeof substances !== 'undefined') {
                                        substances.forEach(substance => {
                                            csvContent += substance.id + ',"' + substance.name + '","' + substance.color + '","' + substance.createdAt + '"\n';
                                        });
                                    }
                                    csvContent += "\nREGISTROS\n";
                                    csvContent += "ID,Sustancia,Dosis,Unidad,Fecha_Hora,Notas,Fecha_Creacion\n";
                                    if (typeof entries !== 'undefined') {
                                        entries.forEach(entry => {
                                            csvContent += entry.id + ',"' + entry.substance + '",' + entry.dose + ',"' + entry.unit + '","' + entry.date + '","' + (entry.notes || '') + '","' + entry.createdAt + '"\n';
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
                        """.trimIndent(), null)
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

                addJavascriptInterface(WebAppInterface(context, context as MainActivity), "Android")

                setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                    try {
                        val request = DownloadManager.Request(Uri.parse(url))
                        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)
                        if (mimetype.contains("csv") && !fileName.endsWith(".csv")) {
                            fileName = fileName.substringBeforeLast(".") + ".csv"
                        }
                        request.setMimeType(mimetype)
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setAllowedOverMetered(true)
                        request.setAllowedOverRoaming(true)
                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)
                        Toast.makeText(context, "Descargando $fileName...", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}
