package com.rainbeat.ui.screens

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.rainbeat.data.LocalMediaItem
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.navigation.Screen
import com.rainbeat.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderExplorerScreen(viewModel: SharedViewModel, navController: NavController) {
    val rootPath = Environment.getExternalStorageDirectory().absolutePath
    val currentPath by viewModel.currentFolderPath.collectAsState()

    val audioList by viewModel.audioList.collectAsState()
    val videoList by viewModel.videoList.collectAsState()

    var itemToRename by remember { mutableStateOf<LocalMediaItem?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Trigger recomputation when media lists change OR when currentPath changes
    LaunchedEffect(audioList, videoList, currentPath) {
        viewModel.computeFolderContents()
    }

    // Consume pre-computed folder contents from the ViewModel
    val contents by viewModel.folderContents.collectAsState()
    val subDirs = contents.subDirs
    val currentFiles = contents.currentFiles

    BackHandler(enabled = currentPath != rootPath || isSearching) {
        if (isSearching) {
            isSearching = false
            searchQuery = ""
        } else {
            viewModel.navigateUp()
        }
    }

    if (itemToRename != null) {
        var newName by remember { mutableStateOf(itemToRename!!.title) }
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename File", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = SurfaceVariantDark,
                        containerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.renameItem(context, itemToRename!!, newName)
                    }
                    itemToRename = null
                }) {
                    Text("Rename", color = NeonCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceVariantDark
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        if (isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    isSearching = false
                    searchQuery = ""
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search files...") },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SurfaceVariantDark,
                        containerColor = SurfaceDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { 
                    if (currentPath != rootPath) {
                        viewModel.navigateUp()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                
                Row {
                    IconButton(onClick = { isSearching = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (isSearching) {
            val filteredList = remember(audioList, videoList, searchQuery) {
                if (searchQuery.isBlank()) {
                    emptyList()
                } else {
                    val result = mutableListOf<LocalMediaItem>()
                    val filterLogic = { item: LocalMediaItem ->
                        if (item.title.contains(searchQuery, ignoreCase = true) || item.artist.contains(searchQuery, ignoreCase = true)) {
                            result.add(item)
                        }
                    }
                    audioList.forEach(filterLogic)
                    videoList.forEach(filterLogic)
                    result
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredList, key = { "search_${it.id}" }) { fileItem ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .clickable {
                                if (fileItem.isVideo) {
                                    viewModel.playVideoItem(fileItem)
                                    navController.navigate(Screen.VideoPlayer.route) { launchSingleTop = true }
                                } else {
                                    viewModel.playAudioItem(fileItem)
                                    navController.navigate(Screen.Player.route) { launchSingleTop = true }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = if (fileItem.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile
                        Icon(icon, contentDescription = "Type", tint = TextSecondary)
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fileItem.title, color = TextPrimary, maxLines = 1)
                            if (fileItem.artist.isNotBlank() && fileItem.artist != "<unknown>") {
                                Text(fileItem.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("⋮", color = NeonCyan, style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(SurfaceVariantDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = TextPrimary) },
                                    onClick = { menuExpanded = false; itemToRename = fileItem }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color.Red) },
                                    onClick = { menuExpanded = false; viewModel.deleteItem(context, fileItem) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                if (currentPath == rootPath) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LibraryButton(
                                title = "MUSIC LIBRARY",
                                subtitle = "All Audio Files",
                                icon = Icons.Default.LibraryMusic,
                                iconColor = Color.Green,
                                onClick = { navController.navigate(Screen.MusicLibrary.route) },
                                modifier = Modifier.weight(1f)
                            )
                            LibraryButton(
                                title = "VIDEO LIBRARY",
                                subtitle = "All Video Files",
                                icon = Icons.Default.Movie,
                                iconColor = Color(0xFFFFC107),
                                onClick = { navController.navigate(Screen.VideoLibrary.route) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (currentPath != rootPath) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceDark)
                                .clickable {
                                    viewModel.navigateUp()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Up", tint = TextSecondary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("..", color = TextPrimary)
                        }
                    }
                }

                items(subDirs, key = { it }) { dirPath ->
                    val dirName = dirPath.substringAfterLast('/')
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .clickable {
                                viewModel.navigateToFolder(dirPath)
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = "Type", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(dirName, color = TextPrimary, maxLines = 1)
                    }
                }

                items(currentFiles, key = { it.uri.toString() }) { fileItem ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceDark)
                            .clickable {
                                if (fileItem.isVideo) {
                                    val folderVideos = currentFiles.filter { it.isVideo }
                                    val index = folderVideos.indexOf(fileItem).coerceAtLeast(0)
                                    viewModel.playFolderVideoItems(folderVideos, index)
                                    navController.navigate(Screen.VideoPlayer.route) { launchSingleTop = true }
                                } else {
                                    val folderAudio = currentFiles.filter { !it.isVideo }
                                    val index = folderAudio.indexOf(fileItem).coerceAtLeast(0)
                                    viewModel.playFolderAudioItems(folderAudio, index)
                                    navController.navigate(Screen.Player.route) { launchSingleTop = true }
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = if (fileItem.isVideo) Icons.Default.VideoFile else Icons.Default.AudioFile
                        Icon(icon, contentDescription = "Type", tint = TextSecondary)
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fileItem.title, color = TextPrimary, maxLines = 1)
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("⋮", color = NeonCyan, style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                modifier = Modifier.background(SurfaceVariantDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename", color = TextPrimary) },
                                    onClick = { menuExpanded = false; itemToRename = fileItem }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = Color.Red) },
                                    onClick = { menuExpanded = false; viewModel.deleteItem(context, fileItem) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151923))
            .border(1.dp, iconColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconColor,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = Color.Gray,
            fontSize = 11.sp,
            fontStyle = FontStyle.Italic
        )
    }
}
