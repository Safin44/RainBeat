package com.rainbeat.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import com.rainbeat.ui.SharedViewModel
import com.rainbeat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun VideoPlayerScreen(
    viewModel: SharedViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    val isPlaying by viewModel.videoPlayerController.isPlaying.collectAsState()
    val currentPosition by viewModel.videoPlayerController.currentPosition.collectAsState()
    val duration by viewModel.videoPlayerController.duration.collectAsState()
    val currentItem by viewModel.videoPlayerController.currentMediaItem.collectAsState()
    val playbackSpeed by viewModel.videoPlayerController.playbackSpeed.collectAsState()
    val isExternalIntent by viewModel.isExternalIntent.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var seekDelta by remember { mutableIntStateOf(0) }
    var showSeekIndicator by remember { mutableStateOf(false) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableIntStateOf(0) } // 0=fit, 1=fill, 2=crop
    var showSpeedPicker by remember { mutableStateOf(false) }

    // Slider state (separate from player position to avoid jitter during drag)
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }

    val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    LaunchedEffect(progress) {
        if (!isSliderDragging) {
            sliderPosition = progress
        }
    }

    // Update position
    LaunchedEffect(isPlaying) {
        while (true) {
            if (isPlaying) {
                viewModel.videoPlayerController.updatePosition()
            }
            delay(500)
        }
    }

    var isInPipMode by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val consumer = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            isInPipMode = info.isInPictureInPictureMode
        }
        (context as? androidx.activity.ComponentActivity)?.addOnPictureInPictureModeChangedListener(consumer)
        onDispose {
            (context as? androidx.activity.ComponentActivity)?.removeOnPictureInPictureModeChangedListener(consumer)
        }
    }
    // Auto-hide controls
    LaunchedEffect(showControls, showSpeedPicker, isInPipMode) {
        if (isInPipMode) {
            showControls = false
        } else if (showControls && !isLocked && isPlaying && !showSpeedPicker) {
            delay(4000)
            showControls = false
        }
    }

    // Set orientation on enter, restore on exit
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }



    // Initialize brightness/volume
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        volumeLevel = currentVolume.toFloat() / maxVolume.toFloat()

        try {
            val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            brightnessLevel = currentBrightness / 255f
        } catch (_: Exception) {
            brightnessLevel = 0.5f
        }
    }

    BackHandler {
        if (showSpeedPicker) {
            showSpeedPicker = false
        } else {
            // Don't pause on back, let it play in the mini-player
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                if (isLocked) {
                    detectTapGestures { showControls = !showControls }
                } else {
                    detectTapGestures(
                        onPress = {
                            coroutineScope {
                                var didLongPress = false
                                val originalSpeed = playbackSpeed
                                val job = launch {
                                    delay(400)
                                    didLongPress = true
                                    isFastForwarding = true
                                    viewModel.videoPlayerController.setPlaybackSpeed(2f)
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (didLongPress) {
                                    isFastForwarding = false
                                    viewModel.videoPlayerController.setPlaybackSpeed(originalSpeed)
                                }
                            }
                        },
                        onTap = {
                            if (showSpeedPicker) {
                                showSpeedPicker = false
                            } else {
                                showControls = !showControls
                            }
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2) {
                                viewModel.videoPlayerController.seekTo((currentPosition - 15000).coerceAtLeast(0))
                                seekDelta = -15
                            } else {
                                viewModel.videoPlayerController.seekTo((currentPosition + 15000).coerceAtMost(duration))
                                seekDelta = 15
                            }
                            showSeekIndicator = true
                        }
                    )
                }
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = viewModel.videoPlayerController.getPlayer()
                playerView.resizeMode = when (aspectRatioMode) {
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Brightness gesture (left half)
        if (!isLocked && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterStart)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showBrightnessIndicator = true },
                            onDragEnd = { showBrightnessIndicator = false },
                            onVerticalDrag = { _, dragAmount ->
                                val delta = -dragAmount / size.height
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                activity?.window?.attributes = activity?.window?.attributes?.apply {
                                    screenBrightness = brightnessLevel
                                }
                            }
                        )
                    }
            )

            // Volume gesture (right half)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.3f)
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { showVolumeIndicator = true },
                            onDragEnd = { showVolumeIndicator = false },
                            onVerticalDrag = { _, dragAmount ->
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val delta = -dragAmount / size.height
                                volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                val newVolume = (volumeLevel * maxVolume).toInt()
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            }
                        )
                    }
            )
        }

        // Brightness Indicator
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
        ) {
            GestureIndicator(
                icon = Icons.Default.BrightnessHigh,
                level = brightnessLevel,
                label = "${(brightnessLevel * 100).toInt()}%"
            )
        }

        // Volume Indicator
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
        ) {
            GestureIndicator(
                icon = if (volumeLevel > 0.5f) Icons.Default.VolumeUp
                       else if (volumeLevel > 0f) Icons.Default.VolumeDown
                       else Icons.Default.VolumeOff,
                level = volumeLevel,
                label = "${(volumeLevel * 100).toInt()}%"
            )
        }

        // Seek Indicator (double-tap)
        LaunchedEffect(showSeekIndicator) {
            if (showSeekIndicator) {
                delay(800)
                showSeekIndicator = false
            }
        }
        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (seekDelta > 0) "+${seekDelta}s" else "${seekDelta}s",
                    color = NeonCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Fast-forward Indicator
        AnimatedVisibility(
            visible = isFastForwarding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⏩ 2x Speed", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls && !isInPipMode,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                if (isLocked) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Unlock",
                            tint = NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { 
                            // Don't pause on back, let it play in the mini-player
                            onBack() 
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Text(
                            text = currentItem?.mediaMetadata?.title?.toString() ?: "Video",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )

                        // Speed button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playbackSpeed != 1f) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { showSpeedPicker = !showSpeedPicker }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = if (playbackSpeed != 1f) NeonCyan else TextSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Aspect ratio toggle
                        IconButton(onClick = {
                            aspectRatioMode = (aspectRatioMode + 1) % 3
                        }) {
                            Icon(
                                imageVector = when (aspectRatioMode) {
                                    0 -> Icons.Default.FitScreen
                                    1 -> Icons.Default.ZoomOutMap
                                    else -> Icons.Default.Fullscreen
                                },
                                contentDescription = "Aspect Ratio",
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Orientation toggle
                        IconButton(onClick = {
                            val current = activity?.requestedOrientation
                            activity?.requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }) {
                            Icon(
                                Icons.Default.ScreenRotation,
                                contentDescription = "Rotate",
                                tint = NeonCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Lock button
                        IconButton(onClick = { isLocked = true }) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = "Lock",
                                tint = TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Center Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.videoPlayerController.playPrevious() }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = TextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.videoPlayerController.seekTo((currentPosition - 10000).coerceAtLeast(0))
                        }) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Rewind 10s",
                                tint = NeonCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Play/Pause — clean solid circle
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(NeonCyan)
                                .clickable {
                                    if (isPlaying) viewModel.videoPlayerController.pause()
                                    else viewModel.videoPlayerController.play()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        IconButton(onClick = {
                            viewModel.videoPlayerController.seekTo((currentPosition + 10000).coerceAtMost(duration))
                        }) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Forward 10s",
                                tint = NeonCyan,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.videoPlayerController.playNext() }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = TextPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Bottom Bar: Seek + Time
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { newVal ->
                                isSliderDragging = true
                                sliderPosition = newVal
                                val newPosition = (sliderPosition * duration).toLong()
                                viewModel.videoPlayerController.seekTo(newPosition)
                            },
                            onValueChangeFinished = {
                                isSliderDragging = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatVideoTime(if (isSliderDragging) (sliderPosition * duration).toLong() else currentPosition),
                                color = TextPrimary,
                                fontSize = 12.sp
                            )
                            Text(formatVideoTime(duration), color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Speed Picker Panel
        AnimatedVisibility(
            visible = showSpeedPicker,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 16.dp)
        ) {
            SpeedPickerPanel(
                currentSpeed = playbackSpeed,
                onSpeedSelected = { speed ->
                    viewModel.videoPlayerController.setPlaybackSpeed(speed)
                    showSpeedPicker = false
                }
            )
        }
    }
}

@Composable
private fun SpeedPickerPanel(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .heightIn(max = 250.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xDD0A0E17))
            .padding(8.dp)
            .width(120.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            "Speed",
            color = NeonCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        speeds.forEach { speed ->
            val isSelected = speed == currentSpeed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) NeonCyan.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSpeedSelected(speed) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (speed == 1f) "Normal" else "${speed}x",
                    color = if (isSelected) NeonCyan else TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun GestureIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    level: Float,
    label: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier
                .width(4.dp)
                .height(80.dp),
            color = NeonCyan,
            trackColor = SurfaceVariantDark
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatVideoTime(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) - TimeUnit.HOURS.toMinutes(hours)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(
        TimeUnit.MILLISECONDS.toMinutes(ms)
    )
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
