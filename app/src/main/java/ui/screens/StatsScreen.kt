package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.viewinterop.AndroidView
import com.d4vram.psychologger.ui.components.WebCard
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.*

@Composable
fun StatsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WebCard(Modifier.fillMaxWidth()) {
            Text("Frecuencia de Uso", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            AndroidView(factory = { ctx ->
                BarChart(ctx).apply {
                    data = BarData(
                        BarDataSet(
                            listOf(
                                BarEntry(1f, 3f),
                                BarEntry(2f, 5f),
                                BarEntry(3f, 2f)
                            ), "Consumo"
                        )
                    )
                    description.isEnabled = false
                    animateY(500)
                }
            },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp))
        }
        // Añade más WebCards para patrones, tolerancia…
    }
}

