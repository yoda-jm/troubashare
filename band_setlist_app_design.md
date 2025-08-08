# TroubaShare - Band Setlist Management App
## Technical Design Document

### Table of Contents
1. [Overview and Goals](#overview-and-goals)
2. [User Stories](#user-stories)
3. [Functional Requirements](#functional-requirements)
4. [Non-Functional Requirements](#non-functional-requirements)
5. [UI/UX Flow](#uiux-flow)
6. [Data Model and Storage](#data-model-and-storage)
7. [Offline/Online Sync Design](#offlineonline-sync-design)
8. [Security Considerations](#security-considerations)
9. [Future Enhancements](#future-enhancements)

---

## Overview and Goals

### Project Overview
TroubaShare is an Android application designed to streamline setlist management and music coordination for bands and musical groups. The app enables musicians to organize songs, manage multiple versions for different instruments, create and share setlists, and perform in a distraction-free concert mode with offline capabilities.

### Primary Goals
- **Simplify Performance Management**: Provide a centralized platform for managing songs, setlists, and member-specific content
- **Enable Offline Performance**: Ensure full functionality during live performances without internet dependency
- **Support Collaboration**: Allow seamless sharing and synchronization of content across band members
- **Enhance User Experience**: Offer intuitive navigation and annotation capabilities for sheet music and lyrics
- **Ensure Reliability**: Provide stable, fast performance during critical live performance scenarios

---

## User Stories

### Band Leader/Administrator
- As a band leader, I want to create and manage multiple groups so I can organize different ensembles
- As a band leader, I want to create setlists and share them with members so everyone has the same performance order
- As a band leader, I want to upload songs with multiple versions so each member gets their appropriate part
- As a band leader, I want to track setlist changes so I can notify members of updates

### Individual Musicians
- As a musician, I want to annotate my sheet music so I can add personal notes and markings
- As a musician, I want to access my content offline so I can perform without internet connectivity
- As a musician, I want to navigate songs quickly during performance so I don't disrupt the flow
- As a musician, I want to toggle my annotations on/off so I can choose when to see my notes
- As a musician, I want to sync updates automatically so I always have the latest versions

### General Users
- As a user, I want to search my song library so I can quickly find specific pieces
- As a user, I want to organize songs with metadata so I can filter and categorize effectively
- As a user, I want to use the app in dark mode so I can perform in low-light environments
- As a user, I want to import content easily so I can migrate from existing systems

---

## Functional Requirements

### 1. Group & Member Management
**REQ-1.1**: The system shall allow users to create multiple groups with unique names
**REQ-1.2**: Each group shall support adding/removing individual members with unique names
**REQ-1.3**: Users shall be able to switch between groups within the same app instance
**REQ-1.4**: The system shall maintain member-specific content associations per group

### 2. Song Library Management
**REQ-2.1**: Users shall be able to add songs with metadata including:
- Title (required)
- Artist
- Musical key
- Tempo (BPM)
- Tags (comma-separated)
- Notes/description
- Creation/modification timestamps

**REQ-2.2**: Each song shall support multiple versions (e.g., "Piano/Voice", "Full Band", "Acoustic")
**REQ-2.3**: For each member-version combination, the system shall support:
- PDF files (multi-page supported)
- Image files (JPG, PNG)
- Freehand annotation overlays
- Typed text notes

**REQ-2.4**: When a member has no content for a song, concert mode shall display a centered message on a white background
**REQ-2.5**: The system shall provide search and filter capabilities based on all metadata fields
**REQ-2.6**: Users shall be able to bulk import content via file selection or cloud storage integration

### 3. Canvas Overlay System
**REQ-3.1**: Each page of PDF or image content shall support an associated freehand annotation layer
**REQ-3.2**: Annotation overlays shall be toggleable ON/OFF independently per page
**REQ-3.3**: The system shall provide an editing mode for creating/modifying annotations
**REQ-3.4**: Zoom and pan operations shall maintain perfect alignment between overlay and underlying content
**REQ-3.5**: Annotation tools shall include:
- Pen/brush with variable thickness
- Eraser functionality
- Color selection (minimum 8 colors)
- Undo/redo capability (minimum 10 operations)

### 4. Setlist Management
**REQ-4.1**: Users shall be able to create multiple named setlists
**REQ-4.2**: Setlists shall contain ordered references to song-version combinations (not copies)
**REQ-4.3**: The system shall support drag-and-drop reordering of setlist items
**REQ-4.4**: Setlists shall include metadata:
- Name
- Description
- Creation date
- Last modified timestamp
- Associated group

**REQ-4.5**: Users shall be able to duplicate existing setlists
**REQ-4.6**: The system shall track setlist versions for sync conflict resolution

### 5. Concert Mode
**REQ-5.1**: Concert mode shall provide a fullscreen, distraction-free interface
**REQ-5.2**: The interface shall display:
- Current song title at top center
- Left/right navigation arrows
- Optional digital clock
- Optional performance timer
- Current position indicator (e.g., "3 of 12")

**REQ-5.3**: Navigation arrows shall be visually disabled when at first/last song
**REQ-5.4**: Users shall select their member identity before entering concert mode
**REQ-5.5**: Concert mode shall work entirely offline once content is downloaded
**REQ-5.6**: The system shall support gesture navigation (swipe left/right) in addition to button navigation

### 6. Sync & Storage
**REQ-6.1**: The system shall integrate with cloud storage providers (Google Drive, Dropbox)
**REQ-6.2**: All content shall be cached locally for offline access
**REQ-6.3**: The system shall implement delta synchronization based on modification timestamps
**REQ-6.4**: Users shall receive notifications when shared content is updated
**REQ-6.5**: The system shall support manual export/import via email or file sharing
**REQ-6.6**: Sync conflicts shall be resolved with user intervention (no automatic overwrites)

---

## Non-Functional Requirements

### Performance
- **NFR-1**: Concert mode shall launch within 2 seconds of selection
- **NFR-2**: Page navigation in concert mode shall respond within 200ms
- **NFR-3**: The app shall support libraries with up to 1000 songs without performance degradation
- **NFR-4**: Annotation rendering shall maintain 60fps during pan/zoom operations

### Reliability
- **NFR-5**: The app shall function fully offline once content is synced
- **NFR-6**: Data corruption recovery mechanisms shall be in place
- **NFR-7**: The app shall gracefully handle low memory conditions
- **NFR-8**: Sync operations shall be atomic (all-or-nothing)

### Usability
- **NFR-9**: New users shall be able to create their first setlist within 10 minutes
- **NFR-10**: Concert mode navigation shall be operable with single-hand gestures
- **NFR-11**: The app shall support Android accessibility standards
- **NFR-12**: UI elements shall be optimized for both phone and tablet form factors

### Compatibility
- **NFR-13**: Minimum Android API level 24 (Android 7.0) support
- **NFR-14**: Support for PDF files up to 50MB
- **NFR-15**: Image files up to 20MB shall be supported
- **NFR-16**: The app shall work across different screen densities and orientations

---

## UI/UX Flow

### Screen Hierarchy

```
Main Activity
├── Group Selection Screen
│   └── Group Management Dialog
├── Library Management Screen
│   ├── Song Details Screen
│   │   ├── Version Management Screen
│   │   └── Annotation Editor Screen
│   └── Search/Filter Screen
├── Setlist Management Screen
│   ├── Setlist Editor Screen
│   └── Setlist Selection Screen
├── Concert Mode Screen
├── Settings Screen
└── Sync Status Screen
```

### Key Screen Descriptions

#### 1. Group Selection Screen
- **Purpose**: Primary entry point for group/member management
- **Components**:
  - Group list with last accessed indicator
  - "Add Group" floating action button
  - Member selection dropdown per group
  - Quick stats (song count, setlist count)

#### 2. Library Management Screen  
- **Purpose**: Central hub for song organization
- **Components**:
  - Search bar with filter chips
  - Song grid/list with metadata preview
  - Sort options (title, artist, date added, key)
  - "Add Song" floating action button
  - Bulk selection mode

#### 3. Song Details Screen
- **Purpose**: Comprehensive song information and version management
- **Components**:
  - Metadata editing form
  - Version tabs (Piano, Guitar, Bass, etc.)
  - Content upload area (PDF/image)
  - Preview pane with annotation toggle
  - Member assignment matrix

#### 4. Annotation Editor Screen
- **Purpose**: Full-screen annotation creation/editing
- **Components**:
  - Content viewer with zoom/pan
  - Tool palette (pen, eraser, colors)
  - Undo/redo buttons
  - Layer visibility toggle
  - Save/cancel actions

#### 5. Setlist Management Screen
- **Purpose**: Overview of all setlists with quick actions
- **Components**:
  - Setlist cards with metadata
  - "New Setlist" creation option
  - Share/export buttons
  - Last modified indicators
  - Concert mode entry point

#### 6. Setlist Editor Screen
- **Purpose**: Detailed setlist composition and ordering
- **Components**:
  - Drag-and-drop song list
  - Song search and add interface
  - Version selection per song
  - Setlist metadata editor
  - Preview mode

#### 7. Concert Mode Screen
- **Purpose**: Performance-optimized fullscreen interface
- **Components**:
  - Minimal chrome design
  - Large content display area
  - Gesture navigation zones
  - Optional status indicators
  - Emergency exit gesture

### Navigation Patterns
- **Bottom Navigation**: Primary sections (Library, Setlists, Groups)
- **Top App Bar**: Context actions and search
- **Floating Action Buttons**: Primary creation actions
- **Swipe Gestures**: Concert mode navigation and quick actions
- **Long Press**: Context menus and bulk selection

---

## Data Model and Storage

### Core Entities

#### Group
```kotlin
data class Group(
    val id: String,
    val name: String,
    val createdDate: LocalDateTime,
    val lastModified: LocalDateTime,
    val members: List<Member>
)

data class Member(
    val id: String,
    val name: String,
    val role: String? = null
)
```

#### Song
```kotlin
data class Song(
    val id: String,
    val title: String,
    val artist: String? = null,
    val key: String? = null,
    val tempo: Int? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val createdDate: LocalDateTime,
    val lastModified: LocalDateTime,
    val versions: Map<String, SongVersion>
)

data class SongVersion(
    val id: String,
    val name: String,
    val memberContent: Map<String, MemberContent>
)

data class MemberContent(
    val memberId: String,
    val pdfPath: String? = null,
    val imagePath: String? = null,
    val annotationPath: String? = null,
    val textNotes: String? = null
)
```

#### Setlist
```kotlin
data class Setlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val groupId: String,
    val createdDate: LocalDateTime,
    val lastModified: LocalDateTime,
    val items: List<SetlistItem>
)

data class SetlistItem(
    val id: String,
    val songId: String,
    val versionId: String,
    val order: Int,
    val notes: String? = null
)
```

#### Annotation Layer
```kotlin
data class AnnotationLayer(
    val id: String,
    val contentId: String,
    val pageNumber: Int,
    val strokes: List<AnnotationStroke>,
    val lastModified: LocalDateTime
)

data class AnnotationStroke(
    val id: String,
    val points: List<Point>,
    val color: Int,
    val thickness: Float,
    val timestamp: LocalDateTime
)
```

### Storage Architecture

#### Local Storage (SQLite + Room)
- **Primary Database**: Core entity storage with relationships
- **File Storage**: PDF, image, and annotation files in app-specific directories
- **Cache Management**: LRU eviction for large files
- **Transaction Support**: Atomic operations for data consistency

#### Cloud Storage Integration
- **Google Drive API**: Primary sync backend
- **Dropbox API**: Alternative sync option
- **File Organization**: Structured folder hierarchy per group
- **Metadata Sync**: JSON manifests for incremental updates

#### File System Layout
```
/Android/data/com.troubashare/files/
├── groups/
│   └── {groupId}/
│       ├── songs/
│       │   └── {songId}/
│       │       ├── versions/
│       │       │   └── {versionId}/
│       │       │       ├── members/
│       │       │       │   └── {memberId}/
│       │       │       │       ├── content.pdf
│       │       │       │       ├── content.jpg
│       │       │       │       └── annotations/
│       │       │       │           └── page_{n}.json
│       │       └── metadata.json
│       └── setlists/
│           └── {setlistId}.json
├── cache/
└── sync/
    └── manifests/
```

---

## Offline/Online Sync Design

### Sync Architecture Overview
The application implements a **hybrid online-offline architecture** with eventual consistency, prioritizing offline functionality while providing seamless synchronization when connectivity is available.

### Sync Components

#### 1. Local State Management
- **Authoritative Local Store**: SQLite database serves as single source of truth for UI
- **Change Tracking**: All modifications generate change events with timestamps
- **Conflict Detection**: Vector clocks or lamport timestamps for ordering
- **Pending Operations Queue**: Store sync operations for later execution

#### 2. Cloud Storage Integration
```kotlin
interface CloudStorageProvider {
    suspend fun uploadFile(localPath: String, remotePath: String): Result<String>
    suspend fun downloadFile(remotePath: String, localPath: String): Result<Unit>
    suspend fun listFiles(remotePath: String): Result<List<CloudFile>>
    suspend fun getFileMetadata(remotePath: String): Result<CloudFileMetadata>
    suspend fun deleteFile(remotePath: String): Result<Unit>
}

class GoogleDriveProvider : CloudStorageProvider { /* ... */ }
class DropboxProvider : CloudStorageProvider { /* ... */ }
```

#### 3. Sync State Machine
```kotlin
sealed class SyncState {
    object Offline : SyncState()
    object Syncing : SyncState()
    object UpToDate : SyncState()
    data class ConflictResolution(val conflicts: List<SyncConflict>) : SyncState()
    data class Error(val message: String) : SyncState()
}
```

### Sync Strategies

#### 1. Delta Synchronization
- **Manifest-Based**: JSON manifests track file versions and checksums
- **Incremental Updates**: Only changed files are transferred
- **Bandwidth Optimization**: Compression and differential updates where possible

#### 2. Conflict Resolution
- **Detection**: Compare modification timestamps and content hashes
- **Resolution Strategies**:
  - **User Choice**: Present conflicts for manual resolution
  - **Last Writer Wins**: Automatic resolution based on timestamp (with user confirmation)
  - **Version Branching**: Keep both versions with clear labeling

#### 3. Sync Triggers
- **Manual Sync**: User-initiated via pull-to-refresh or menu action
- **Automatic Sync**: Background sync on WiFi connection
- **Event-Driven**: Sync when receiving shared setlist notifications
- **Periodic Sync**: Configurable background sync intervals

### Offline Capabilities

#### 1. Full Offline Operation
- **Complete Functionality**: All core features work without internet
- **Local Caching**: All content pre-downloaded and cached
- **Queue Operations**: Upload changes when connectivity returns
- **Status Indicators**: Clear offline/online status communication

#### 2. Selective Sync
- **On-Demand Download**: Download specific setlists/songs as needed
- **Storage Management**: User-configurable cache size limits
- **Priority System**: Prioritize recently accessed and upcoming performance content

### Data Consistency Guarantees

#### 1. ACID Properties for Local Operations
- **Atomicity**: All database operations are transactional
- **Consistency**: Foreign key constraints and data validation
- **Isolation**: Concurrent operation safety
- **Durability**: WAL mode for crash recovery

#### 2. Eventual Consistency for Sync
- **Convergence**: All devices eventually reach the same state
- **Conflict-free Replicated Data Types (CRDTs)** for annotation merging
- **Tombstone Records**: Soft deletes for sync coordination

---

## Security Considerations

### Data Protection

#### 1. Local Data Security
- **Database Encryption**: SQLCipher for encrypted local storage
- **File System Encryption**: Android's scoped storage with encryption at rest
- **Key Management**: Android Keystore for encryption key storage
- **Secure Deletion**: Overwrite sensitive data on deletion

#### 2. Cloud Storage Security
- **OAuth 2.0**: Secure authentication with cloud providers
- **Token Management**: Secure storage and refresh of access tokens
- **End-to-End Encryption**: Client-side encryption before cloud upload
- **Zero-Knowledge Architecture**: Cloud providers cannot access plaintext content

#### 3. Network Security
- **TLS/HTTPS**: All network communications encrypted in transit
- **Certificate Pinning**: Prevent man-in-the-middle attacks
- **Request Signing**: HMAC signatures for API request integrity
- **Rate Limiting**: Prevent abuse and DoS attacks

### Privacy Protection

#### 1. Data Minimization
- **No Telemetry**: No analytics or tracking without explicit consent
- **Local Processing**: All sensitive operations performed locally
- **Minimal Permissions**: Request only necessary Android permissions
- **Anonymous Usage**: Optional anonymized usage statistics

#### 2. User Control
- **Granular Permissions**: Fine-grained control over data sharing
- **Export/Portability**: Full data export capability
- **Account Deletion**: Complete data removal on user request
- **Audit Logs**: Optional logging of data access and modifications

### Access Control

#### 1. Multi-User Security
- **User Isolation**: Strict separation of different user data
- **Group Permissions**: Role-based access within groups
- **Sharing Controls**: Explicit consent for content sharing
- **Revocation**: Ability to revoke access to shared content

#### 2. Device Security
- **Screen Lock Integration**: Require device unlock for app access
- **Biometric Authentication**: Optional fingerprint/face unlock
- **Session Management**: Automatic lock after inactivity
- **Wipe on Compromise**: Remote wipe capability for lost devices

### Compliance and Best Practices

#### 1. Regulatory Compliance
- **GDPR Compliance**: EU data protection regulation adherence
- **CCPA Compliance**: California consumer privacy act compliance
- **Data Retention**: Configurable retention policies
- **Consent Management**: Clear, granular consent mechanisms

#### 2. Security Development Lifecycle
- **Secure Coding**: OWASP mobile security guidelines
- **Dependency Scanning**: Regular security audit of dependencies
- **Penetration Testing**: Regular security assessments
- **Vulnerability Management**: Rapid response to security issues

---

## Future Enhancements

### Phase 2 Features (6-12 months)

#### 1. Enhanced Collaboration
- **Real-time Collaboration**: Live editing of setlists with conflict resolution
- **Group Chat Integration**: In-app messaging for performance coordination
- **Voice Annotations**: Audio notes attached to songs or pages
- **Video Integration**: Performance recordings linked to setlist items

#### 2. Advanced Music Features
- **Transposition Engine**: Automatic key changes with chord detection
- **Metronome Integration**: Built-in metronome with setlist tempo sync
- **Audio Playback**: MP3/WAV file support for backing tracks or references
- **MIDI Integration**: Connect with electronic instruments and DAWs

#### 3. Performance Analytics
- **Usage Statistics**: Song popularity and performance frequency tracking
- **Performance Timing**: Actual vs. estimated song durations
- **Setlist Analytics**: Performance success metrics and recommendations
- **Member Engagement**: Track individual member app usage and contributions

### Phase 3 Features (1-2 years)

#### 1. AI-Powered Features
- **Smart Setlist Generation**: ML-based setlist recommendations based on historical data
- **Optical Music Recognition**: Automatic chord chart extraction from images
- **Natural Language Processing**: Voice-to-text for quick note-taking
- **Performance Prediction**: Suggest optimal song ordering based on energy levels

#### 2. Extended Platform Support
- **iOS Version**: Complete feature parity iOS application
- **Web Application**: Browser-based access for quick editing and viewing
- **Desktop Sync**: Windows/Mac desktop applications for large-screen editing
- **Smart TV Integration**: Display setlists on TVs for audience viewing

#### 3. Professional Features
- **Multi-Group Management**: Professional users managing multiple bands
- **Client Portal**: Venue/event organizer access to setlists and requirements
- **Integration APIs**: Connect with booking platforms and music services
- **Advanced Reporting**: Comprehensive analytics and performance reports

### Technical Debt and Improvements

#### 1. Performance Optimization
- **Database Optimization**: Query optimization and indexing improvements
- **Memory Management**: Reduce memory footprint for large libraries
- **Background Processing**: Improved sync performance with coroutines
- **UI Optimization**: Lazy loading and efficient rendering for large lists

#### 2. Architecture Evolution
- **Modular Architecture**: Feature-based modules for better maintainability
- **Reactive Architecture**: Full migration to reactive programming patterns
- **Microservices Backend**: Dedicated backend services for enhanced features
- **GraphQL Integration**: More efficient data fetching for complex queries

#### 3. Developer Experience
- **Automated Testing**: Comprehensive test suite with CI/CD pipeline
- **Code Generation**: Reduce boilerplate with annotation processing
- **Documentation**: Interactive documentation with code examples
- **Monitoring**: Production monitoring and crash reporting integration

---

## Technical Implementation Notes

### Development Stack
- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite wrapper)
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **PDF Rendering**: PdfRenderer (Android framework)
- **Dependency Injection**: Hilt
- **Async Operations**: Kotlin Coroutines + Flow

### Key Libraries and Dependencies
```gradle
// UI and Architecture
implementation 'androidx.compose.ui:ui:$compose_version'
implementation 'androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version'
implementation 'androidx.navigation:navigation-compose:$nav_version'

// Database and Storage
implementation 'androidx.room:room-runtime:$room_version'
implementation 'androidx.room:room-ktx:$room_version'

// Network and Cloud Integration
implementation 'com.google.api-client:google-api-client-android:$google_api_version'
implementation 'com.dropbox.core:dropbox-core-sdk:$dropbox_version'

// Graphics and Annotations
implementation 'androidx.graphics:graphics-core:$graphics_version'
implementation 'io.coil-kt:coil-compose:$coil_version'

// Security
implementation 'net.sqlcipher:android-database-sqlcipher:$sqlcipher_version'
```

### Performance Considerations
- **Lazy Loading**: Implement virtual scrolling for large song lists
- **Image Optimization**: Automatic image compression and caching
- **Background Sync**: Use WorkManager for reliable background operations
- **Memory Management**: Implement proper lifecycle-aware resource management

This technical design document provides a comprehensive foundation for developing TroubaShare, ensuring scalability, maintainability, and excellent user experience for band setlist management and performance coordination.