package com.d4vram.psychologger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.d4vram.psychologger.ui.navigation.Screen
import com.d4vram.psychologger.ui.navigation.bottomNavItems
import com.d4vram.psychologger.ui.screens.CalendarScreen
import com.d4vram.psychologger.ui.screens.ResourcesScreen
import com.d4vram.psychologger.ui.screens.StatsScreen
import com.d4vram.psychologger.ui.theme.PsychoLoggerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Para que tu contenido Compose ocupe toda la pantalla,
        // incluyendo debajo de status bar y nav bar
        enableEdgeToEdge()

        setContent {
            PsychoLoggerTheme {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Barra inferior en Compose, usando Material3
                        NavigationBar {
                            val navBackStack by navController.currentBackStackEntryAsState()
                            val currentRoute = navBackStack?.destination?.route

                            bottomNavItems.forEach { screen ->
                                NavigationBarItem(
                                    icon =    { Icon(screen.icon, contentDescription = null) },
                                    label =   { Text(stringResource(screen.label)) },
                                    selected = currentRoute == screen.route,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                // evita duplicar pantallas en la pila
                                                popUpTo(navController.graph.startDestinationId) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    // Host de navegación Compose
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Calendar.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Calendar.route)  { CalendarScreen() }
                        composable(Screen.Stats.route)     { StatsScreen() }
                        composable(Screen.Resources.route) { ResourcesScreen() }
                    }
                }
            }
        }
    }
}
