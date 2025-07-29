package com.d4vram.psychologger.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Public
import androidx.compose.ui.graphics.vector.ImageVector
import com.d4vram.psychologger.R

sealed class Screen(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    object Calendar : Screen("calendar", R.string.tab_calendar, Icons.Filled.Event)
    object Stats    : Screen("stats",    R.string.tab_stats,    Icons.Filled.BarChart)
    object Resources: Screen("resources",R.string.tab_resources,Icons.Filled.Public)
}

