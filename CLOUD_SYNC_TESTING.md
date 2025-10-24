# Cloud Sync Testing Guide

## Overview

This guide provides instructions for testing the TroubaShare cloud synchronization implementation across multiple devices. The cloud sync system uses Google Drive as the backend and supports collaborative editing with conflict resolution.

## Prerequisites

1. **Multiple Test Devices/Emulators**: At least 2 Android devices or emulators
2. **Google Account**: A Google account with Drive access enabled
3. **Test Data**: Sample PDF files for testing annotations
4. **Network Connection**: Active internet connection on all test devices

## Build and Installation

### 1. Build the App

```bash
./gradlew assembleDebug
```

### 2. Install on Test Devices

For physical devices connected via ADB:
```bash
adb -s <device-id-1> install app/build/outputs/apk/debug/app-debug.apk
adb -s <device-id-2> install app/build/outputs/apk/debug/app-debug.apk
```

For emulators:
```bash
adb -e install app/build/outputs/apk/debug/app-debug.apk
```

## Test Scenarios

### Test 1: Basic Group Creation and Sync

**Objective**: Verify that a group can be created and synced to Google Drive

**Steps**:
1. On Device 1:
   - Open TroubaShare
   - Go to Settings → Cloud Sync
   - Sign in with Google account
   - Create a new group "Test Group 1"
   - Add yourself as a member
   - Enable cloud sync for the group
2. Verify:
   - Check logs for successful folder creation
   - Confirm group manifest uploaded to Drive
   - Check that sync status shows "Synced"

**Expected Result**: Group folder created in Drive with manifest.json

**Log Tags to Monitor**:
- `CloudSyncManager`
- `GoogleDriveProvider`

---

### Test 2: Song Upload and Download

**Objective**: Verify that PDF songs are uploaded and can be downloaded on other devices

**Steps**:
1. On Device 1:
   - Add a PDF song to the library
   - Assign it to "Test Group 1"
   - Trigger sync (or wait for auto-sync)
2. On Device 2:
   - Sign in with same Google account
   - Join "Test Group 1"
   - Enable cloud sync
   - Trigger sync
3. Verify:
   - Song appears in Device 2's library
   - PDF file downloads successfully
   - Metadata (title, artist) matches

**Expected Result**: Song visible and playable on both devices

**Files to Check in Drive**:
- `/TroubaShare/Test Group 1/songs/<song-name>.pdf`
- `/TroubaShare/Test Group 1/changelog/<timestamp>_song_create.json`

---

### Test 3: Annotation Creation and Sync

**Objective**: Verify annotations sync across devices

**Steps**:
1. On Device 1:
   - Open a synced song
   - Add annotations (pen strokes, highlights, text)
   - Save annotations
   - Trigger sync
2. On Device 2:
   - Open the same song
   - Trigger sync
   - View annotations

**Expected Result**: Annotations from Device 1 visible on Device 2

**Log Messages to Look For**:
```
Uploading annotation: <annotation-id>
Successfully synced annotation for song: <song-name>
Downloaded annotation: <annotation-id>
Applied annotation change for song: <song-name>
```

---

### Test 4: Conflict Detection - Simultaneous Edits

**Objective**: Test conflict detection when both devices edit the same entity

**Steps**:
1. Disable auto-sync on both devices
2. On Device 1:
   - Edit song metadata (title or artist)
   - Keep changes local (don't sync)
3. On Device 2:
   - Edit the same song's metadata differently
   - Keep changes local
4. Enable sync on Device 1, wait for upload
5. Enable sync on Device 2

**Expected Result**:
- Conflict detected
- ConflictResolver determines winner (newer timestamp)
- One version wins, other is overwritten
- Both devices end up with same data

**Log Messages to Look For**:
```
Detected <n> conflicts for group <group-id>
Conflict type: SIMULTANEOUS_EDIT
Auto-resolving conflict with last-writer-wins
Resolved conflict: <conflict-id>
```

---

### Test 5: Annotation Merge - Overlapping Edits

**Objective**: Test annotation merging when both devices annotate the same page

**Steps**:
1. Disable auto-sync on both devices
2. On Device 1:
   - Open song, go to page 1
   - Draw red pen strokes
   - Save locally (don't sync)
3. On Device 2:
   - Open same song, go to page 1
   - Draw blue pen strokes
   - Save locally (don't sync)
4. Enable sync on Device 1, wait for upload
5. Enable sync on Device 2

**Expected Result**:
- Conflict detected as ANNOTATION_OVERLAP
- Auto-merge combines both sets of strokes
- Both red and blue strokes visible on both devices
- Duplicate strokes eliminated by ID

**Log Messages to Look For**:
```
Found <n> annotation layers for merge
Merging <n> layers with <n> total strokes into <n> unique strokes
Merged into primary annotation layer <id>
Annotation merge complete for: <song-name>
```

---

### Test 6: Setlist Sync

**Objective**: Verify setlists sync with correct song references

**Steps**:
1. On Device 1:
   - Create a setlist "Test Setlist"
   - Add 3-5 songs to the setlist
   - Assign setlist to "Test Group 1"
   - Trigger sync
2. On Device 2:
   - Trigger sync
   - Navigate to setlists

**Expected Result**:
- Setlist appears with correct name
- All songs in correct order
- Songs are playable

**Files to Check in Drive**:
- `/TroubaShare/Test Group 1/setlists/<setlist-id>.json`
- `/TroubaShare/Test Group 1/changelog/<timestamp>_setlist_create.json`

---

### Test 7: Checksum Deduplication

**Objective**: Verify files aren't re-uploaded if unchanged

**Steps**:
1. On Device 1:
   - Add a PDF song
   - Trigger sync (uploads file)
2. Enable verbose logging
3. Trigger sync again without changes

**Expected Result**:
- Checksum comparison detects no changes
- File not re-uploaded
- Log shows "File <name> unchanged"

**Log Messages to Look For**:
```
File <filename> unchanged (checksum: <hash>...)
Skipping upload for unchanged file
```

---

### Test 8: Retry Logic - Network Failures

**Objective**: Test exponential backoff retry on failures

**Steps**:
1. On Device 1:
   - Enable airplane mode
   - Add a song and trigger sync
   - Observe retry attempts in logs
2. Re-enable network after 2 retry attempts

**Expected Result**:
- Multiple retry attempts logged
- Exponential backoff delays (1s, 2s, 4s)
- Eventually succeeds when network returns
- Or fails gracefully after max attempts

**Log Messages to Look For**:
```
Attempt 1/3 failed for 'Upload PDF: <filename>': <error>. Retrying in 1000ms...
Attempt 2/3 failed for 'Upload PDF: <filename>': <error>. Retrying in 2000ms...
Operation 'Upload PDF: <filename>' succeeded after 3 attempts
```

---

### Test 9: Delete Propagation

**Objective**: Verify deletions sync across devices

**Steps**:
1. On Device 1:
   - Delete a synced song
   - Trigger sync
2. On Device 2:
   - Trigger sync

**Expected Result**: Song deleted from Device 2

**Files to Check in Drive**:
- Song file should still exist (soft delete)
- Changelog entry: `<timestamp>_song_delete.json`

---

### Test 10: Member Management

**Objective**: Test adding/removing members from group

**Steps**:
1. On Device 1:
   - Go to group settings
   - Add a new member "Test User 2"
   - Trigger sync
2. On Device 2:
   - Trigger sync
   - Check group members list

**Expected Result**:
- New member visible on Device 2
- Manifest updated with new member

**Files to Check in Drive**:
- `/TroubaShare/Test Group 1/manifest.json` (updated memberCount and members array)
- `/TroubaShare/Test Group 1/changelog/<timestamp>_member_create.json`

---

## Monitoring and Debugging

### Enable Verbose Logging

Add this to your test devices:
```bash
adb logcat | grep -E "(CloudSyncManager|GoogleDriveProvider|ConflictResolver|ChangeTracker)"
```

### Key Log Tags

- **CloudSyncManager**: Overall sync orchestration
- **GoogleDriveProvider**: Drive API calls
- **ConflictResolver**: Conflict detection and resolution
- **ChangeTracker**: Local change detection
- **SyncWorker**: Background sync jobs

### Common Issues

#### Issue: Authentication Failures
**Symptom**: "Failed to get access token" errors
**Solution**:
- Re-authenticate in app settings
- Check Google Cloud Console OAuth configuration
- Verify Drive API is enabled

#### Issue: Checksum Mismatches
**Symptom**: Files re-upload every sync
**Solution**:
- Check MD5 calculation in `calculateMD5()`
- Verify Drive API returns checksums
- Check file encoding/corruption

#### Issue: Conflicts Not Auto-Resolving
**Symptom**: Conflicts require manual resolution
**Solution**:
- Check `canAutoResolve` flag logic
- Verify conflict type detection
- Review timestamp comparison window (5 minutes)

#### Issue: Annotations Duplicating
**Symptom**: Multiple copies of same strokes
**Solution**:
- Check stroke ID uniqueness
- Verify `distinctBy` deduplication logic
- Review annotation layer merge code

---

## Performance Benchmarks

### Expected Sync Times (approximate)

| Operation | Typical Duration |
|-----------|-----------------|
| Group creation | 2-3 seconds |
| PDF upload (5MB) | 10-15 seconds |
| Annotation sync | 1-2 seconds |
| Full group sync (10 songs) | 30-45 seconds |
| Conflict resolution | < 1 second |
| Changelog download | 2-5 seconds |

### Network Usage

- PDF file: ~5-10 MB per song
- Annotation: ~1-10 KB per annotation
- Changelog entry: ~1-2 KB
- Manifest: ~5-10 KB

---

## Test Checklist

Use this checklist to track testing progress:

- [ ] Basic group creation and sync
- [ ] Song upload and download
- [ ] Annotation creation and sync
- [ ] Conflict detection - simultaneous edits
- [ ] Annotation merge - overlapping edits
- [ ] Setlist sync
- [ ] Checksum deduplication
- [ ] Retry logic - network failures
- [ ] Delete propagation
- [ ] Member management
- [ ] Multi-device annotation viewing (Concert Mode)
- [ ] Large file handling (>10MB PDFs)
- [ ] Offline mode with queue
- [ ] Background sync worker

---

## Reporting Issues

When reporting issues, include:

1. **Device Info**: Model, Android version, app version
2. **Steps to Reproduce**: Exact sequence of actions
3. **Expected vs Actual**: What should happen vs what happened
4. **Logs**: Relevant logcat output with timestamps
5. **Drive State**: List of files in Drive folder
6. **Screenshots**: If UI-related

### Log Collection Command

```bash
adb logcat -d > sync_test_logs_$(date +%Y%m%d_%H%M%S).txt
```

---

## Advanced Testing

### Stress Testing

1. **Large Group**: Create group with 50+ songs
2. **Rapid Edits**: Make 10+ edits in 1 minute
3. **Concurrent Conflicts**: 3+ devices editing simultaneously
4. **Large Annotations**: Draw 1000+ strokes on single page
5. **Network Instability**: Toggle airplane mode during sync

### Edge Cases

1. **Empty Groups**: Sync group with no content
2. **Duplicate Names**: Songs with identical titles
3. **Special Characters**: Files with unicode/emoji names
4. **Corrupted Files**: Upload invalid PDF
5. **Storage Limits**: Fill Drive to near quota
6. **Clock Skew**: Devices with different system times

---

## Success Criteria

Cloud sync implementation is considered successful if:

1. ✅ All test scenarios pass without crashes
2. ✅ Data consistency maintained across devices
3. ✅ Conflicts auto-resolve correctly
4. ✅ Annotations merge without loss
5. ✅ Network failures handled gracefully
6. ✅ Performance within expected benchmarks
7. ✅ No data loss or corruption
8. ✅ UI updates reflect sync state accurately

---

## Next Steps

After completing manual testing:

1. **Create Automated Tests**: Write integration tests for sync logic
2. **Add Instrumented Tests**: UI tests for sync workflows
3. **Performance Profiling**: Measure memory and CPU usage
4. **User Acceptance Testing**: Beta test with real musicians
5. **Documentation**: Update user-facing docs with sync features

---

## Contact

For questions about cloud sync implementation:
- Review code in `app/src/main/java/com/troubashare/data/cloud/`
- Check design docs: `collaboration_design.md` and `gap_analysis.md`
- See Drive setup guide: `GOOGLE_DRIVE_SETUP.md`
