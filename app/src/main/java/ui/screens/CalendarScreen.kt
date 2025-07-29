package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prolificinteractive.materialcalendarview.MaterialCalendarView

@Composable
fun CalendarScreen() {
    AndroidView(
        factory = { ctx ->
            MaterialCalendarView(ctx).apply {
                // Selección simple de un día
                setSelectionMode(MaterialCalendarView.SELECTION_MODE_SINGLE)
                // Aquí podrías configurar colores, listeners, etc.
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
