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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.ui.screens.PinEntryScreen
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme

class SecurityCheckActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appLockManager = AppLockManager(this)

        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            PsychoLoggerTheme {
                var showPin by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(300)
                    if (appLockManager.isBiometricAvailable() && appLockManager.canShowPromptNow()) {
                        appLockManager.showBiometricPrompt(
                            activity = this@SecurityCheckActivity,
                            onSuccess = {
                                startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                finish()
                            },
                            onError = {
                            },
                            onUsePin = { if (appLockManager.hasPinSet()) showPin = true }
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
                            text = stringResource(R.string.security_check_title),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.security_check_description),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = {
                                if (appLockManager.isBiometricAvailable() && appLockManager.canShowPromptNow()) {
                                    appLockManager.showBiometricPrompt(
                                        activity = this@SecurityCheckActivity,
                                        onSuccess = {
                                            startActivity(Intent(this@SecurityCheckActivity, MainActivity::class.java))
                                            finish()
                                        },
                                        onError = { },
                                        onUsePin = { if (appLockManager.hasPinSet()) showPin = true }
                                    )
                                } else {
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
                            Text(stringResource(R.string.security_check_biometric), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { if (appLockManager.hasPinSet()) showPin = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(stringResource(R.string.security_check_pin), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }

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
        val appLockManager = AppLockManager(this)
        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val appLockManager = AppLockManager(this)
        appLockManager.resetBiometricState()
        if (!appLockManager.needsAuth()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
