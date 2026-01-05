package com.example.aialarmclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.aialarmclock.ui.screens.HomeScreen
import com.example.aialarmclock.ui.screens.QuestionConfigScreen
import com.example.aialarmclock.ui.screens.ResponseHistoryScreen
import com.example.aialarmclock.ui.screens.SetAlarmScreen
import com.example.aialarmclock.ui.screens.SettingsScreen
import com.example.aialarmclock.ui.theme.AIAlarmClockTheme
import com.example.aialarmclock.ui.viewmodels.AlarmViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results
        permissions.entries.forEach { entry ->
            when (entry.key) {
                Manifest.permission.RECORD_AUDIO -> {
                    if (!entry.value) {
                        // Microphone permission denied - show message
                    }
                }
                Manifest.permission.POST_NOTIFICATIONS -> {
                    // Notification permission result
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        setContent {
            AIAlarmClockTheme {
                MainScreen()
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Microphone permission for speech recognition
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Alarm")
    object SetAlarm : Screen("set_alarm", "Set Alarm")
    object QuestionConfig : Screen("question_config", "Questions")
    object Settings : Screen("settings", "Settings")
    object History : Screen("history", "History")
}

@Composable
fun MainScreen(viewModel: AlarmViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val bottomNavItems = listOf(Screen.Home, Screen.History, Screen.Settings)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    Screen.Home -> Icons.Default.Home
                                    Screen.History -> Icons.Default.History
                                    Screen.Settings -> Icons.Default.Settings
                                    else -> Icons.Default.Home
                                },
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onSetAlarmClick = { navController.navigate(Screen.SetAlarm.route) },
                    onConfigureQuestionsClick = { navController.navigate(Screen.QuestionConfig.route) }
                )
            }
            composable(Screen.SetAlarm.route) {
                SetAlarmScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.QuestionConfig.route) {
                QuestionConfigScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(Screen.History.route) {
                ResponseHistoryScreen(viewModel = viewModel)
            }
        }
    }
}
