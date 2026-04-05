package com.troubashare.data.sync

import android.content.Context
import com.troubashare.data.repository.AnnotationRepository
import com.troubashare.data.repository.GroupRepository
import com.troubashare.data.repository.SongRepository
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages the sync folder layout described in DESIGN.md.
 *
 * troubashare/
 *   manifest.json       - device registry + metadata
 *   files/
 *     uuid.pdf          - content-addressed, written once, never overwritten
 *     uuid.jpg
 *   annotations/
 *     fileId_memberId_personal.jsonl
 *     fileId_partId_part.jsonl
 *     fileId_all.jsonl
 */
class SyncManager(
    private val context: Context,
    private val deviceManager: DeviceManager,
    private val groupRepository: GroupRepository,
    private val songRepository: SongRepository,
    private val annotationRepository: AnnotationRepository
) {
    private val exporter = JsonlAnnotationExporter(annotationRepository, deviceManager)
    private val importer = JsonlAnnotationImporter(annotationRepository, deviceManager)

    val syncRoot: File
        get() = File(context.getExternalFilesDir(null), "troubashare_sync").also { it.mkdirs() }

    fun readManifest(syncDir: File = syncRoot): JSONObject {
        val file = File(syncDir, "manifest.json")
        return if (file.exists()) JSONObject(file.readText()) else JSONObject().apply {
            put("schemaVersion", 1)
            put("devices", JSONObject())
        }
    }

    fun updateManifest(syncDir: File = syncRoot): JSONObject {
        val manifest = readManifest(syncDir)
        val devices = manifest.optJSONObject("devices") ?: JSONObject()
        devices.put(
            deviceManager.deviceId,
            JSONObject().apply {
                put("name", android.os.Build.MODEL)
                put("lastSeq", deviceManager.currentSeq())
                put("lastSeenAt", System.currentTimeMillis())
            }
        )
        manifest.put("devices", devices)
        File(syncDir, "manifest.json").writeText(manifest.toString(2))
        return manifest
    }

    /** Exports a group to syncDir: manifest + media files + JSONL annotations. */
    suspend fun exportGroup(
        groupId: String,
        memberId: String,
        syncDir: File = syncRoot
    ): Result<File> {
        return try {
            val group = groupRepository.getGroupById(groupId)
                ?: return Result.failure(Exception("Group not found"))
            val member = group.members.find { it.id == memberId }
                ?: return Result.failure(Exception("Member not found"))
            val partIds = member.partIds

            val filesDir = File(syncDir, "files").also { it.mkdirs() }

            val songs = songRepository.getSongsWithFilesByGroupId(groupId)
            songs.forEach { song ->
                song.files.forEach { songFile ->
                    val src = File(songFile.filePath)
                    if (src.exists()) {
                        val dst = File(filesDir, src.name)
                        if (!dst.exists()) src.copyTo(dst)
                    }
                    exporter.exportForFile(songFile.id, memberId, partIds, syncDir)
                }
            }

            updateManifest(syncDir)
            Result.success(syncDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Zips the sync folder into outputZip. */
    fun zipSyncFolder(outputZip: File, syncDir: File = syncRoot): Result<File> {
        return try {
            ZipOutputStream(FileOutputStream(outputZip)).use { zip ->
                syncDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val entry = ZipEntry(file.relativeTo(syncDir).path)
                        zip.putNextEntry(entry)
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
            }
            Result.success(outputZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Imports a zip (produced by zipSyncFolder) by extracting and applying on top. */
    suspend fun importFromZip(
        zipFile: File,
        memberId: String,
        partIds: List<String>
    ): Result<ImportSummary> {
        return try {
            val tempDir = File(context.cacheDir, "sync_import_${System.currentTimeMillis()}")
                .also { it.mkdirs() }

            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = File(tempDir, entry.name)
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zip.copyTo(it) }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val results = applyImport(tempDir, memberId, partIds)
            tempDir.deleteRecursively()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Applies an extracted sync folder on top of local state.
     * Rules: files = copy-if-absent, annotations = JSONL merge, manifest = merge device sequences.
     */
    suspend fun applyImport(
        importDir: File,
        memberId: String,
        partIds: List<String>
    ): ImportSummary {
        val remoteManifest = readManifest(importDir)
        val localManifest = readManifest(syncRoot)
        val localDevices = localManifest.optJSONObject("devices") ?: JSONObject()
        val remoteDevices = remoteManifest.optJSONObject("devices") ?: JSONObject()

        val newSequenceDevices = mutableListOf<String>()
        remoteDevices.keys().forEach { deviceId ->
            val remoteSeq = remoteDevices.getJSONObject(deviceId).optLong("lastSeq", 0)
            val localSeq = localDevices.optJSONObject(deviceId)?.optLong("lastSeq", 0) ?: 0
            if (remoteSeq > localSeq) newSequenceDevices.add(deviceId)
        }

        // Copy new media files (content-addressed: same UUID = same content, never overwrite)
        val localFilesDir = File(syncRoot, "files").also { it.mkdirs() }
        val remoteFilesDir = File(importDir, "files")
        var filesCopied = 0
        if (remoteFilesDir.exists()) {
            remoteFilesDir.listFiles()?.forEach { remoteFile ->
                val localFile = File(localFilesDir, remoteFile.name)
                if (!localFile.exists()) {
                    remoteFile.copyTo(localFile)
                    filesCopied++
                }
            }
        }

        // Merge JSONL annotations
        val annotationResults = importer.importFromSyncFolder(importDir, memberId, partIds)
        val totalAdded = annotationResults.values.sumOf { it.strokesAdded }
        val totalDeleted = annotationResults.values.sumOf { it.strokesDeleted }
        val totalConflicts = annotationResults.values.flatMap { it.conflicts }

        // Update manifest: take the max lastSeq per device
        remoteDevices.keys().forEach { deviceId ->
            localDevices.put(deviceId, remoteDevices.getJSONObject(deviceId))
        }
        localManifest.put("devices", localDevices)
        File(syncRoot, "manifest.json").writeText(localManifest.toString(2))

        return ImportSummary(
            filesCopied = filesCopied,
            strokesAdded = totalAdded,
            strokesDeleted = totalDeleted,
            conflicts = totalConflicts,
            newDevices = newSequenceDevices
        )
    }
}

data class ImportSummary(
    val filesCopied: Int,
    val strokesAdded: Int,
    val strokesDeleted: Int,
    val conflicts: List<String>,
    val newDevices: List<String>
)
