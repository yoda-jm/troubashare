# Annotation State Management Analysis

## Current Architecture Problems

### 1. **State Flow Architecture**

```
User Interaction (Toolbar/Canvas)
    ↓
onDrawingStateChanged (updates UI state immediately)
    ↓
onStrokeUpdated (persists to database)
    ↓
updateStroke() in ViewModel
    ↓
annotationRepository.removeStrokeFromAnnotation()
annotationRepository.addStrokeToAnnotation()
    ↓
loadAnnotations() ← **PROBLEM: Reloads everything from database**
    ↓
_annotations.value = annotationList (async Flow)
    ↓
UI recomposed with database values (overwrites slider changes)
```

### 2. **Critical Issues with Current Implementation**

#### Issue #1: **Race Condition Between UI State and Database State**

**What happens:**
1. User drags width slider from 5px to 10px
2. `onValueChange` updates `drawingState.selectedStroke.strokeWidth = 10`
3. UI shows 10px (correct)
4. `onValueChangeFinished` calls `onStrokeUpdated(stroke, stroke)`
5. ViewModel's `updateStroke()` starts async operation
6. `loadAnnotations()` is called, which starts async Flow collection
7. **Meanwhile** user continues dragging slider to 12px
8. UI shows 12px
9. Database finishes loading, emits value with strokeWidth = 10
10. UI reverts to 10px ← **BUG: User sees value revert**

**Why it happens:**
- `loadAnnotations()` is called after EVERY database update
- Database operations are async
- Flow emissions happen on a different schedule than UI updates
- No debouncing or batching of updates

#### Issue #2: **Slider Remember State Captures at Wrong Time**

**Current implementation:**
```kotlin
var sliderStartStroke by remember { mutableStateOf<AnnotationStroke?>(null) }

Slider(
    onValueChange = {
        if (sliderStartStroke == null) {
            sliderStartStroke = drawingState.selectedStroke // Captures here
        }
        // Update UI
    },
    onValueChangeFinished = {
        sliderStartStroke?.let { start ->
            onStrokeUpdated(start, drawingState.selectedStroke)
            sliderStartStroke = null
        }
    }
)
```

**Problem:**
- `remember` without keys means state persists across recompositions
- If user drags slider, releases, then drags again WITHOUT changing selection:
  - `sliderStartStroke` is still `null` from previous reset
  - Correctly captures new start value ✓
- BUT if database reload triggers recomposition DURING drag:
  - `sliderStartStroke` might get confused
  - The captured "start" value might not match actual drag start

#### Issue #3: **Calling onStrokeUpdated with Same Stroke Twice**

**Current code:**
```kotlin
onValueChangeFinished = {
    val currentStroke = drawingState.selectedStroke
    onStrokeUpdated?.invoke(currentStroke, currentStroke) // Same stroke!
}
```

**Problem:**
- ViewModel's `updateStroke(old, new)` expects DIFFERENT strokes
- It does `removeStrokeFromAnnotation(old)` then `addStrokeToAnnotation(new)`
- When `old == new` (same ID), it removes and re-adds the SAME stroke
- This is inefficient and can cause race conditions

#### Issue #4: **Color Picker Immediate Persistence**

**Current code:**
```kotlin
.clickable {
    val oldStroke = drawingState.selectedStroke
    val updatedStroke = oldStroke.copy(color = newColor)
    onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
    onStrokeUpdated?.invoke(oldStroke, updatedStroke) // Persists immediately
}
```

**Problem:**
- Every color change triggers database update + reload
- User rapidly clicking colors causes database thrashing
- Each update calls `loadAnnotations()` which is async
- Multiple overlapping Flow emissions can arrive out of order

### 3. **What Bugs You Should Observe**

Based on this architecture, you should observe:

#### Bug A: **Slider Values Revert After Dragging**
- **Steps to reproduce:**
  1. Select a stroke
  2. Drag width slider from 5px → 15px rapidly
  3. Watch the value
- **Expected:** Slider stays at 15px
- **Actual:** Slider jumps back to intermediate value (like 10px or 12px)
- **Why:** Database reload from earlier update overwrites current UI state

#### Bug B: **Slider Drag Feels Choppy/Laggy**
- **Steps to reproduce:**
  1. Select a stroke
  2. Drag slider smoothly
  3. Observe responsiveness
- **Expected:** Smooth continuous drag
- **Actual:** Slider thumb jumps around, doesn't follow finger smoothly
- **Why:** Multiple async database reloads causing recompositions during drag

#### Bug C: **Color Changes Sometimes Don't Stick**
- **Steps to reproduce:**
  1. Select a stroke
  2. Rapidly click different colors (Red → Blue → Green → Yellow)
  3. Check final color
- **Expected:** Stroke is Yellow
- **Actual:** Stroke might be Blue or Green (wrong color)
- **Why:** Database updates arrive out of order, later-started but earlier-finished update overwrites

#### Bug D: **Moving Stroke After Editing Reverts Changes**
- **Steps to reproduce:**
  1. Select a stroke
  2. Change color to Red
  3. Change width to 15px
  4. Immediately drag stroke to new position
- **Expected:** Stroke stays Red, 15px wide
- **Actual:** Stroke reverts to original color/width when you release drag
- **Why:** `originalStroke` in drag handler captures state from `drawingState.selectedStroke` at drag start, but database reload might have overwritten this with old values

#### Bug E: **Slider Click-to-Position Doesn't Work**
- **Steps to reproduce:**
  1. Select a stroke
  2. Click directly on slider track (not on thumb)
  3. Observe value
- **Expected:** Slider jumps to clicked position
- **Actual:** Slider sets value, then immediately reverts
- **Why:** `onValueChange` fires once, then `onValueChangeFinished` fires immediately, triggering database reload that reverts

---

## Proposed Solution: Optimistic UI with Delayed Persistence

### Core Principles

1. **Separate UI State from Database State**
   - UI state (`drawingState.selectedStroke`) is source of truth for rendering
   - Database state (`_annotations`) is source of truth for persistence
   - Never let database reload overwrite active UI edits

2. **Optimistic Updates**
   - All UI changes update state immediately
   - Persistence happens in background
   - Only reload from database when necessary

3. **Debounced Persistence**
   - Batch multiple rapid edits into single database transaction
   - Use debounce timer (e.g., 500ms after last edit)
   - Cancel pending saves if new edit arrives

4. **Explicit Save Points**
   - Persist when:
     - User deselects stroke
     - User switches to different tool
     - User selects different stroke
     - User navigates away
     - Auto-save timer expires after edits

### New State Flow

```
User Interaction (Toolbar/Canvas)
    ↓
onDrawingStateChanged (updates UI state immediately)
    ↓
UI renders from drawingState.selectedStroke (optimistic)
    ↓
[Optional] Start debounce timer (500ms)
    ↓
Timer expires OR explicit save point reached
    ↓
onStrokeUpdated (persists to database)
    ↓
updateStroke() in ViewModel
    ↓
annotationRepository.updateStroke()  ← NEW: Single update operation
    ↓
Update _annotations.value directly WITHOUT reloading
    ↓
IF selectedStroke.id matches, DO NOT overwrite drawingState.selectedStroke
```

### Implementation Strategy

#### Change 1: **Remove loadAnnotations() from updateStroke()**

**Before:**
```kotlin
fun updateStroke(oldStroke: AnnotationStroke, newStroke: AnnotationStroke) {
    viewModelScope.launch {
        annotationRepository.removeStrokeFromAnnotation(it.id, oldStroke)
        annotationRepository.addStrokeToAnnotation(it.id, newStroke)
        loadAnnotations() // ← REMOVE THIS
    }
}
```

**After:**
```kotlin
fun updateStroke(oldStroke: AnnotationStroke, newStroke: AnnotationStroke) {
    viewModelScope.launch {
        // Update database
        annotationRepository.removeStrokeFromAnnotation(it.id, oldStroke)
        annotationRepository.addStrokeToAnnotation(it.id, newStroke)

        // Update local state directly (optimistic)
        _annotations.value = _annotations.value.map { annotation ->
            if (annotation.strokes.any { it.id == oldStroke.id }) {
                annotation.copy(
                    strokes = annotation.strokes.map { stroke ->
                        if (stroke.id == oldStroke.id) newStroke else stroke
                    }
                )
            } else {
                annotation
            }
        }

        // Update selectedStroke ONLY if not currently being edited
        if (_drawingState.value.selectedStroke?.id == oldStroke.id &&
            !isCurrentlyEditing) {
            _drawingState.value = _drawingState.value.copy(selectedStroke = newStroke)
        }
    }
}
```

#### Change 2: **Add Debounce to Slider Updates**

**Before:**
```kotlin
Slider(
    onValueChange = { newWidth ->
        val updatedStroke = drawingState.selectedStroke.copy(strokeWidth = newWidth)
        onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
    },
    onValueChangeFinished = {
        onStrokeUpdated?.invoke(currentStroke, currentStroke)
    }
)
```

**After:**
```kotlin
// In composable scope
var pendingSaveJob by remember { mutableStateOf<Job?>(null) }
val scope = rememberCoroutineScope()

Slider(
    onValueChange = { newWidth ->
        // Update UI immediately
        val oldStroke = drawingState.selectedStroke
        val updatedStroke = oldStroke.copy(strokeWidth = newWidth)
        onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))

        // Cancel pending save and schedule new one
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(500) // Debounce 500ms
            onStrokeUpdated?.invoke(oldStroke, updatedStroke)
        }
    },
    onValueChangeFinished = {
        // Force immediate save when user releases
        pendingSaveJob?.cancel()
        scope.launch {
            val finalStroke = drawingState.selectedStroke
            onStrokeUpdated?.invoke(initialStroke, finalStroke)
        }
    }
)
```

#### Change 3: **Track "Currently Editing" State**

Add to ViewModel:
```kotlin
private val _isEditingStroke = MutableStateFlow(false)
val isEditingStroke: StateFlow<Boolean> = _isEditingStroke.asStateFlow()

fun setEditingStroke(editing: Boolean) {
    _isEditingStroke.value = editing
}
```

Use in UI:
```kotlin
// When slider drag starts
LaunchedEffect(isSliderDragging) {
    viewModel.setEditingStroke(isSliderDragging)
}
```

#### Change 4: **Batch Color Changes**

Don't persist immediately on every color click. Instead:
```kotlin
.clickable {
    val oldStroke = drawingState.selectedStroke
    val updatedStroke = oldStroke.copy(color = newColor)
    onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
    // Don't call onStrokeUpdated here!
}

// Persist when deselecting or switching tools
LaunchedEffect(drawingState.selectedStroke?.id, drawingState.tool) {
    // Previous stroke's edits should be saved when selection changes
    snapshotFlow { drawingState.selectedStroke }.collect { currentStroke ->
        // Save previous stroke if it changed
    }
}
```

#### Change 5: **Better AnnotationCanvas Drag**

```kotlin
// At drag start, capture stroke from UI state
val dragStartStroke = drawingState.selectedStroke

// During drag, update UI only
var currentStroke = dragStartStroke
drag(down.id) { change ->
    val updatedStroke = currentStroke.copy(/* new position */)
    onDrawingStateChanged(drawingState.copy(selectedStroke = updatedStroke))
    currentStroke = updatedStroke
}

// After drag ends, persist once
onStrokeUpdated(dragStartStroke, currentStroke)
```

---

## Expected Behavior After Fix

1. **Slider drag**: Smooth, continuous, no jumps or reversions
2. **Color changes**: Instant visual feedback, no flickering
3. **Drag after edit**: Preserves all color/width/opacity changes
4. **Database**: Updated in background without disrupting UI
5. **Performance**: No unnecessary database round-trips

---

## Migration Path

1. **Phase 1**: Remove `loadAnnotations()` from `updateStroke()`
2. **Phase 2**: Add debouncing to sliders
3. **Phase 3**: Add "currently editing" flag
4. **Phase 4**: Batch color picker updates
5. **Phase 5**: Test all scenarios

Each phase can be tested independently.
