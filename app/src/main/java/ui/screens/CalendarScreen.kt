package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.d4vram.psychologger.ui.components.WebCard
import com.d4vram.psychologger.ui.components.WebButton

@Composable
fun CalendarScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Tarjeta que envuelve el calendario
        WebCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    MaterialCalendarView(ctx).apply {
                        // Selección simple de un día
                        setSelectionMode(MaterialCalendarView.SELECTION_MODE_SINGLE)
                        // Configuraciones adicionales si es necesario
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Botón para agregar una nueva entrada
        WebButton(
            onClick = { /* Navegar a pantalla de añadir evento */ },
            text = "Añadir entrada"
        )
    }
}
