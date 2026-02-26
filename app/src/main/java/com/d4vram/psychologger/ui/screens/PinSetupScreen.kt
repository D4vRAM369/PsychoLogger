package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.R

@Composable
fun PinSetupScreen(
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1: primer PIN, 2: confirmar PIN
    var errorMessage by remember { mutableStateOf("") }

    val pinTooShortError = stringResource(R.string.pin_too_short_error)
    val pinsNotMatchingError = stringResource(R.string.pins_not_matching_error)

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
            // Icono
            Text(
                text = if (step == 1) "🔢" else "✅",
                fontSize = 80.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Título
            Text(
                text = if (step == 1) stringResource(R.string.pin_setup_title) else stringResource(R.string.confirm_pin_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Descripción
            Text(
                text = if (step == 1)
                    stringResource(R.string.pin_setup_description)
                else
                    stringResource(R.string.pin_confirm_description),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Campo de entrada PIN
            OutlinedTextField(
                value = if (step == 1) pin else confirmPin,
                onValueChange = {
                    if (step == 1) {
                        if (it.length <= 6) pin = it
                    } else {
                        if (it.length <= 6) confirmPin = it
                    }
                    errorMessage = ""
                },
                label = { Text(if (step == 1) stringResource(R.string.pin_input_label) else stringResource(R.string.confirm_pin_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Mensaje de error
            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(stringResource(R.string.cancel_button))
                }

                Button(
                    onClick = {
                        if (step == 1) {
                            if (pin.length < 4) {
                                errorMessage = pinTooShortError
                            } else {
                                step = 2
                                errorMessage = ""
                            }
                        } else {
                            if (pin == confirmPin) {
                                onPinSet(pin)
                            } else {
                                errorMessage = pinsNotMatchingError
                                confirmPin = ""
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = if (step == 1) pin.length >= 4 else confirmPin.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (step == 1) stringResource(R.string.next_button) else stringResource(R.string.confirm_button))
                }
            }

            // Información adicional
            Spacer(modifier = Modifier.height(32.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.pin_security_info_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pin_security_message),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
