package com.d4vram.psychologger.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

@Composable
fun StatsScreen() {
    AndroidView(
        factory = { ctx ->
            BarChart(ctx).apply {
                data = BarData(
                    BarDataSet(
                        listOf(
                            BarEntry(1f, 3f),
                            BarEntry(2f, 5f),
                            BarEntry(3f, 2f)
                        ),
                        "Consumo"
                    )
                )
                description.isEnabled = false
                animateY(500)
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
