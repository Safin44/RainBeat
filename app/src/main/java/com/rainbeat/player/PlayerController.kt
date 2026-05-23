package com.rainbeat.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var _controllerFuture: ListenableFuture<MediaController>? = null
    val controllerFuture: ListenableFuture<MediaController>?
        get() = _controllerFuture

    private var mediaController: MediaController? = null
    
    private var pendingMediaItems: List<MediaItem>? = null
    private var pendingStartIndex: Int = 0
    private var pendingPlayAuto: Boolean = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    // 0 = OFF, 1 = REPEAT_ALL, 2 = REPEAT_ONE
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        _controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        _controllerFuture?.addListener(
            {
                mediaController = _controllerFuture?.get()
                mediaController?.addListener(playerListener)
                
                pendingMediaItems?.let { items ->
                    setMediaItems(items, pendingStartIndex, pendingPlayAuto)
                    pendingMediaItems = null
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
            
            // Save last played ID
            mediaItem?.mediaId?.let { id ->
                context.getSharedPreferences("rainbeat_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_played_id", id)
                    .apply()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun playNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun playPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
        // Immediately update position so UI reflects the change even when paused
        _currentPosition.value = position.coerceAtLeast(0)
    }

    fun setMediaItems(items: List<MediaItem>, startIndex: Int = 0, playAuto: Boolean = true) {
        if (items.isNotEmpty() && startIndex in items.indices) {
            _currentMediaItem.value = items[startIndex]
        }
        
        if (mediaController == null) {
            pendingMediaItems = items
            pendingStartIndex = startIndex
            pendingPlayAuto = playAuto
            return
        }
        
        mediaController?.setMediaItems(items, startIndex, 0)
        mediaController?.prepare()
        if (playAuto) {
            mediaController?.play()
        }
    }

    fun toggleShuffle() {
        val newValue = !(mediaController?.shuffleModeEnabled ?: false)
        mediaController?.shuffleModeEnabled = newValue
        _shuffleEnabled.value = newValue
    }

    fun cycleRepeatMode() {
        val current = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = next
        _repeatMode.value = next
    }

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun updatePosition() {
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0) ?: 0L
    }

    fun getPlayer(): Player? {
        return mediaController
    }

    fun stopAndClose() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _isPlaying.value = false
        _currentMediaItem.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun release() {
        _controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController?.removeListener(playerListener)
        mediaController = null
    }
}
