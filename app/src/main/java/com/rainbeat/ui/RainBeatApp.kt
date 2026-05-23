package com.rainbeat.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rainbeat.ui.navigation.Screen
import com.rainbeat.ui.theme.BlackBackground
import com.rainbeat.ui.theme.NeonBlue
import com.rainbeat.ui.theme.NeonCyan
import com.rainbeat.ui.theme.TextSecondary
import com.rainbeat.ui.screens.PlayerScreen
import com.rainbeat.ui.screens.MusicLibraryScreen
import com.rainbeat.ui.screens.VideoLibraryScreen
import com.rainbeat.ui.screens.FolderExplorerScreen
import com.rainbeat.ui.screens.VideoPlayerScreen
import com.rainbeat.ui.screens.MiniPlayerBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun RainBeatApp(viewModel: SharedViewModel) {
    val navController = rememberNavController()
    
    val navigateTo by viewModel.navigateToExternalMedia.collectAsState()
    LaunchedEffect(navigateTo) {
        navigateTo?.let { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            viewModel.onExternalMediaNavigated()
        }
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isVideoPlayerScreen = currentRoute == Screen.VideoPlayer.route
    val isPlayerScreen = currentRoute == Screen.Player.route

    LaunchedEffect(isVideoPlayerScreen) {
        viewModel.setInVideoPlayerScreen(isVideoPlayerScreen)
    }

    Scaffold(
        bottomBar = {
            // Hide bottom nav and mini-player when in full-screen video player
            if (!isVideoPlayerScreen) {
                Column {
                    // Mini-player bar (hidden when on PlayerScreen or VideoPlayerScreen)
                    if (!isPlayerScreen) {
                        MiniPlayerBar(
                            viewModel = viewModel,
                            onTap = {
                                val dest = if (viewModel.activeMode.value == "VIDEO") {
                                    Screen.VideoPlayer.route
                                } else {
                                    Screen.Player.route
                                }
                                navController.navigate(dest) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }

                    // NavigationBar removed as per requirements
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.FolderExplorer.route,
            modifier = if (isVideoPlayerScreen) Modifier else Modifier.padding(innerPadding)
        ) {
            composable(Screen.Player.route) { PlayerScreen(viewModel, onBack = { navController.popBackStack() }) }
            composable(Screen.MusicLibrary.route) { MusicLibraryScreen(viewModel, navController) }
            composable(Screen.VideoLibrary.route) { VideoLibraryScreen(viewModel, navController) }
            composable(Screen.FolderExplorer.route) { FolderExplorerScreen(viewModel, navController) }
            composable(Screen.VideoPlayer.route) {
                VideoPlayerScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
