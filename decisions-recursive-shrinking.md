# Updated Plan: Recursive List Shrinking - Decisions Made

## Summary of Updates

The plan has been updated based on your feedback. Here are the key decisions:

## Decision 1: Access sizeRange from Constructor ✅

**Question**: Should we pass `sizeRange` as a parameter to the recursive function or access it via `sizeGen`?

**Answer**: Access it from the constructor property, NOT via `sizeGen`.

**Implementation**:

```kotlin
private class ListGenerator<T>(
    private val gen: Gen<T>,
    private val sizeRange: IntRange,  // ← Store as property
) : Gen<List<T>>() {
    private val sizeGen = Gen.int(sizeRange)

    private fun generateListShrinks(
        size: Int,
        elementResults: List<GenResult<T>>,
    ): Sequence<GenResult<List<T>>> = sequence {
        // Use this.sizeRange directly, not sizeGen.range
        val origin = when {
            sizeRange.last < 0 -> sizeRange.last
            sizeRange.first > 0 -> sizeRange.first
            else -> 0
        }
        val sizeShrinks = IntGenerator.shrink(size, sizeRange, origin)
        // ...
    }
}
```

**Rationale**:

- Cleaner - doesn't rely on internal structure of `sizeGen`
- More explicit - the dependency is clear
- Simpler signature - recursive function doesn't need the parameter passed in

**Status**: ✅ `sizeRange` already changed to property in `ListGenerator.kt`

## Decision 2: New Tests Required ✅

**Question**: Do we need new tests to verify recursive shrinking works?

**Answer**: Yes, we will need new tests.

**What existing tests verify**:

- ✅ First-level shrinks have correct values
- ✅ Shrink ordering is correct at first level
- ✅ Basic shrinking behavior (size shrinks, element shrinks)

**What new tests must verify**:

- ❓ Second-level shrinks exist (shrinks have their own shrinks)
- ❓ Deep shrinking terminates properly (doesn't infinite loop)
- ❓ Shrink tree structure is correct at multiple depths
- ❓ Depth-first traversal can find minimal counterexamples

**Next Step**: Plan the test design

- What specific scenarios to test?
- How to structure the tests?
- What assertions to make?

## Current Status

### Completed ✅

1. Plan created with recursive shrinking strategy
2. Depth-first traversal implications understood
3. Decision made: use `sizeRange` from constructor
4. Decision made: write new tests
5. `sizeRange` changed to property in implementation

### Pending ❓

1. ✅ **Edge case review** - Completed (see below)
2. **Test design planning** - What and how to test
3. **Implementation** - Recursive `generateListShrinks()` function
4. **Test implementation** - Write the new tests

## Decision 3: Edge Cases ✅

**Definition of "Minimal"**: A value is minimal if it has been generated and has no shrinks (conceptually - the shrink
sequence is empty).

**CRITICAL**: We do NOT check if sequences are empty (e.g., `.isEmpty()`, `.any()`, `.none()`) in the implementation.
`shrinks` is a lazy `Sequence`, and materializing it would cause performance problems. This definition is for *
*documentation purposes only** - to clarify that we shouldn't invent shrinks where the generator doesn't provide them.

### Edge Case Behaviors

#### 1. Empty List

- **Input**: `[]` (size = 0)
- **Shrinks**: Empty sequence (no shrinks)
- **Rationale**: Empty list is already minimal (size at origin, no elements to shrink)

#### 2. All Elements Minimal

- **Input**: `[0, 0, 0]` (all elements have no shrinks)
- **Shrinks**: Size shrinks only: `[]`, `[0]`, `[0, 0]`
- **Rationale**: Elements are minimal (no shrinks available), but size can still shrink toward origin

#### 3. Single Element, Minimal

- **Input**: `[0]` (element has no shrinks)
- **Shrinks**: `[]` (size shrink only)
- **Rationale**: Element is minimal, only size can shrink

#### 4. Single Element, Non-Minimal

- **Input**: `[5]` (element has shrinks: 0, 2, 3)
- **Shrinks**: `[]` (size), `[0]`, `[2]`, `[3]` (element)
- **Rationale**: Both size and element can shrink

#### 5. Mixed Minimal/Non-Minimal Elements

- **Input**: `[0, 5]` (first minimal, second non-minimal)
- **Shrinks**:
    - Size shrinks: `[]`, `[0]`, `[5]`
    - Element shrinks: `[0, 0]`, `[0, 2]`, `[0, 3]` (only index 1 shrinks)
- **Rationale**: Only non-minimal elements produce element shrinks

#### 6. Size Already at Range Minimum

- **Input**: List of size 2 with `sizeRange = 2..5`
- **Shrinks**: Element shrinks only (no size shrinks)
- **Rationale**: Size is already at origin of range (2), cannot shrink further

#### 7. Recursive Termination

- **Scenario**: `[1]` shrinks to `[0]`
- **When recursively shrinking `[0]`**:
    - Size shrinks to `[]` (empty)
    - Element `0` is minimal (no element shrinks)
    - So `[0]` produces only `[]` as its shrink
- **Termination**: Shrinking terminates when reaching empty list or when all elements are minimal and size is at minimum
  of range

### Implementation Implications

1. **Empty list check**: `if (size == 0)` → return `emptySequence()`
2. **Element shrink generation**: Only yield shrinks for elements that have `shrinks.any()` (non-empty shrink sequence)
3. **Natural termination**: Recursion stops when:
    - Size reaches 0 (empty list has no shrinks)
    - All elements are minimal AND size is at range origin

## Next Actions

You mentioned: "We'll need to write some new tests, but lets think about that next"

**I'm ready for the next step**: Let's discuss what the new tests should look like.

Some questions to guide test design:

- Should we test that shrinks exist at second level, or actually verify specific values?
- Should we test termination by checking for cycles or just trust the implementation?
- Should we test depth-first behavior specifically, or let integration tests verify that?
- What's the simplest test that proves recursive shrinking works?

Let me know when you're ready to discuss test design!

