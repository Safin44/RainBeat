package com.rainbeat.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoPlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private fun ensurePlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

            val player = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build()
            player.addListener(playerListener)
            exoPlayer = player
        }
        return exoPlayer!!
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _duration.value = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = exoPlayer?.duration?.coerceAtLeast(0) ?: 0L
            }
        }
    }

    fun play() {
        ensurePlayer().play()
    }

    fun pause() {
        ensurePlayer().pause()
    }

    fun playNext() {
        ensurePlayer().seekToNextMediaItem()
    }

    fun playPrevious() {
        ensurePlayer().seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        ensurePlayer().seekTo(position)
        _currentPosition.value = position.coerceAtLeast(0)
    }

    fun setMediaItems(items: List<MediaItem>, startIndex: Int = 0) {
        if (items.isNotEmpty() && startIndex in items.indices) {
            _currentMediaItem.value = items[startIndex]
        }
        ensurePlayer().setMediaItems(items, startIndex, 0)
        ensurePlayer().prepare()
        ensurePlayer().play()
    }

    fun updatePosition() {
        _currentPosition.value = exoPlayer?.currentPosition?.coerceAtLeast(0) ?: 0L
    }

    fun getPlayer(): ExoPlayer? {
        return ensurePlayer()
    }

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        ensurePlayer().setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun stopAndClose() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _isPlaying.value = false
        _currentMediaItem.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun release() {
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }
}
