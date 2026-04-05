# TroubaShare — Data Model & Storage Design

## Core Concepts

A **Group** is a band, choir, or ensemble. It has **Members** and optionally **Parts**.

A **Part** is a named role within the group (Vocalist, Guitarist, Soprano, Piano…).
Parts are optional — small bands often don't need them. Whether a group uses Parts is
chosen at group creation time via a **group type**:

- **Band** — no Parts. Each member just has a name. Every member manages their own
  file selections independently.
- **Ensemble** — Parts are defined (Soprano, Alto, Tenor, Bass; or Guitar, Bass, Drums…).
  Members are assigned to one or more Parts. File selections can be managed at the
  Part level so all members of a Part automatically see the right files.

Under the hood the data model is identical — Parts are simply absent in Band mode.
The group type only controls which UI surfaces are shown.

A **Song** has a pool of **files** (PDFs, images, tabs, chord charts…). Files belong to
the song, not to any member. Anyone can upload into the pool.

Each Member (or Part in Ensemble mode) **selects** which files from the pool they
actually use for a given song. The guitarist picks `guitar_tabs.pdf` + `lyrics.pdf`.
The drummer picks only `drum_chart.pdf`. A member who sings and plays guitar picks
from both sets.

**Annotations** are written by a Member on a specific file. They have a **scope**:
- `PERSONAL` — only visible to the author (default)
- `PART` — visible to all members of the author's Part (Ensemble mode only)
- `ALL` — visible to everyone in the group (conductor cues, shared rehearsal marks)

---

## Entities

### Group
```
id          String
name        String
type        Enum    BAND | ENSEMBLE
createdAt   Long
updatedAt   Long
```

### Part  (Ensemble mode only)
```
id          String
groupId     String  → Group
name        String   (e.g. "Soprano", "Guitarist", "Drums")
color       String?  (optional hex color for UI distinction)
```

### Member
```
id          String
groupId     String  → Group
name        String
partIds     List<String>  → Part[]  (empty in Band mode; one or more in Ensemble mode)
```

### Song
```
id          String
groupId     String  → Group
title       String
artist      String?
key         String?
tempo       Int?
tags        List<String>
notes       String?
createdAt   Long
updatedAt   Long
```

### SongFile  (files belong to the song, not to a member)
```
id           String
songId       String  → Song
fileName     String
filePath     String  (local path; file named by UUID on disk)
fileType     Enum    PDF | IMAGE
uploadedBy   String  → Member  (audit only, not access control)
createdAt    Long
```

### FileSelection
Links a Member (Band mode) or a Part (Ensemble mode) to the files they use for a song.

```
id            String
songFileId    String  → SongFile
selectionType Enum    MEMBER | PART
memberId      String?  → Member  (Band mode, or personal override in Ensemble mode)
partId        String?  → Part    (Ensemble mode default, applies to all Part members)
displayOrder  Int
```

In Ensemble mode a Part-level selection is the default for all members of that Part.
A Member-level selection is a personal addition or reordering on top of what the Part
provides.

### Annotation
```
id          String
fileId      String  → SongFile
memberId    String  → Member  (who wrote it)
pageNumber  Int
scope       Enum    PERSONAL | PART | ALL
partId      String?  → Part  (only when scope = PART)
createdAt   Long
updatedAt   Long
strokes     → AnnotationStroke[]
```

### AnnotationStroke
```
id           String  (UUID — stable identifier for merge)
annotationId String  → Annotation
tool         Enum    PEN | TEXT
color        Long    (ARGB)
strokeWidth  Float
opacity      Float
text         String?  (TEXT tool only)
points       → AnnotationPoint[]
createdAt    Long
```

### AnnotationPoint
```
strokeId    String  → AnnotationStroke
x           Float   (0.0–1.0, relative to page width)
y           Float   (0.0–1.0, relative to page height)
```
Per-point pressure and timestamps are omitted — not used in practice.

### Setlist
```
id          String
groupId     String  → Group
name        String
venue       String?
eventDate   Long?
description String?
items       → SetlistItem[]
createdAt   Long
updatedAt   Long
```

### SetlistItem
```
id          String
setlistId   String  → Setlist
songId      String  → Song
position    Int
key         String?   (performance override)
tempo       Int?      (performance override)
notes       String?
duration    Int?      (seconds)
```

---

## Relationship Map

```
Group (BAND or ENSEMBLE)
 ├── Part[]           ← Ensemble only
 │    └── Member[] (via partIds)
 ├── Member[]         ← all modes
 ├── Song[]
 │    ├── SongFile[]        (the pool — belongs to the song)
 │    └── FileSelection[]   (Member or Part picks from the pool)
 └── Setlist[]
      └── SetlistItem[] → Song
```

---

## How it works in practice

### Band mode — "The Trio", song "Hey Jude"

No Parts. Each member picks their own files.

```
Members:  Alice · Bob · Charlie

Files in pool:
  lyrics.pdf
  guitar_tabs.pdf
  drum_chart.pdf
  full_score.pdf

FileSelections (all MEMBER type):
  Alice   → lyrics.pdf          order 1
  Bob     → guitar_tabs.pdf     order 1
  Bob     → lyrics.pdf          order 2
  Charlie → drum_chart.pdf      order 1

Annotations on guitar_tabs.pdf:
  Bob's personal fingering notes  → scope PERSONAL  (only Bob sees)
  Bob shares a timing note        → scope ALL        (Alice and Charlie see it too)
```

### Ensemble mode — "City Choir", song "Ave Maria"

Parts defined. File selections managed at Part level.

```
Parts:    Soprano · Alto · Tenor · Bass · Conductor
Members:  8 sopranos, 6 altos, 6 tenors, 4 basses, 1 conductor

Files in pool:
  soprano_part.pdf
  alto_part.pdf
  tenor_part.pdf
  bass_part.pdf
  full_score.pdf

FileSelections (PART type — set once by conductor/admin):
  Part Soprano    → soprano_part.pdf
  Part Alto       → alto_part.pdf
  Part Tenor      → tenor_part.pdf
  Part Bass       → bass_part.pdf
  Part Conductor  → full_score.pdf

All 8 sopranos automatically see soprano_part.pdf — uploaded once.

Alice (soprano) annotates with scope=PERSONAL → only Alice sees it.
Conductor annotates soprano_part.pdf with scope=ALL → all sopranos see it.
Section leader annotates with scope=PART → all sopranos see it.
```

---

## Versioning & Sync State

### The core problem

A missing item in a remote copy is ambiguous: was it never there, or was it deleted?
Without a record of the deletion you cannot tell. Similarly, "which copy is newer"
cannot be answered by wall-clock time alone — two devices can have wrong clocks, or
both can have changed different things simultaneously.

### Device identity

Every installation generates a stable **deviceId** (UUID) on first launch. This never
changes and is stored locally. It identifies the source of every write.

### Per-entity version metadata

Every mutable entity (Song, SongFile, FileSelection, Member, Part, Annotation,
AnnotationStroke) carries:

```
version     Int     starts at 1, increments on every local change
updatedBy   String  deviceId of last writer
updatedAt   Long    wall-clock timestamp (informational only, not used for ordering)
```

### Device sequence numbers

Each device maintains a monotonically increasing **local sequence counter** — a single
integer that increments with every write operation (any entity, any change). This is
the authoritative "how far along am I" number.

`manifest.json` records the last known sequence for every device that has ever written
to this group:

```json
{
  "schemaVersion": 1,
  "devices": {
    "alice-tablet": { "name": "Alice's Tablet", "lastSeq": 23, "lastSeenAt": 1712345678 },
    "bob-phone":    { "name": "Bob's Phone",    "lastSeq": 41, "lastSeenAt": 1712345699 }
  }
}
```

**Am I newer or older?**
- Remote `devices[myDeviceId].lastSeq` < my local seq → remote is behind, push my changes
- Remote `devices[myDeviceId].lastSeq` > my local seq → impossible (I wrote those changes)
- Remote `devices[otherDeviceId].lastSeq` > my `lastSeenSeq[otherDevice]` → they have
  new changes I haven't pulled yet

### Tombstones — how deletions work

**Never physically remove a record.** Mark it deleted instead:

```
deleted     Boolean   true when deleted
deletedAt   Long      timestamp
deletedBy   String    deviceId
```

A tombstone propagates to every device on sync. Once all known devices have a
`lastSeq` that covers the deletion event, the tombstone can be garbage-collected
(this is optional — keeping tombstones indefinitely is safe, just slightly wasteful).

**Why this matters:** if Alice deletes a song and Bob syncs, Bob sees
`deleted: true` and removes it locally. Without tombstones, Bob would never know the
song was deleted — he'd just see it missing and assume it was never on his device.

### Stroke-level versioning in JSONL

The JSONL annotation files use the same pattern at the stroke level. Deleted strokes
stay in the file as tombstone lines:

```json
{"id":"a1b2c3","page":0,"tool":"PEN","color":"#FF000000","width":5.0,"opacity":1.0,"pts":"0.102,0.203;0.304,0.405","v":3,"by":"alice-tablet","at":1712345678}
{"id":"d4e5f6","deleted":true,"deletedAt":1712345699,"deletedBy":"bob-phone"}
```

Fields added to every stroke line: `"v"` (version), `"by"` (deviceId), `"at"` (timestamp).

---

## Storage

### Local (on device)
Room DB for all structured data (groups, songs, setlists, parts, selections,
annotations). Fast queries, offline access, reactive flows.

PDF and image files stored under the app's files directory, named by UUID:
```
<app>/files/<uuid>.pdf
<app>/files/<uuid>.jpg
```

### JSONL annotation format (export / sync)
One file per `(songFileId, memberId, scope)`. One stroke per line.

```
annotations/<fileId>_<memberId>_personal.jsonl
annotations/<fileId>_<partId>_part.jsonl
annotations/<fileId>_all.jsonl
```

Each line (live stroke):
```json
{"id":"a1b2c3","page":0,"tool":"PEN","color":"#FF000000","width":5.0,"opacity":1.0,"pts":"0.102,0.203;0.304,0.405","v":1,"by":"alice-tablet","at":1712345678}
```

Each line (deleted stroke — tombstone):
```json
{"id":"d4e5f6","deleted":true,"deletedAt":1712345699,"deletedBy":"bob-phone"}
```

**Why one stroke per line:**
- Add a stroke → append a line
- Delete a stroke → replace the line with a tombstone
- Two people add different strokes → no conflict (different UUIDs, different lines)
- Two people edit the same stroke → conflict on exactly that one line (same UUID,
  different `v`/`by` → show both, let user choose)

### Sync folder (Google Drive or local share)
Sync as a plain folder — no zip needed. Drive handles versioning natively and shows
diffs on the text files.

```
troubashare/
  manifest.json                        ← device registry + all metadata (groups,
                                          songs, setlists, parts, members, selections)
  files/
    <uuid>.pdf                         ← content-addressed; written once, never
    <uuid>.jpg                            overwritten (UUID = identity)
  annotations/
    <fileId>_<memberId>_personal.jsonl
    <fileId>_<partId>_part.jsonl
    <fileId>_all.jsonl
```

**Sync rules:**

| What | Rule |
|------|------|
| `files/` | Immutable. Same UUID = same content. Never overwrite. |
| New file in remote | UUID not seen locally → download it |
| File tombstone in remote manifest | `deleted:true` → remove locally |
| `annotations/*.jsonl` | Merge by stroke UUID (see below) |
| `manifest.json` | Merge per-entity by `version` field; higher version wins |
| Conflict | Same UUID, same `v`, different content → flag for user |

**JSONL merge (per file):**
```
for each line in remote .jsonl:
  stroke is tombstone (deleted:true):
    mark stroke deleted locally, regardless of local version
  stroke not in local file:
    add it
  stroke in local file, same v:
    identical → skip
  stroke in local file, remote v > local v:
    update local to remote version
  stroke in local file, both changed (different v, different by):
    flag conflict → show both versions, let user pick
```

### Export / sharing (email, one-off zip)
Zip the sync folder. Recipient imports by applying on top of their local state using
the same JSONL merge algorithm above. The device registry in `manifest.json` tells
the importer exactly which sequences are new:

```
read manifest.json → devices[]
for each device not yet known locally, or with lastSeq > my lastSeenSeq[device]:
  those changes are new → apply them
  update my lastSeenSeq[device]
```

No zip-to-zip diffing needed. The sequence numbers make it unambiguous what is new.

---

## Implementation order

1. Add `Group.type` (BAND | ENSEMBLE)
2. Add `Part` entity; wire to Group (only shown when type = ENSEMBLE)
3. Add `Member.partIds` (empty list in Band mode)
4. Remove `SongFile.memberId` (ownership); add `uploadedBy` (audit)
5. Add `FileSelection` entity (Member-level for Band; Part- or Member-level for Ensemble)
6. Add `Annotation.scope` and `Annotation.partId`
7. Update song detail UI — show file pool + per-member/part selections
8. Update concert mode — load files via selections for the current member
9. JSONL export/import — zip the sync folder, apply-on-top merge on import
10. Drive sync — sync folder directly, no zip
