# Edge Cases: Recursive List Shrinking

## Definition of "Minimal"

**A value is minimal if it has been generated and has no shrinks.**

**IMPORTANT**: This is a conceptual definition for documentation purposes only. We do NOT check if a sequence is empty (
e.g., `.isEmpty()` or `.none()`) in the implementation, as `shrinks` is a lazy `Sequence` and materializing it would
cause performance issues.

The implementation relies on **lazy evaluation**:

- If a sequence is empty, iterating over it with `forEach` or `flatMap` simply does nothing
- No explicit checks needed - empty sequences naturally contribute nothing
- The shrink generation logic automatically produces empty sequences when appropriate (e.g., when a value is at its
  origin)

## Edge Case Catalog

### 1. Empty List

**Input**: `[]` (size = 0)

**Expected Shrinks**: Empty sequence (no shrinks)

**Why**:

- Size is already at origin (0)
- No elements to shrink
- Already the simplest possible list

**Implementation Check**:

```kotlin
if (size == 0) return GenResult(emptyList(), emptySequence())
```

---

### 2. All Elements Minimal

**Input**: `[0, 0, 0]`

- All elements have `GenResult.shrinks.isEmpty()`
- Assuming Int origin is 0

**Expected Shrinks**:

- Size shrinks: `[]`, `[0]`, `[0, 0]` (tail removal), `[0, 0]` (head removal)
- Element shrinks: None (all elements are minimal)

**Why**:

- Elements cannot shrink further (already at origin)
- But list size can still shrink toward 0

**Recursive Behavior**:

- `[0, 0]` (size shrink) itself shrinks to:
    - Size: `[]`, `[0]`
    - Elements: None
- `[0]` (size shrink) itself shrinks to:
    - Size: `[]`
    - Elements: None

---

### 3. Single Element, Minimal

**Input**: `[0]`

- Element is at origin, has no shrinks

**Expected Shrinks**:

- Size shrinks: `[]`
- Element shrinks: None

**Why**: Element is already minimal, only size can reduce

**Recursive Behavior**:

- `[]` (the size shrink) has no further shrinks (termination)

---

### 4. Single Element, Non-Minimal

**Input**: `[5]`

- Element shrinks to: `0`, `2`, `3` (assuming Int(0..∞))

**Expected Shrinks**:

- Size shrinks: `[]`
- Element shrinks: `[0]`, `[2]`, `[3]`

**Total**: 4 shrinks

**Why**: Both size and element can shrink

**Recursive Behavior**:

- `[]` shrinks to: (nothing - termination)
- `[0]` shrinks to: `[]` (element is now minimal)
- `[2]` shrinks to: `[]`, `[0]`, `[1]` (recursive element shrinking)
- `[3]` shrinks to: `[]`, `[0]`, `[2]` (recursive element shrinking)

This is the power of recursive shrinking!

---

### 5. Mixed Minimal/Non-Minimal Elements

**Input**: `[0, 5]`

- Element 0: minimal (no shrinks)
- Element 1: non-minimal (shrinks to 0, 2, 3)

**Expected Shrinks**:

- Size shrinks: `[]`, `[0]` (tail), `[5]` (head)
- Element shrinks:
    - Index 0: None (element is minimal)
    - Index 1: `[0, 0]`, `[0, 2]`, `[0, 3]`

**Total**: 6 shrinks

**Why**: Only non-minimal elements contribute to element shrinks

**Recursive Behavior**:

- `[5]` (head removal) shrinks to: `[]`, `[0]`, `[2]`, `[3]`
- `[0, 0]` shrinks to: `[]`, `[0]` (both elements now minimal)

---

### 6. Size Already at Range Minimum

**Input**: List of size 2 with `sizeRange = 2..5`

- Example: `[3, 7]`

**Expected Shrinks**:

- Size shrinks: None (size 2 is already at origin of range)
- Element shrinks:
    - Index 0: `[0, 7]`, ... (if 3 shrinks to 0, etc.)
    - Index 1: `[3, 0]`, ... (if 7 shrinks to 0, etc.)

**Why**:

- IntGenerator.shrink(2, 2..5, origin=2) returns empty sequence
- Size is at its minimum, cannot shrink further

**Recursive Behavior**:

- Element shrinks themselves can still shrink recursively
- Size shrinks only appear when element shrinking creates smaller lists? No - element shrinking keeps size constant

---

### 7. Recursive Shrinking Termination

**Scenario**: `[1]` shrinks to `[0]`

**When recursively shrinking `[0]`**:

- Size can shrink: 1 → 0, yielding `[]`
- Element cannot shrink: `0` is minimal

**So `[0]` shrinks to**: `[]`

**When recursively shrinking `[]`**:

- Size cannot shrink: already at 0
- No elements to shrink

**So `[]` shrinks to**: (empty sequence - termination!)

**Full shrink tree**:

```
[1]
├─ [] (dead end)
└─ [0]
   └─ [] (dead end)
```

**Termination Guarantee**:

- Size monotonically decreases toward 0
- Elements monotonically approach their origin
- Both are bounded below
- Therefore, recursion MUST terminate

---

## Implementation Implications

### 1. Empty List Guard

```kotlin
if (size == 0) return GenResult(emptyList(), emptySequence())
```

This immediately returns for empty lists, preventing unnecessary work.

### 2. Element Shrink Generation - No Filtering Needed

We do NOT filter elements by checking if they have shrinks:

```kotlin
// ✅ CORRECT - Don't check if shrinks exist
elementResults.indices.asSequence().flatMap { index ->
    elementResults[index].shrinks.map { ... }
    // If shrinks is empty, flatMap produces nothing naturally
}

// ❌ WRONG - Don't materialize the sequence!
if (elementResults[index].shrinks.any()) { ... }  // This evaluates the lazy sequence!
```

The lazy sequence approach is cleaner and more efficient. Empty sequences naturally contribute nothing when we use
`flatMap` or `forEach`.

### 3. Size Origin Calculation

```kotlin
val origin = when {
    sizeRange.last < 0 -> sizeRange.last
    sizeRange.first > 0 -> sizeRange.first
    else -> 0
}
```

This matches IntGenerator's logic for determining the origin.

### 4. Natural Termination

No explicit termination check needed! The recursion terminates naturally:

- Empty list returns `emptySequence()`
- Elements with no shrinks contribute nothing to element shrinks
- Size with no shrinks (at origin) contributes nothing to size shrinks

---

## Test Coverage

These edge cases should be covered by:

1. **Existing tests**:
    - Empty list behavior (if it exists)
    - Lists with multiple elements

2. **New tests needed**:
    - All elements minimal: `[0, 0, 0]` → verify only size shrinks
    - Single element minimal: `[0]` → verify only `[]` shrink
    - Single element non-minimal: `[5]` → verify recursive shrinking to `[0]` → `[]`
    - Mixed minimal/non-minimal: `[0, 5]` → verify element shrinks only for index 1
    - Size at range minimum: Gen.int().list(2..5) size 2 → verify no size shrinks
    - Deep recursion: Verify `[5]` → `[2]` → `[1]` → `[0]` → `[]` path exists

---

## Summary

**Edge cases are handled naturally by the recursive implementation through lazy evaluation.**

The key insight: minimality is defined conceptually (a value with no shrinks), but we **never check if shrinks exist**
in the implementation. Instead:

- Empty sequences naturally produce nothing when iterated (`forEach`, `flatMap`)
- Size shrinking stops at origin because `IntGenerator.shrink()` returns an empty sequence
- Element shrinking stops when elements are at origin for the same reason
- Recursion terminates when both size and elements produce empty shrink sequences

**No explicit checks needed** - the lazy sequence evaluation handles everything:

- ✅ Don't call `.isEmpty()`, `.any()`, or `.none()` on shrink sequences
- ✅ Just iterate with `forEach` or `flatMap` - empty sequences do nothing
- ✅ Let the generator logic determine what shrinks to produce

