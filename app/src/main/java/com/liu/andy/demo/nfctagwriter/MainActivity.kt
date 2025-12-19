package com.liu.andy.demo.nfctagwriter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.liu.andy.demo.nfctagwriter.navigation.Screen
import com.liu.andy.demo.nfctagwriter.ui.ntag424.NTag424Screen
import com.liu.andy.demo.nfctagwriter.ui.ntag21x.NTag21XScreen
import com.liu.andy.demo.nfctagwriter.ui.settings.SettingsScreen
import com.liu.andy.demo.nfctagwriter.ui.theme.NFCTagWriterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NFCTagWriterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                val items = listOf(
                    Screen.NTag424,
                    Screen.NTag21X,
                    Screen.Settings
                )
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    is Screen.NTag424 -> Icons.Filled.Refresh
                                    is Screen.NTag21X -> Icons.Filled.Slideshow
                                    is Screen.Settings -> Icons.Filled.Settings
                                    else -> Icons.Filled.Settings
                                },
                                contentDescription = screen.route
                            )
                        },
                        label = { Text(screen.route.replaceFirstChar { it.uppercase() }) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.NTag424.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.NTag424.route) {
                NTag424Screen(navController = navController)
            }
            composable(Screen.NTag21X.route) {
                NTag21XScreen(navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
