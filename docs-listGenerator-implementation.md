# ListGenerator v2 Implementation

## Overview

Implemented the v2 ListGenerator with three key behaviors:

1. Shrinks the list size (removing values at the end/tail)
2. Shrinks the list size (removing values at the start/head)
3. Shrinks each element of the list individually

## Implementation Strategy

### Generation Phase (Caching Approach)

```kotlin
val sizeResult = sizeGen.generate(producer)
val size = sizeResult.value
val elementResults = List(size) { gen.generate(producer) }
```

All elements are generated upfront and cached as `GenResult<T>` objects. This:

- Prevents value explosion during shrinking
- Preserves complete shrink trees for each element
- Ensures deterministic shrinking

### Shrinking Strategy

Shrinks are produced in two phases:

#### Phase 1: Size Shrinks

For each shrunk size from the size generator (which shrinks toward 0):

- If newSize = 0: Return empty list
- If newSize < currentSize: Return two variants:
    - **Tail removal**: `take(newSize)` - keeps first N elements
    - **Head removal**: `takeLast(newSize)` - keeps last N elements

This allows the shrinker to try different subsets of the list.

#### Phase 2: Element Shrinks

For each element index (0, 1, 2, ...):

- Take the element's shrink tree
- For each shrink, create a new list with that element shrunk
- Other elements remain unchanged

This shrinks each element individually while keeping the list size constant.

## Example: Shrinking `[1, 2, 3]`

**Initial value:** `[1, 2, 3]`

**Size shrinks:**

- 3 → 0: `[]`
- 3 → 2 (tail removal): `[1, 2]`
- 3 → 2 (head removal): `[2, 3]`

**Element shrinks:**

- Index 0: 1 → 0: `[0, 2, 3]`
- Index 1: 2 → 0: `[1, 0, 3]`
- Index 1: 2 → 1: `[1, 1, 3]`
- Index 2: 3 → 0: `[1, 2, 0]`
- Index 2: 3 → 2: `[1, 2, 2]`

**Result:** `[[], [1, 2], [2, 3], [0, 2, 3], [1, 0, 3], [1, 1, 3], [1, 2, 0], [1, 2, 2]]`

This matches the test expectations exactly.

## Producer Consumption Pattern

For a list of size N with element generator G:

1. `producer.int(sizeRange)` - generate the size
2. `G.generate(producer)` - called N times for each element

Example for `Gen.int(0..5).list()` generating `[1, 4]`:

- `producer.int(0..100)` → 2 (size)
- `producer.int(0..5)` → 1 (element 0)
- `producer.int(0..5)` → 4 (element 1)

Test stub values: `[2, 1, 4]`

## Design Decisions

### Why Tail AND Head Removal?

Different removal strategies help find minimal counterexamples faster:

- Tail removal: Good when the failure is caused by earlier elements
- Head removal: Good when the failure is caused by later elements

Example: If only the last element causes failure, head removal shrinks directly to `[lastElement]`, while tail removal
would take many steps.

### Why Element Shrinks After Size Shrinks?

Size reduction is typically more impactful (smaller lists are simpler), so we try that first. Once we have the smallest
failing size, we shrink individual elements.

### Why No Recursive Shrinking?

Each shrink returns `emptySequence()` for its own shrinks. The framework handles recursion by traversing the shrink
tree. This keeps the implementation simple and avoids stack overflow for large lists.

## Alignment with Caching Philosophy

Like OneOfGenerator, ListGenerator uses eager caching:

- All elements generated upfront
- Complete shrink trees preserved
- Deterministic shrinking guaranteed
- Trade-off: Generates all N elements even if only testing size 0

This aligns with ktcheck's values of correctness and debuggability over raw performance.

