# Plan: Recursive Shrinking Tests for v1 ListGenerator

## Problem Statement

We have successfully implemented and tested recursive shrinking for the v2 `ListGenerator`. Now we want to write
equivalent tests for the v1 implementation to verify it also supports recursive shrinking.

## Key Differences Between v1 and v2

### v2 Architecture (Current - Tested)

- Uses `GenResult<T>` with explicit `value` and `shrinks` properties
- `shrinks: Sequence<GenResult<T>>` - nested structure is directly accessible
- Can navigate the shrink tree by accessing `.shrinks` on each `GenResult`
- Test pattern:
  ```kotlin
  val result = gen.generate(StubValueProducer(listOf(1, 4)))
  val firstLevel = result.shrinks.toList()
  val secondLevel = firstLevel[0].shrinks.toList()
  ```

### v1 Architecture (Target - To Test)

- Uses `ProducerTree` - a lazy binary tree structure
- Shrinks are represented as `Sequence<ProducerTree>` - trees that can generate shrunk values
- Each `ProducerTree` can be used to generate a value via `gen.generate(tree, mode)`
- No direct access to nested shrinks - must regenerate from each shrink tree
- Test pattern:
  ```kotlin
  val tree = ProducerTree.new().withLeft(...)
  val (value, shrinkTrees) = gen.generate(tree, GenMode.Initial)
  val firstLevelValues = shrinkTrees.map { gen.generate(it, GenMode.Shrinking).value }
  
  // For second level - need to generate from first shrink tree
  val firstShrinkTree = shrinkTrees.first()
  val (shrunkValue, secondLevelShrinkTrees) = gen.generate(firstShrinkTree, GenMode.Shrinking)
  ```

## Core Challenge

**v2**: The shrink tree structure is explicit and navigable (`GenResult` contains nested `GenResult`s)

**v1**: The shrink tree structure is implicit and lazy (`ProducerTree` generates values, which themselves have shrink
trees when generated)

## Solution Approach

### Option 1: Direct Tree Navigation (Recommended)

Since v1 generators return `Sequence<ProducerTree>` for shrinks, we need to:

1. Generate the initial value from a `ProducerTree`
2. Get the first-level shrink trees
3. **For each first-level shrink tree**, generate its value to get second-level shrink trees
4. Continue this pattern for deeper levels

**Test Structure**:

```kotlin
@Test
fun `single non-minimal element shrinks recursively`() {
    val gen = Gen.int(0..5).list()

    // Setup: Create a tree that generates [4]
    val tree = ProducerTree.new()
        .withLeft(left.withValue(1))  // size = 1
        .withRight(right.left.withValue(4))  // element[0] = 4

    // Level 1: Generate initial value and get shrink trees
    val (value, level1ShrinkTrees) = gen.generate(tree, GenMode.Initial)
    expectThat(value).isEqualTo(listOf(4))

    // Find the shrink tree that produces [0] (element shrink)
    val listOf0Tree = level1ShrinkTrees.find { shrinkTree ->
        gen.generate(shrinkTree, GenMode.Shrinking).value == listOf(0)
    }
    expectThat(listOf0Tree).isNotNull()

    // Level 2: Generate from the [0] shrink tree to get its shrinks
    val (listOf0Value, level2ShrinkTrees) = gen.generate(listOf0Tree!!, GenMode.Shrinking)
    expectThat(listOf0Value).isEqualTo(listOf(0))

    // Verify [0] can shrink to []
    val level2Values = level2ShrinkTrees.map {
        gen.generate(it, GenMode.Shrinking).value
    }.toList()
    expectThat(level2Values).contains(listOf(emptyList()))
}
```

### Option 2: Helper Function Abstraction

Create a helper function that wraps `ProducerTree` in a structure similar to v2's approach:

```kotlin
// Helper that makes v1 look like v2 for testing
data class TreeResult<T>(
    val value: T,
    val shrinkTrees: Sequence<TreeResult<T>>
)

fun <T> Gen<T>.generateRecursive(tree: ProducerTree): TreeResult<T> {
    val (value, shrinks) = generate(tree, GenMode.Initial)
    return TreeResult(
        value = value,
        shrinkTrees = shrinks.map { shrinkTree ->
            generateRecursive(shrinkTree)  // Recursive wrapper
        }
    )
}
```

**Problem with this approach**: This would eagerly evaluate the entire shrink tree, defeating the purpose of lazy
evaluation. **NOT RECOMMENDED**.

### Option 3: Materialized Path Testing (Hybrid)

Only materialize the specific paths we want to test:

```kotlin
@Test
fun `single non-minimal element shrinks recursively`() {
    val gen = Gen.int(0..5).list()
    val tree = buildTree(listOf(1, 4))  // helper to build tree

    // Navigate path: initial → [0] → []
    val path = navigateShrinkPath(gen, tree) { currentValue, shrinks ->
        when {
            currentValue == listOf(4) -> shrinks.find {
                gen.generate(it, GenMode.Shrinking).value == listOf(0)
            }
            currentValue == listOf(0) -> shrinks.find {
                gen.generate(it, GenMode.Shrinking).value == emptyList<Int>()
            }
            else -> null
        }
    }

    expectThat(path).isEqualTo(listOf(listOf(4), listOf(0), emptyList()))
}
```

## Recommended Implementation: Option 1 with Helper

Use Option 1 (direct tree navigation) but add a helper to reduce boilerplate:

```kotlin
// Helper to build ProducerTree from RNG values
fun buildListTree(rngValues: List<Any>): ProducerTree {
    return ProducerTree.new()
        .run {
            withLeft(left.withValue(rngValues.first()))
        }
        .run {
            val root = this
            val remainingValues = rngValues.drop(1)
            if (remainingValues.isEmpty()) return@run root

            val lastAffectedNode = root.traverseRight(rngValues.size - 1)
            remainingValues.foldRightIndexed(lastAffectedNode) { index, value, acc ->
                val updatedNode = acc.copy { left(value) }
                val updatedParentNode = root.traverseRight(index).withRight(updatedNode)
                updatedParentNode
            }
        }
}

// Helper to find a shrink tree by its generated value
fun <T> Gen<T>.findShrinkTreeByValue(
    shrinkTrees: Sequence<ProducerTree>,
    targetValue: T
): ProducerTree? {
    return shrinkTrees.find { tree ->
        generate(tree, GenMode.Shrinking).value == targetValue
    }
}
```

## Test Scenarios to Implement

Same 6 scenarios as v2, adapted for v1:

1. **Empty list has no shrinks** ✅
2. **Single minimal element shrinks recursively** ✅
3. **Single non-minimal element shrinks recursively** ✅ (Critical)
4. **Two-element list shrinks recursively** ✅
5. **Three-level depth test** ✅
6. **All elements minimal - only size shrinks recursively** ✅

## Test Structure

```kotlin
@Nested
inner class `Recursive Shrinking` {
    @Test
    fun `empty list has no shrinks`() {
        val gen = Gen.int(0..5).list()
        val tree = buildListTree(listOf(0))  // size = 0

        val (value, shrinks) = gen.generate(tree, GenMode.Initial)
        expectThat(value).isEmpty()
        expectThat(shrinks.toList()).isEmpty()
    }

    @Test
    fun `single minimal element shrinks recursively`() {
        val gen = Gen.int(0..5).list()
        val tree = buildListTree(listOf(1, 0))  // [0]

        val (value, level1Trees) = gen.generate(tree, GenMode.Initial)
        expectThat(value).isEqualTo(listOf(0))

        // Find the empty list shrink
        val emptyListTree = gen.findShrinkTreeByValue(level1Trees, emptyList())
        expectThat(emptyListTree).isNotNull()

        // Verify [] has no further shrinks
        val (emptyValue, level2Trees) = gen.generate(emptyListTree!!, GenMode.Shrinking)
        expectThat(emptyValue).isEmpty()
        expectThat(level2Trees.toList()).isEmpty()
    }

    // ... similar patterns for other tests
}
```

## Key Implementation Details

### 1. Tree Building Helper

Already exists in `ListGeneratorTest.kt` as part of `generateWithShrunkValuesForListGen`. Extract this into a reusable
helper:

```kotlin
companion object {
    fun buildListTree(rngValues: List<Any>): ProducerTree {
        return ProducerTree.new()
            .run {
                withLeft(left.withValue(rngValues.first()))
            }
            .run {
                val root = this
                val remainingValues = rngValues.drop(1)
                if (remainingValues.isEmpty()) return@run root

                val lastAffectedNode = root.traverseRight(rngValues.size - 1)
                remainingValues.foldRightIndexed(lastAffectedNode) { index, value, acc ->
                    val updatedNode = acc.copy { left(value) }
                    val updatedParentNode = root.traverseRight(index).withRight(updatedNode)
                    updatedParentNode
                }
            }
    }

    fun <T> Gen<T>.findShrinkTreeByValue(
        shrinkTrees: Sequence<ProducerTree>,
        targetValue: T
    ): ProducerTree? {
        return shrinkTrees.find { tree ->
            generate(tree, GenMode.Shrinking).value == targetValue
        }
    }
}
```

### 2. GenMode Usage

**Critical**: When generating from shrink trees, use `GenMode.Shrinking` not `GenMode.Initial`:

```kotlin
// ❌ WRONG
gen.generate(shrinkTree, GenMode.Initial)

// ✅ CORRECT
gen.generate(shrinkTree, GenMode.Shrinking)
```

This matters for distinct collections - in shrinking mode, smaller sizes are acceptable.

### 3. Lazy Evaluation

Like v2, don't materialize entire sequences:

```kotlin
// ❌ WRONG - materializes all shrinks
val allShrinks = shrinkTrees.map { gen.generate(it, GenMode.Shrinking) }.toList()

// ✅ CORRECT - only materialize what we need
val firstFewShrinks = shrinkTrees.take(10).toList()
val targetShrink = shrinkTrees.find { /* condition */ }
```

### 4. Strikt Assertions

Same issue with `.contains()` requiring extra wrapping:

```kotlin
// For List<List<Int>>
expectThat(level2Values).contains(listOf(emptyList()))  // wraps emptyList() in listOf()
expectThat(level2Values).contains(listOf(listOf(0)))    // wraps listOf(0) in listOf()
```

## Testing Strategy

### Phase 1: Write Tests (TDD)

1. Add nested `Recursive Shrinking` class to `gen/ListGeneratorTest.kt`
2. Implement all 6 test scenarios
3. Run tests - expect failures (v1 may not support recursive shrinking yet)

### Phase 2: Verify Current Behavior

1. Check if v1 already supports recursive shrinking
2. If tests pass - great! Document that it works
3. If tests fail - identify what needs to be implemented

### Phase 3: Implementation (if needed)

1. Update v1 `ListGenerator` to support recursive shrinking
2. This is a separate task - may require significant changes to the ProducerTree-based approach

## Success Criteria

- [ ] 6 test scenarios implemented for v1
- [ ] Tests compile without errors
- [ ] Tests use correct v1 patterns (ProducerTree, GenMode.Shrinking, etc.)
- [ ] Tests verify recursive shrinking at multiple levels
- [ ] Helper functions reduce boilerplate
- [ ] Tests follow lazy evaluation principles

## Files to Modify

1. **`src/test/kotlin/com/tamj0rd2/ktcheck/gen/ListGeneratorTest.kt`**
    - Add `@Nested inner class \`Recursive Shrinking\``
    - Add helper functions in companion object
    - Implement 6 test scenarios

## Notes

- **v1 may already support recursive shrinking** via the ProducerTree structure - the tests will reveal this
- The test patterns will be more verbose than v2 due to needing to generate from trees explicitly
- This is valuable because it verifies both implementations support the same shrinking behavior
- If v1 doesn't support recursive shrinking yet, these tests serve as a specification for what needs to be implemented

