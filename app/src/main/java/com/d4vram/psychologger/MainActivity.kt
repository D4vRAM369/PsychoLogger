package com.d4vram.psychologger

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Launcher moderno para selección de archivos
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val results = if (result.data?.data != null && result.resultCode == RESULT_OK) {
                arrayOf(result.data!!.data!!)
            } else {
                null
            }
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

        setContent {
            PsychoLoggerTheme {
                WebViewScreen(
                    context = this,
                    onFileChooser = { callback ->
                        filePathCallback = callback
                        openFileChooser()
                    }
                )
            }
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/csv",
                "text/comma-separated-values",
                "application/csv"
            ))
        }

        try {
            val chooser = Intent.createChooser(intent, "Seleccionar archivo CSV")
            fileChooserLauncher.launch(chooser)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            Toast.makeText(this, "Error al abrir selector: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Clase para interfaz JavaScript
class WebAppInterface(private val context: Context) {

    @JavascriptInterface
    fun downloadCSV(csvContent: String, filename: String) {
        try {
            // Crear nombre de archivo con timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
            val finalFilename = if (filename.isBlank()) "bitacora_psiconáutica_$timestamp.csv" else filename

            // Crear archivo en la carpeta de Descargas
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, finalFilename)

            // Escribir contenido CSV
            FileOutputStream(file).use { fos ->
                fos.write(csvContent.toByteArray(Charsets.UTF_8))
            }

            // Notificar al sistema que hay un nuevo archivo
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(file)
            context.sendBroadcast(intent)

            // Mostrar mensaje de éxito
            Toast.makeText(context, "CSV exportado: $finalFilename", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Toast.makeText(context, "Error al exportar CSV: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    context: Context,
    onFileChooser: (ValueCallback<Array<Uri>>) -> Unit
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        factory = { _ ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()

                        // Manejar enlaces externos
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                                return true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No se puede abrir: $url", Toast.LENGTH_SHORT).show()
                                return true
                            }
                        }
                        return false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)

                        // Inyectar JavaScript para reemplazar la función de exportación
                        view?.evaluateJavascript("""
                            // Reemplazar la función exportToCSV existente
                            window.exportToCSV = function() {
                                try {
                                    // Generar contenido CSV
                                    let csvContent = "data:text/csv;charset=utf-8,";

                                    // Sustancias CSV
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

                                    // Usar la interfaz nativa para descargar
                                    const today = new Date().toISOString().split('T')[0];
                                    const filename = 'bitacora_psiconáutica_' + today + '.csv';
                                    
                                    if (typeof Android !== 'undefined') {
                                        Android.downloadCSV(csvContent, filename);
                                    } else {
                                        console.error('Interfaz Android no disponible');
                                    }
                                } catch (error) {
                                    console.error('Error en exportToCSV:', error);
                                    if (typeof Android !== 'undefined') {
                                        Android.showToast('Error al exportar CSV: ' + error.message);
                                    }
                                }
                            };
                            
                            // Manejar enlaces externos
                            document.addEventListener('click', function(e) {
                                if (e.target.tagName === 'A' && e.target.href && 
                                    (e.target.href.startsWith('http://') || e.target.href.startsWith('https://'))) {
                                    e.preventDefault();
                                    window.open(e.target.href, '_blank');
                                }
                            });
                        """, null)
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        Toast.makeText(context, "Error cargando página: ${error?.description}", Toast.LENGTH_SHORT).show()
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (filePathCallback != null) {
                            onFileChooser(filePathCallback)
                            return true
                        }
                        return false
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        consoleMessage?.let {
                            println("WebView Console [${it.messageLevel()}]: ${it.message()} -- Line ${it.lineNumber()} of ${it.sourceId()}")
                        }
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }
                }

                // Configuración de WebView
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

                // Agregar interfaz JavaScript
                addJavascriptInterface(WebAppInterface(context), "Android")

                // Download listener para otros tipos de archivos
                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    try {
                        val request = DownloadManager.Request(Uri.parse(url))
                        var fileName = URLUtil.guessFileName(url, contentDisposition, mimetype)

                        if (mimetype.contains("csv") && !fileName.endsWith(".csv")) {
                            fileName = fileName.split(".")[0] + ".csv"
                        }

                        request.setMimeType(mimetype)
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        request.setAllowedOverMetered(true)
                        request.setAllowedOverRoaming(true)
                        request.addRequestHeader("User-Agent", userAgent)

                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)

                        Toast.makeText(context, "Descargando $fileName...", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error al descargar: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                // Cargar el archivo HTML desde assets
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}