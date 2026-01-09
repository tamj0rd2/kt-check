# Depth-First Shrinking: Key Insight for Recursive Implementation

## The Critical Understanding

The test framework uses **depth-first traversal** for shrinking:

```
When a shrink FAILS → go DEEPER (try shrinking the shrink)
When a shrink PASSES → backtrack and try the next sibling
```

This has profound implications for the recursive shrinking implementation.

## Example: Finding Minimal Counterexample

Test: `list.isEmpty() || list[0] == 0`

Initial failure: `[5, 3, 8]`

### Without Recursion (Current)

```
[5, 3, 8] fails
├─ [] passes ✓ (backtrack)
├─ [5, 3] fails ✗ DEAD END! Can't go deeper
│  Report as minimal: [5, 3]
```

The framework stops at `[5, 3]` even though `[5]` would also fail (and is smaller).

### With Recursion (Proposed)

```
[5, 3, 8] fails
├─ [] passes ✓ (backtrack)
├─ [5, 3] fails ✗ GO DEEPER!
│  ├─ [] passes ✓ (backtrack)
│  ├─ [5] fails ✗ GO DEEPER!
│  │  └─ [0] passes ✓ (backtrack)
│  │  Found minimal: [5]
```

The framework can explore deeper levels and find `[5]` as the minimal counterexample.

## Why This Matters for Implementation

### 1. Lazy Evaluation is Essential

Because we go **deep first**, not **wide**:

- Most siblings are never evaluated
- Only the failing path is explored
- Lazy `sequence {}` is perfect for this

### 2. Shrink Ordering is Critical

The **first** failing shrink determines the path:

- Size shrinks before element shrinks → good (smaller is simpler)
- Earlier elements before later ones → good (index 0 matters most)
- Must preserve this ordering in recursive calls

### 3. We Don't Generate the Full Tree

For `[5, 3, 8]` with recursive shrinking:

- **Total possible shrinks at all levels**: 50+ nodes
- **Actually evaluated**: ~5 nodes (just the failing path)
- **Performance**: Excellent due to lazy evaluation + depth-first

### 4. Each Shrink Must Be Self-Contained

When we create `GenResult([5, 3], shrinks = ...)`, the shrinks must be **complete** - they should include:

- Size shrinks of `[5, 3]` (i.e., `[]`, `[5]`, `[3]`)
- Element shrinks of `[5, 3]` (i.e., `[0, 3]`, `[5, 0]`, etc.)

This is what the recursive `generateListShrinks()` function provides.

## Implementation Implication

The recursive function signature:

```kotlin
private fun generateListShrinks(
    size: Int,
    sizeRange: IntRange,
    elementResults: List<GenResult<T>>,
): Sequence<GenResult<List<T>>>
```

This function is called:

1. Once for the initial generation (with original size & elements)
2. Recursively for each shrunk list (with new size & subset of elements)
3. Only evaluated lazily as the framework traverses depth-first

## The Beauty of the Design

```kotlin
// For [5, 3], this returns:
GenResult(
    value = [5, 3],
    shrinks = generateListShrinks(2, elementResults = [5, 3])  // Lazy!
)

// That lazy sequence, when enumerated, yields:
// - GenResult([], ...)           ← passes, backtrack
// - GenResult([5], shrinks = generateListShrinks(1, [5]))  ← fails, GO DEEPER!
//     └─ That shrinks to GenResult([0], ...) ← passes, found minimum!
```

The recursion is **lazy** (sequences) and **demand-driven** (depth-first), making it both elegant and efficient.

## Summary

**Depth-first traversal + lazy evaluation + recursive shrinks = powerful minimal counterexample finder**

Without recursion: stuck at first-level dead ends
With recursion: can explore arbitrarily deep to find truly minimal counterexamples

The framework's depth-first strategy makes recursive shrinking both **necessary** (to go deeper) and **efficient** (most
paths never explored).

