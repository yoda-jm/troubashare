package com.troubashare.data.file

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

class FileManager(private val context: Context) {
    
    private val appDir = File(context.filesDir, "troubashare")
    
    init {
        // Ensure app directory exists
        if (!appDir.exists()) {
            appDir.mkdirs()
        }
    }
    
    suspend fun saveFile(
        groupId: String,
        songId: String,
        memberId: String,
        fileName: String,
        inputStream: InputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentDir = getContentDirectory(groupId, songId, memberId)
            contentDir.mkdirs()
            
            val file = File(contentDir, fileName)
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteFile(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists() && file.delete()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("File not found or could not be deleted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getContentDirectory(groupId: String, songId: String, memberId: String): File {
        return File(appDir, "groups/$groupId/songs/$songId/members/$memberId")
    }
    
    fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
    
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0L
    }
    
    // Get file extension from filename
    fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
    
    // Check if file is supported PDF or image format
    fun isSupportedFileType(fileName: String): Boolean {
        val extension = getFileExtension(fileName)
        return extension in listOf("pdf", "jpg", "jpeg", "png", "gif", "webp")
    }
    
    // Check if file is PDF
    fun isPdfFile(fileName: String): Boolean {
        return getFileExtension(fileName) == "pdf"
    }
    
    // Check if file is image
    fun isImageFile(fileName: String): Boolean {
        val extension = getFileExtension(fileName)
        return extension in listOf("jpg", "jpeg", "png", "gif", "webp")
    }
}