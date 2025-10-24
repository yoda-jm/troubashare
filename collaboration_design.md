# TroubaShare - Collaborative Editing Design
## Google Drive Integration with Import/Export/Merge System

### 🎯 **Design Goals**

1. **Seamless Collaboration**: Band members can work on setlists/annotations independently and merge changes
2. **Conflict Resolution**: Handle simultaneous edits gracefully with user-friendly resolution
3. **Offline-First**: Full functionality without internet, sync when available
4. **Backwards Compatible**: Existing local-only workflow continues working
5. **Band-Centric**: Group ownership model where band leader controls shared content

---

## 🏗️ **Architecture Overview**

### **Three-Tier Sync Architecture**

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Local Device  │◄──►│  Google Drive    │◄──►│  Other Devices  │
│                 │    │   (Shared Repo)  │    │                 │
├─────────────────┤    ├──────────────────┤    ├─────────────────┤
│ • Room Database │    │ • Group Folders  │    │ • Room Database │
│ • Local Files   │    │ • JSON Manifests │    │ • Local Files   │
│ • Annotation    │    │ • Content Files  │    │ • Annotation    │
│   Layers        │    │ • Change Logs    │    │   Layers        │
│ • Conflict UI   │    │ • Lock Files     │    │ • Conflict UI   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### **Core Components**

1. **SyncManager**: Orchestrates all sync operations
2. **ConflictResolver**: Handles merge conflicts with user interaction
3. **GoogleDriveProvider**: Cloud storage abstraction
4. **ChangeTracker**: Monitors local modifications
5. **MergeEngine**: Intelligent merging of annotations and data

---

## 📁 **Google Drive File Structure**

### **Organized Hierarchy**

```
📁 TroubaShare/
├── 📁 groups/
│   └── 📁 {group-name-sanitized}/
│       ├── 📄 group-manifest.json          # Group metadata & members
│       ├── 📁 songs/
│       │   └── 📁 {song-title-sanitized}/
│       │       ├── 📄 song-manifest.json   # Song metadata
│       │       ├── 📁 files/
│       │       │   ├── 📄 {member}-content.pdf
│       │       │   ├── 📄 {member}-content.jpg
│       │       │   └── 📄 {member}-annotations.json
│       │       └── 📁 versions/
│       │           ├── 📄 {timestamp}-song-metadata.json
│       │           └── 📄 {timestamp}-{member}-annotations.json
│       ├── 📁 setlists/
│       │   ├── 📄 {setlist-name}.json      # Setlist definition
│       │   └── 📁 versions/
│       │       └── 📄 {timestamp}-{setlist-name}.json
│       └── 📁 sync/
│           ├── 📄 last-sync.json           # Sync timestamps per device
│           └── 📄 change-log.json          # Ordered list of all changes
```

### **Manifest File Examples**

**group-manifest.json**
```json
{
  "groupId": "uuid-here",
  "name": "The Jazz Quartet",
  "createdBy": "device-id-1",
  "createdAt": "2024-01-15T10:30:00Z",
  "lastModified": "2024-01-16T14:20:00Z",
  "version": 5,
  "members": [
    {
      "memberId": "member-1",
      "name": "Alice (Piano)",
      "role": "Piano",
      "addedBy": "device-id-1",
      "addedAt": "2024-01-15T10:30:00Z"
    }
  ],
  "permissions": {
    "adminDevices": ["device-id-1"],
    "memberDevices": ["device-id-2", "device-id-3"],
    "readOnlyDevices": []
  }
}
```

**change-log.json**
```json
{
  "changes": [
    {
      "changeId": "uuid-change-1",
      "deviceId": "device-id-2", 
      "timestamp": "2024-01-16T14:20:00Z",
      "type": "song_annotation_updated",
      "entityId": "song-uuid-123",
      "memberId": "member-2",
      "checksum": "sha256-hash",
      "description": "Added guitar chord markings to 'Blue Moon'"
    },
    {
      "changeId": "uuid-change-2",
      "deviceId": "device-id-1",
      "timestamp": "2024-01-16T14:25:00Z", 
      "type": "setlist_song_added",
      "entityId": "setlist-uuid-456",
      "songId": "song-uuid-789",
      "description": "Added 'Autumn Leaves' to Friday Night Set"
    }
  ],
  "lastChangeId": "uuid-change-2"
}
```

---

## 🔄 **Sync Flow Design**

### **1. Initial Group Creation & Sharing**

```kotlin
// Band leader creates group and enables sharing
class GroupSharingFlow {
    
    suspend fun enableCloudSharing(group: Group): Result<ShareCode> {
        // 1. Authenticate with Google Drive
        val authResult = googleDriveProvider.authenticate()
        if (authResult.isFailure) return authResult.mapToShareCode()
        
        // 2. Create group folder structure
        val folderResult = createGroupFolderStructure(group)
        if (folderResult.isFailure) return folderResult.mapToShareCode()
        
        // 3. Upload initial group manifest
        val manifestResult = uploadGroupManifest(group)
        if (manifestResult.isFailure) return manifestResult.mapToShareCode()
        
        // 4. Generate shareable code/link
        val shareCode = generateShareCode(group.id, folderResult.folderId)
        
        // 5. Save cloud settings locally
        cloudSettingsRepository.saveGroupCloudSettings(
            groupId = group.id,
            cloudFolderId = folderResult.folderId,
            isAdmin = true,
            shareCode = shareCode
        )
        
        return Result.success(shareCode)
    }
    
    private fun generateShareCode(groupId: String, folderId: String): ShareCode {
        // Create shareable link like: troubashare://join?group=abc123&folder=xyz789
        return ShareCode(
            code = "TB-${groupId.take(8).uppercase()}",
            deepLink = "troubashare://join?group=$groupId&folder=$folderId",
            qrCode = generateQRCode("troubashare://join?group=$groupId&folder=$folderId")
        )
    }
}
```

### **2. Joining Shared Group**

```kotlin
class GroupJoinFlow {
    
    suspend fun joinSharedGroup(shareCode: String): Result<Group> {
        // 1. Parse share code to extract group/folder info
        val shareInfo = parseShareCode(shareCode)
        
        // 2. Authenticate with Google Drive
        val authResult = googleDriveProvider.authenticate()
        if (authResult.isFailure) return authResult.mapToGroup()
        
        // 3. Verify access to shared folder
        val accessResult = verifyFolderAccess(shareInfo.folderId)
        if (accessResult.isFailure) return accessResult.mapToGroup()
        
        // 4. Download group manifest
        val manifestResult = downloadGroupManifest(shareInfo.folderId)
        if (manifestResult.isFailure) return manifestResult.mapToGroup()
        
        // 5. Perform initial full sync
        val syncResult = syncManager.performInitialSync(manifestResult.group)
        if (syncResult.isFailure) return syncResult.mapToGroup()
        
        // 6. Register device in group permissions
        val registerResult = registerDeviceInGroup(manifestResult.group.id)
        
        return Result.success(manifestResult.group)
    }
}
```

### **3. Continuous Sync Process**

```kotlin
class ContinuousSyncManager {
    
    private val syncInterval = 30.seconds // Configurable
    private val conflictResolver = ConflictResolver()
    
    fun startContinuousSync(groupId: String) {
        viewModelScope.launch {
            while (isGroupShared(groupId)) {
                delay(syncInterval)
                performDeltaSync(groupId)
            }
        }
    }
    
    private suspend fun performDeltaSync(groupId: String) {
        try {
            // 1. Check for remote changes
            val remoteChanges = downloadChangeLog(groupId)
            val lastLocalSync = getLastSyncTimestamp(groupId)
            val newChanges = remoteChanges.filter { it.timestamp > lastLocalSync }
            
            // 2. Check for local changes to upload
            val localChanges = changeTracker.getPendingChanges(groupId)
            
            // 3. Handle conflicts if both exist
            if (newChanges.isNotEmpty() && localChanges.isNotEmpty()) {
                handleSyncConflicts(groupId, localChanges, newChanges)
            } else {
                // 4. Apply remote changes
                if (newChanges.isNotEmpty()) {
                    applyRemoteChanges(groupId, newChanges)
                }
                
                // 5. Upload local changes  
                if (localChanges.isNotEmpty()) {
                    uploadLocalChanges(groupId, localChanges)
                }
            }
            
            // 6. Update sync timestamp
            updateLastSyncTimestamp(groupId)
            
        } catch (e: Exception) {
            // Handle network errors, show offline mode
            handleSyncError(groupId, e)
        }
    }
}
```

---

## 🤝 **Intelligent Conflict Resolution**

### **Conflict Detection Strategy**

```kotlin
data class SyncConflict(
    val entityType: EntityType, // SONG, SETLIST, ANNOTATION, GROUP_MEMBER
    val entityId: String,
    val localVersion: ConflictVersion,
    val remoteVersion: ConflictVersion,
    val conflictType: ConflictType
)

enum class ConflictType {
    SIMULTANEOUS_EDIT,     // Both sides modified same entity
    DELETE_MODIFY,         // One deleted, other modified
    STRUCTURE_CHANGE,      // Group/member structure conflicts
    ANNOTATION_OVERLAP     // Overlapping annotation changes
}

class ConflictResolver {
    
    suspend fun detectConflicts(
        localChanges: List<Change>,
        remoteChanges: List<Change>
    ): List<SyncConflict> {
        
        val conflicts = mutableListOf<SyncConflict>()
        
        // Group changes by entity to find conflicts
        val changesByEntity = (localChanges + remoteChanges)
            .groupBy { "${it.entityType}:${it.entityId}" }
            
        changesByEntity.forEach { (entityKey, changes) ->
            if (changes.size > 1) {
                // Multiple changes to same entity - potential conflict
                val localChange = changes.find { it.deviceId == currentDeviceId }
                val remoteChanges = changes.filter { it.deviceId != currentDeviceId }
                
                remoteChanges.forEach { remoteChange ->
                    val conflict = analyzeConflict(localChange, remoteChange)
                    if (conflict != null) {
                        conflicts.add(conflict)
                    }
                }
            }
        }
        
        return conflicts
    }
    
    private fun analyzeConflict(local: Change?, remote: Change): SyncConflict? {
        return when {
            local == null -> null // No local change, no conflict
            
            local.type == ChangeType.DELETE && remote.type == ChangeType.MODIFY ->
                SyncConflict(local.entityType, local.entityId, local.version, remote.version, ConflictType.DELETE_MODIFY)
                
            local.type == ChangeType.MODIFY && remote.type == ChangeType.DELETE ->
                SyncConflict(local.entityType, local.entityId, local.version, remote.version, ConflictType.DELETE_MODIFY)
                
            local.type == ChangeType.MODIFY && remote.type == ChangeType.MODIFY &&
            areTimestampsClose(local.timestamp, remote.timestamp) ->
                SyncConflict(local.entityType, local.entityId, local.version, remote.version, ConflictType.SIMULTANEOUS_EDIT)
                
            else -> null
        }
    }
}
```

### **User-Friendly Conflict Resolution UI**

```kotlin
@Composable
fun ConflictResolutionDialog(
    conflicts: List<SyncConflict>,
    onResolveConflict: (SyncConflict, ResolutionAction) -> Unit,
    onResolveAll: (ResolutionStrategy) -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { 
            Text("🤝 Sync Conflicts Detected")
        },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "${conflicts.size} conflicts need your attention:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                items(conflicts) { conflict ->
                    ConflictItem(
                        conflict = conflict,
                        onResolve = { action -> onResolveConflict(conflict, action) }
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onResolveAll(ResolutionStrategy.KEEP_LOCAL) }) {
                    Text("Keep All Local")
                }
                TextButton(onClick = { onResolveAll(ResolutionStrategy.ACCEPT_REMOTE) }) {
                    Text("Accept All Remote")
                }
                Button(onClick = { onResolveAll(ResolutionStrategy.MERGE_SMART) }) {
                    Text("Smart Merge")
                }
            }
        }
    )
}

@Composable
fun ConflictItem(
    conflict: SyncConflict,
    onResolve: (ResolutionAction) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Conflict description
            Text(
                text = when (conflict.conflictType) {
                    ConflictType.SIMULTANEOUS_EDIT -> 
                        "📝 ${conflict.remoteVersion.authorName} also edited '${conflict.localVersion.entityName}'"
                    ConflictType.DELETE_MODIFY -> 
                        "🗑️ ${conflict.remoteVersion.authorName} deleted '${conflict.localVersion.entityName}' while you modified it"
                    ConflictType.ANNOTATION_OVERLAP -> 
                        "🎨 ${conflict.remoteVersion.authorName} added annotations to the same area"
                    else -> "⚠️ Conflict in '${conflict.localVersion.entityName}'"
                },
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Resolution options
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onResolve(ResolutionAction.KEEP_LOCAL) }
                ) {
                    Text("Keep Mine")
                }
                
                OutlinedButton(
                    onClick = { onResolve(ResolutionAction.ACCEPT_REMOTE) }
                ) {
                    Text("Use ${conflict.remoteVersion.authorName}'s")
                }
                
                if (conflict.conflictType == ConflictType.ANNOTATION_OVERLAP) {
                    Button(
                        onClick = { onResolve(ResolutionAction.MERGE_ANNOTATIONS) }
                    ) {
                        Text("Merge Both")
                    }
                }
            }
        }
    }
}
```

---

## 🎨 **Advanced Annotation Merging**

### **Smart Annotation Conflict Resolution**

```kotlin
class AnnotationMergeEngine {
    
    data class AnnotationConflict(
        val pageNumber: Int,
        val localStrokes: List<AnnotationStroke>,
        val remoteStrokes: List<AnnotationStroke>,
        val overlapsDetected: List<OverlapRegion>
    )
    
    suspend fun mergeAnnotations(
        localAnnotations: List<Annotation>,
        remoteAnnotations: List<Annotation>
    ): AnnotationMergeResult {
        
        val conflicts = detectAnnotationConflicts(localAnnotations, remoteAnnotations)
        
        return if (conflicts.isEmpty()) {
            // No conflicts - simple merge
            AnnotationMergeResult.AutoMerged(
                merged = combineAnnotations(localAnnotations, remoteAnnotations)
            )
        } else {
            // Conflicts detected - need user resolution
            AnnotationMergeResult.ConflictsDetected(
                conflicts = conflicts,
                previewMerged = generatePreviewMerge(localAnnotations, remoteAnnotations)
            )
        }
    }
    
    private fun detectAnnotationConflicts(
        local: List<Annotation>,
        remote: List<Annotation>
    ): List<AnnotationConflict> {
        
        val conflicts = mutableListOf<AnnotationConflict>()
        
        // Group by page number
        val localByPage = local.groupBy { it.pageNumber }
        val remoteByPage = remote.groupBy { it.pageNumber }
        
        val allPages = (localByPage.keys + remoteByPage.keys).distinct()
        
        allPages.forEach { pageNumber ->
            val localPageAnnotations = localByPage[pageNumber] ?: emptyList()
            val remotePageAnnotations = remoteByPage[pageNumber] ?: emptyList()
            
            if (localPageAnnotations.isNotEmpty() && remotePageAnnotations.isNotEmpty()) {
                val overlaps = findOverlappingRegions(localPageAnnotations, remotePageAnnotations)
                
                if (overlaps.isNotEmpty()) {
                    conflicts.add(
                        AnnotationConflict(
                            pageNumber = pageNumber,
                            localStrokes = localPageAnnotations.flatMap { it.strokes },
                            remoteStrokes = remotePageAnnotations.flatMap { it.strokes },
                            overlapsDetected = overlaps
                        )
                    )
                }
            }
        }
        
        return conflicts
    }
    
    private fun findOverlappingRegions(
        localAnnotations: List<Annotation>,
        remoteAnnotations: List<Annotation>
    ): List<OverlapRegion> {
        
        val overlaps = mutableListOf<OverlapRegion>()
        
        localAnnotations.forEach { localAnnotation ->
            remoteAnnotations.forEach { remoteAnnotation ->
                val overlap = calculateStrokeOverlap(localAnnotation.strokes, remoteAnnotation.strokes)
                if (overlap.overlapPercentage > 0.3) { // 30% overlap threshold
                    overlaps.add(overlap)
                }
            }
        }
        
        return overlaps
    }
}

@Composable
fun AnnotationConflictPreview(
    conflict: AnnotationConflict,
    pdfBitmap: Bitmap,
    onResolve: (AnnotationMergeStrategy) -> Unit
) {
    Column {
        Text("🎨 Annotation Conflicts on Page ${conflict.pageNumber + 1}")
        
        // Side-by-side preview
        Row {
            // Local annotations preview
            Card(modifier = Modifier.weight(1f)) {
                Column {
                    Text("Your annotations", style = MaterialTheme.typography.labelSmall)
                    AnnotationPreview(
                        bitmap = pdfBitmap,
                        strokes = conflict.localStrokes,
                        highlightColor = Color.Blue
                    )
                }
            }
            
            // Remote annotations preview
            Card(modifier = Modifier.weight(1f)) {
                Column {
                    Text("${conflict.remoteAuthor}'s annotations", style = MaterialTheme.typography.labelSmall)
                    AnnotationPreview(
                        bitmap = pdfBitmap,
                        strokes = conflict.remoteStrokes,
                        highlightColor = Color.Red
                    )
                }
            }
        }
        
        // Merged preview
        Card {
            Column {
                Text("Merged result preview", style = MaterialTheme.typography.labelSmall)
                AnnotationPreview(
                    bitmap = pdfBitmap,
                    strokes = conflict.localStrokes + conflict.remoteStrokes,
                    overlaps = conflict.overlapsDetected
                )
            }
        }
        
        // Resolution options
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onResolve(AnnotationMergeStrategy.KEEP_LOCAL) }
            ) {
                Text("Keep Mine Only")
            }
            
            OutlinedButton(
                onClick = { onResolve(AnnotationMergeStrategy.KEEP_REMOTE) }
            ) {
                Text("Use Theirs Only")
            }
            
            Button(
                onClick = { onResolve(AnnotationMergeStrategy.MERGE_BOTH) }
            ) {
                Text("Merge Both")
            }
            
            OutlinedButton(
                onClick = { onResolve(AnnotationMergeStrategy.LAYER_SEPARATE) }
            ) {
                Text("Keep as Separate Layers")
            }
        }
    }
}
```

---

## 📤 **Import/Export System**

### **Group Export for Sharing**

```kotlin
class GroupExportManager {
    
    suspend fun exportGroupBundle(group: Group): Result<ExportBundle> {
        return try {
            // 1. Gather all group data
            val songs = songRepository.getSongsByGroupId(group.id)
            val setlists = setlistRepository.getSetlistsByGroupId(group.id)
            val allFiles = songs.flatMap { it.files }
            val allAnnotations = annotationRepository.getAnnotationsByGroupId(group.id)
            
            // 2. Create export bundle
            val bundle = ExportBundle(
                metadata = ExportMetadata(
                    groupName = group.name,
                    exportedBy = getCurrentDeviceId(),
                    exportedAt = System.currentTimeMillis(),
                    version = "1.0",
                    checksum = calculateBundleChecksum(group, songs, setlists)
                ),
                group = group,
                songs = songs,
                setlists = setlists,
                files = bundleFiles(allFiles),
                annotations = allAnnotations
            )
            
            // 3. Create ZIP package
            val zipFile = createZipBundle(bundle)
            
            Result.success(bundle.copy(zipFilePath = zipFile.absolutePath))
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun shareGroupBundle(bundle: ExportBundle, shareMethod: ShareMethod): Result<Unit> {
        return when (shareMethod) {
            ShareMethod.EMAIL -> shareViaEmail(bundle)
            ShareMethod.FILE_SHARE -> shareViaFileManager(bundle)
            ShareMethod.QR_CODE -> generateSharingQRCode(bundle)
            ShareMethod.GOOGLE_DRIVE -> uploadToGoogleDrive(bundle)
        }
    }
    
    private suspend fun shareViaEmail(bundle: ExportBundle): Result<Unit> {
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_EMAIL, arrayOf())
            putExtra(Intent.EXTRA_SUBJECT, "TroubaShare Group: ${bundle.group.name}")
            putExtra(Intent.EXTRA_TEXT, buildEmailBody(bundle))
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(bundle.zipFilePath)))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        return try {
            context.startActivity(Intent.createChooser(emailIntent, "Share Group"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildEmailBody(bundle: ExportBundle): String {
        return """
            📚 TroubaShare Group Export
            
            Group: ${bundle.group.name}
            Songs: ${bundle.songs.size}
            Setlists: ${bundle.setlists.size}
            Members: ${bundle.group.members.size}
            
            To import this group:
            1. Save the attached ZIP file
            2. Open TroubaShare
            3. Go to Settings > Import Group
            4. Select the ZIP file
            
            Happy performing! 🎵
        """.trimIndent()
    }
}
```

### **Group Import with Merge Options**

```kotlin
class GroupImportManager {
    
    suspend fun importGroupBundle(zipFilePath: String): Result<ImportPreview> {
        return try {
            // 1. Extract and validate ZIP
            val extractedBundle = extractZipBundle(zipFilePath)
            if (!validateBundleIntegrity(extractedBundle)) {
                return Result.failure(Exception("Bundle corrupted or invalid"))
            }
            
            // 2. Check for conflicts with existing data
            val conflicts = detectImportConflicts(extractedBundle)
            
            // 3. Create import preview
            val preview = ImportPreview(
                bundle = extractedBundle,
                conflicts = conflicts,
                importStrategy = if (conflicts.isEmpty()) 
                    ImportStrategy.DIRECT_IMPORT 
                else 
                    ImportStrategy.REQUIRES_RESOLUTION
            )
            
            Result.success(preview)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun detectImportConflicts(bundle: ExportBundle): List<ImportConflict> {
        val conflicts = mutableListOf<ImportConflict>()
        
        // Check group name conflicts
        val existingGroup = groupRepository.getGroupByName(bundle.group.name)
        if (existingGroup != null) {
            conflicts.add(
                ImportConflict(
                    type = ConflictType.GROUP_NAME_EXISTS,
                    existing = existingGroup,
                    importing = bundle.group,
                    resolutionOptions = listOf(
                        ResolutionOption.RENAME_IMPORTED,
                        ResolutionOption.MERGE_GROUPS,
                        ResolutionOption.REPLACE_EXISTING
                    )
                )
            )
        }
        
        // Check song conflicts
        bundle.songs.forEach { importingSong ->
            val existingSong = songRepository.findSongByTitleAndArtist(
                importingSong.title, 
                importingSong.artist
            )
            if (existingSong != null) {
                conflicts.add(
                    ImportConflict(
                        type = ConflictType.SONG_EXISTS,
                        existing = existingSong,
                        importing = importingSong,
                        resolutionOptions = listOf(
                            ResolutionOption.SKIP_IMPORT,
                            ResolutionOption.MERGE_SONGS,
                            ResolutionOption.IMPORT_AS_NEW_VERSION
                        )
                    )
                )
            }
        }
        
        return conflicts
    }
}

@Composable
fun ImportConflictDialog(
    preview: ImportPreview,
    onResolveConflict: (ImportConflict, ResolutionOption) -> Unit,
    onProceedImport: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("📦 Import Group: ${preview.bundle.group.name}") },
        text = {
            LazyColumn {
                item {
                    ImportSummaryCard(preview.bundle)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                if (preview.conflicts.isNotEmpty()) {
                    item {
                        Text(
                            "⚠️ Conflicts Detected",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    items(preview.conflicts) { conflict ->
                        ImportConflictCard(
                            conflict = conflict,
                            onResolve = onResolveConflict
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onProceedImport,
                enabled = preview.conflicts.all { it.resolution != null }
            ) {
                Text(if (preview.conflicts.isEmpty()) "Import" else "Import with Resolutions")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}
```

---

## 🔄 **Complete User Workflow**

### **Scenario: Band Collaboration**

**Step 1: Band Leader Setup**
```
1. Creates group "Jazz Ensemble"
2. Adds members: Alice (Piano), Bob (Bass), Carol (Vocals), Dave (Drums)
3. Uploads songs with individual charts per member
4. Creates setlist "Friday Night Gig"
5. Enables cloud sharing: "Share Group" → Gets code "TB-JAZZ2024"
6. Shares code via WhatsApp: "Join our band: TB-JAZZ2024"
```

**Step 2: Members Join**
```
1. Carol receives share code
2. Opens TroubaShare → "Join Group" → Enters "TB-JAZZ2024"
3. App downloads all songs, setlists, her vocal charts
4. Carol adds annotations to her charts (lyrics highlighting)
5. Changes sync automatically to other members
```

**Step 3: Collaborative Editing**
```
1. Alice (Piano) edits setlist, adds "Blue Moon" 
2. Bob (Bass) simultaneously adds "All of Me"
3. Next sync detects conflict: "Both Alice and Bob edited 'Friday Night Gig'"
4. Conflict resolution shows: "Alice added Blue Moon, Bob added All of Me"
5. Smart merge combines both additions automatically
6. All members see updated setlist with both songs
```

**Step 4: Performance Day**
```
1. All members have offline copies synced
2. No internet at venue - no problem!
3. Each member uses Concert Mode with their specific charts
4. Annotations from practice sessions visible during performance
5. Setlist navigation works perfectly offline
```

### **Advanced Scenarios**

**Scenario: Emergency Song Addition**
```
1. 30 minutes before show: venue requests "Fly Me to the Moon"
2. Alice quickly uploads piano chart
3. Other members get notification: "Alice added new song"
4. Bob adds bass chart, Carol adds lyrics
5. Auto-sync ensures everyone has the complete song
6. Song added to setlist and ready for performance
```

**Scenario: Annotation Collaboration**
```
1. Dave (Drums) marks tempo changes on "All of Me"
2. Carol (Vocals) adds breath marks to same song
3. Both annotations don't overlap - auto-merge succeeds
4. Alice sees both sets of markings during practice
5. Band is better synchronized using shared markings
```

---

## 🛡️ **Security & Privacy**

### **Data Protection**
```kotlin
class SecureCloudSync {
    
    // Client-side encryption before cloud upload
    suspend fun encryptBeforeUpload(data: ByteArray, groupKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, groupKey)
        return cipher.doFinal(data)
    }
    
    // Generate group-specific encryption key
    fun generateGroupKey(groupId: String, deviceId: String): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
    
    // Google Drive sees only encrypted files
    suspend fun uploadSecurely(filePath: String, groupKey: SecretKey): Result<String> {
        val originalData = File(filePath).readBytes()
        val encryptedData = encryptBeforeUpload(originalData, groupKey)
        
        return googleDriveProvider.uploadFile(
            localData = encryptedData,
            remotePath = "encrypted_${UUID.randomUUID()}.enc"
        )
    }
}
```

### **Permission Management**
```kotlin
enum class GroupPermission {
    ADMIN,          // Can add/remove members, manage sharing
    EDITOR,         // Can edit songs, setlists, annotations
    VIEWER,         // Can view and annotate only
    PERFORMER       // Concert mode only, no editing
}

class PermissionManager {
    
    fun checkPermission(deviceId: String, groupId: String, action: Action): Boolean {
        val permission = getDevicePermission(deviceId, groupId)
        
        return when (action) {
            Action.ADD_MEMBER -> permission == GroupPermission.ADMIN
            Action.EDIT_SONG -> permission in listOf(GroupPermission.ADMIN, GroupPermission.EDITOR)
            Action.ADD_ANNOTATION -> permission != GroupPermission.PERFORMER
            Action.VIEW_CONTENT -> true // All permissions allow viewing
        }
    }
}
```

---

## 📊 **Implementation Priority**

### **Phase 1: Core Sync (4 weeks)**
1. ✅ Google Drive authentication and basic file operations
2. ✅ Group manifest creation and sharing codes  
3. ✅ Basic sync infrastructure and change tracking
4. ✅ Simple conflict detection (last-writer-wins)

### **Phase 2: Advanced Merging (3 weeks)**
5. ✅ Intelligent conflict resolution UI
6. ✅ Annotation merge engine with overlap detection
7. ✅ Import/Export system with ZIP bundles

### **Phase 3: Polish & Security (3 weeks)** 
8. ✅ Client-side encryption for cloud storage
9. ✅ Permission management and access control
10. ✅ Performance optimization and error handling

---

## 🎯 **Success Metrics**

### **Technical Metrics**
- **Sync Success Rate**: >99% successful synchronizations
- **Conflict Resolution**: <5% of syncs require manual intervention
- **Performance**: Sync operations complete within 10 seconds for typical groups
- **Reliability**: Works offline 100% of the time after initial sync

### **User Experience Metrics**
- **Setup Time**: New members can join and sync within 5 minutes
- **Collaboration Efficiency**: 90% reduction in "who has the latest version?" questions
- **User Satisfaction**: >4.5 stars with collaborative features
- **Support Tickets**: <2% related to sync/merge issues

---

## 🏁 **Conclusion**

This collaborative editing design provides:

✅ **Seamless band collaboration** with intuitive sharing  
✅ **Intelligent conflict resolution** that handles real-world scenarios  
✅ **Offline-first architecture** that never blocks performance  
✅ **Advanced annotation merging** for musical collaboration  
✅ **Secure, encrypted synchronization** with Google Drive  
✅ **Professional import/export** for easy group sharing  

**Key Innovation**: The annotation merge engine with visual conflict resolution makes this uniquely suitable for musicians who need to collaborate on sheet music markings while maintaining individual customizations.

**Implementation Estimate**: 10-12 weeks with this phased approach, resulting in a production-ready collaborative band management system that sets a new standard for musician workflow tools.