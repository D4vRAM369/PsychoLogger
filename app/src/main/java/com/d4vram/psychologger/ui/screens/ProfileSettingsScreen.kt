package com.d4vram.psychologger.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.d4vram.psychologger.MainActivity
import com.d4vram.psychologger.BackupManager
import com.d4vram.psychologger.BackupPreferences
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.d4vram.psychologger.AccessHistoryManager
import android.provider.OpenableColumns

@Composable
fun ProfileSettingsScreen(
    nickname: String,
    onNicknameChange: (String) -> Unit,
    isDarkTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    isNotificationsEnabled: Boolean,
    onNotificationsToggle: (Boolean) -> Unit,
    isBiometricEnabled: Boolean,
    onBiometricToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    onExportData: suspend () -> String, // Función para obtener datos en formato CSV
    onImportData: (String) -> Unit, // Función para importar datos CSV
    onClearData: () -> Unit, // Función para limpiar datos
    accessHistory: List<AccessHistoryManager.AccessEvent> = emptyList(),
    onClearAccessHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importData by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    // Estado del diálogo de backup avanzado
    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var includeMediaInBackup by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(false) }
    var showAccessHistoryDialog by remember { mutableStateOf(false) }

    // Estados SAF para backups
    val backupPreferences = remember { BackupPreferences(context) }
    var autobackupEnabled by remember { mutableStateOf(backupPreferences.isAutobackupEnabled()) }
    var autobackupFolderName by remember { mutableStateOf(backupPreferences.getAutobackupFolderName()) }
    var pendingBackupFile by remember { mutableStateOf<File?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Launcher SAF para backup manual (elegir dónde guardar el archivo)
    val backupSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { destinationUri ->
            val backupFile = pendingBackupFile
            if (backupFile != null) {
                coroutineScope.launch {
                    Toast.makeText(context, "💾 Guardando backup...", Toast.LENGTH_SHORT).show()

                    val success = withContext(Dispatchers.IO) {
                        val backupManager = BackupManager(context)
                        val result = backupManager.writeBackupToUri(backupFile, destinationUri)
                        backupManager.deleteTempBackup(backupFile)
                        result
                    }

                    pendingBackupFile = null

                    if (success) {
                        Toast.makeText(context, "✅ Backup guardado correctamente", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "❌ Error al guardar backup", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } ?: run {
            // Usuario canceló el picker
            pendingBackupFile?.let { file ->
                coroutineScope.launch(Dispatchers.IO) {
                    BackupManager(context).deleteTempBackup(file)
                }
                pendingBackupFile = null
            }
            Toast.makeText(context, "❌ Backup cancelado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher SAF para configurar carpeta de autobackups
    val autobackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { folderUri ->
            // Persistir permisos SAF
            val persisted = backupPreferences.persistUriPermissions(folderUri)

            if (persisted) {
                // Obtener nombre legible de la carpeta
                val folderName = DocumentFile.fromTreeUri(context, folderUri)?.name ?: "Carpeta seleccionada"

                // Guardar configuración
                backupPreferences.saveAutobackupFolderUri(folderUri, folderName)
                backupPreferences.setAutobackupEnabled(true)

                // Actualizar UI
                autobackupFolderName = folderName
                autobackupEnabled = true

                Toast.makeText(context, "✅ Carpeta de autobackup configurada: $folderName", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "❌ No se pudieron obtener permisos", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(context, "❌ No se seleccionó carpeta", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Launcher para seleccionar archivo CSV (OpenDocument es más flexible con MIME types)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            // Obtener el nombre REAL del archivo desde el ContentResolver
            var fileName: String? = null
            context.contentResolver.query(selectedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            // Validar extensión .csv
            if (fileName == null || !fileName!!.lowercase().endsWith(".csv")) {
                Toast.makeText(context, "❌ Solo se permiten archivos .csv", Toast.LENGTH_SHORT).show()
                return@let
            }

            Toast.makeText(context, "🔄 Procesando CSV...", Toast.LENGTH_SHORT).show()

            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val content = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                if (content.isNotEmpty()) {
                    importData = content
                    showImportDialog = true
                } else {
                    Toast.makeText(context, "❌ El archivo está vacío", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "❌ Error al leer archivo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header con icono y título
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icono del ojo (como en otros paneles)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color(0xFF06B6D4),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👁️",
                        fontSize = 24.sp
                    )
                }
                
                // Título principal
                Text(
                    text = "Mi Perfil Psiconáutico",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                
                // Botón de configuración
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color(0xFFF59E42),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙️",
                        fontSize = 18.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtítulo
            Text(
                text = "Tu compañera vital para un consumo consciente y responsable de sustancias psiconáuticas",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // SECCIÓN 1: Información Personal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Información Personal",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Campo de nickname
                    Text(
                        text = "Nickname",
                        fontSize = 14.sp,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Tu nickname") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF8B5CF6).copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Botón guardar perfil
                    Button(
                        onClick = { /* Guardar nickname */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Guardar Perfil",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // SECCIÓN 2: Configuración de la App
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Configuración de la App",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Tema oscuro
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Tema Oscuro",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Interfaz en modo oscuro",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isDarkTheme,
                            onCheckedChange = onThemeToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8B5CF6),
                                checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Notificaciones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Notificaciones",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Recordatorios y alertas",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isNotificationsEnabled,
                            onCheckedChange = onNotificationsToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8B5CF6),
                                checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Autenticación biométrica
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Huella Digital",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "Acceso con biometría",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isBiometricEnabled,
                            onCheckedChange = onBiometricToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF8B5CF6),
                                checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // SECCIÓN 3: Gestión de Datos (COMBINADA)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Gestión de Datos",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Exportar datos CSV Simple
                    Button(
                        onClick = { 
                            coroutineScope.launch {
                                val data = onExportData()
                                val mainActivity = context as? MainActivity
                                mainActivity?.webAppInterface?.shareCSV(data, "bitacora_${System.currentTimeMillis()}.csv")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF06B6D4)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "📊 Exportar Historial (CSV)",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Backup Total Encriptado
                    OutlinedButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "📦 Backup Total (Media + Cifrado)",
                            color = Color(0xFF06B6D4),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Separador visual
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sección Autobackup
                    Text(
                        text = "Backup Automático",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B5CF6)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Configurar carpeta de autobackup
                    OutlinedButton(
                        onClick = { autobackupFolderLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (autobackupFolderName != null) Color(0xFF10B981) else Color(0xFF8B5CF6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "📁 Carpeta de Autobackup",
                                color = if (autobackupFolderName != null) Color(0xFF10B981) else Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (autobackupFolderName != null) "✅ $autobackupFolderName" else "❌ No configurada",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle autobackup cada 12h
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Autobackup cada 12h",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = if (autobackupEnabled) "Activo" else "Desactivado",
                                fontSize = 12.sp,
                                color = if (autobackupEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = autobackupEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && autobackupFolderName == null) {
                                    Toast.makeText(context, "⚠️ Primero configura una carpeta", Toast.LENGTH_SHORT).show()
                                    autobackupFolderLauncher.launch(null)
                                } else {
                                    backupPreferences.setAutobackupEnabled(enabled)
                                    autobackupEnabled = enabled
                                    Toast.makeText(
                                        context,
                                        if (enabled) "✅ Autobackup activado" else "❌ Autobackup desactivado",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF10B981),
                                checkedTrackColor = Color(0xFF10B981).copy(alpha = 0.3f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Separador visual
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Importar datos CSV (múltiples MIME types para compatibilidad)
                    OutlinedButton(
                        onClick = {
                            filePickerLauncher.launch(arrayOf(
                                "text/csv",
                                "text/comma-separated-values",
                                "text/plain",
                                "application/csv",
                                "*/*"
                            ))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "📁 Seleccionar CSV para Importar",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E42)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Limpiar datos
                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "🗑️ Limpiar Todos los Datos",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEC4899)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // SECCIÓN 4: Privacidad y Seguridad
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "Privacidad y Seguridad",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Configuración de privacidad
                    OutlinedButton(
                        onClick = { 
                            Toast.makeText(context, "🚧 En construcción... 🏗️", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "🔒 Configuración de Privacidad",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEC4899)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Historial de accesos
                    OutlinedButton(
                        onClick = { showAccessHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "📊 Historial de Accesos",
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Botón de volver
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Volver",
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Diálogo de confirmación para limpiar datos
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("⚠️ Confirmar Limpieza") },
            text = { Text("¿Estás seguro de que quieres eliminar TODOS los datos almacenados? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showClearDataDialog = false
                    }
                ) {
                    Text("SÍ, LIMPIAR TODO", color = Color(0xFFEC4899))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("CANCELAR")
                }
            }
        )
    }
    
    // Diálogo de confirmación para importar datos
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("📂 Importar Datos CSV") },
            text = { Text("¿Qué deseas hacer con los datos actuales?\n\n• AÑADIR: Agregar datos nuevos\n• REEMPLAZAR: Borrar todo y usar datos del CSV") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportData(importData)
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text("AÑADIR", color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text("CANCELAR")
                }
            },
            modifier = Modifier.padding(16.dp)
        )
    }

    // Diálogo de Exportación Avanzada (Backup)
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Text(
                    "📦 Exportación Avanzada",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF06B6D4)
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Se exportará una base de datos completa con tus notas en formato Markdown, experiencias, fotos, audios, y configuraciones personalizadas (Sets, Settings, etc.). Esto garantiza que si desinstalas la app, recuperarás todo al importar este backup.",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Multimedia
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Incluir Archivos Multimedia", color = Color.White, fontWeight = FontWeight.Medium)
                            Text("Fotos y Notas de Voz", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = includeMediaInBackup,
                            onCheckedChange = { includeMediaInBackup = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cifrado
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cifrar Backup (AES-256)", color = Color(0xFFEC4899), fontWeight = FontWeight.Medium)
                            Text("Recomendado para máxima privacidad", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = encryptBackup,
                            onCheckedChange = { encryptBackup = it }
                        )
                    }

                    if (encryptBackup) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = backupPassword,
                            onValueChange = { backupPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Contraseña de cifrado") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Text(
                            "⚠️ Si olvidas esta clave, no podrás restaurar el backup.",
                            fontSize = 12.sp,
                            color = Color(0xFFF59E42),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (encryptBackup && backupPassword.isBlank()) {
                            Toast.makeText(context, "Introduce una contraseña", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Usar SAF para guardar el backup
                        coroutineScope.launch {
                            Toast.makeText(context, "🔄 Preparando backup...", Toast.LENGTH_SHORT).show()

                            val backupManager = BackupManager(context)
                            val localStorageData = onExportData()

                            val tempFile = withContext(Dispatchers.IO) {
                                backupManager.createBackupInCache(
                                    localStorageData = localStorageData,
                                    password = if (encryptBackup) backupPassword else null,
                                    includeMedia = includeMediaInBackup
                                )
                            }

                            if (tempFile != null) {
                                showBackupDialog = false
                                pendingBackupFile = tempFile

                                Toast.makeText(context, "✅ Backup creado. Selecciona dónde guardarlo...", Toast.LENGTH_SHORT).show()

                                // Lanzar SAF picker con nombre sugerido
                                backupSafLauncher.launch(tempFile.name)
                            } else {
                                Toast.makeText(context, "❌ Error al crear backup", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                ) {
                    Text("CREAR BACKUP", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("CANCELAR", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = Color.White
        )
    }

    // Diálogo de historial de accesos
    if (showAccessHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showAccessHistoryDialog = false },
            title = { Text("📊 Historial de Accesos") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (accessHistory.isEmpty()) {
                        Text(
                            "No hay accesos registrados aún.",
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Text(
                            "Últimos ${accessHistory.size} accesos:",
                            color = Color(0xFF8B5CF6),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        androidx.compose.foundation.lazy.LazyColumn {
                            items(accessHistory.size) { index ->
                                val event = accessHistory[index]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${event.formattedDate} ${event.formattedTime}",
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        event.methodLabel,
                                        color = Color(0xFF06B6D4),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAccessHistoryDialog = false }) {
                    Text("CERRAR", color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                if (accessHistory.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearAccessHistory()
                        showAccessHistoryDialog = false
                    }) {
                        Text("LIMPIAR", color = Color(0xFFEC4899))
                    }
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = Color.White
        )
    }
}

// Preview para desarrollo
@Composable
fun ProfileSettingsScreenPreview() {
    ProfileSettingsScreen(
        nickname = "Zer0",
        onNicknameChange = {},
        isDarkTheme = true,
        onThemeToggle = {},
        isNotificationsEnabled = true,
        onNotificationsToggle = {},
        isBiometricEnabled = false,
        onBiometricToggle = {},
        onBack = {},
        onExportData = { "SUSTANCIAS\nID,Nombre,Color,Fecha_Creacion\n1,LSD,#FF0000,2024-01-01\n\nREGISTROS\nID,Sustancia,Dosis,Unidad,Fecha_Hora,Set,Setting,Notas,Fecha_Creacion,Fecha_Actualizacion" },
        onImportData = {},
        onClearData = {}
    )
}

// Extension para encontrar la Activity desde el Contexto (Compose suele wrappearlo)
fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
