# Edge Cases Confirmed - Ready for Implementation

## What You Confirmed

Your statement **"What you sent does match my expectations"** confirmed all the edge case behaviors documented.

## Key Confirmations

### 1. Definition of Minimal ✅

**A value is minimal if it has been generated and has no shrinks.**

**Important**: We do NOT check if shrinks exist (e.g., `.isEmpty()` or `.none()`) because `shrinks` is a lazy
`Sequence`. Materializing it would cause performance problems.

This definition is **conceptual documentation only**:

- If a generator produces a value with no shrinks (the sequence is naturally empty), we shouldn't invent shrinks
- Not about the value itself (e.g., `0`), but about whether the generator provides shrinks for it
- Context-dependent: `5` might be minimal if it's from `Gen.int(5..10, origin=5)` (origin is 5, so no shrinks generated)

### 2. Empty List ✅

**Input**: `[]` → **Shrinks**: Empty sequence

Already at origin, simplest possible list.

### 3. All Elements Minimal ✅

**Input**: `[0, 0, 0]` (all elements have no shrinks)
→ **Shrinks**: Size shrinks only (`[]`, `[0]`, `[0, 0]`)

Elements can't shrink, but list size can.

### 4. Single Element Behaviors ✅

- **Minimal**: `[0]` → only `[]`
- **Non-minimal**: `[5]` → `[]`, `[0]`, `[2]`, `[3]`

### 5. Mixed Elements ✅

**Input**: `[0, 5]` → Size shrinks + element shrinks for non-minimal elements only

Only elements with shrinks contribute to element shrinks.

### 6. Natural Termination ✅

Recursion stops naturally when:

- Size reaches 0 (empty list)
- All elements are minimal AND size is at range origin

No explicit termination checks needed.

## Implementation Readiness

All edge cases are **handled naturally** by the recursive implementation:

```kotlin
private fun generateListShrinks(
    size: Int,
    elementResults: List<GenResult<T>>,
): Sequence<GenResult<List<T>>> = sequence {
    // Calculate origin for size shrinking
    val origin = when {
        sizeRange.last < 0 -> sizeRange.last
        sizeRange.first > 0 -> sizeRange.first
        else -> 0
    }
    
    // Size shrinks - naturally empty when size == origin
    IntGenerator.shrink(size, sizeRange, origin).forEach { newSize ->
        when {
            newSize == 0 -> {
                yield(GenResult(emptyList(), emptySequence()))
            }
            newSize < size -> {
                // Tail removal - RECURSIVE
                val tailElements = elementResults.take(newSize)
                yield(GenResult(
                    value = tailElements.map { it.value },
                    shrinks = generateListShrinks(newSize, tailElements)
                ))
                
                // Head removal - RECURSIVE  
                val headElements = elementResults.takeLast(newSize)
                yield(GenResult(
                    value = headElements.map { it.value },
                    shrinks = generateListShrinks(newSize, headElements)
                ))
            }
        }
    }
    
    // Element shrinks - naturally empty when all elements are minimal
    elementResults.indices.forEach { index ->
        elementResults[index].shrinks.forEach { shrunkElement ->
            val newElements = elementResults.mapIndexed { i, elem ->
                if (i == index) shrunkElement else elem
            }
            yield(GenResult(
                value = newElements.map { it.value },
                shrinks = generateListShrinks(size, newElements)  // RECURSIVE
            ))
        }
    }
}
```

**Key observations**:

- ✅ `IntGenerator.shrink(size, range, origin)` returns empty sequence when `size == origin`
- ✅ `elementResults[index].shrinks` is an empty sequence when element is minimal
- ✅ `forEach` over empty sequences does nothing (no iteration, no work)
- ✅ Recursion naturally terminates - we never need to check if sequences are empty

**We rely on lazy evaluation**: Empty sequences naturally contribute nothing when we iterate over them with `forEach` or
`flatMap`. No explicit checks needed!

## Documentation Created

1. **`decisions-recursive-shrinking.md`** - Updated with edge case section
2. **`plan-recursive-list-shrinking.md`** - Updated with edge case section
3. **`edge-cases-recursive-list-shrinking.md`** - Comprehensive edge case catalog with examples and implementation notes

## Status: All Prerequisites Complete ✅

- ✅ Recursive shrinking strategy defined
- ✅ Depth-first traversal implications understood
- ✅ sizeRange access decision made
- ✅ New tests decision made
- ✅ **Edge cases reviewed and confirmed**

## Next: Test Design

Ready to plan the test design. Key questions:

- What specific scenarios should we test?
- How do we verify second-level shrinks exist?
- What's the simplest test that proves recursive shrinking works?
- Should we test specific shrink values or just structure?

When you're ready, let's discuss test design!

