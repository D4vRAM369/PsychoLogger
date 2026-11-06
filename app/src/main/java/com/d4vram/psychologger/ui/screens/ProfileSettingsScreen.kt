package com.d4vram.psychologger.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.ui.res.stringResource
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
import com.d4vram.psychologger.R
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
    onExportData: suspend () -> String,
    onImportData: (String) -> Unit,
    onClearData: () -> Unit,
    accessHistory: List<AccessHistoryManager.AccessEvent> = emptyList(),
    onClearAccessHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importData by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    var showBackupDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var showBackupPassword by remember { mutableStateOf(false) }
    var includeMediaInBackup by remember { mutableStateOf(true) }
    var encryptBackup by remember { mutableStateOf(false) }
    var showAccessHistoryDialog by remember { mutableStateOf(false) }

    val backupPreferences = remember { BackupPreferences(context) }
    var autobackupEnabled by remember { mutableStateOf(backupPreferences.isAutobackupEnabled()) }
    var autobackupFolderName by remember { mutableStateOf(backupPreferences.getAutobackupFolderName()) }
    var pendingBackupFile by remember { mutableStateOf<File?>(null) }

    val coroutineScope = rememberCoroutineScope()

    val backupSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { destinationUri ->
            val backupFile = pendingBackupFile
            if (backupFile != null) {
                coroutineScope.launch {
                    Toast.makeText(context, context.getString(R.string.toast_saving_backup), Toast.LENGTH_SHORT).show()

                    val success = withContext(Dispatchers.IO) {
                        val backupManager = BackupManager(context)
                        val result = backupManager.writeBackupToUri(backupFile, destinationUri)
                        backupManager.deleteTempBackup(backupFile)
                        result
                    }

                    pendingBackupFile = null

                    if (success) {
                        Toast.makeText(context, context.getString(R.string.toast_backup_saved), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_backup_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } ?: run {
            pendingBackupFile?.let { file ->
                coroutineScope.launch(Dispatchers.IO) {
                    BackupManager(context).deleteTempBackup(file)
                }
                pendingBackupFile = null
            }
            Toast.makeText(context, context.getString(R.string.toast_backup_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    val autobackupFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { folderUri ->
            val persisted = backupPreferences.persistUriPermissions(folderUri)

            if (persisted) {
                val folderName = DocumentFile.fromTreeUri(context, folderUri)?.name ?: "Folder"

                backupPreferences.saveAutobackupFolderUri(folderUri, folderName)
                backupPreferences.setAutobackupEnabled(true)

                autobackupFolderName = folderName
                autobackupEnabled = true

                Toast.makeText(context, context.getString(R.string.toast_autobackup_configured, folderName), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_permissions_error), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(context, context.getString(R.string.toast_no_folder_selected), Toast.LENGTH_SHORT).show()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            var fileName: String? = null
            context.contentResolver.query(selectedUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }

            if (fileName == null || !fileName!!.lowercase().endsWith(".csv")) {
                Toast.makeText(context, context.getString(R.string.toast_csv_only), Toast.LENGTH_SHORT).show()
                return@let
            }

            Toast.makeText(context, context.getString(R.string.toast_processing_csv), Toast.LENGTH_SHORT).show()

            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val content = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                if (content.isNotEmpty()) {
                    importData = content
                    showImportDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.toast_file_empty), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_file_read_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Text(
                    text = stringResource(R.string.profile_title),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

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

            Text(
                text = stringResource(R.string.profile_subtitle),
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
                        text = stringResource(R.string.profile_personal_info),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.profile_nickname),
                        fontSize = 14.sp,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.profile_nickname_placeholder)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF8B5CF6),
                            unfocusedBorderColor = Color(0xFF8B5CF6).copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { /* Guardar nickname */ },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.profile_save),
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
                        text = stringResource(R.string.config_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.config_dark_theme),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.config_dark_theme_desc),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.config_notifications),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.config_notifications_desc),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.config_fingerprint),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.config_fingerprint_desc),
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

            // SECCIÓN 3: Gestión de Datos
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
                        text = stringResource(R.string.data_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val mainActivity = context as? MainActivity
                            mainActivity?.executeJavaScript("exportToCSV()")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF06B6D4)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.data_export_csv),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showBackupDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.data_backup_full),
                            color = Color(0xFF06B6D4),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.data_autobackup),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF8B5CF6)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                                stringResource(R.string.data_autobackup_folder),
                                color = if (autobackupFolderName != null) Color(0xFF10B981) else Color(0xFF8B5CF6),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (autobackupFolderName != null) "✅ $autobackupFolderName" else stringResource(R.string.data_autobackup_not_configured),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.data_autobackup_12h),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = if (autobackupEnabled) stringResource(R.string.data_autobackup_active) else stringResource(R.string.data_autobackup_inactive),
                                fontSize = 12.sp,
                                color = if (autobackupEnabled) Color(0xFF10B981) else Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = autobackupEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && autobackupFolderName == null) {
                                    Toast.makeText(context, context.getString(R.string.toast_configure_folder_first), Toast.LENGTH_SHORT).show()
                                    autobackupFolderLauncher.launch(null)
                                } else {
                                    backupPreferences.setAutobackupEnabled(enabled)
                                    autobackupEnabled = enabled
                                    Toast.makeText(
                                        context,
                                        if (enabled) context.getString(R.string.toast_autobackup_enabled) else context.getString(R.string.toast_autobackup_disabled),
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

                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.1f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

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
                            stringResource(R.string.data_import_csv),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFF59E42)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.data_clear_all),
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
                        text = stringResource(R.string.privacy_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.toast_under_construction), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.privacy_settings),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEC4899)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showAccessHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.privacy_access_history),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B5CF6)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.back),
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
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = { Text(stringResource(R.string.dialog_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showClearDataDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_clear_confirm), color = Color(0xFFEC4899))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Diálogo de confirmación para importar datos
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.dialog_import_title)) },
            text = { Text(stringResource(R.string.dialog_import_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportData(importData)
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text(stringResource(R.string.dialog_import_add), color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            modifier = Modifier.padding(16.dp)
        )
    }

    // Diálogo de Exportación Avanzada (Backup)
    if (showBackupDialog) {
        val showDesc = stringResource(R.string.password_show)
        val hideDesc = stringResource(R.string.password_hide)
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Text(
                    stringResource(R.string.dialog_backup_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF06B6D4)
                )
            },
            text = {
                // verticalScroll: con el teclado abierto el campo y el botón siguen alcanzables
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.dialog_backup_description),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dialog_backup_include_media), color = Color.White, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.dialog_backup_include_media_desc), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = includeMediaInBackup,
                            onCheckedChange = { includeMediaInBackup = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.dialog_backup_encrypt), color = Color(0xFFEC4899), fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.dialog_backup_encrypt_desc), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
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
                            placeholder = { Text(stringResource(R.string.dialog_backup_password_placeholder)) },
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.password_min_hint)) },
                            isError = backupPassword.isNotEmpty() && backupPassword.length < 8,
                            visualTransformation = if (showBackupPassword)
                                androidx.compose.ui.text.input.VisualTransformation.None
                            else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showBackupPassword = !showBackupPassword }) {
                                    Text(
                                        if (showBackupPassword) "🙈" else "👁",
                                        modifier = Modifier.semantics {
                                            contentDescription = if (showBackupPassword) hideDesc else showDesc
                                        }
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Text(
                            stringResource(R.string.dialog_backup_password_warning),
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
                            Toast.makeText(context, context.getString(R.string.dialog_backup_password_required), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (encryptBackup && backupPassword.length < 8) {
                            Toast.makeText(context, context.getString(R.string.password_too_short), Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.toast_preparing_backup), Toast.LENGTH_SHORT).show()

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

                                Toast.makeText(context, context.getString(R.string.toast_backup_created), Toast.LENGTH_SHORT).show()

                                backupSafLauncher.launch(tempFile.name)
                            } else {
                                Toast.makeText(context, context.getString(R.string.toast_backup_create_error), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4))
                ) {
                    Text(stringResource(R.string.dialog_backup_create), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.6f))
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
            title = { Text(stringResource(R.string.dialog_access_history_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    if (accessHistory.isEmpty()) {
                        Text(
                            stringResource(R.string.dialog_access_history_empty),
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        Text(
                            stringResource(R.string.dialog_access_history_count, accessHistory.size),
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
                    Text(stringResource(R.string.close), color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                if (accessHistory.isNotEmpty()) {
                    TextButton(onClick = {
                        onClearAccessHistory()
                        showAccessHistoryDialog = false
                    }) {
                        Text(stringResource(R.string.clear), color = Color(0xFFEC4899))
                    }
                }
            },
            containerColor = Color(0xFF1A1A2E),
            textContentColor = Color.White
        )
    }
}

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
        onExportData = { "" },
        onImportData = {},
        onClearData = {}
    )
}

fun Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
