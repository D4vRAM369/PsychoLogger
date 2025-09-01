package com.d4vram.psychologger

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.ui.screens.PinEntryScreen
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme

/**
 * Pantalla intermedia de verificación.
 * Nueva lógica: fuente única de verdad = AppLockManager.needsAuth()
 * - Si needsAuth() => mostramos LockScreen “ligera” con auto-prompt 1 vez y botones Biometría/PIN.
 * - Si NO => saltamos a MainActivity.
 */
class SecurityCheckActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appLockManager = AppLockManager(this)

        // Si NO necesita autenticación, saltamos directo a Main
        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // Sí necesita autenticación: mostramos pantalla de bloqueo minimal
        setContent {
            PsychoLoggerTheme {
                var showPin by remember { mutableStateOf(false) }

                // Lanzar biometría 1 sola vez al entrar (si está disponible)
                LaunchedEffect(Unit) {
                    if (appLockManager.isBiometricAvailable()) {
                        appLockManager.showBiometricPrompt(
                            activity = this@SecurityCheckActivity,
                            onSuccess = {
                                // AppLockManager ya hace unlockApp()
                                startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                finish()
                            },
                            onError = {
                                // No reintentamos solos: dejamos botones abajo
                            }
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🔒", fontSize = 72.sp)

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Verificación de seguridad",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Autentícate para continuar.",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(32.dp))

                        // Botón biometría
                        Button(
                            onClick = {
                                if (appLockManager.isBiometricAvailable()) {
                                    appLockManager.showBiometricPrompt(
                                        activity = this@SecurityCheckActivity,
                                        onSuccess = {
                                            // AppLockManager ya hace unlockApp()
                                            startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                            finish()
                                        },
                                        onError = { /* mostrar un toast si quieres */ }
                                    )
                                } else {
                                    // Si no hay biometría, abrimos PIN directamente
                                    if (appLockManager.hasPinSet()) showPin = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text("🔐 Desbloquear con biometría", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Botón PIN
                        OutlinedButton(
                            onClick = { if (appLockManager.hasPinSet()) showPin = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("🔢 Usar PIN", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Diálogo / capa de PIN
                    if (showPin) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
                        ) {
                            PinEntryScreen(
                                onPinCorrect = { pin ->
                                    if (appLockManager.verifyPin(pin)) {
                                        appLockManager.unlockApp()
                                        startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                        finish()
                                    } else {
                                        // Puedes mostrar un snackbar/toast desde aquí
                                    }
                                },
                                onBackToBiometric = { showPin = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Si el sistema nos relanza y ya NO necesita auth, vamos a Main.
        val appLockManager = AppLockManager(this)
        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-chequeo defensivo: si ya se autenticó mientras estábamos en background, saltamos.
        val appLockManager = AppLockManager(this)
        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
