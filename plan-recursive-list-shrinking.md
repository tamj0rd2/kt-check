# Plan: Recursive Shrinking for v2 ListGenerator

## Current State (Non-Recursive)

The current implementation produces shrinks like this:

```kotlin
GenResult(
    value = [1, 4],
    shrinks = sequenceOf(
        GenResult([], emptySequence()),          // Size shrink - no further shrinks
        GenResult([1], emptySequence()),         // Size shrink - no further shrinks
        GenResult([4], emptySequence()),         // Size shrink - no further shrinks
        GenResult([0, 4], emptySequence()),      // Element shrink - no further shrinks
        GenResult([1, 0], emptySequence()),      // Element shrink - no further shrinks
        GenResult([1, 2], emptySequence()),      // Element shrink - no further shrinks
        GenResult([1, 3], emptySequence()),      // Element shrink - no further shrinks
    )
)
```

**Problem**: Each shrink returns `emptySequence()` for its own shrinks. This means shrinking stops at depth 1.

## What is Recursive Shrinking?

Recursive shrinking means each shrunk value can itself be shrunk further. Example:

```kotlin
GenResult(
    value = [1, 4],
    shrinks = sequenceOf(
        // Size shrink to [1] - which can further shrink its element
        GenResult([1], sequenceOf(
            GenResult([0], emptySequence())      // [1] → [0]
        )),
        // Size shrink to [4] - which can further shrink its element
        GenResult([4], sequenceOf(
            GenResult([0], emptySequence()),     // [4] → [0]
            GenResult([2], emptySequence()),     // [4] → [2]
            GenResult([3], emptySequence()),     // [4] → [3]
        )),
        // Element shrink to [0, 4] - which can further shrink the second element
        GenResult([0, 4], sequenceOf(
            GenResult([0, 0], emptySequence()),  // [0, 4] → [0, 0]
            GenResult([0, 2], emptySequence()),  // [0, 4] → [0, 2]
            GenResult([0, 3], emptySequence()),  // [0, 4] → [0, 3]
        )),
        // ... and so on
    )
)
```

**Benefit**: The test framework can traverse deeper into the shrink tree to find smaller counterexamples.

### Depth-First Traversal

The test framework uses **depth-first** shrinking traversal:

- If a shrink fails, it continues down that path (shrinking the shrink)
- Only backtracks when a shrink passes or dead-end is reached
- Explores sibling shrinks only after exhausting the current branch

Example scenario:

- Test fails with `[1, 4]`
- Framework tries `[]` (first size shrink) - passes → backtrack
- Framework tries `[1]` (second size shrink) - **still fails** → go deeper
    - Framework tries `[0]` (element shrink of `[1]`) - passes → backtrack
    - No more shrinks of `[1]`
- Minimal counterexample found: `[1]`
- Framework tries `[4]` (third size shrink) - still fails → go deeper
    - Framework tries `[0]` (element shrink) - passes
    - Framework tries `[2]` (element shrink) - may explore this path too

Without recursion, the framework would stop at `[1]` or `[4]` without trying `[0]`.
With recursion, it can find the truly minimal counterexample by going deeper.

## How IntGenerator Does It

```kotlin
private fun generateShrinks(value: Int): Sequence<GenResult<Int>> = sequence {
    shrink(value, range, origin).forEach { shrunkValue ->
        yield(GenResult(shrunkValue, generateShrinks(shrunkValue)))  // ← RECURSIVE CALL
    }
}
```

Key insight: `generateShrinks` calls itself recursively for each shrunk value.

## Proposed Implementation Strategy

### Step 1: Create a Recursive Shrink Generator Function

```kotlin
private fun generateListShrinks(
    size: Int,
    elementResults: List<GenResult<T>>,
): Sequence<GenResult<List<T>>> = sequence {
    // Phase 1: Size shrinks (with recursion)
    // Use sizeRange from constructor property
    val origin = when {
        sizeRange.last < 0 -> sizeRange.last
        sizeRange.first > 0 -> sizeRange.first
        else -> 0
    }
    val sizeShrinks = IntGenerator.shrink(size, sizeRange, origin)
    
    sizeShrinks.forEach { newSize ->
        when {
            newSize == 0 -> {
                // Empty list has no further shrinks
                yield(GenResult(emptyList(), emptySequence()))
            }
            newSize < size -> {
                // Tail removal - recursively shrink the resulting list
                val tailRemovalElements = elementResults.take(newSize)
                yield(GenResult(
                    value = tailRemovalElements.map { it.value },
                    shrinks = generateListShrinks(newSize, tailRemovalElements)
                ))
                
                // Head removal - recursively shrink the resulting list
                val headRemovalElements = elementResults.takeLast(newSize)
                yield(GenResult(
                    value = headRemovalElements.map { it.value },
                    shrinks = generateListShrinks(newSize, headRemovalElements)
                ))
            }
        }
    }
    
    // Phase 2: Element shrinks (with recursion)
    elementResults.indices.forEach { index ->
        elementResults[index].shrinks.forEach { shrunkElementResult ->
            val newElementResults = elementResults.mapIndexed { i, elemResult ->
                if (i == index) shrunkElementResult else elemResult
            }
            yield(GenResult(
                value = newElementResults.map { it.value },
                shrinks = generateListShrinks(size, newElementResults)
            ))
        }
    }
}
```

### Step 2: Update Main Generate Method

```kotlin
override fun GenContext.generate(): GenResult<List<T>> {
    val sizeResult = sizeGen.generate(producer)
    val size = sizeResult.value
    val elementResults = List(size) { gen.generate(producer) }
    
    return GenResult(
        value = elementResults.map { it.value },
        shrinks = generateListShrinks(size, elementResults),
    )
}
```

## Key Considerations

### 1. **Infinite Recursion Risk**

**Problem**: If we're not careful, recursion could go on forever.

**Solution**: The recursion naturally terminates because:

- Size shrinks toward 0 (empty list has no shrinks)
- Element shrinks terminate when elements reach their origin
- Each recursive call works with strictly smaller values

### 2. **Stack Overflow Risk**

**Problem**: Deep recursion could cause stack overflow.

**Solution**: The `sequence { }` builder uses **lazy evaluation** with suspending functions, not call stack recursion.
The sequence is only evaluated when enumerated, and the framework controls traversal depth.

### 3. **Performance Considerations**

**Problem**: Recursive shrinking could be slow - we're generating shrinks of shrinks of shrinks...

**Solution**:

- Sequences are lazy - only computed when needed
- **Depth-first traversal means the framework goes deep, not wide**:
    - Once a failing shrink is found, it explores that path fully before trying siblings
    - This means we don't generate all shrinks at each level - only the path being explored
    - Most of the shrink tree is never evaluated
- For large lists, this is still better than not finding minimal counterexamples

**Example traversal for `[1, 4]` where both `[1]` and `[4]` fail**:

```
[1, 4] fails
├─ [] evaluated → passes, backtrack
├─ [1] evaluated → fails, go deeper
│  └─ [0] evaluated → passes, backtrack (found minimum: [1])
└─ [4] NOT evaluated (already found [1] as minimum)
```

Only 3 shrinks evaluated, not all 7!

### 4. **Access to sizeRange**

**Problem**: We need access to the size range in the recursive function to determine the origin for shrinking.

**Solution**: ✅ **DECIDED - Pass `sizeRange` as a constructor parameter and use it in the recursive function.**

```kotlin
private class ListGenerator<T>(
    private val gen: Gen<T>,
    private val sizeRange: IntRange,  // Keep as property
) : Gen<List<T>>()

private fun generateListShrinks(
    size: Int,
    elementResults: List<GenResult<T>>,
): Sequence<GenResult<List<T>>> = sequence {
    // Use this.sizeRange from the class
    val sizeShrinks = IntGenerator.shrink(size, sizeRange, /* origin calculation */)
    // ...
}
```

This approach:

- Keeps the recursive function signature clean
- Avoids accessing internal properties of `sizeGen`
- Makes `sizeRange` available throughout the class

### 5. **Shrink Ordering Matters**

With depth-first traversal, the **order of shrinks is critical** because the first failing path is explored fully before
trying siblings.

Current ordering:

1. Size shrinks (toward 0)
2. Element shrinks (index 0, then 1, then 2, ...)

This is good because:

- **Smaller lists are typically simpler** → size shrinks first
- **Earlier elements often matter more** → shrink index 0 before index 1

But we need to ensure recursive shrinks maintain this property. For a shrunk list like `[1]` (after removing tail), we
want:

1. Size shrink to `[]` (try empty first)
2. Element shrink to `[0]` (try origin value)

This ordering is naturally preserved by our recursive implementation since we use the same `generateListShrinks`
function.

### 6. **Testing Strategy**

✅ **DECIDED - We will need new tests to verify recursive shrinking works properly.**

**Where tests will go:**

- Tests will be added to `src/test/kotlin/com/tamj0rd2/ktcheck/v2/ListGeneratorTest.kt`
- NOT in the contract - directly in the v2 test class
- Use production APIs directly: `Gen.int().list()` instead of contract abstraction methods

**Existing tests verify:**

1. First-level shrinks work correctly
2. The values of first-level shrinks match expectations

**New tests needed to verify:**

1. Second-level shrinks exist and are accessible
2. Deep shrinking terminates properly
3. The shrink tree structure is correct

Example test scenarios:

```kotlin
@Nested
inner class `Recursive Shrinking` {
    @Test
    fun `list shrinks recursively`() {
        // Start with [1, 4]
        // Verify that [1] (size shrink) has its own shrinks
        // Verify that [1] can shrink to [0]
        
        val gen = Gen.int(0..5).list()  // Use production API directly
        val result = gen.generate(StubValueProducer(listOf(2, 1, 4)))
        
        // Get first level shrinks
        val firstLevelShrinks = result.shrinks.toList()
        
        // Find the [1] shrink (should be second size shrink)
        val listOf1 = firstLevelShrinks.find { it.value == listOf(1) }
        assertNotNull(listOf1)
        
        // Verify it has shrinks (recursive!)
        val secondLevelShrinks = listOf1.shrinks.toList()
        expectThat(secondLevelShrinks.map { it.value }).contains(
            emptyList(),  // Size shrink to 0
            listOf(0),    // Element shrink
        )
    }
}
```

**Test design planned in detail** in `test-plan-recursive-list-shrinking.md` - 6 comprehensive scenarios covering edge
cases and deep recursion.

## Comparison: Current vs Recursive

### Current (Non-Recursive)

```
[1, 4]
├─ [] (dead end)
├─ [1] (dead end) ← framework stops here if this fails
├─ [4] (dead end)
├─ [0, 4] (dead end)
├─ [1, 0] (dead end)
├─ [1, 2] (dead end)
└─ [1, 3] (dead end)
```

Depth: 1 level
**Problem**: If `[1]` fails, framework cannot shrink it further. Reports `[1]` as minimal when `[0]` might pass.

### Recursive (Depth-First Traversal)

```
[1, 4] (fails)
├─ [] (passes) ← try first, backtrack
├─ [1] (fails) ← try second, GO DEEPER
│  └─ [0] (passes) ← found minimum: [1]
├─ [4] (not explored - already found [1])
│  ├─ [0] (not explored)
│  ├─ [2] (not explored)
│  └─ [3] (not explored)
├─ [0, 4] (not explored)
│  └─ ...
└─ ... (not explored)
```

Depth: Multiple levels
**Benefit**: Framework can go deeper when a shrink fails, finding truly minimal counterexamples.

**Key insight**: With depth-first traversal, we don't evaluate the entire tree! Only the path leading to the minimal
counterexample.

### Concrete Example: Why Recursive Shrinking Matters

Consider a test that fails if the list is non-empty and the first element is non-zero:

```kotlin
forAll(Gen.int(0..10).list(1..3)) { list ->
    list.isEmpty() || list[0] == 0
}
```

**Failing input**: `[5, 3, 8]` (randomly generated)

**Without recursive shrinking** (current implementation):

```
[5, 3, 8] fails
├─ [] passes ✓
├─ [5, 3] fails ← STOPS HERE (dead end, no further shrinks)
└─ ...
```

**Reported counterexample**: `[5, 3]` (not minimal!)

**With recursive shrinking** (proposed):

```
[5, 3, 8] fails
├─ [] passes ✓
├─ [5, 3] fails ← go deeper
│  ├─ [] passes ✓
│  ├─ [5] fails ← go deeper
│  │  └─ [0] passes ✓ (FOUND MINIMUM!)
│  └─ [3] not explored
└─ [3, 8] not explored
```

**Reported counterexample**: `[5]` (better, but not perfect yet)

Actually, with full depth-first traversal continuing:

```
[5, 3, 8] fails
├─ [] passes ✓
├─ [5, 3] fails ← go deeper
│  ├─ [] passes ✓
│  ├─ [5] fails ← go deeper
│  │  └─ [0] passes ✓
│  └─ backtrack
├─ [3, 8] fails ← next sibling (since [5, 3] path exhausted)
│  ├─ [] passes ✓
│  ├─ [3] fails ← go deeper
│  │  └─ [0] passes ✓
│  └─ [8] not needed
└─ ...
```

The framework would report either `[5]` or `[3]` depending on which path it exhausted first. Both are better than
`[5, 3, 8]`, and the recursive shrinking made this possible!

## Expected Changes to Test Behavior

**Important**: The existing tests should still pass! They only check the first level of shrinks.

The test helper extracts values:

```kotlin
shrinks.map { it.value }.toList()
```

This gets the **values** of first-level shrinks, ignoring the nested shrink trees.

So recursive shrinking is **backward compatible** with existing tests.

## Implementation Steps

1. Create the `generateListShrinks` recursive function
2. Update `generate()` to call `generateListShrinks`
3. Run existing tests to verify backward compatibility
4. (Optional) Add new tests to verify recursive shrinking works

## Questions for Feedback

1. ✅ **Does this approach make sense?** Is recursive shrinking what you expected?
    - **ANSWERED**: Yes, confirmed with depth-first traversal understanding

2. ✅ **Should we pass `sizeRange` as a parameter** or access it via `sizeGen`?
    - **ANSWERED**: Use `sizeRange` from constructor property, not via `sizeGen`

3. ✅ **Do we need new tests** to verify recursive shrinking, or trust that existing tests prove it works?
    - **ANSWERED**: Yes, we'll need new tests. Test design to be planned next.

4. ✅ **Performance concerns?** Are you worried about the computational cost of recursive shrinking?
    - **ANSWERED**: No concerns - depth-first traversal makes it efficient

5. ✅ **Any edge cases I'm missing?** Empty lists, single-element lists, maximum recursion depth?
    - **ANSWERED**: Edge cases documented in `edge-cases-recursive-list-shrinking.md`
    - All handled naturally by the recursive implementation

## Next Steps

1. ✅ Update plan with decisions about `sizeRange` access
2. ✅ Update plan with decision about new tests
3. ✅ Review and document edge cases
4. ✅ **Plan test design** - what scenarios to test, how to structure tests
    - **COMPLETED**: Detailed test plan created in `test-plan-recursive-list-shrinking.md`
    - **6 test scenarios** covering: empty lists, minimal elements, non-minimal elements, two-element lists, deep
      recursion (3+ levels), all-elements-minimal
    - **Testing principles** documented: no sequence materialization, spot-checking, lazy evaluation
5. ❓ Implement the recursive shrinking
6. ❓ Implement the new tests

## Documentation Index

- **Main Plan**: `plan-recursive-list-shrinking.md` (this file)
- **Test Plan**: `test-plan-recursive-list-shrinking.md` - Comprehensive test design with 6 scenarios
- **Edge Cases**: `edge-cases-recursive-list-shrinking.md` - Detailed edge case analysis
- **Confirmed Edge Case Behaviors**: `edge-cases-confirmed.md` - Validated edge case decisions
- **Decisions**: `decisions-recursive-shrinking.md` - Key implementation decisions
- **Important Note**: `IMPORTANT-dont-materialize-sequences.md` - Critical performance guidance

**Status**: Test design complete. Ready to implement recursive shrinking and tests!

