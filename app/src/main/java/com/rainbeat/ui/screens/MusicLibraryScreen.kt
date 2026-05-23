package com.rainbeat.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.rainbeat.R
import com.rainbeat.data.LocalMediaItem
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import com.rainbeat.ui.navigation.Screen
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(viewModel: SharedViewModel, navController: NavController) {
    val audioList by viewModel.audioList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentMediaItemState = viewModel.playerController.currentMediaItem.collectAsState()
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
            .padding(16.dp)
    ) {
        // Selection mode top bar / normal title
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically()
            },
            label = "titleBar"
        ) { isSelecting ->
            if (isSelecting) {
                SelectionTopBar(
                    selectedCount = selectedItems.size,
                    totalCount = audioList.size,
                    onSelectAll = {
                        if (selectedItems.size == audioList.size) {
                            selectedItems.clear()
                        } else {
                            audioList.forEach { selectedItems[it.id] = it }
                        }
                    },
                    onCancel = {
                        selectedItems.clear()
                        selectionMode = false
                    },
                    onPlaySelected = {
                        val items = audioList.filter { it.id in selectedItems }
                        viewModel.playSelectedAudioItems(items)
                        selectedItems.clear()
                        selectionMode = false
                        navController.navigate(Screen.Player.route) {
                            launchSingleTop = true
                        }
                    }
                )
            } else {
                Text(
                    "Music Library",
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
            placeholder = { Text("Search songs...") },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = NeonBlue,
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
                CircularProgressIndicator(color = NeonBlue)
            }
        } else if (audioList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No music found on device.", color = TextSecondary)
            }
        } else {
            val filteredList = remember(audioList, searchQuery) {
                audioList.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(filteredList, key = { _, item -> item.id }) { _, item ->
                    val context = LocalContext.current
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
                                viewModel.playAudioItem(item)
                                navController.navigate(Screen.Player.route) {
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

@Composable
fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    onPlaySelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceVariantDark)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Cancel button
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextPrimary)
        }

        // Selection count
        Text(
            text = "$selectedCount selected",
            color = NeonCyan,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )

        Row {
            // Select all button
            IconButton(onClick = onSelectAll) {
                Icon(
                    Icons.Default.SelectAll,
                    contentDescription = "Select All",
                    tint = if (selectedCount == totalCount) NeonCyan else TextSecondary
                )
            }

            // Play selected button
            IconButton(
                onClick = onPlaySelected,
                enabled = selectedCount > 0
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play Selected",
                    tint = if (selectedCount > 0) NeonBlue else TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaListItem(
    item: LocalMediaItem,
    playingMediaId: State<String?>? = null,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onAction: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    val isPlaying by remember {
        derivedStateOf { playingMediaId?.value == item.id.toString() }
    }

    val bgColor = when {
        isSelected -> NeonCyan.copy(alpha = 0.15f)
        isPlaying -> NeonBlue.copy(alpha = 0.2f)
        else -> SurfaceDark
    }

    val borderMod = if (isSelected) {
        Modifier.border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(borderMod)
            .background(bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection checkbox (visible in selection mode)
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) NeonCyan else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceVariantDark)
        ) {
            if (item.isVideo && item.albumArtUri != null) {
                // For video items, use VideoFrameDecoder to extract first frame as thumbnail
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.albumArtUri)
                        .size(256)
                        .crossfade(false)
                        .error(R.drawable.default_album_art)
                        .fallback(R.drawable.default_album_art)
                        .build(),
                    contentDescription = "Video Thumbnail",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // For audio items — album art URI may exist but point to nothing
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.albumArtUri)
                        .size(256)
                        .crossfade(false)
                        .error(R.drawable.default_album_art)
                        .fallback(R.drawable.default_album_art)
                        .build(),
                    contentDescription = "Album Art",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                color = when {
                    isSelected -> NeonCyan
                    isPlaying -> NeonCyan
                    else -> TextPrimary
                },
                maxLines = 1
            )
            Text(item.artist, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }

        // Hide menu in selection mode
        if (!selectionMode) {
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
                        text = { Text("Play Next", color = TextPrimary) },
                        onClick = { menuExpanded = false; onAction?.invoke("PLAY_NEXT") }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        onClick = { menuExpanded = false; onAction?.invoke("SHARE") }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename", color = TextPrimary) },
                        onClick = { menuExpanded = false; onAction?.invoke("RENAME") }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color.Red) },
                        onClick = { menuExpanded = false; onAction?.invoke("DELETE") }
                    )
                }
            }
        }
    }
}
