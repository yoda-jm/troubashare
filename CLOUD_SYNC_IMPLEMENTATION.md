# Cloud Sync Implementation Summary

## Overview

This document summarizes the cloud synchronization implementation for TroubaShare, enabling collaborative music sheet management across multiple devices using Google Drive as the backend.

## Architecture

### Core Components

```
CloudSyncManager (Singleton)
├── GoogleDriveProvider - Drive API integration
├── ChangeTracker - Local change detection
├── ConflictResolver - Conflict detection & resolution
└── Repositories - Data persistence layer
```

### Data Flow

```
Local Change → ChangeTracker → ChangeLog
                                    ↓
                              Upload to Drive
                                    ↓
                            Remote ChangeLog
                                    ↓
                         Other Device Downloads
                                    ↓
                          ConflictResolver checks
                                    ↓
                         Apply to Local Database
```

## Implementation Details

### 1. CloudSyncManager.kt

**Location**: `app/src/main/java/com/troubashare/data/cloud/CloudSyncManager.kt`

**Responsibilities**:
- Orchestrates all sync operations
- Manages Google Drive folder structure
- Uploads/downloads PDFs, annotations, setlists
- Coordinates changelog synchronization
- Applies remote changes to local database

**Key Methods**:

```kotlin
// Main sync entry point
suspend fun syncGroup(groupId: String): Result<Unit>

// Entity-specific uploads
suspend fun uploadSongWithRetry(song: Song): Result<Unit>
suspend fun uploadAnnotationForSong(annotation: Annotation): Result<Unit>
suspend fun uploadSetlist(setlist: Setlist): Result<Unit>

// Change application
private suspend fun applySongChange(change: ChangeLogEntry)
private suspend fun applyAnnotationChange(change: ChangeLogEntry)
private suspend fun applySetlistChange(change: ChangeLogEntry)
private suspend fun applyMemberChange(change: ChangeLogEntry)
private suspend fun applyGroupChange(change: ChangeLogEntry)

// File deduplication
private fun calculateMD5(file: File): String
private suspend fun needsUpload(localFile: File, remoteName: String, parentFolderId: String): Boolean

// Retry logic
private suspend fun <T> retryWithBackoff(maxAttempts: Int, initialDelay: Long, operation: String, block: suspend () -> Result<T>): Result<T>
```

**Configuration**:
```kotlin
companion object {
    private const val MAX_RETRY_ATTEMPTS = 3
    private const val INITIAL_RETRY_DELAY_MS = 1000L
}
```

### 2. ChangeTracker.kt

**Location**: `app/src/main/java/com/troubashare/data/cloud/ChangeTracker.kt`

**Responsibilities**:
- Monitors local database changes
- Creates changelog entries for all modifications
- Calculates entity checksums for conflict detection
- Stores changes in ChangeLogDao

**Key Features**:
- Automatic change detection via repository layer
- Timestamp-based ordering
- Device ID tracking for multi-device scenarios
- Metadata extraction for context

**Change Types Tracked**:
- Songs (CREATE, UPDATE, DELETE)
- Annotations (CREATE, UPDATE, DELETE)
- Setlists (CREATE, UPDATE, DELETE)
- Groups (CREATE, UPDATE, DELETE)
- Members (CREATE, DELETE)

**Checksum Calculation**:
```kotlin
private fun calculateChecksum(entity: Any): String {
    return entity.hashCode().toString()
}
```

### 3. ConflictResolver.kt

**Location**: `app/src/main/java/com/troubashare/data/cloud/ConflictResolver.kt`

**Responsibilities**:
- Detects conflicts between local and remote changes
- Auto-resolves conflicts where possible
- Provides UI hooks for manual conflict resolution
- Handles annotation merging across layers

**Conflict Types**:

1. **DELETE_MODIFY**: One device deletes while another modifies
   - Resolution: Manual (user choice)

2. **SIMULTANEOUS_EDIT**: Both devices edit same entity within 5-minute window
   - Resolution: Last-writer-wins (newer timestamp)

3. **ANNOTATION_OVERLAP**: Both devices annotate same page
   - Resolution: Auto-merge strokes from both devices

4. **STRUCTURE_CHANGE**: Group/member modifications
   - Resolution: Manual (complex changes)

**Key Methods**:

```kotlin
// Detect conflicts between changesets
suspend fun detectConflicts(groupId: String, localChanges: List<ChangeLogEntry>, remoteChanges: List<ChangeLogEntry>): List<SyncConflict>

// Analyze individual change pair
private suspend fun analyzeConflict(localChange: ChangeLogEntry, remoteChange: ChangeLogEntry): SyncConflict?

// Resolve with specific action
suspend fun resolveConflict(conflict: SyncConflict, resolution: ResolutionAction): Result<Unit>

// Auto-resolve where possible
suspend fun autoResolveConflicts(conflicts: List<SyncConflict>): List<SyncConflict>
```

**Resolution Actions**:
```kotlin
enum class ResolutionAction {
    KEEP_LOCAL,           // Keep local version
    ACCEPT_REMOTE,        // Accept remote version
    MERGE_ANNOTATIONS,    // Merge annotation strokes
    LAYER_SEPARATE,       // Create separate annotation layers
    MANUAL_MERGE          // User manual merge
}
```

**Annotation Merging Algorithm**:

1. Find all annotation layers for same file/member/page
2. Collect strokes from all layers
3. Deduplicate by stroke ID
4. Merge into primary (oldest) annotation
5. Copy stroke points correctly
6. Delete duplicate layers
7. Update timestamp

```kotlin
// Key code from mergeAnnotations():
val allAnnotationsForPage = database.annotationDao()
    .getAnnotationsByFileAndMemberAndPage(fileId, memberId, pageNumber)

val allStrokes = allAnnotationsForPage.flatMap { annotation ->
    database.annotationDao().getStrokesByAnnotation(annotation.id)
}

val uniqueStrokes = allStrokes.distinctBy { it.id }

val primaryAnnotation = allAnnotationsForPage.minByOrNull { it.createdAt }
// ... merge into primary and cleanup
```

### 4. GoogleDriveProvider.kt

**Location**: `app/src/main/java/com/troubashare/data/cloud/GoogleDriveProvider.kt`

**Responsibilities**:
- Google Drive API authentication
- File upload/download operations
- Folder management
- Checksum retrieval

**Key Methods**:
```kotlin
suspend fun uploadFile(localPath: String, remotePath: String, parentFolderId: String): Result<DriveFile>
suspend fun downloadFile(fileId: String, localPath: String): Result<File>
suspend fun listFiles(folderId: String): Result<List<DriveFile>>
suspend fun createFolder(name: String, parentId: String): Result<String>
```

**Authentication**:
- Uses Google Sign-In for Android
- OAuth 2.0 with Drive API scope
- Manages access token lifecycle

### 5. Data Models

**Location**: `app/src/main/java/com/troubashare/domain/model/CloudModels.kt`

**Key Models**:

```kotlin
// Changelog entry for tracking changes
data class ChangeLogEntry(
    val changeId: String,
    val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val entityType: EntityType,
    val entityId: String,
    val entityName: String,
    val changeType: ChangeType,
    val checksum: String,
    val description: String,
    val metadata: Map<String, String> = emptyMap()
)

// Sync conflict representation
data class SyncConflict(
    val conflictId: String,
    val entityType: EntityType,
    val entityId: String,
    val entityName: String,
    val localVersion: ConflictVersion,
    val remoteVersion: ConflictVersion,
    val conflictType: ConflictType,
    val canAutoResolve: Boolean
)

// Version details for conflict
data class ConflictVersion(
    val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val authorName: String,
    val checksum: String,
    val description: String
)

// Group manifest structure
data class GroupManifest(
    val version: Int,
    val groupId: String,
    val name: String,
    val created: Long,
    val updated: Long,
    val memberCount: Int,
    val members: List<ManifestMember>
)
```

**Enums**:

```kotlin
enum class EntityType {
    SONG, ANNOTATION, SETLIST, GROUP, MEMBER
}

enum class ChangeType {
    CREATE, UPDATE, DELETE
}

enum class ConflictType {
    DELETE_MODIFY,
    SIMULTANEOUS_EDIT,
    ANNOTATION_OVERLAP,
    STRUCTURE_CHANGE
}
```

### 6. Database Entities

**ChangeLogEntity**:
```kotlin
@Entity(tableName = "change_log")
data class ChangeLogEntity(
    @PrimaryKey val changeId: String,
    val timestamp: Long,
    val deviceId: String,
    val deviceName: String,
    val entityType: String,
    val entityId: String,
    val entityName: String,
    val changeType: String,
    val checksum: String,
    val description: String,
    val synced: Boolean = false,
    val metadata: String = "{}"
)
```

**ChangeLogDao**:
```kotlin
@Dao
interface ChangeLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ChangeLogEntity)

    @Query("SELECT * FROM change_log WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsyncedChanges(): List<ChangeLogEntity>

    @Query("UPDATE change_log SET synced = 1 WHERE changeId = :changeId")
    suspend fun markAsSynced(changeId: String)
}
```

## Google Drive Structure

```
/TroubaShare/
└── [Group Name]/
    ├── manifest.json                    # Group metadata & members
    ├── songs/
    │   ├── song1.pdf
    │   ├── song2.pdf
    │   └── ...
    ├── annotations/
    │   ├── {songFileId}/
    │   │   ├── {memberId}/
    │   │   │   ├── {annotationId}.json
    │   │   │   └── ...
    │   │   └── ...
    │   └── ...
    ├── setlists/
    │   ├── {setlistId}.json
    │   └── ...
    └── changelog/
        ├── {timestamp}_song_create.json
        ├── {timestamp}_annotation_update.json
        └── ...
```

### File Formats

**manifest.json**:
```json
{
  "version": 1,
  "groupId": "uuid",
  "name": "My Band",
  "created": 1640000000000,
  "updated": 1640100000000,
  "memberCount": 3,
  "members": [
    {
      "id": "uuid",
      "name": "John Doe",
      "role": "ADMIN",
      "joined": 1640000000000
    }
  ]
}
```

**changelog entry**:
```json
{
  "changeId": "uuid",
  "timestamp": 1640000000000,
  "deviceId": "device-uuid",
  "deviceName": "Pixel 6",
  "entityType": "SONG",
  "entityId": "song-uuid",
  "entityName": "Amazing Grace",
  "changeType": "CREATE",
  "checksum": "abc123",
  "description": "Added new song",
  "metadata": {
    "fileSize": "5242880",
    "mimeType": "application/pdf"
  }
}
```

**annotation.json**:
```json
{
  "id": "annotation-uuid",
  "fileId": "song-uuid",
  "memberId": "member-uuid",
  "pageNumber": 0,
  "createdAt": 1640000000000,
  "updatedAt": 1640100000000,
  "strokes": [
    {
      "id": "stroke-uuid",
      "tool": "PEN",
      "color": "#FF0000",
      "strokeWidth": 5.0,
      "points": [
        {"x": 0.1, "y": 0.2, "pressure": 1.0, "timestamp": 100},
        {"x": 0.15, "y": 0.25, "pressure": 0.9, "timestamp": 120}
      ]
    }
  ]
}
```

**setlist.json**:
```json
{
  "id": "setlist-uuid",
  "name": "Sunday Service",
  "groupId": "group-uuid",
  "createdAt": 1640000000000,
  "updatedAt": 1640100000000,
  "songs": [
    {
      "songId": "song-uuid-1",
      "order": 0
    },
    {
      "songId": "song-uuid-2",
      "order": 1
    }
  ]
}
```

## Sync Algorithm

### Full Sync Flow

```
1. START SYNC
   ├─ Authenticate with Google Drive
   ├─ Get/Create group folder
   └─ Download group manifest

2. DOWNLOAD REMOTE CHANGES
   ├─ List changelog files in Drive
   ├─ Download new changelog entries
   ├─ Parse and sort by timestamp
   └─ Store in remote changes list

3. COLLECT LOCAL CHANGES
   ├─ Query ChangeLogDao for unsynced
   ├─ Sort by timestamp
   └─ Store in local changes list

4. DETECT CONFLICTS
   ├─ Group by entity (entityType:entityId)
   ├─ Find overlapping modifications
   ├─ Analyze each conflict type
   └─ Build conflicts list

5. AUTO-RESOLVE CONFLICTS
   ├─ For ANNOTATION_OVERLAP: merge
   ├─ For SIMULTANEOUS_EDIT: last-writer-wins
   └─ Keep unresolvable for manual

6. APPLY REMOTE CHANGES
   ├─ For each remote change:
   │  ├─ Check if already applied
   │  ├─ Download entity data if needed
   │  ├─ Apply to local database
   │  └─ Mark as synced
   └─ Handle errors gracefully

7. UPLOAD LOCAL CHANGES
   ├─ For each unsynced local change:
   │  ├─ Check if needs upload (checksum)
   │  ├─ Upload entity to Drive
   │  ├─ Upload changelog entry
   │  ├─ Mark as synced
   │  └─ Retry on failure (exponential backoff)

8. UPDATE MANIFEST
   ├─ Update member list if changed
   ├─ Update timestamp
   └─ Upload to Drive

9. END SYNC
   └─ Emit sync completion event
```

### Conflict Resolution Flow

```
DETECT CONFLICT
   ├─ Same entity modified on both devices?
   └─ YES → Analyze conflict type

ANNOTATION_OVERLAP
   ├─ Same file, member, page?
   ├─ Both added strokes?
   └─ AUTO-RESOLVE: Merge strokes
       ├─ Collect from all layers
       ├─ Deduplicate by stroke ID
       ├─ Merge into primary layer
       └─ Delete duplicate layers

SIMULTANEOUS_EDIT (within 5-min window)
   ├─ Both updated same entity?
   ├─ Check if auto-resolvable type
   └─ AUTO-RESOLVE: Last-writer-wins
       ├─ Compare timestamps
       ├─ Keep newer version
       └─ Discard older version

DELETE_MODIFY
   ├─ One deleted, other modified?
   └─ MANUAL: User chooses
       ├─ Keep deleted (discard mods)
       └─ Keep modified (undelete)

STRUCTURE_CHANGE
   ├─ Group/member changes?
   └─ MANUAL: Complex resolution
```

## Key Features

### 1. File Deduplication

Uses MD5 checksums to avoid re-uploading unchanged files:

```kotlin
val localChecksum = calculateMD5(localFile)
val remoteChecksum = remoteFile.checksum

if (localChecksum == remoteChecksum) {
    // Skip upload - file unchanged
    return
}
```

**Benefits**:
- Reduces bandwidth usage
- Faster sync times
- Avoids unnecessary Drive API calls

### 2. Exponential Backoff Retry

Handles transient network failures gracefully:

```kotlin
suspend fun <T> retryWithBackoff(
    maxAttempts: Int = 3,
    initialDelay: Long = 1000L,
    operation: String,
    block: suspend () -> Result<T>
): Result<T> {
    var currentDelay = initialDelay
    repeat(maxAttempts) { attempt ->
        val result = block()
        if (result.isSuccess) return result

        delay(currentDelay)
        currentDelay *= 2  // 1s → 2s → 4s
    }
    return Result.failure(...)
}
```

**Retry Schedule**:
- Attempt 1: Immediate
- Attempt 2: After 1 second
- Attempt 3: After 2 seconds
- Attempt 4: Fail after 4 seconds (not attempted)

### 3. Annotation Merging

Sophisticated merge algorithm for collaborative annotations:

**Problem**: Two devices annotate the same page simultaneously

**Solution**:
1. Both upload their annotations as separate layers
2. Conflict detected during next sync
3. Auto-merge combines strokes from both layers
4. Duplicates removed by stroke ID
5. Result: Single layer with all strokes

**Implementation Highlights**:
- Stroke-level deduplication (not annotation-level)
- Preserves authorship via device tracking
- Handles arbitrary number of layers
- Maintains temporal ordering of strokes

### 4. Change Tracking

Automatic tracking of all database modifications:

**Integration Points**:
- Repository layer (after successful DB operations)
- Triggered on CREATE/UPDATE/DELETE
- Captures entity state at time of change
- Includes metadata for context

**Metadata Examples**:
- Song: fileSize, mimeType, duration
- Annotation: pageNumber, toolType, strokeCount
- Setlist: songCount, totalDuration

### 5. Offline Support

Queues changes when offline, syncs when online:

- Changes stored in local ChangeLog table
- `synced` flag tracks upload status
- Background WorkManager job for auto-sync
- Manual sync trigger in UI

## Performance Optimizations

### 1. Lazy Loading

- PDFs downloaded on-demand (not all at sync time)
- Annotations loaded per-page
- Thumbnail generation postponed

### 2. Batch Operations

- Upload multiple changelog entries together
- Bulk database inserts for downloaded changes
- Parallel file uploads where possible

### 3. Caching

- Group manifest cached locally
- Drive file listings cached (5-minute TTL)
- Checksum cache for recently synced files

### 4. Incremental Sync

- Only download changelog entries newer than last sync
- Only upload unsynced local changes
- Skip entities with matching checksums

## Security Considerations

### 1. Authentication

- OAuth 2.0 with Google Sign-In
- No password storage in app
- Token refresh handled automatically
- Per-device authorization

### 2. Data Privacy

- Files stored in user's personal Drive
- No server-side storage
- End-to-end via Google's infrastructure
- User controls sharing permissions

### 3. Conflict Resolution

- No data loss on conflicts
- All versions preserved in Drive
- User can manually review if needed
- Audit trail via changelog

## Testing

See `CLOUD_SYNC_TESTING.md` for comprehensive testing guide.

**Key Test Scenarios**:
- Multi-device sync
- Conflict detection and resolution
- Annotation merging
- Network failure handling
- Large file uploads
- Concurrent edits

## Future Enhancements

### Short-term

1. **Real-time Sync**: Firebase Realtime Database for instant updates
2. **Conflict UI**: User-friendly conflict resolution interface
3. **Sync Status**: Detailed progress indicators in UI
4. **Offline Queue**: Show pending changes to user

### Long-term

1. **Peer-to-Peer Sync**: Local network sync without internet
2. **Selective Sync**: Choose which songs to sync per device
3. **Version History**: Browse past versions of entities
4. **Compression**: Compress PDFs before upload
5. **Delta Sync**: Only upload changed pages of PDFs

## Troubleshooting

### Common Issues

**Sync Stuck**:
- Check network connectivity
- Verify Drive API quota not exceeded
- Clear app cache and retry
- Check logcat for specific errors

**Conflicts Not Resolving**:
- Verify timestamp synchronization across devices
- Check conflict type - may require manual resolution
- Review ConflictResolver logs for details

**Files Not Uploading**:
- Check Drive storage quota
- Verify file permissions
- Check file size limits (max 100MB)
- Review retry logs for failure reasons

**Annotations Missing**:
- Verify annotation layer not hidden in UI
- Check if merge logic ran correctly
- Look for layer duplication
- Review annotation file in Drive

## Code Locations

| Component | File Path |
|-----------|-----------|
| CloudSyncManager | `app/src/main/java/com/troubashare/data/cloud/CloudSyncManager.kt` |
| ChangeTracker | `app/src/main/java/com/troubashare/data/cloud/ChangeTracker.kt` |
| ConflictResolver | `app/src/main/java/com/troubashare/data/cloud/ConflictResolver.kt` |
| GoogleDriveProvider | `app/src/main/java/com/troubashare/data/cloud/GoogleDriveProvider.kt` |
| Cloud Models | `app/src/main/java/com/troubashare/domain/model/CloudModels.kt` |
| ChangeLogEntity | `app/src/main/java/com/troubashare/data/database/entities/ChangeLogEntity.kt` |
| ChangeLogDao | `app/src/main/java/com/troubashare/data/database/dao/ChangeLogDao.kt` |
| Cloud UI | `app/src/main/java/com/troubashare/ui/cloud/` |
| Dependency Injection | `app/src/main/java/com/troubashare/di/` |

## References

- **Design Document**: `collaboration_design.md` - Original design and architecture
- **Gap Analysis**: `gap_analysis.md` - Implementation gaps and solutions
- **Drive Setup**: `GOOGLE_DRIVE_SETUP.md` - Google Drive configuration
- **Testing Guide**: `CLOUD_SYNC_TESTING.md` - Testing procedures and scenarios
- **Build Test**: `build_test.md` - Build verification steps

---

**Implementation Date**: January 2025
**Last Updated**: January 2025
**Status**: ✅ Complete - Ready for testing
