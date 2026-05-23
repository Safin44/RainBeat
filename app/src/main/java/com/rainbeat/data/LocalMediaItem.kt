package com.rainbeat.data

import android.net.Uri

data class LocalMediaItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val duration: Long,
    val isVideo: Boolean,
    val albumArtUri: Uri? = null,
    val filePath: String = ""
)
