package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.R

@Composable
fun SettingsScreen(
    isAppLockEnabled: Boolean,
    onAppLockToggle: (Boolean) -> Unit,
    isSoftTheme: Boolean,
    onThemeToggle: (Boolean) -> Unit,
    onAdvancedSettings: () -> Unit,
    onPinSetup: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.security_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.security_app_lock),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.security_app_lock_desc),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAppLockEnabled,
                        onCheckedChange = onAppLockToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.security_theme),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSoftTheme) stringResource(R.string.security_theme_soft) else stringResource(R.string.security_theme_original),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isSoftTheme,
                        onCheckedChange = onThemeToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF8B5CF6),
                            checkedTrackColor = Color(0xFF8B5CF6).copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAdvancedSettings,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAppLockEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(stringResource(R.string.security_advanced))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onPinSetup,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isAppLockEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Text(stringResource(R.string.security_setup_pin))
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    }
}
