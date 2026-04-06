# Access Modes & Conductor Design

## Overview

Three modes are available when entering the library. The mode determines which
layers are visible and which are editable.

| Mode | Edits | Sees |
|------|-------|------|
| **Admin** | Shared layer + library structure | All shared + promoted layers |
| **Performer** | Own personal layer | Own + shared + promoted layers (read-only) |
| **Conductor** | Own personal layer | Everything (all members' layers, read-only except own) |

## Layer visibility rules

```
Admin:      layer.isShared || layer.isPromoted
Performer:  layer.ownerId == memberId || layer.isShared || layer.isPromoted
Conductor:  true   (all layers visible)
```

`canEditLayer()` is unchanged — only the layer owner can write to their layer.

## Promoted layers

A conductor (or any member) can **promote** one of their personal layers so it
becomes read-only visible to all other members, like a broadcast.

This is distinct from the group shared layer:

| | Group shared layer | Promoted layer |
|---|---|---|
| Owner | `_shared_` (admin) | A real member (e.g. conductor) |
| Editable by | Admin mode | Owner only |
| Visible to | All | All |
| Purpose | Band-wide generic notes | Conductor cues, tempo markings |

### Data model change

One new boolean on `AnnotationLayer`:

```kotlin
val isPromoted: Boolean = false
```

DB migration v12 → v13:
```sql
ALTER TABLE annotation_layers ADD COLUMN isPromoted INTEGER NOT NULL DEFAULT 0
```

### UX

- In the layer sheet, each personal layer has a "Share with band" toggle.
- Promoted layers show a broadcast icon instead of the person icon.
- Members see promoted layers as read-only, labeled with the owner's name.
- Conductor mode: all layers visible, with name + "(read-only)" for layers not
  owned by the current user.

## Mode selection

Mode is a **session choice** — not stored on the Member entity. When opening
the library the user picks Admin / Performer / Conductor from a dialog or
settings screen. The chosen mode drives:

- Which navigation destinations are accessible (admin screens hidden in
  Performer/Conductor mode).
- Which layers are loaded in `FileViewerViewModel.loadLayersAndAnnotations()`.
- Which layer is auto-selected as active on open.

The current member identity (who is "me") is set separately and is orthogonal
to the mode.
