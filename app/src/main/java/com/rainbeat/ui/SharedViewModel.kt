package com.rainbeat.ui

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.rainbeat.data.LocalMediaItem
import com.rainbeat.data.MediaRepository
import com.rainbeat.player.PlayerController
import com.rainbeat.player.VideoPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.result.IntentSenderRequest
import android.app.PendingIntent
import android.provider.MediaStore
import android.os.Build
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SharedViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    val playerController: PlayerController,
    val videoPlayerController: VideoPlayerController,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _audioList = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val audioList: StateFlow<List<LocalMediaItem>> = _audioList.asStateFlow()

    private val _videoList = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    val videoList: StateFlow<List<LocalMediaItem>> = _videoList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _activeMode = MutableStateFlow("AUDIO") // "AUDIO" or "VIDEO"
    val activeMode: StateFlow<String> = _activeMode.asStateFlow()
    
    private val _pendingIntentRequest = MutableStateFlow<IntentSenderRequest?>(null)
    val pendingIntentRequest: StateFlow<IntentSenderRequest?> = _pendingIntentRequest.asStateFlow()
    
    private val _navigateToExternalMedia = MutableStateFlow<String?>(null)
    val navigateToExternalMedia: StateFlow<String?> = _navigateToExternalMedia.asStateFlow()

    private val _isExternalIntent = MutableStateFlow(false)
    val isExternalIntent: StateFlow<Boolean> = _isExternalIntent.asStateFlow()

    private val _isInVideoPlayerScreen = MutableStateFlow(false)
    val isInVideoPlayerScreen: StateFlow<Boolean> = _isInVideoPlayerScreen.asStateFlow()

    fun setInVideoPlayerScreen(inScreen: Boolean) {
        _isInVideoPlayerScreen.value = inScreen
    }
    
    private var pendingUpdateOperation: (() -> Unit)? = null

    fun onIntentSenderResult(success: Boolean) {
        if (success) {
            pendingUpdateOperation?.invoke()
        }
        pendingUpdateOperation = null
        _pendingIntentRequest.value = null
    }

    fun onExternalMediaNavigated() {
        _navigateToExternalMedia.value = null
    }

    fun onPermissionsGranted() {
        if (!_hasPermissions.value) {
            _hasPermissions.value = true
            loadMedia()
        }
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _isLoading.value = true
            _audioList.value = mediaRepository.getAudioFiles()
            _videoList.value = mediaRepository.getVideoFiles()
            _isLoading.value = false

            // Auto-load last played item if nothing is playing
            if (playerController.currentMediaItem.value == null && _audioList.value.isNotEmpty()) {
                val prefs = context.getSharedPreferences("rainbeat_prefs", Context.MODE_PRIVATE)
                val lastId = prefs.getString("last_played_id", null)
                val targetItem = _audioList.value.find { it.id.toString() == lastId } ?: _audioList.value.first()
                val originalIndex = findAudioIndex(targetItem)
                
                val mediaItems = _audioList.value.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                        .setUri(localItem.uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(localItem.title)
                                .setArtist(localItem.artist)
                                .setArtworkUri(localItem.albumArtUri)
                                .build()
                        )
                        .build()
                }
                playerController.setMediaItems(mediaItems, originalIndex, playAuto = false)
            }
        }
    }

    /**
     * Find the original index of an item in the audio list by its ID.
     * This fixes the search result index mismatch bug where a filtered index
     * was incorrectly used against the full list.
     */
    private fun findAudioIndex(item: LocalMediaItem): Int {
        return _audioList.value.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
    }

    /**
     * Find the original index of an item in the video list by its ID.
     */
    private fun findVideoIndex(item: LocalMediaItem): Int {
        return _videoList.value.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
    }

    fun playAudioItem(item: LocalMediaItem) {
        viewModelScope.launch {
            val originalIndex = findAudioIndex(item)
            val mediaItems = withContext(Dispatchers.Default) {
                _audioList.value.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .setArtworkUri(localItem.albumArtUri)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "AUDIO"
            videoPlayerController.pause()
            playerController.setMediaItems(mediaItems, originalIndex)
        }
    }

    fun playVideoItem(item: LocalMediaItem) {
        viewModelScope.launch {
            val originalIndex = findVideoIndex(item)
            val mediaItems = withContext(Dispatchers.Default) {
                _videoList.value.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "VIDEO"
            playerController.pause()
            videoPlayerController.setMediaItems(mediaItems, originalIndex)
        }
    }

    fun playExternalUri(uri: android.net.Uri, isVideo: Boolean, context: Context) {
        _isExternalIntent.value = true
        val title = "External Media"
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .build()
            )
            .build()
            
        if (isVideo) {
            _activeMode.value = "VIDEO"
            playerController.pause()
            videoPlayerController.setMediaItems(listOf(mediaItem), 0)
            _navigateToExternalMedia.value = "video_player"
        } else {
            _activeMode.value = "AUDIO"
            videoPlayerController.pause()
            playerController.setMediaItems(listOf(mediaItem), 0)
            _navigateToExternalMedia.value = "player"
        }
    }

    fun playFolderAudioItems(items: List<LocalMediaItem>, startIndex: Int) {
        viewModelScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                items.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .setArtworkUri(localItem.albumArtUri)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "AUDIO"
            videoPlayerController.pause()
            playerController.setMediaItems(mediaItems, startIndex)
        }
    }

    fun playFolderVideoItems(items: List<LocalMediaItem>, startIndex: Int) {
        viewModelScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                items.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "VIDEO"
            playerController.pause()
            videoPlayerController.setMediaItems(mediaItems, startIndex)
        }
    }

    fun playNext(item: LocalMediaItem) {
        val player = if (_activeMode.value == "VIDEO") videoPlayerController.getPlayer() else playerController.getPlayer()
        if (player != null) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setArtworkUri(item.albumArtUri)
                        .build()
                )
                .build()
            val currentIndex = player.currentMediaItemIndex
            player.addMediaItem(currentIndex + 1, mediaItem)
            Toast.makeText(context, "Added to Play Next", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareItem(ctx: Context, item: LocalMediaItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/*" else "audio/*"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(shareIntent, "Share Media"))
    }

    fun deleteItem(ctx: Context, item: LocalMediaItem) {
        val performDelete = {
            try {
                val deleted = ctx.contentResolver.delete(item.uri, null, null)
                if (deleted > 0) {
                    // Immediate UI feedback
                    _audioList.value = _audioList.value.filter { it.id != item.id }
                    _videoList.value = _videoList.value.filter { it.id != item.id }
                    
                    // Update folder contents if we are in a folder
                    _currentFolderPath.value?.let { computeFolderContents(it) }

                    Toast.makeText(ctx, "Deleted successfully", Toast.LENGTH_SHORT).show()
                    loadMedia() // Sync with database
                } else {
                    Toast.makeText(ctx, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    pendingUpdateOperation = { deleteItem(ctx, item) }
                    _pendingIntentRequest.value = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                } else {
                    Toast.makeText(ctx, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createDeleteRequest(ctx.contentResolver, listOf(item.uri))
            pendingUpdateOperation = {
                // After user approves, the system deletes it. We just need to refresh UI.
                _audioList.value = _audioList.value.filter { it.id != item.id }
                _videoList.value = _videoList.value.filter { it.id != item.id }
                _currentFolderPath.value?.let { computeFolderContents(it) }
                
                Toast.makeText(ctx, "Deleted successfully", Toast.LENGTH_SHORT).show()
                loadMedia()
            }
            _pendingIntentRequest.value = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } else {
            performDelete()
        }
    }

    fun renameItem(ctx: Context, item: LocalMediaItem, newName: String) {
        val performRename = {
            try {
                val oldFile = File(item.filePath)
                val extension = if (item.filePath.contains(".")) {
                    item.filePath.substringAfterLast(".")
                } else if (item.isVideo) "mp4" else "mp3"

                // If user didn't provide extension, append original one
                val finalDisplayName = if (newName.contains(".")) newName else "$newName.$extension"
                val finalTitle = newName.substringBeforeLast(".")

                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, finalDisplayName)
                    put(MediaStore.MediaColumns.TITLE, finalTitle)
                }

                val updated = ctx.contentResolver.update(item.uri, values, null, null)
                if (updated > 0) {
                    val newPath = if (oldFile.parent != null) {
                        File(oldFile.parent, finalDisplayName).absolutePath
                    } else item.filePath // Fallback if parent is null

                    // Immediately update local state for instant UI feedback
                    _audioList.value = _audioList.value.map {
                        if (it.id == item.id) it.copy(title = finalTitle, filePath = newPath) else it
                    }
                    _videoList.value = _videoList.value.map {
                        if (it.id == item.id) it.copy(title = finalTitle, filePath = newPath) else it
                    }
                    
                    // Update folder contents if we are in a folder
                    _currentFolderPath.value?.let { computeFolderContents(it) }

                    Toast.makeText(ctx, "Renamed successfully", Toast.LENGTH_SHORT).show()
                    loadMedia() // Sync with database
                } else {
                    Toast.makeText(ctx, "Rename failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is android.app.RecoverableSecurityException) {
                    pendingUpdateOperation = { renameItem(ctx, item, newName) }
                    _pendingIntentRequest.value = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                } else {
                    Toast.makeText(ctx, "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = MediaStore.createWriteRequest(ctx.contentResolver, listOf(item.uri))
            pendingUpdateOperation = performRename
            _pendingIntentRequest.value = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        } else {
            performRename()
        }
    }

    // ── Multi-select playback ──

    /**
     * Play a custom selection of audio items as a queue.
     * The first item in the list starts playing immediately.
     */
    fun playSelectedAudioItems(items: List<LocalMediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                items.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .setArtworkUri(localItem.albumArtUri)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "AUDIO"
            videoPlayerController.pause()
            playerController.setMediaItems(mediaItems, 0)
        }
    }

    /**
     * Play a custom selection of video items as a queue.
     */
    fun playSelectedVideoItems(items: List<LocalMediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            val mediaItems = withContext(Dispatchers.Default) {
                items.map { localItem ->
                    MediaItem.Builder()
                        .setMediaId(localItem.id.toString())
                .setUri(localItem.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(localItem.title)
                        .setArtist(localItem.artist)
                        .build()
                )
                        .build()
                }
            }
            _activeMode.value = "VIDEO"
            playerController.pause()
            videoPlayerController.setMediaItems(mediaItems, 0)
        }
    }

    // ── Folder Explorer: offload heavy filtering to background thread ──

    data class FolderContents(
        val subDirs: List<String> = emptyList(),
        val currentFiles: List<LocalMediaItem> = emptyList()
    )

    private val _currentFolderPath = MutableStateFlow(
        Environment.getExternalStorageDirectory().absolutePath
    )
    val currentFolderPath: StateFlow<String> = _currentFolderPath.asStateFlow()

    private val _folderContents = MutableStateFlow(FolderContents())
    val folderContents: StateFlow<FolderContents> = _folderContents.asStateFlow()

    fun navigateToFolder(path: String) {
        _currentFolderPath.value = path
        computeFolderContents(path)
    }

    fun navigateUp() {
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val current = _currentFolderPath.value
        if (current != rootPath) {
            val lastSlash = current.lastIndexOf('/')
            val parent = if (lastSlash > 0) current.substring(0, lastSlash) else rootPath
            navigateToFolder(parent)
        }
    }

    /**
     * Compute folder contents on a background thread to avoid ANR
     * from iterating large media lists during Compose recomposition.
     */
    fun computeFolderContents(path: String = _currentFolderPath.value) {
        viewModelScope.launch(Dispatchers.Default) {
            val dirs = mutableSetOf<String>()
            val files = mutableListOf<LocalMediaItem>()
            
            val processItem = { item: LocalMediaItem ->
                val filePath = item.filePath
                if (filePath.startsWith(path) && filePath.length > path.length) {
                    val relativePath = filePath.substring(path.length).removePrefix("/")
                    val slashIndex = relativePath.indexOf('/')
                    if (slashIndex == -1) {
                        files.add(item)
                    } else {
                        val subDirName = relativePath.substring(0, slashIndex)
                        val fullSubDirPath =
                            if (path.endsWith("/")) path + subDirName else "$path/$subDirName"
                        dirs.add(fullSubDirPath)
                    }
                }
            }

            _audioList.value.forEach(processItem)
            _videoList.value.forEach(processItem)

            _folderContents.value = FolderContents(
                subDirs = dirs.toList().sorted(),
                currentFiles = files.sortedBy { it.title }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // DO NOT release singleton controllers here.
        // PlayerController and VideoPlayerController are @Singleton scoped and
        // outlive the ViewModel. Releasing them here would leave them in a broken
        // state (null mediaController / exoPlayer) with no way to re-initialize,
        // since their init{} block only runs once.
    }
}
