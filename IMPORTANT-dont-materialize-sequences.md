# IMPORTANT: Don't Materialize Lazy Sequences

## The Clarification

**Critical Point**: The definition of "minimal" (a value with no shrinks) is **conceptual documentation only**. We do
NOT check if sequences are empty in the actual implementation.

## Why This Matters

### The Problem with Checking

```kotlin
// ❌ WRONG - Don't do this!
if (elementResults[index].shrinks.isEmpty()) { ... }
if (elementResults[index].shrinks.any()) { ... }
if (elementResults[index].shrinks.none()) { ... }
```

**Why this is wrong**:

- `shrinks` is a lazy `Sequence<GenResult<T>>`
- Calling `.isEmpty()`, `.any()`, or `.none()` **materializes** the sequence
- This defeats the purpose of lazy evaluation
- Could cause performance issues or even evaluate the entire shrink tree unnecessarily

### The Right Approach

```kotlin
// ✅ CORRECT - Let lazy evaluation handle it
elementResults.indices.asSequence().flatMap { index ->
    elementResults[index].shrinks.map { shrunkElementResult ->
        // ... generate list with this element shrunk
    }
}
```

**Why this is right**:

- If `shrinks` is empty, `flatMap` simply produces nothing
- No iteration happens, no work is done
- The sequence remains lazy throughout
- Natural, efficient, correct

## What "Minimal" Actually Means

### Documentation Purpose

When we say "a value is minimal if it has no shrinks," we mean:

- **The generator doesn't produce shrinks for this value**
- This is determined by the generator's logic (e.g., `IntGenerator` doesn't shrink values at their origin)
- Not something we check - it's just how generators work

### Example

```kotlin
// For Int(0..10, origin=0):
Gen.int(0..10).generate(producer) 
// If it produces 0:
// - GenResult(0, shrinks = emptySequence())
// - The sequence is empty because IntGenerator.shrink(0, 0..10, 0) returns empty

// We don't check this! We just use it:
shrinks.forEach { ... }  // Does nothing if empty - perfect!
```

## Updated Documentation

All documentation has been updated to clarify:

1. **Definition is conceptual**: "Minimal" describes what happens, not a check we perform
2. **No sequence materialization**: We never call `.isEmpty()`, `.any()`, `.none()`
3. **Rely on lazy evaluation**: Empty sequences naturally contribute nothing

## Files Updated

- ✅ `edge-cases-confirmed.md` - Clarified definition, removed `.isEmpty()` references
- ✅ `edge-cases-recursive-list-shrinking.md` - Removed filtering suggestions, emphasized lazy evaluation
- ✅ `decisions-recursive-shrinking.md` - Added warning about not materializing sequences

## The Implementation

Our implementation is already correct - it never materializes sequences:

```kotlin
// Size shrinks
val sizeShrinks = sizeResult.shrinks.flatMap { shrunkSizeResult ->
    // If sizeResult.shrinks is empty, flatMap produces nothing
    // No checking needed!
}

// Element shrinks
val elementShrinks = elementResults.indices.asSequence().flatMap { index ->
    elementResults[index].shrinks.map { shrunkElementResult ->
        // If elementResults[index].shrinks is empty, map produces nothing
        // No checking needed!
    }
}
```

Perfect! The lazy evaluation handles everything.

## Key Takeaway

**The definition of "minimal" is documentation to explain behavior, NOT a check to implement.**

We document that:

- Empty lists are minimal (conceptually, they have no shrinks to produce)
- Elements at origin are minimal (IntGenerator produces empty shrink sequences for them)
- This helps readers understand the edge cases

But we implement by:

- Just using the sequences as-is
- Letting lazy evaluation handle empty vs non-empty
- Never checking if sequences have elements

This is simpler, more efficient, and more correct!

