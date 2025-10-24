package com.troubashare.data.cloud

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import com.troubashare.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume

class GoogleDriveProvider(
    private val context: Context
) : CloudStorageProvider {
    
    companion object {
        private const val TAG = "GoogleDriveProvider"
        private const val APPLICATION_NAME = "TroubaShare"
    }
    
    private var driveService: Drive? = null
    private var googleSignInClient: GoogleSignInClient? = null
    private var currentAccount: CloudAccountInfo? = null
    
    init {
        initializeGoogleSignIn()
    }
    
    private fun initializeGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE))
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    override suspend fun authenticate(): Result<CloudAccountInfo> = withContext(Dispatchers.IO) {
        try {
            // Check if already signed in
            Log.d(TAG, "Context type: ${context::class.simpleName}")
            val existingAccount = GoogleSignIn.getLastSignedInAccount(context)
            Log.d(TAG, "Checking authentication - existing account: ${existingAccount?.email}")
            
            if (existingAccount != null) {
                val hasPermissions = GoogleSignIn.hasPermissions(existingAccount, Scope(DriveScopes.DRIVE))
                Log.d(TAG, "Account has Drive permissions: $hasPermissions")
                
                if (hasPermissions) {
                    Log.d(TAG, "Initializing Drive service for: ${existingAccount.email}")
                    return@withContext initializeDriveService(existingAccount)
                }
            }
            
            // Need to sign in - this would typically trigger sign-in flow in UI
            Log.d(TAG, "Authentication required - no valid account found")
            Result.failure(CloudException.AuthenticationRequired("User needs to sign in to Google Drive"))
            
        } catch (e: Exception) {
            Log.e(TAG, "Authentication failed", e)
            Result.failure(CloudException.NetworkError("Authentication failed: ${e.message}", e))
        }
    }
    
    suspend fun handleSignInResult(account: GoogleSignInAccount): Result<CloudAccountInfo> = withContext(Dispatchers.IO) {
        try {
            initializeDriveService(account)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Drive service", e)
            Result.failure(CloudException.UnknownError("Failed to initialize Drive service", e))
        }
    }
    
    private suspend fun initializeDriveService(account: GoogleSignInAccount): Result<CloudAccountInfo> {
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                context, 
                listOf(DriveScopes.DRIVE)
            ).apply {
                selectedAccount = account.account
            }
            
            driveService = Drive.Builder(
                NetHttpTransport(),
                GsonFactory(),
                credential
            )
                .setApplicationName(APPLICATION_NAME)
                .build()
            
            // Get account info
            val accountInfo = CloudAccountInfo(
                accountId = account.id ?: "",
                accountName = account.displayName ?: "",
                email = account.email ?: ""
            )
            
            currentAccount = accountInfo
            
            return Result.success(accountInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Drive service", e)
            return Result.failure(CloudException.UnknownError("Failed to initialize Drive service", e))
        }
    }
    
    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            googleSignInClient?.signOut()
            driveService = null
            currentAccount = null
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CloudException.UnknownError("Sign out failed", e))
        }
    }
    
    override suspend fun isAuthenticated(): Boolean {
        return driveService != null && currentAccount != null
    }
    
    override suspend fun getAccountInfo(): CloudAccountInfo? {
        return currentAccount
    }
    
    override suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        parentFolderId: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val localFile = java.io.File(localPath)
            if (!localFile.exists()) {
                return@withContext Result.failure(
                    CloudException.FileNotFound("Local file not found: $localPath")
                )
            }
            
            val fileMetadata = File().apply {
                name = remotePath.substringAfterLast("/")
                if (parentFolderId != null) {
                    parents = listOf(parentFolderId)
                }
            }
            
            val mediaContent = com.google.api.client.http.FileContent(
                getMimeType(localPath), 
                localFile
            )
            
            val result = drive.files().create(fileMetadata, mediaContent)
                .setFields("id,name,size,modifiedTime,md5Checksum,mimeType")
                .execute()
            
            val cloudFile = CloudFile(
                id = result.id,
                name = result.name,
                path = remotePath,
                size = result.getSize() ?: 0L,
                mimeType = result.mimeType ?: "application/octet-stream",
                modifiedTime = result.modifiedTime?.toStringRfc3339() ?: "",
                checksum = result.md5Checksum
            )
            
            Result.success(cloudFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(CloudException.NetworkError("Upload failed: ${e.message}", e))
        }
    }
    
    /**
     * Upload string content directly as a file
     */
    suspend fun uploadFileContent(
        content: String,
        fileName: String,
        parentFolderId: String?,
        mimeType: String = "text/plain"
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val fileMetadata = File().apply {
                name = fileName
                if (parentFolderId != null) {
                    parents = listOf(parentFolderId)
                }
            }
            
            val mediaContent = com.google.api.client.http.ByteArrayContent(
                mimeType,
                content.toByteArray(Charsets.UTF_8)
            )
            
            val result = drive.files().create(fileMetadata, mediaContent)
                .setFields("id,name,size,modifiedTime,md5Checksum,mimeType")
                .execute()
            
            val cloudFile = CloudFile(
                id = result.id,
                name = result.name,
                path = fileName,
                size = result.getSize() ?: 0L,
                mimeType = result.mimeType ?: mimeType,
                modifiedTime = result.modifiedTime?.toStringRfc3339() ?: "",
                checksum = result.md5Checksum
            )
            
            Result.success(cloudFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Content upload failed", e)
            Result.failure(CloudException.NetworkError("Content upload failed: ${e.message}", e))
        }
    }
    
    /**
     * Download file content as string
     */
    suspend fun downloadFileContent(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val outputStream = java.io.ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            
            val content = outputStream.toString("UTF-8")
            Result.success(content)
            
        } catch (e: Exception) {
            Log.e(TAG, "Content download failed", e)
            Result.failure(CloudException.NetworkError("Content download failed: ${e.message}", e))
        }
    }
    
    override suspend fun downloadFile(
        cloudFile: CloudFile,
        localPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val outputStream = FileOutputStream(localPath)
            drive.files().get(cloudFile.id).executeMediaAndDownloadTo(outputStream)
            outputStream.close()
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(CloudException.NetworkError("Download failed: ${e.message}", e))
        }
    }
    
    override suspend fun deleteFile(cloudFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            drive.files().delete(cloudFileId).execute()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Delete failed", e)
            Result.failure(CloudException.NetworkError("Delete failed: ${e.message}", e))
        }
    }
    
    override suspend fun listFiles(
        folderId: String?,
        nameContains: String?
    ): Result<List<CloudFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val query = buildString {
                if (folderId != null) {
                    append("'$folderId' in parents")
                }
                if (nameContains != null) {
                    if (isNotEmpty()) append(" and ")
                    append("name contains '$nameContains'")
                }
                if (isEmpty()) {
                    append("trashed = false")
                } else {
                    append(" and trashed = false")
                }
            }
            
            val result = drive.files().list()
                .setQ(query)
                .setFields("files(id,name,size,modifiedTime,md5Checksum,mimeType,webViewLink)")
                .execute()
            
            val cloudFiles = result.files.map { file ->
                CloudFileInfo(
                    id = file.id,
                    name = file.name,
                    size = file.getSize() ?: 0L,
                    modifiedTime = file.modifiedTime?.toStringRfc3339() ?: "",
                    checksum = file.md5Checksum,
                    downloadUrl = file.webViewLink
                )
            }
            
            Result.success(cloudFiles)
            
        } catch (e: Exception) {
            Log.e(TAG, "List files failed", e)
            Result.failure(CloudException.NetworkError("List files failed: ${e.message}", e))
        }
    }
    
    override suspend fun createFolder(
        name: String,
        parentFolderId: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val fileMetadata = File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                if (parentFolderId != null) {
                    parents = listOf(parentFolderId)
                }
            }
            
            val result = drive.files().create(fileMetadata)
                .setFields("id,name,size,modifiedTime,mimeType")
                .execute()
            
            val cloudFile = CloudFile(
                id = result.id,
                name = result.name,
                path = "/$name",
                size = 0L,
                mimeType = result.mimeType,
                modifiedTime = result.modifiedTime?.toStringRfc3339() ?: ""
            )
            
            Result.success(cloudFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Create folder failed", e)
            Result.failure(CloudException.NetworkError("Create folder failed: ${e.message}", e))
        }
    }
    
    override suspend fun deleteFolder(folderId: String): Result<Unit> {
        return deleteFile(folderId) // Same operation in Google Drive
    }
    
    override suspend fun getFileInfo(cloudFileId: String): Result<CloudFileInfo> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val file = drive.files().get(cloudFileId)
                .setFields("id,name,size,modifiedTime,md5Checksum,mimeType,webViewLink")
                .execute()
            
            val cloudFileInfo = CloudFileInfo(
                id = file.id,
                name = file.name,
                size = file.getSize() ?: 0L,
                modifiedTime = file.modifiedTime?.toStringRfc3339() ?: "",
                checksum = file.md5Checksum,
                downloadUrl = file.webViewLink
            )
            
            Result.success(cloudFileInfo)
            
        } catch (e: Exception) {
            Log.e(TAG, "Get file info failed", e)
            Result.failure(CloudException.NetworkError("Get file info failed: ${e.message}", e))
        }
    }
    
    override suspend fun updateFileMetadata(
        cloudFileId: String,
        name: String?,
        description: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val fileMetadata = File().apply {
                if (name != null) this.name = name
                if (description != null) this.description = description
            }
            
            val result = drive.files().update(cloudFileId, fileMetadata)
                .setFields("id,name,size,modifiedTime,md5Checksum,mimeType")
                .execute()
            
            val cloudFile = CloudFile(
                id = result.id,
                name = result.name,
                path = "/${result.name}",
                size = result.getSize() ?: 0L,
                mimeType = result.mimeType,
                modifiedTime = result.modifiedTime?.toStringRfc3339() ?: "",
                checksum = result.md5Checksum
            )
            
            Result.success(cloudFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Update metadata failed", e)
            Result.failure(CloudException.NetworkError("Update metadata failed: ${e.message}", e))
        }
    }
    
    override suspend fun shareFolder(folderId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val permission = Permission().apply {
                role = "reader"
                type = "anyone"
            }
            
            drive.permissions().create(folderId, permission).execute()
            
            val file = drive.files().get(folderId)
                .setFields("webViewLink")
                .execute()
            
            Result.success(file.webViewLink ?: "")
            
        } catch (e: Exception) {
            Log.e(TAG, "Share folder failed", e)
            Result.failure(CloudException.NetworkError("Share folder failed: ${e.message}", e))
        }
    }
    
    override suspend fun addFolderPermission(
        folderId: String,
        email: String,
        permission: CloudPermission
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val drivePermission = Permission().apply {
                type = "user"
                role = when (permission) {
                    CloudPermission.OWNER -> "owner"
                    CloudPermission.EDITOR -> "writer"
                    CloudPermission.VIEWER -> "reader"
                    CloudPermission.COMMENTER -> "commenter"
                }
                emailAddress = email
            }
            
            drive.permissions().create(folderId, drivePermission).execute()
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Add permission failed", e)
            Result.failure(CloudException.NetworkError("Add permission failed: ${e.message}", e))
        }
    }
    
    override suspend fun uploadStream(
        inputStream: InputStream,
        remotePath: String,
        mimeType: String,
        parentFolderId: String?
    ): Result<CloudFile> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val fileMetadata = File().apply {
                name = remotePath.substringAfterLast("/")
                if (parentFolderId != null) {
                    parents = listOf(parentFolderId)
                }
            }
            
            val mediaContent = com.google.api.client.http.InputStreamContent(mimeType, inputStream)
            
            val result = drive.files().create(fileMetadata, mediaContent)
                .setFields("id,name,size,modifiedTime,md5Checksum,mimeType")
                .execute()
            
            val cloudFile = CloudFile(
                id = result.id,
                name = result.name,
                path = remotePath,
                size = result.getSize() ?: 0L,
                mimeType = result.mimeType,
                modifiedTime = result.modifiedTime?.toStringRfc3339() ?: "",
                checksum = result.md5Checksum
            )
            
            Result.success(cloudFile)
            
        } catch (e: Exception) {
            Log.e(TAG, "Upload stream failed", e)
            Result.failure(CloudException.NetworkError("Upload stream failed: ${e.message}", e))
        }
    }
    
    override suspend fun downloadStream(cloudFileId: String): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            val drive = driveService ?: return@withContext Result.failure(
                CloudException.AuthenticationRequired()
            )
            
            val inputStream = drive.files().get(cloudFileId).executeMediaAsInputStream()
            Result.success(inputStream)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download stream failed", e)
            Result.failure(CloudException.NetworkError("Download stream failed: ${e.message}", e))
        }
    }
    
    override suspend fun batchUpload(
        files: List<Pair<String, String>>,
        parentFolderId: String?
    ): Result<List<CloudFile>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<CloudFile>()
            
            files.forEach { (localPath, remotePath) ->
                val result = uploadFile(localPath, remotePath, parentFolderId)
                result.fold(
                    onSuccess = { results.add(it) },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }
            
            Result.success(results)
            
        } catch (e: Exception) {
            Log.e(TAG, "Batch upload failed", e)
            Result.failure(CloudException.NetworkError("Batch upload failed: ${e.message}", e))
        }
    }
    
    override suspend fun batchDownload(
        files: List<Pair<CloudFile, String>>
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<String>()
            
            files.forEach { (cloudFile, localPath) ->
                val result = downloadFile(cloudFile, localPath)
                result.fold(
                    onSuccess = { results.add(localPath) },
                    onFailure = { return@withContext Result.failure(it) }
                )
            }
            
            Result.success(results)
            
        } catch (e: Exception) {
            Log.e(TAG, "Batch download failed", e)
            Result.failure(CloudException.NetworkError("Batch download failed: ${e.message}", e))
        }
    }
    
    private fun getMimeType(filePath: String): String {
        return when (filePath.substringAfterLast(".").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "json" -> "application/json"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}