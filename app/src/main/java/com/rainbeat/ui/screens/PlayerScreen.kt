package com.rainbeat.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rainbeat.R
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Composable
fun PlayerScreen(viewModel: SharedViewModel, onBack: () -> Unit = {}) {
    val currentItem by viewModel.playerController.currentMediaItem.collectAsState()
    val isPlaying by viewModel.playerController.isPlaying.collectAsState()
    val currentPosition by viewModel.playerController.currentPosition.collectAsState()
    val duration by viewModel.playerController.duration.collectAsState()
    val shuffleEnabled by viewModel.playerController.shuffleEnabled.collectAsState()
    val repeatMode by viewModel.playerController.repeatMode.collectAsState()
    val isExternalIntent by viewModel.isExternalIntent.collectAsState()

    // Update position periodically
    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying) {
                viewModel.playerController.updatePosition()
            }
            kotlinx.coroutines.delay(500)
        }
    }

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSliderDragging by remember { mutableStateOf(false) }

    // Swipe seek state
    var isSwiping by remember { mutableStateOf(false) }
    var swipeSeekPosition by remember { mutableLongStateOf(0L) }
    var totalSwipeDelta by remember { mutableFloatStateOf(0f) }

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // Only update slider from player when NOT dragging
    LaunchedEffect(progress) {
        if (!isSliderDragging) {
            sliderPosition = progress
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF141A3A),
            BlackBackground
        )
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val onBackHandler: () -> Unit = {
        onBack()
    }
    
    androidx.activity.compose.BackHandler {
        onBackHandler()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var menuExpanded by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackHandler) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
            Text(
                "NOW PLAYING",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreHoriz, contentDescription = "Menu", tint = TextSecondary)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(SurfaceVariantDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = TextPrimary) },
                        onClick = {
                            menuExpanded = false
                            android.widget.Toast.makeText(context, "Added to Playlist", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share", color = TextPrimary) },
                        onClick = {
                            menuExpanded = false
                            android.widget.Toast.makeText(context, "Sharing...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Details", color = TextPrimary) },
                        onClick = {
                            menuExpanded = false
                            android.widget.Toast.makeText(context, "Details", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Album Art — swipe left/right for next/prev, horizontal drag to seek
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(SurfaceDark)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSwiping = true
                            totalSwipeDelta = 0f
                            swipeSeekPosition = currentPosition
                        },
                        onDragEnd = {
                            val threshold = size.width * 0.35f
                            if (abs(totalSwipeDelta) > threshold) {
                                // Large swipe → next/prev song
                                if (totalSwipeDelta > 0) {
                                    viewModel.playerController.playPrevious()
                                } else {
                                    viewModel.playerController.playNext()
                                }
                            } else if (abs(totalSwipeDelta) > 20f) {
                                // Small swipe → seek to swipeSeekPosition
                                viewModel.playerController.seekTo(swipeSeekPosition)
                            }
                            isSwiping = false
                        },
                        onDragCancel = { isSwiping = false },
                        onHorizontalDrag = { _, dragAmount ->
                            totalSwipeDelta += dragAmount
                            // YouTube-style: horizontal drag seeks through song
                            // Each full-width swipe = ~60 seconds of seek
                            if (duration > 0) {
                                val seekMs = (dragAmount / size.width) * 60000f
                                swipeSeekPosition = (swipeSeekPosition + seekMs.toLong())
                                    .coerceIn(0L, duration)
                                viewModel.playerController.seekTo(swipeSeekPosition)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(currentItem?.mediaMetadata?.artworkUri)
                    .crossfade(true)
                    .error(R.drawable.default_album_art)
                    .fallback(R.drawable.default_album_art)
                    .build(),
                contentDescription = "Album Art",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )

            // Swipe seek overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = isSwiping,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
                modifier = Modifier.matchParentSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // totalSwipeDelta is in pixels; 300px is a good threshold for next/prev
                        if (abs(totalSwipeDelta) > 300f) {
                            Text(
                                text = if (totalSwipeDelta > 0) "⟵ Previous" else "Next ⟶",
                                color = NeonCyan,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = formatTime(swipeSeekPosition),
                                color = NeonCyan,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val delta = swipeSeekPosition - currentPosition
                            Text(
                                text = if (delta >= 0) "+${formatTime(abs(delta))}" else "-${formatTime(abs(delta))}",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Song Info
        Text(
            text = currentItem?.mediaMetadata?.title?.toString() ?: "No Media Selected",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = currentItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
            color = TextSecondary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Progress Bar
        Slider(
            value = sliderPosition,
            onValueChange = { newProgress ->
                isSliderDragging = true
                sliderPosition = newProgress
                val newPosition = (sliderPosition * duration).toLong()
                viewModel.playerController.seekTo(newPosition)
            },
            onValueChangeFinished = {
                isSliderDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = NeonCyan,
                activeTrackColor = NeonCyan,
                inactiveTrackColor = SurfaceVariantDark
            ),
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(if (isSliderDragging) (sliderPosition * duration).toLong() else currentPosition), color = TextSecondary, fontSize = 12.sp)
            Text(formatTime(duration), color = TextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            Icon(
                Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (shuffleEnabled) NeonCyan else TextSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { viewModel.playerController.toggleShuffle() }
            )

            // Previous
            Icon(
                Icons.Default.SkipPrevious,
                contentDescription = "Previous",
                tint = TextPrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { viewModel.playerController.playPrevious() }
            )

            // -10s Button with icon
            SeekButton(
                icon = Icons.Default.Replay10,
                contentDescription = "Rewind 10s",
                onClick = {
                    viewModel.playerController.seekTo((currentPosition - 10000).coerceAtLeast(0))
                }
            )

            // Play/Pause Button — clean, no dark mark
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .clickable {
                        if (isPlaying) viewModel.playerController.pause()
                        else viewModel.playerController.play()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = BlackBackground,
                    modifier = Modifier.size(36.dp)
                )
            }

            // +10s Button with icon
            SeekButton(
                icon = Icons.Default.Forward10,
                contentDescription = "Forward 10s",
                onClick = {
                    viewModel.playerController.seekTo((currentPosition + 10000).coerceAtMost(duration))
                }
            )

            // Next
            Icon(
                Icons.Default.SkipNext,
                contentDescription = "Next",
                tint = TextPrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { viewModel.playerController.playNext() }
            )

            // Repeat Button
            Icon(
                imageVector = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                contentDescription = "Repeat",
                tint = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> TextSecondary
                    else -> NeonCyan
                },
                modifier = Modifier
                    .size(24.dp)
                    .clickable { viewModel.playerController.cycleRepeatMode() }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SeekButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = tween(100),
        label = "seek_scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(NeonCyan.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = NeonCyan,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}
