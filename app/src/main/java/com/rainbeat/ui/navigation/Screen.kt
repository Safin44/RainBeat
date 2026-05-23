package com.rainbeat.ui.navigation

sealed class Screen(val route: String, val title: String) {
    object Player : Screen("player", "Now Playing")
    object MusicLibrary : Screen("music_library", "Music")
    object VideoLibrary : Screen("video_library", "Video")
    object FolderExplorer : Screen("folder_explorer", "Folders")
    object VideoPlayer : Screen("video_player", "Video Player")
}
