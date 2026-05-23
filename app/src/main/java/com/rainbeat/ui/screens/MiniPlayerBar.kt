package com.rainbeat.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.rainbeat.R
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MiniPlayerBar(
    viewModel: SharedViewModel,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeMode by viewModel.activeMode.collectAsState()

    val audioItem by viewModel.playerController.currentMediaItem.collectAsState()
    val audioPlaying by viewModel.playerController.isPlaying.collectAsState()
    val audioPos by viewModel.playerController.currentPosition.collectAsState()
    val audioDur by viewModel.playerController.duration.collectAsState()

    val videoItem by viewModel.videoPlayerController.currentMediaItem.collectAsState()
    val videoPlaying by viewModel.videoPlayerController.isPlaying.collectAsState()
    val videoPos by viewModel.videoPlayerController.currentPosition.collectAsState()
    val videoDur by viewModel.videoPlayerController.duration.collectAsState()

    val currentItem = if (activeMode == "VIDEO") videoItem else audioItem
    val isPlaying = if (activeMode == "VIDEO") videoPlaying else audioPlaying
    val currentPosition = if (activeMode == "VIDEO") videoPos else audioPos
    val duration = if (activeMode == "VIDEO") videoDur else audioDur

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    // Update position periodically for progress bar
    LaunchedEffect(isPlaying, activeMode) {
        while (true) {
            if (isPlaying) {
                if (activeMode == "VIDEO") viewModel.videoPlayerController.updatePosition()
                else viewModel.playerController.updatePosition()
            }
            kotlinx.coroutines.delay(500)
        }
    }

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    // Only show if a media item is loaded
    AnimatedVisibility(
        visible = currentItem != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Column(
            modifier = modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (kotlin.math.abs(offsetX.value) > size.width / 3) {
                                scope.launch {
                                    val target = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat()
                                    offsetX.animateTo(target, tween(200))
                                    if (activeMode == "VIDEO") {
                                        viewModel.videoPlayerController.stopAndClose()
                                    } else {
                                        viewModel.playerController.stopAndClose()
                                    }
                                    offsetX.snapTo(0f)
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, tween(300))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f, tween(300))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                            }
                        }
                    )
                }
        ) {
            // Thin progress indicator at the top
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = NeonCyan,
                trackColor = Color.Transparent
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                SurfaceDark,
                                Color(0xFF0F1628),
                                SurfaceDark
                            )
                        )
                    )
                    .clickable(onClick = onTap)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art Thumbnail
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceVariantDark)
                ) {
                    if (activeMode == "VIDEO") {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    useController = false
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                }
                            },
                            update = { view ->
                                view.player = viewModel.videoPlayerController.getPlayer()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val ctx = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(ctx)
                                .data(currentItem?.mediaMetadata?.artworkUri)
                                .crossfade(true)
                                .error(R.drawable.default_album_art)
                                .fallback(R.drawable.default_album_art)
                                .build(),
                            contentDescription = "Album Art",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Song Title & Artist
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentItem?.mediaMetadata?.artist?.toString() ?: "Unknown Artist",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeonCyan.copy(alpha = 0.15f))
                        .clickable {
                            if (activeMode == "VIDEO") {
                                if (isPlaying) viewModel.videoPlayerController.pause()
                                else viewModel.videoPlayerController.play()
                            } else {
                                if (isPlaying) viewModel.playerController.pause()
                                else viewModel.playerController.play()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Queue Icon
                Icon(
                    Icons.Default.QueueMusic,
                    contentDescription = "Queue",
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
