package com.d4vram.psychologger

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme

class SecurityCheckActivity : FragmentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inicializar AppLockManager
        val appLockManager = AppLockManager(this)
        
        // VERIFICACIÓN ABSOLUTA DE SEGURIDAD ANTES de cualquier renderizado
        // En Android 16, necesitamos ser más agresivos con la verificación
        val isSecurityBypass = appLockManager.isSecurityBypassAttempt()
        val shouldForceLock = appLockManager.shouldForceLockOnStartup()
        val isFirstLaunchAfterForceStop = appLockManager.isFirstLaunchAfterForceStop()
        
        // BLOQUEO TOTAL si hay CUALQUIER indicio de bypass
        if (isSecurityBypass || shouldForceLock || isFirstLaunchAfterForceStop) {
            // BLOQUEO TOTAL: Mostrar SOLO pantalla de bloqueo
            setContent {
                PsychoLoggerTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Icono de candado
                            Text(
                                text = "🔒",
                                fontSize = 80.sp
                            )
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Título
                            Text(
                                text = "Verificación de Seguridad Requerida",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Descripción
                            Text(
                                text = "La aplicación ha detectado un posible intento de bypass de seguridad. Debes autenticarte para continuar.",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            // Botón para desbloquear con huella
                            Button(
                                onClick = {
                                    if (appLockManager.isBiometricAvailable()) {
                                        appLockManager.showBiometricPrompt(
                                            activity = this@SecurityCheckActivity,
                                            onSuccess = {
                                                appLockManager.unlockApp()
                                                // Ir a MainActivity
                                                startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                                finish()
                                            },
                                            onError = { error ->
                                                // Mostrar error
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "🔐 Desbloquear con Huella",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Botón para usar PIN
                            OutlinedButton(
                                onClick = {
                                    if (appLockManager.hasPinSet()) {
                                        // Mostrar pantalla de PIN
                                        setContent {
                                            PsychoLoggerTheme {
                                                com.d4vram.psychologger.ui.screens.PinEntryScreen(
                                                    onPinCorrect = { pin ->
                                                        if (appLockManager.verifyPin(pin)) {
                                                            appLockManager.unlockApp()
                                                            // Ir a MainActivity
                                                            startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                                            finish()
                                                        }
                                                    },
                                                    onBackToBiometric = {
                                                        // Volver a la pantalla de bloqueo
                                                        recreate()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    text = "🔢 Usar Código PIN",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Seguridad OK: Ir directamente a MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
    
    override fun onStart() {
        super.onStart()
        
        // Verificación en onStart para capturar casos especiales de Android 16
        val appLockManager = AppLockManager(this)
        
        if (appLockManager.isFirstLaunchAfterForceStop()) {
            // BLOQUEO INMEDIATO: Forzar pantalla de bloqueo
            appLockManager.lockApp()
            
            // Mostrar pantalla de bloqueo INMEDIATAMENTE
            setContent {
                PsychoLoggerTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔒 BLOQUEO DE SEGURIDAD ACTIVADO",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Se ha detectado un posible intento de bypass de seguridad. La aplicación está bloqueada por seguridad.",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            Button(
                                onClick = {
                                    if (appLockManager.isBiometricAvailable()) {
                                        appLockManager.showBiometricPrompt(
                                            activity = this@SecurityCheckActivity,
                                            onSuccess = {
                                                appLockManager.unlockApp()
                                                startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                                finish()
                                            },
                                            onError = { error ->
                                                // Mostrar error
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(
                                    text = "🔐 AUTENTICACIÓN REQUERIDA",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        // Verificación adicional en onResume para Android 16
        // Esto captura casos donde la app se "resucita" sin onCreate
        val appLockManager = AppLockManager(this)
        
        if (appLockManager.shouldForceLockOnStartup()) {
            // Forzar bloqueo inmediato
            appLockManager.lockApp()
            
            // Mostrar pantalla de bloqueo
            setContent {
                PsychoLoggerTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "🔒 Verificación de Seguridad Requerida",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "La aplicación ha detectado un posible intento de bypass de seguridad. Debes autenticarte para continuar.",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(48.dp))
                            
                            Button(
                                onClick = {
                                    if (appLockManager.isBiometricAvailable()) {
                                        appLockManager.showBiometricPrompt(
                                            activity = this@SecurityCheckActivity,
                                            onSuccess = {
                                                appLockManager.unlockApp()
                                                startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                                finish()
                                            },
                                            onError = { error ->
                                                // Mostrar error
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(
                                    text = "🔐 Desbloquear con Huella",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
