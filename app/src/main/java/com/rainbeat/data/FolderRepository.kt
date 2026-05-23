package com.rainbeat.data

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepository @Inject constructor() {
    
    fun listFolders(path: String): List<File> {
        val root = File(path)
        if (!root.exists() || !root.isDirectory) {
            return emptyList()
        }
        val files = root.listFiles() ?: return emptyList()
        return files.filter { it.isDirectory || it.isMediaFile() }.sortedBy { !it.isDirectory }
    }

    private fun File.isMediaFile(): Boolean {
        val ext = extension.lowercase()
        return ext in listOf("mp3", "wav", "ogg", "mp4", "mkv", "webm", "m4a", "flac")
    }
}
