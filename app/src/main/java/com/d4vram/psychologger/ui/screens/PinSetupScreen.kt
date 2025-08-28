package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinSetupScreen(
    onPinSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(1) } // 1: primer PIN, 2: confirmar PIN
    var errorMessage by remember { mutableStateOf("") }
    
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
                text = if (step == 1) "Configurar PIN de Seguridad" else "Confirmar PIN",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Descripción
            Text(
                text = if (step == 1) 
                    "Crea un PIN de 4-6 dígitos para acceder a la aplicación" 
                else 
                    "Ingresa nuevamente el PIN para confirmar",
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
                label = { Text(if (step == 1) "PIN (4-6 dígitos)" else "Confirmar PIN") },
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
                    Text("Cancelar")
                }
                
                Button(
                    onClick = {
                        if (step == 1) {
                            if (pin.length < 4) {
                                errorMessage = "El PIN debe tener al menos 4 dígitos"
                            } else {
                                step = 2
                                errorMessage = ""
                            }
                        } else {
                            if (pin == confirmPin) {
                                onPinSet(pin)
                            } else {
                                errorMessage = "Los PINs no coinciden"
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
                    Text(if (step == 1) "Siguiente" else "Confirmar")
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
                        text = "🔐 Información de Seguridad",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Este PIN se almacenará de forma encriptada en tu dispositivo. Guárdalo en un lugar seguro.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
