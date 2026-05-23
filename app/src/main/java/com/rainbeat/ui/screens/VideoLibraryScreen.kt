package com.rainbeat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.rainbeat.data.LocalMediaItem
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import com.rainbeat.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(viewModel: SharedViewModel, navController: NavController) {
    val videoList by viewModel.videoList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMediaItemState = viewModel.videoPlayerController.currentMediaItem.collectAsState()
    val playingMediaId = remember {
        derivedStateOf { currentMediaItemState.value?.mediaId }
    }

    var itemToRename by remember { mutableStateOf<LocalMediaItem?>(null) }

    // Multi-select state
    var selectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateMapOf<Long, LocalMediaItem>() }

    // Exit selection mode when selection becomes empty
    LaunchedEffect(selectedItems.size) {
        if (selectionMode && selectedItems.isEmpty()) {
            selectionMode = false
        }
    }

    if (itemToRename != null) {
        var newName by remember { mutableStateOf(itemToRename!!.title) }
        val context = androidx.compose.ui.platform.LocalContext.current
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
            .padding(16.dp)
    ) {
        // Selection mode top bar / normal title
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
            },
            label = "videoTitleBar"
        ) { isSelecting ->
            if (isSelecting) {
                SelectionTopBar(
                    selectedCount = selectedItems.size,
                    totalCount = videoList.size,
                    onSelectAll = {
                        if (selectedItems.size == videoList.size) {
                            selectedItems.clear()
                        } else {
                            videoList.forEach { selectedItems[it.id] = it }
                        }
                    },
                    onCancel = {
                        selectedItems.clear()
                        selectionMode = false
                    },
                    onPlaySelected = {
                        val items = videoList.filter { it.id in selectedItems }
                        viewModel.playSelectedVideoItems(items)
                        selectedItems.clear()
                        selectionMode = false
                        navController.navigate(Screen.VideoPlayer.route) {
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                Text(
                    "Video Library",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        var searchQuery by remember { mutableStateOf("") }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search videos...") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = SurfaceVariantDark,
                containerColor = SurfaceDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NeonCyan)
            }
        } else if (videoList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No videos found on device.", color = TextSecondary)
            }
        } else {
            val filteredList = remember(videoList, searchQuery) {
                videoList.filter {
                    it.title.contains(searchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredList, key = { _, item -> item.id }) { _, item ->
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val isSelected = item.id in selectedItems
                    MediaListItem(
                        item = item,
                        playingMediaId = playingMediaId,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (selectionMode) {
                                if (isSelected) {
                                    selectedItems.remove(item.id)
                                } else {
                                    selectedItems[item.id] = item
                                }
                            } else {
                                viewModel.playVideoItem(item)
                                navController.navigate(Screen.VideoPlayer.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onLongClick = {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedItems[item.id] = item
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                "PLAY_NEXT" -> viewModel.playNext(item)
                                "SHARE" -> viewModel.shareItem(context, item)
                                "DELETE" -> viewModel.deleteItem(context, item)
                                "RENAME" -> itemToRename = item
                            }
                        }
                    )
                }
            }
        }
    }
}
