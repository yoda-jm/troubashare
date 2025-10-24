package com.troubashare.data.cloud

import com.troubashare.domain.model.*
import java.io.InputStream

/**
 * Abstract interface for cloud storage providers (Google Drive, Dropbox, etc.)
 */
interface CloudStorageProvider {
    
    /**
     * Authentication and account management
     */
    suspend fun authenticate(): Result<CloudAccountInfo>
    suspend fun signOut(): Result<Unit>
    suspend fun isAuthenticated(): Boolean
    suspend fun getAccountInfo(): CloudAccountInfo?
    
    /**
     * File operations
     */
    suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        parentFolderId: String? = null
    ): Result<CloudFile>
    
    suspend fun downloadFile(
        cloudFile: CloudFile,
        localPath: String
    ): Result<Unit>
    
    suspend fun deleteFile(cloudFileId: String): Result<Unit>
    
    suspend fun listFiles(
        folderId: String? = null,
        nameContains: String? = null
    ): Result<List<CloudFileInfo>>
    
    /**
     * Folder operations
     */
    suspend fun createFolder(
        name: String,
        parentFolderId: String? = null
    ): Result<CloudFile>
    
    suspend fun deleteFolder(folderId: String): Result<Unit>
    
    /**
     * Metadata operations
     */
    suspend fun getFileInfo(cloudFileId: String): Result<CloudFileInfo>
    suspend fun updateFileMetadata(
        cloudFileId: String,
        name: String? = null,
        description: String? = null
    ): Result<CloudFile>
    
    /**
     * Sharing operations
     */
    suspend fun shareFolder(folderId: String): Result<String> // Returns shareable link
    suspend fun addFolderPermission(
        folderId: String,
        email: String,
        permission: CloudPermission
    ): Result<Unit>
    
    /**
     * Streaming operations for large files
     */
    suspend fun uploadStream(
        inputStream: InputStream,
        remotePath: String,
        mimeType: String,
        parentFolderId: String? = null
    ): Result<CloudFile>
    
    suspend fun downloadStream(cloudFileId: String): Result<InputStream>
    
    /**
     * Batch operations
     */
    suspend fun batchUpload(
        files: List<Pair<String, String>>, // localPath to remotePath
        parentFolderId: String? = null
    ): Result<List<CloudFile>>
    
    suspend fun batchDownload(
        files: List<Pair<CloudFile, String>> // cloudFile to localPath
    ): Result<List<String>>
}

enum class CloudPermission {
    OWNER,
    EDITOR,
    VIEWER,
    COMMENTER
}

/**
 * Result wrapper for cloud operations
 */
sealed class CloudResult<T> {
    data class Success<T>(val data: T) : CloudResult<T>()
    data class Error<T>(val exception: CloudException) : CloudResult<T>()
}

/**
 * Cloud-specific exceptions
 */
sealed class CloudException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class AuthenticationRequired(message: String = "Authentication required") : CloudException(message)
    class NetworkError(message: String, cause: Throwable? = null) : CloudException(message, cause)
    class QuotaExceeded(message: String = "Storage quota exceeded") : CloudException(message)
    class FileNotFound(message: String) : CloudException(message)
    class PermissionDenied(message: String) : CloudException(message)
    class InvalidOperation(message: String) : CloudException(message)
    class UnknownError(message: String, cause: Throwable? = null) : CloudException(message, cause)
}

/**
 * Upload progress callback
 */
interface UploadProgressCallback {
    fun onProgress(bytesUploaded: Long, totalBytes: Long)
    fun onComplete(cloudFile: CloudFile)
    fun onError(exception: CloudException)
}

/**
 * Download progress callback
 */
interface DownloadProgressCallback {
    fun onProgress(bytesDownloaded: Long, totalBytes: Long)
    fun onComplete(localPath: String)
    fun onError(exception: CloudException)
}