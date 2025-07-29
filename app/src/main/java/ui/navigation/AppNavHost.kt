package com.d4vram.psychologger.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.d4vram.psychologger.ui.screens.CalendarScreen
import com.d4vram.psychologger.ui.screens.ResourcesScreen
import com.d4vram.psychologger.ui.screens.StatsScreen

/**
 * Lista de pantallas que quieres en la BottomBar
 * (asegúrate de tener definidas las rutas, iconos y labels en Screen.kt)
 */
val bottomNavItems = listOf(
    Screen.Calendar,
    Screen.Stats,
    Screen.Resources
)

@Composable
fun AppNavHost() {
    // 1. Controlador de navegación
    val navController = rememberNavController()

    // 2. Scaffold global con tema y fondo
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor   = MaterialTheme.colorScheme.onBackground,
        bottomBar      = { BottomBar(navController) }
    ) { innerPadding ->
        // 3. Aquí van tus pantallas
        NavHost(
            navController   = navController,
            startDestination = Screen.Calendar.route,
            modifier         = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Calendar.route)  { CalendarScreen() }
            composable(Screen.Stats.route)     { StatsScreen() }
            composable(Screen.Resources.route) { ResourcesScreen() }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor   = MaterialTheme.colorScheme.onSurface
    ) {
        // 4. Detectar la ruta actual
        val navBackStack by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStack?.destination?.route

        bottomNavItems.forEach { screen ->
            NavigationBarItem(
                icon      = { Icon(screen.icon, contentDescription = null) },
                label     = { Text(stringResource(screen.label)) },
                selected  = currentRoute == screen.route,
                onClick   = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            // evita duplicar pantallas
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    }
                }
            )
        }
    }
}
