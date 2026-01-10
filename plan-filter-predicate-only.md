# Plan: v2 Filter Generator Implementation (Predicate Filtering Only)

## Context

We need to implement predicate filtering for v2 to achieve feature parity with v1. This plan focuses ONLY on the
`filter(predicate)` functionality and intentionally excludes exception handling (`ignoreExceptions`).

## Current v1 Implementation (Predicate Filtering)

### FilterGenerator for Predicates

```kotlin
private class FilterGenerator<T>(
    private val threshold: Int,
    private val getResult: GenContext.() -> PredicateResult<T>,
) : Gen<T>() {
    override fun GenContext.generate(): GenResult<T> {
        var lastFailure: Exception? = null

        return generateSequence(tree) { it.right }  // Traverse tree for retries
            .take(threshold)
            .map { getResult(GenContext(it.left, mode)) }
            .filterIsInstance<Succeeded<T>>()
            .map { (genResult) ->
                // Filter shrinks to only include values that pass predicate
                val validShrinks = genResult.shrinks
                    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
                    .map { tree.withLeft(it) }

                genResult.copy(shrinks = validShrinks)
            }
            .firstOrNull()
            ?: throw FilterLimitReached(threshold, lastFailure)
    }
}
```

### Extension Functions

```kotlin
fun <T> Gen<T>.filter(predicate: (T) -> Boolean) = filter(100, predicate)

fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> =
    FilterGenerator(threshold) {
        val result = generate(tree, mode)
        if (predicate(result.value)) Succeeded(result) else Failed()
    }
```

### Key Behaviors

1. **Retry mechanism**: Try up to `threshold` times to find a value that passes the predicate
2. **Shrink filtering**: Only include shrinks that pass the predicate (prevents infinite shrinking loops)
3. **Exception on failure**: Throws `FilterLimitReached` if no valid value found within threshold attempts

## Landmark Steps

### Step 1: Update BaseGeneratorContract ✅

**Goal**: Add factory methods for filter generators

**Changes to `BaseGeneratorContract.kt`**:

```kotlin
interface BaseGeneratorContract {
    // ...existing methods...

    // Factory methods for filtered generators
    fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T>
    fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T>
}
```

### Step 2: Create FilterGeneratorTestContract ✅

**Goal**: Define contract with predicate filtering tests

**New file**: `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/FilterGeneratorTestContract.kt`

**Content**:

```kotlin
package com.tamj0rd2.ktcheck.contracts

import com.tamj0rd2.ktcheck.gen.FilterLimitReached
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThan

interface FilterGeneratorTestContract : BaseGeneratorContract {

    @Test
    fun `can filter generated values`() {
        val gen = intGen(1..10).filterGen { it % 2 == 0 }

        gen.samples().take(100).forEach { value ->
            expectThat(value % 2).isEqualTo(0)
        }
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = intGen(1..10).filterGen { it > 10 }

        assertThrows<FilterLimitReached> {
            gen.sample()
        }
    }

    @Test
    fun `doesn't produce shrinks that would fail the predicate`() {
        val gen = intGen(1..4).filterGen { it > 2 }

        // Generate a value and get its shrinks
        val (value, shrinks) = gen.generateWithShrunkValues(Seed.random())

        // Original value should pass predicate
        expectThat(value).isGreaterThan(2)

        // All shrinks should also pass predicate
        shrinks.forEach { shrunkValue ->
            expectThat(shrunkValue).isGreaterThan(2)
        }
    }
}
```

### Step 3: Implement in BaseGenTest (v1) ✅

**Goal**: Add filter factory methods for v1

**Changes to `gen/BaseGenTest.kt`**:

```kotlin
abstract class BaseGenTest : BaseGeneratorContract {
    // ...existing methods...

    override fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T> {
        return (this as Gen<T>).filter(predicate)
    }

    override fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T> {
        return (this as Gen<T>).filter(threshold, predicate)
    }
}
```

### Step 4: Update v1 FilterGeneratorTest ✅

**Goal**: Make v1 test implement the contract

**Changes to `gen/FilterGeneratorTest.kt`**:

Remove the `PredicateFiltering` nested class and implement the contract directly:

```kotlin
package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.contracts.FilterGeneratorTestContract

class FilterGeneratorTest : BaseGenTest(), FilterGeneratorTestContract {
    // Predicate filtering tests now inherited from contract

    // Keep the IgnoreExceptions nested class for now (out of scope)
    @Nested
    inner class IgnoreExceptions {
        // ...existing exception tests remain unchanged...
    }
}
```

**Verify**: Run v1 tests to ensure they pass.

### Step 5: Create FilterGeneratorV2Test ✅

**Goal**: Create v2 test class (will fail until implementation)

**New file**: `src/test/kotlin/com/tamj0rd2/ktcheck/v2/FilterGeneratorV2Test.kt`

**Content**:

```kotlin
package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contracts.FilterGeneratorTestContract

internal class FilterGeneratorV2Test : BaseGenV2Test(), FilterGeneratorTestContract {
    // All tests inherited from contract
}
```

### Step 6: Stub v2 Implementation in BaseGenV2Test ✅

**Goal**: Add factory methods that throw NotImplementedError (temporary)

**Changes to `v2/BaseGenV2Test.kt`**:

```kotlin
abstract class BaseGenV2Test : BaseGeneratorContract {
    // ...existing methods...

    override fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T> {
        throw NotImplementedError("filter not yet implemented for v2")
    }

    override fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T> {
        throw NotImplementedError("filter not yet implemented for v2")
    }
}
```

### Step 7: Implement FilterGeneratorV2 ✅

**Goal**: Port v1's predicate filtering logic to v2 architecture

**New file**: `src/main/kotlin/com/tamj0rd2/ktcheck/v2/FilterGeneratorV2.kt`

**Key differences from v1**:

- v1 traverses `tree.right` for retries
- v2 calls `gen.generate(producer)` repeatedly for retries
- v1 filters `Sequence<ProducerTree>` shrinks
- v2 filters `Sequence<GenResultV2<T>>` shrinks

**Implementation**:

```kotlin
package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.gen.FilterLimitReached

private class FilterGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val threshold: Int,
    private val predicate: (T) -> Boolean,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var attempts = 0

        while (attempts < threshold) {
            val result = gen.generate(producer)

            if (predicate(result.value)) {
                // Filter shrinks to only include values that pass predicate
                val validShrinks = filterValidShrinks(result.shrinks)
                return result.copy(shrinks = validShrinks)
            }

            attempts++
        }

        throw FilterLimitReached(threshold, null)
    }

    private fun filterValidShrinks(
        shrinks: Sequence<GenResultV2<T>>
    ): Sequence<GenResultV2<T>> {
        return shrinks
            .filter { shrunkResult -> predicate(shrunkResult.value) }
            .map { shrunkResult ->
                // Recursively filter nested shrinks
                shrunkResult.copy(
                    shrinks = filterValidShrinks(shrunkResult.shrinks)
                )
            }
    }
}

fun <T> GenV2<T>.filter(predicate: (T) -> Boolean): GenV2<T> =
    filter(100, predicate)

fun <T> GenV2<T>.filter(threshold: Int, predicate: (T) -> Boolean): GenV2<T> =
    FilterGeneratorV2(gen = this, threshold = threshold, predicate = predicate)
```

**Key points**:

- Retry by calling `gen.generate(producer)` repeatedly (different random values each time)
- Filter shrinks by testing `predicate(shrunkResult.value)` directly
- Recursively filter nested shrinks to maintain the invariant at all levels
- Reuse v1's `FilterLimitReached` exception

### Step 8: Update BaseGenV2Test ✅

**Goal**: Remove NotImplementedError and use actual implementation

**Changes to `v2/BaseGenV2Test.kt`**:

```kotlin
abstract class BaseGenV2Test : BaseGeneratorContract {
    // ...existing methods...

    override fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T> {
        return (this as GenV2<T>).filter(predicate)
    }

    override fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T> {
        return (this as GenV2<T>).filter(threshold, predicate)
    }
}
```

### Step 9: Verify ✅

**Goal**: Run all tests and ensure both v1 and v2 pass

```bash
./gradlew :test --tests "com.tamj0rd2.ktcheck.gen.FilterGeneratorTest" --console=plain
./gradlew :test --tests "com.tamj0rd2.ktcheck.v2.FilterGeneratorV2Test" --console=plain
```

## Implementation Order

1. Update `BaseGeneratorContract` - Add `filterGen()` factory methods
2. Create `FilterGeneratorTestContract` - Add 3 predicate filtering tests
3. Implement factory methods in `BaseGenTest` (v1)
4. Update v1 `FilterGeneratorTest` to implement contract
5. Verify v1 tests pass
6. Create `FilterGeneratorV2Test` (empty, will fail)
7. Add stub implementations in `BaseGenV2Test` (NotImplementedError)
8. Implement `FilterGeneratorV2` with extension functions
9. Update `BaseGenV2Test` to use actual implementations
10. Verify v2 tests pass

## Success Criteria

- [ ] `BaseGeneratorContract` has `filterGen()` factory methods
- [ ] `FilterGeneratorTestContract` created with 3 tests
- [ ] v1 `FilterGeneratorTest` implements contract and passes all tests
- [ ] `FilterGeneratorV2Test` created and implements contract
- [ ] `FilterGeneratorV2` correctly implements predicate filtering
- [ ] Shrink filtering prevents infinite shrinking loops
- [ ] v2 reuses v1's `FilterLimitReached` exception
- [ ] All tests pass for both v1 and v2

## Out of Scope

- ❌ `ignoreExceptions()` functionality - Deferred for future work
- ❌ Exception handling tests - Remain in v1 only for now
- ❌ Multiple exception types - Deferred for future work

## Notes

### Why v2 Shrink Filtering is Simpler

In v1, shrinks are `Sequence<ProducerTree>` - we must regenerate from each tree to test the predicate.

In v2, shrinks are `Sequence<GenResultV2<T>>` - we already have the values, so we can test the predicate directly on
`shrunkResult.value`.

This makes v2's implementation actually simpler for predicate filtering!

### Recursive Filtering

Both v1 and v2 must recursively filter shrinks because:

- If a shrink fails the predicate, it shouldn't be included
- The shrinks of valid shrinks might also fail the predicate
- We need to filter at every level of the shrink tree

### Retry Behavior Difference

**v1**: Traverses `tree.right` for retries, reusing the same seed path
**v2**: Calls `gen.generate(producer)` repeatedly, getting different random values

For filtering, this difference is fine - we just need *any* valid value, not a specific one.

## Next Step

Start with **Step 1: Update BaseGeneratorContract** to add the filter factory methods.

