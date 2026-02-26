package com.d4vram.psychologger.ui.screens

import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.d4vram.psychologger.R

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
    onExportData: () -> String, // Función para obtener datos en formato CSV
    onImportData: (String) -> Unit, // Función para importar datos CSV
    onClearData: () -> Unit // Función para limpiar datos
) {
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importData by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    // Launcher para seleccionar archivo CSV
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                val inputStream = context.contentResolver.openInputStream(selectedUri)
                val content = inputStream?.bufferedReader().use { it?.readText() } ?: ""
                if (content.isNotEmpty()) {
                    importData = content
                    showImportDialog = true
                }
            } catch (e: Exception) {
                // Manejar error
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
                    text = stringResource(R.string.my_psychonaut_profile_title),
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
                        text = stringResource(R.string.personal_information_section),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Campo de nickname
                    Text(
                        text = stringResource(R.string.nickname_label),
                        fontSize = 14.sp,
                        color = Color(0xFF8B5CF6),
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = onNicknameChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.nickname_placeholder)) },
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
                            stringResource(R.string.save_profile_button),
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
                        text = stringResource(R.string.app_settings_section),
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
                                text = stringResource(R.string.dark_theme_label),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.dark_theme_description),
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
                                text = stringResource(R.string.notifications_label),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.notifications_description),
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
                                text = stringResource(R.string.biometric_label),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.biometric_description),
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
                        text = stringResource(R.string.data_management_section),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Exportar datos
                    Button(
                        onClick = {
                            Log.d("PsychoExport", "🔴 BOTÓN SIMPLE CLICKEADO!")
                            Toast.makeText(context, context.getString(R.string.profile_export_click_detected_toast), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = true,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExporting) Color(0xFF06B6D4).copy(alpha = 0.6f) else Color(0xFF06B6D4),
                            disabledContainerColor = Color(0xFF06B6D4).copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isExporting) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.exporting_status),
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.export_data_button),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Importar datos
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("text/csv") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.import_data_button),
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
                            stringResource(R.string.clear_data_button),
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
                        text = stringResource(R.string.privacy_security_section),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF06B6D4)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Configuración de privacidad
                    OutlinedButton(
                        onClick = { /* Abrir configuración de privacidad */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.privacy_settings_button),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEC4899)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Historial de accesos
                    OutlinedButton(
                        onClick = { /* Mostrar historial */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.access_history_button),
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
                    stringResource(R.string.back_button),
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
            title = { Text(stringResource(R.string.confirm_clear_title)) },
            text = { Text(stringResource(R.string.confirm_clear_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearData()
                        showClearDataDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm_clear_button), color = Color(0xFFEC4899))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            }
        )
    }

    // Diálogo de confirmación para importar datos
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_data_dialog_title)) },
            text = { Text(stringResource(R.string.import_data_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImportData(importData)
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text(stringResource(R.string.add_option), color = Color(0xFF06B6D4))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportDialog = false
                        importData = ""
                    }
                ) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
            modifier = Modifier.padding(16.dp)
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
