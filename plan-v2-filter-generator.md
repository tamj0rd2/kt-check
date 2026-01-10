# Plan: v2 Filter Generator Implementation

## Context

We need to implement filter generation for v2 to achieve feature parity with v1. The v1 implementation supports:

- Filtering generated values with a predicate
- Ignoring specific exception types during generation
- Configurable threshold for retry attempts
- Filtering shrinks to prevent infinite shrinking loops

## Current v1 Implementation Analysis

### FilterGenerator Components

1. **PredicateResult** - Sealed interface representing success/failure:
   ```kotlin
   sealed interface PredicateResult<T> {
       value class Succeeded<T>(val genResult: GenResult<T>)
       value class Failed<T>(val failure: Exception? = null)
   }
   ```

2. **FilterGenerator** - Core generator that retries until predicate succeeds:
    - Takes a threshold (max retry attempts)
    - Takes a function that returns `PredicateResult<T>`
    - Filters shrinks to only include values that pass the predicate
    - Throws `FilterLimitReached` if threshold exceeded

3. **Extension Functions**:
    - `filter(predicate: (T) -> Boolean)` - Filter by predicate (default threshold 100)
    - `filter(threshold: Int, predicate: (T) -> Boolean)` - Filter with custom threshold
    - `ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100)` - Ignore specific exceptions

### Key Implementation Details

**v1 uses ProducerTree traversal**:

```kotlin
generateSequence(tree) { it.right }  // Traverse right subtree for retries
    .take(threshold)
    .map { getResult(GenContext(it.left, mode)) }
    .filterIsInstance<Succeeded<T>>()
    .firstOrNull()
```

**Shrink filtering**:

```kotlin
val validShrinks = genResult.shrinks
    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
    .map { tree.withLeft(it) }
```

This prevents shrinking to values that would fail the filter, which would cause infinite shrinking loops.

## Landmark Steps

### Step 1: Create FilterGeneratorTestContract ✅

**Goal**: Define the contract that specifies expected filter generator behavior

**What to include**:

1. **Predicate Filtering Tests**:
    - Can filter generated values
    - Throws when filter threshold is exceeded
    - Doesn't produce shrinks that would fail the predicate

2. **Exception Ignoring Tests**:
    - Can ignore exceptions in generated values
    - Doesn't produce shrinks that would cause the exception
    - Throws if ignored exception exceeds threshold
    - Non-ignored exceptions propagate
    - Can ignore multiple exception types

**Factory methods to add to BaseGeneratorContract**:

```kotlin
interface BaseGeneratorContract {
    // ...existing methods...

    // Factory methods for filtered generators
    fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T>
    fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T>

    // Factory method for ignoring exceptions
    fun <T> IGen<T>.ignoreExceptionsGen(
        klass: KClass<out Exception>,
        threshold: Int = 100
    ): IGen<T>
}
```

**FilterGeneratorTestContract** (all tests at root level):

```kotlin
interface FilterGeneratorTestContract : BaseGeneratorContract {
    // Predicate filtering tests
    @Test
    fun `can filter generated values`() {
        val gen = intGen(1..10).filterGen { it % 2 == 0 }
        gen.samples().take(100).forEach {
            expectThat(it % 2).isEqualTo(0)
        }
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = intGen(1..10).filterGen { it > 10 }
        expectThrows<FilterLimitReached> { gen.sample() }
    }

    @Test
    fun `doesn't produce shrinks that would fail the predicate`() {
        ...
    }

    // Exception ignoring tests
    @Test
    fun `can ignore exceptions in generated values`() {
        ...
    }

    @Test
    fun `doesn't produce shrinks that would cause the exception`() {
        ...
    }

    @Test
    fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
        ...
    }

    @Test
    fun `if a non-ignored exception is thrown, it propagates`() {
        ...
    }

    @Test
    fun `can ignore multiple exceptions types`() {
        ...
    }
}
```

**Files to modify**:

- `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/BaseGeneratorContract.kt` - Add factory methods
- `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/FilterGeneratorTestContract.kt` - NEW: Create with all tests at root
  level

### Step 2: Move v1 Tests into Contract ✅

**Goal**: Extract existing v1 filter tests and make them implementation-agnostic

**Current v1 tests** (from `FilterGeneratorTest.kt`):

**Predicate Filtering**:

- `can filter generated values`
- `throws if the filter threshold is exceeded`
- `doesn't produce shrinks that would fail the predicate`

**Ignore Exceptions**:

- `can ignore exceptions in generated values`
- `doesn't produce shrinks that would cause the exception`
- `if an ignored exception is thrown more times than the threshold, throws an error`
- `if a non-ignored exception is thrown, it propagates`
- `can ignore multiple exceptions types`

**Challenges**:

1. Tests use `Gen.int().filter()` directly
2. Tests use `ProducerTree` for setup (v1-specific)
3. Need to abstract these details

**Solution**: All tests at root level of `FilterGeneratorTestContract`:

```kotlin
interface FilterGeneratorTestContract : BaseGeneratorContract {
    // Factory methods are in BaseGeneratorContract - no need to define here

    // === Predicate Filtering Tests ===

    @Test
    fun `can filter generated values`() {
        val gen = intGen(1..10).filterGen { it % 2 == 0 }
        gen.samples().take(100).forEach {
            expectThat(it % 2).isEqualTo(0)
        }
    }

    @Test
    fun `throws if the filter threshold is exceeded`() {
        val gen = intGen(1..10).filterGen { it > 10 }
        expectThrows<FilterLimitReached> { gen.sample() }
    }

    @Test
    fun `doesn't produce shrinks that would fail the predicate`() {
        val gen = intGen(1..4).filterGen { it > 2 }
        // Use generateWithShrunkValues from BaseGeneratorContract
        val (value, shrinks) = gen.generateWithShrunkValues(...)
        expectThat(value).isGreaterThan(2)
        shrinks.forEach { shrunkValue ->
            expectThat(shrunkValue).isGreaterThan(2)
        }
    }

    // === Exception Ignoring Tests ===

    @Test
    fun `can ignore exceptions in generated values`() {
        class TestException : Exception()

        val gen = boolGen()
            .map { if (it) throw TestException() else false }
            .ignoreExceptionsGen(TestException::class)

        val values = gen.samples().take(100).toList()
        expectThat(values).all { isFalse() }
    }

    @Test
    fun `doesn't produce shrinks that would cause the exception`() {
        ...
    }

    @Test
    fun `if an ignored exception is thrown more times than the threshold, throws an error`() {
        ...
    }

    @Test
    fun `if a non-ignored exception is thrown, it propagates`() {
        ...
    }

    @Test
    fun `can ignore multiple exceptions types`() {
        ...
    }
}
```

**Note**: No `mapGen()` needed - `IGen` already has `map()` defined.

### Step 3: Make Contract Generic for v1 and v2 ✅

**Goal**: Ensure contract can be implemented by both v1 and v2

**Key differences to handle**:

1. **ProducerTree (v1) vs ValueProducer (v2)**:
    - Contract doesn't expose internal details
    - Tests use `.samples()` which works for both

2. **Gen (v1) vs GenV2 (v2)**:
    - Contract returns `IGen` (common interface)
    - Implementations cast to their specific type

3. **Shrink validation**:
    - Both need to filter shrinks to prevent infinite loops
    - v1: filters ProducerTree shrinks
    - v2: filters GenResult shrinks
    - Implementation detail, not exposed in contract

**Implementation in BaseGeneratorContract** (add to existing interface):

```kotlin
interface BaseGeneratorContract {
    // ...existing methods...

    // Filter generator factory methods
    fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T>
    fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T>
    fun <T> IGen<T>.ignoreExceptionsGen(
        klass: KClass<out Exception>,
        threshold: Int = 100
    ): IGen<T>

    // Map is also needed for exception tests
    fun <T, R> IGen<T>.mapGen(f: (T) -> R): IGen<R>
}
```

**Implementation in v1** (`gen/BaseGenTest.kt`):

```kotlin
abstract class BaseGenTest : BaseGeneratorContract {
    // ...existing methods...

    override fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T> {
        return (this as Gen<T>).filter(predicate)
    }

    override fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T> {
        return (this as Gen<T>).filter(threshold, predicate)
    }

    override fun <T> IGen<T>.ignoreExceptionsGen(
        klass: KClass<out Exception>,
        threshold: Int
    ): IGen<T> {
        return (this as Gen<T>).ignoreExceptions(klass, threshold)
    }
}
```

**Implementation in v2** (`v2/BaseGenV2Test.kt`):

```kotlin
abstract class BaseGenV2Test : BaseGeneratorContract {
    // ...existing methods...

    override fun <T> IGen<T>.filterGen(predicate: (T) -> Boolean): IGen<T> {
        return (this as GenV2<T>).filter(predicate)
    }

    override fun <T> IGen<T>.filterGen(threshold: Int, predicate: (T) -> Boolean): IGen<T> {
        return (this as GenV2<T>).filter(threshold, predicate)
    }

    override fun <T> IGen<T>.ignoreExceptionsGen(
        klass: KClass<out Exception>,
        threshold: Int
    ): IGen<T> {
        return (this as GenV2<T>).ignoreExceptions(klass, threshold)
    }
}
```

**Files to modify**:

- `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/BaseGeneratorContract.kt` - Add filter/map factory methods
- `src/test/kotlin/com/tamj0rd2/ktcheck/gen/BaseGenTest.kt` - Implement factory methods for v1
- `src/test/kotlin/com/tamj0rd2/ktcheck/v2/BaseGenV2Test.kt` - Implement factory methods for v2 (will throw
  NotImplementedError until Step 5)

### Step 4: Create FilterGeneratorV2Test ✅

**Goal**: Create test class that implements the contract for v2

**File**: `src/test/kotlin/com/tamj0rd2/ktcheck/v2/FilterGeneratorV2Test.kt`

**Implementation**:

```kotlin
package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contracts.FilterGeneratorTestContract

internal class FilterGeneratorV2Test : BaseGenV2Test(), FilterGeneratorTestContract {
    // All tests inherited from FilterGeneratorTestContract
    // No nested classes needed
}
```

**Dependencies**:

- Requires `GenV2.filter()` extension functions to exist
- Requires `GenV2.ignoreExceptions()` extension function to exist
- Requires `FilterGeneratorV2` implementation

### Step 5: Implement FilterGeneratorV2 ✅

**Goal**: Port v1's filter logic to v2 architecture

**Key Differences Between v1 and v2**:

| Aspect           | v1 (ProducerTree)            | v2 (GenResult)                    |
|------------------|------------------------------|-----------------------------------|
| Retry mechanism  | Traverse tree.right          | Generate new values from producer |
| Shrink filtering | Filter ProducerTree sequence | Filter GenResult sequence         |
| Context          | GenContext(tree, mode)       | GenContextV2(producer)            |
| Result           | GenResult with tree shrinks  | GenResultV2 with result shrinks   |

**v2 Implementation Strategy**:

**Option A: ValueProducer-based retries** (Recommended)

```kotlin
private class FilterGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val threshold: Int,
    private val getResult: GenContextV2.() -> PredicateResultV2<T>,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var lastFailure: Exception? = null

        // Try up to threshold times
        repeat(threshold) {
            when (val result = getResult()) {
                is SucceededV2 -> {
                    // Filter shrinks to only include valid values
                    val validShrinks = result.genResult.shrinks
                        .filter { shrunkResult ->
                            // Check if shrunk value passes predicate
                            testPredicate(shrunkResult) is SucceededV2
                        }

                    return result.genResult.copy(shrinks = validShrinks)
                }
                is FailedV2 -> lastFailure = result.failure
            }
        }

        throw FilterLimitReachedV2(threshold, lastFailure)
    }

    private fun testPredicate(result: GenResultV2<T>): PredicateResultV2<T> {
        // Helper to test if a result passes the predicate
        // Implementation depends on predicate type
        TODO()
    }
}
```

**Challenge**: v2 doesn't have a tree to traverse. How do we retry?

**Solution**: Generate new values from the producer repeatedly until one passes:

```kotlin
override fun GenContextV2.generate(): GenResultV2<T> {
    var lastFailure: Exception? = null

    repeat(threshold) {
        val result = gen.generate(producer)
        when (val predicateResult = testPredicate(result)) {
            is SucceededV2 -> {
                val validShrinks = filterValidShrinks(result)
                return result.copy(shrinks = validShrinks)
            }
            is FailedV2 -> lastFailure = predicateResult.failure
        }
    }

    throw FilterLimitReachedV2(threshold, lastFailure)
}
```

**Important Note**: Unlike v1 which traverses tree.right, v2 generates new independent values. This means:

- ✅ Simpler implementation
- ⚠️ Different random values on each retry (v1 reuses same seed path)
- ✅ For filtering, this is fine - we just want *any* valid value

**Shrink Filtering Implementation**:

```kotlin
private fun filterValidShrinks(result: GenResultV2<T>): Sequence<GenResultV2<T>> {
    return result.shrinks.filter { shrunkResult ->
        // Test if shrunk value passes predicate
        // Need to capture the predicate logic somehow
        testPredicate(shrunkResult) is SucceededV2
    }
}
```

**Full Implementation**:

```kotlin
// PredicateResult for v2
private sealed interface PredicateResultV2<T> {
    @JvmInline
    value class SucceededV2<T>(val genResult: GenResultV2<T>) : PredicateResultV2<T>

    @JvmInline
    value class FailedV2<T>(val failure: Exception? = null) : PredicateResultV2<T>
}

private class FilterGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val threshold: Int,
    private val test: (T) -> PredicateResultV2<T>,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var lastFailure: Exception? = null

        repeat(threshold) {
            val result = gen.generate(producer)
            when (val testResult = test(result.value)) {
                is SucceededV2 -> {
                    val validShrinks = result.shrinks.filter { shrunkResult ->
                        test(shrunkResult.value) is SucceededV2
                    }
                    return result.copy(shrinks = validShrinks)
                }
                is FailedV2 -> lastFailure = testResult.failure
            }
        }

        throw FilterLimitReachedV2(threshold, lastFailure)
    }
}

class FilterLimitReachedV2 internal constructor(threshold: Int, cause: Throwable?) :
    GenerationException("Filter failed after $threshold misses", cause)

fun <T> GenV2<T>.filter(predicate: (T) -> Boolean) = filter(100, predicate)

fun <T> GenV2<T>.filter(threshold: Int, predicate: (T) -> Boolean): GenV2<T> =
    FilterGeneratorV2(gen = this, threshold = threshold) { value ->
        if (predicate(value)) SucceededV2(GenResultV2(value, emptySequence()))
        else FailedV2()
    }

fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> =
    FilterGeneratorV2(gen = this, threshold = threshold) { value ->
        // Can't really test exceptions here since we already have the value
        // Need different approach...
        TODO()
    }
```

**Problem with Exception Handling**: v2's approach is different. In v1, we can catch exceptions during `generate()`. In
v2, we need to catch them earlier.

**Revised Approach for ignoreExceptions**:

```kotlin
fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> =
    object : GenV2<T>() {
        override fun GenContextV2.generate(): GenResultV2<T> {
            var lastException: Exception? = null

            repeat(threshold) {
                try {
                    val result = this@ignoreExceptions.generate(producer)
                    // Filter shrinks
                    val validShrinks = result.shrinks.filter { shrunkResult ->
                        try {
                            true  // Shrunk value is valid
                        } catch (e: Exception) {
                            !klass.isInstance(e)  // Only filter if it's the ignored exception
                        }
                    }
                    return result.copy(shrinks = validShrinks)
                } catch (e: Exception) {
                    when {
                        !klass.isInstance(e) -> throw e
                        else -> lastException = e
                    }
                }
            }

            throw FilterLimitReachedV2(threshold, lastException)
        }
    }
```

Wait, this has a problem. We can't test shrinks for exceptions without actually generating them, but we already have the
GenResultV2.

**Better Approach**: Wrap the original generator's generation in try-catch:

```kotlin
private class ExceptionIgnoringGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val klass: KClass<out Exception>,
    private val threshold: Int,
) : GenV2<T>() {
    override fun GenContextV2.generate(): GenResultV2<T> {
        var lastException: Exception? = null

        repeat(threshold) {
            try {
                val result = gen.generate(producer)
                // Filter shrinks - only include ones that don't throw
                val validShrinks = result.shrinks.filter { testShrink(it) }
                return result.copy(shrinks = validShrinks)
            } catch (e: Exception) {
                when {
                    !klass.isInstance(e) -> throw e
                    else -> lastException = e
                }
            }
        }

        throw FilterLimitReachedV2(threshold, lastException)
    }

    private fun testShrink(shrunkResult: GenResultV2<T>): Boolean {
        // Problem: How do we test if a GenResultV2 would throw?
        // The value is already generated - it's in shrunkResult.value
        // We can't "test generate" it again
        return true  // For now, accept all shrinks
    }
}
```

**Actually**, looking at v1 more carefully:

In v1, the exception happens when calling `generate(tree, mode)`, not when accessing the value:

```kotlin
Succeeded(generate(tree, mode))  // Exception can be thrown here
```

In v2, the exception would happen when calling `gen.generate(producer)`, not when accessing `result.value`.

So the shrink filtering in v2 should work similarly:

```kotlin
val validShrinks = sequence {
    for (shrunkResult in result.shrinks) {
        // The shrunkResult is already a GenResultV2<T>
        // If generating it threw an exception, we wouldn't have it
        // So all shrinks in result.shrinks are already valid
        yield(shrunkResult)
    }
}
```

**Wait, this is wrong too**. The shrinks are `Sequence<GenResultV2<T>>` - they're already generated results, not trees
to test.

Let me reconsider v1's approach...

In v1:

```kotlin
val validShrinks = genResult.shrinks  // Sequence<ProducerTree>
    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
    .map { tree.withLeft(it) }
```

The shrinks are `ProducerTree`s, not values. So we can test each tree by generating from it.

In v2:

```kotlin
val validShrinks = result.shrinks  // Sequence<GenResultV2<T>>
// These are already generated results with values
// We can't "test generate" them again
```

**Key Insight**: In v2, we need a different approach. We can't re-generate shrinks because they're already GenResultV2s,
not "instructions to generate".

**Solution**: Test the predicate directly on the shrunk value:

```kotlin
val validShrinks = result.shrinks.filter { shrunkResult ->
    predicate(shrunkResult.value)  // Test the predicate on the shrunk value
}
```

For exception ignoring:

```kotlin
val validShrinks = result.shrinks
// All shrinks are already valid - if they threw an exception, 
// they wouldn't be in the sequence
// But we need to handle the case where the shrunk value's shrinks might throw
```

**Actually**, I think the issue is that in v2, shrinks are *already generated*. If a generator throws an exception, it
would have thrown when creating the GenResultV2, not when we access the sequence.

But the shrinks might have their own shrinks that could throw. Let me think about this more carefully...

**Revised Understanding**:

In v1:

- Shrinks are `Sequence<ProducerTree>` - recipes for generation
- We test each recipe by generating from it
- If generation fails (exception/predicate), we filter it out

In v2:

- Shrinks are `Sequence<GenResultV2<T>>` - already-generated results
- Each GenResultV2 has its own shrinks (recursively)
- We need to filter at each level

**Correct v2 Implementation**:

For predicate filtering:

```kotlin
private fun filterValidShrinks(
    result: GenResultV2<T>,
    predicate: (T) -> Boolean
): Sequence<GenResultV2<T>> {
    return result.shrinks
        .filter { predicate(it.value) }  // Filter by predicate
        .map { shrunkResult ->
            // Recursively filter nested shrinks
            shrunkResult.copy(
                shrinks = filterValidShrinks(shrunkResult, predicate)
            )
        }
}
```

For exception ignoring:

- The shrinks in `result.shrinks` are already generated
- If they threw the ignored exception, they wouldn't be there
- But their nested shrinks might throw
- We need to recursively filter

```kotlin
private fun filterValidShrinks(
    result: GenResultV2<T>,
    klass: KClass<out Exception>,
    producer: ValueProducerV2
): Sequence<GenResultV2<T>> {
    return result.shrinks.mapNotNull { shrunkResult ->
        // The shrunk result itself is valid (it's already generated)
        // But its nested shrinks might not be
        // We need to recursively filter
        shrunkResult.copy(
            shrinks = filterValidShrinks(shrunkResult, klass, producer)
        )
    }
}
```

**Hmm, but this still doesn't handle testing for exceptions...**

Let me look at the v1 implementation again more carefully:

```kotlin
FilterGenerator(threshold) {
    try {
        Succeeded(generate(tree, mode))
    } catch (e: Exception) {
        when {
            !klass.isInstance(e) -> throw e
            else -> Failed(e)
        }
    }
}
```

And for shrink filtering:

```kotlin
val validShrinks = genResult.shrinks
    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
```

So for each shrink tree, it calls `getResult` which calls `generate(tree, mode)` which might throw.

In v2, we can't do this because shrinks are already GenResultV2s, not generators.

**Solution**: The v2 shrinks are already filtered! If generating a shrink threw an exception, it wouldn't be in the
sequence. The generator that produced the shrinks would have already filtered them out.

So for v2:

```kotlin
fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> =
    object : GenV2<T>() {
        override fun GenContextV2.generate(): GenResultV2<T> {
            var lastException: Exception? = null

            repeat(threshold) {
                try {
                    val result = this@ignoreExceptions.generate(producer)
                    // Shrinks are already valid - they were generated without throwing
                    // Just return them as-is
                    return result
                } catch (e: Exception) {
                    when {
                        !klass.isInstance(e) -> throw e
                        else -> lastException = e
                    }
                }
            }

            throw FilterLimitReachedV2(threshold, lastException)
        }
    }
```

**But wait**, the shrinks might have their own shrinks that could throw when accessed. But in v2's lazy evaluation
model, accessing a shrink from a sequence doesn't call `generate()` again...

I think I need to reconsider the whole v2 architecture for this. Let me check how v2's shrinks work:

In v2, shrinks are `Sequence<GenResultV2<T>>`. These are produced by the generator's `generateXShrinks()` method. The
shrinks are generated lazily but they're still *results*, not *generators*.

So if a shrink would throw an exception, it would throw when the sequence is consumed, not when we create the
GenResultV2.

**Actually I think the right approach is**:

1. For predicate filtering, filter shrinks by testing the predicate on their values
2. For exception ignoring, we can't filter shrinks in v2 the same way as v1
    - In v1, we re-generate from each shrink tree to test
    - In v2, shrinks are already generated, so they didn't throw
    - But their nested shrinks might throw when consumed

**I think the simplest approach is**: Don't try to pre-filter shrinks for exceptions. Just let the shrinking process
naturally skip shrinks that throw the ignored exception.

Actually, looking at the v1 test:

```kotlin
`doesn't produce shrinks that would cause the exception`
```

This test verifies that the shrinks don't include values that would throw. In v1, this is achieved by testing each
shrink tree.

In v2, we'd need to... actually wrap each shrink in an exception handler? Or filter the sequence?

**Let me re-read the v2 shrinking model from the list generator**:

```kotlin
shrinks = generateListShrinks(size, elementResults)
```

The shrinks are generated as a sequence. They're not "pre-generated" - they're generated lazily when consumed.

So we could wrap the shrinks sequence to catch exceptions:

```kotlin
val validShrinks = result.shrinks.mapNotNull { shrunkResult ->
    try {
        // Access the shrunk value to see if it throws
        shrunkResult.value
        shrunkResult
    } catch (e: Exception) {
        when {
            !klass.isInstance(e) -> throw e
            else -> null  // Filter out this shrink
        }
    }
}
```

But shrunkResult.value shouldn't throw - it's just accessing a property...

**OH! I see the issue now**. In the v1 test with `map`:

```kotlin
Gen.int(1..3)
    .map {
        when (it) {
            1 -> throw TestException()
            else -> it
        }
    }
    .ignoreExceptions(TestException::class)
```

The exception is thrown in the `map` function. So when we generate:

```kotlin
generate(tree, mode)  // This calls map, which might throw
```

In v2, the equivalent would be:

```kotlin
gen.generate(producer)  // This calls map, which might throw
```

And for shrinks:

```kotlin
result.shrinks  // Sequence<GenResultV2<T>>
```

Each element in the sequence was created by calling the mapped generator. If the map throws, we get an exception when
consuming the sequence.

So we can filter:

```kotlin
val validShrinks = result.shrinks.mapNotNull { shrunkResult ->
    // Just accessing shrunkResult shouldn't throw
    // The exception would have been thrown when creating shrunkResult
    // So all shrinks in the sequence are valid
    shrunkResult
}
```

**Wait, this can't be right either.** If the shrink threw an exception, it wouldn't be in the sequence at all...

Let me trace through the v2 list generator to understand how shrinks are created:

```kotlin
shrinks = generateListShrinks(size, elementResults)

private fun generateListShrinks(...): Sequence<GenResultV2<List<T>>> = sequence {
    // Size shrinks
    yield(GenResultV2(emptyList(), emptySequence()))

    // Element shrinks
    elementResults[index].shrinks.forEach { shrunkElementResult ->
        yield(GenResultV2(value = ..., shrinks = ...))
    }
}
```

The `yield` doesn't call `generate()`. It just creates GenResultV2 objects.

So when we do `Gen.map { if (it == 1) throw Exception() }`, the exception is thrown during the generation of the
*element*, not during the creation of the GenResultV2.

In the filter/ignoreExceptions case, we're wrapping an existing generator. So:

```kotlin
val baseGen = Gen.int().map { if (it == 1) throw Ex() }
val filtered = baseGen.ignoreExceptions(Ex::class)

filtered.generate(producer)
```

This calls:

```kotlin
override fun generate(): GenResultV2<T> {
    try {
        val result = baseGen.generate(producer)  // Might throw here
        return result
    } catch (e: Exception) {
        // Handle
    }
}
```

And for shrinks, `result.shrinks` is a sequence. But the elements of that sequence are GenResultV2 objects that were
created by `baseGen`'s shrink logic. They don't throw when accessed, but they might have values that would throw if
regenerated.

But we're not regenerating them. We already have the values.

**EUREKA**: The issue is that in v1, shrinks are trees that *represent* values. We test them by generating from them. In
v2, shrinks are *actual values* wrapped in GenResultV2. We can't "test generate" them because they're already generated.

**The solution**: In v2, we need to test the predicate or exception handler on the VALUE, not on the generation process.

For predicates:

```kotlin
val validShrinks = result.shrinks.filter { shrunkResult ->
    predicate(shrunkResult.value)
}
```

For exceptions:

- We can't test if generating the value would throw, because it's already generated
- The only way a shrink could "cause an exception" is if accessing its value throws
- But in the tests, the exception is thrown during mapping, not during value access

**I think the answer is**: In v2, we can't perfectly replicate v1's shrink filtering for exceptions. But we can:

1. For predicates: Filter shrinks by testing the predicate on their values ✅
2. For exceptions: Filter shrinks that would fail when used in a `checkAll` test

Actually, let me read the v1 test more carefully:

```kotlin
val possiblyThrowingGen = Gen.int(1..3)
    .map {
        when (it) {
            1 -> throw TestException()
            else -> it
        }
    }
    .ignoreExceptions(TestException::class)

val tree = producerTree {
    left(3)
    right {
        left(2)
    }
}

val (value, shrunkTrees) = possiblyThrowingGen.generate(tree, GenMode.Initial)
expectThat(value).isEqualTo(3)
expectThat(shrunkTrees.toList())
    .describedAs("shrunk trees")
    .isNotEmpty()
    .all { leftProducer.isNotEqualTo(PredeterminedValue(1)) }
```

The test verifies that the shrunk trees don't include one with `left(1)`.

In v1, when we filter shrinks:

```kotlin
val validShrinks = genResult.shrinks
    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
```

We call `getResult` which tries to generate from each shrink tree. If it throws the exception, we filter it out.

In v2, we need equivalent logic. But shrinks are GenResultV2s, not trees.

**New idea**: What if, in v2, we don't filter shrinks at all for `ignoreExceptions`? What if we just let the shrinking
process naturally handle it?

When shrinking in a property test:

```kotlin
// Pseudo-code
fun findMinimalFailure(gen, test) {
    val result = gen.generate()
    if (test(result.value)) return result.value

    for (shrink in result.shrinks) {
        try {
            if (test(shrink.value)) return findMinimalFailure(shrink, test)
        } catch (IgnoredException) {
            continue  // Skip this shrink
        }
    }
}
```

If a shrink throws the ignored exception when tested, the shrinking process would skip it. So we don't need to
pre-filter.

**But the test explicitly checks that the shrinks don't include the bad value**, so we do need to filter.

**Final answer**: For v2 `ignoreExceptions`, we need to test each shrink's value in the wrapped context:

```kotlin
fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> {
    return object : GenV2<T>() {
        override fun GenContextV2.generate(): GenResultV2<T> {
            var lastException: Exception? = null

            repeat(threshold) {
                try {
                    val result = this@ignoreExceptions.generate(producer)

                    // Filter shrinks: test each one to see if it throws
                    val validShrinks = result.shrinks.filter { shrunkResult ->
                        try {
                            // The only way to test if the shrink is valid is to
                            // "use" the value in a way that might trigger the exception
                            // But since the exception is from a .map or other operation,
                            // and we already have the value, it won't throw here

                            // We can't re-generate the shrink to test it
                            // So we have to accept all shrinks
                            true
                        } catch (e: Exception) {
                            !klass.isInstance(e)
                        }
                    }

                    return result.copy(shrinks = validShrinks)
                } catch (e: Exception) {
                    when {
                        !klass.isInstance(e) -> throw e
                        else -> lastException = e
                    }
                }
            }

            throw FilterLimitReachedV2(threshold, lastException)
        }
    }
}
```

I'm going in circles. Let me think about this differently.

**The problem**: In v1, shrinks are `Sequence<ProducerTree>`. We can test each tree by calling `generate(tree, mode)` to
see if it throws. In v2, shrinks are `Sequence<GenResultV2<T>>`. The values are already generated, so we can't test if "
generating them" would throw.

**But wait**: If a `map` operation throws during generation, the generation fails. So if
`Gen.int().map { if (it == 1) throw Ex() }.generate(producer)` throws when the producer gives us 1, then there's no
GenResultV2 with value 1. It just throws.

So the shrinks in `result.shrinks` are all values that were successfully generated. They didn't throw during generation.

**The test is checking**: Does the shrink tree include a tree that would generate the value 1?

In v1: Check each tree by generating from it.
In v2: Check each result by... we already have the result.

If we have `GenResultV2(value=2, shrinks=...)`, we know that 2 was successfully generated. One of the shrinks might be
`GenResultV2(value=1, shrinks=...)`, and that means 1 was also successfully generated.

**But the test expects that value 1 is NOT in the shrinks!**

OH! I see it now. The shrinks are created by the underlying generator (e.g., IntGenerator), and then we filter them.

In v1:

```kotlin
val baseResult = generateWithoutFiltering()
val filteredShrinks = baseResult.shrinks.filter { testIt() }
return baseResult.copy(shrinks = filteredShrinks)
```

In v2:

```kotlin
val baseResult = baseGen.generate()  // This is IntGenerator's result
// baseResult.shrinks includes GenResultV2(value=1, ...)
val filteredShrinks = baseResult.shrinks.filter { /* test it somehow */ }
return baseResult.copy(shrinks = filteredShrinks)
```

To test if shrink value 1 would throw:

```kotlin
val filteredShrinks = baseResult.shrinks.filter { shrinkResult ->
    // We need to test if using this shrink's value would throw
    // The original gen is: Gen.int().map { if (it == 1) throw Ex() }
    // shrinkResult.value is the Int value (e.g., 1, 2, 3)
    // But we've already passed through the map, so we have the mapped result

    // Actually, shrinkResult came from baseGen.generate(), which includes the map
    // So if the map threw, we wouldn't have shrinkResult

    // Unless... the shrinks are from BEFORE the map?
}
```

Aha! Let me check: does `Gen.int().map {...}.ignoreExceptions(...)` mean:

- `(Gen.int().map {...}).ignoreExceptions(...)`  // ignoreExceptions wraps the mapped gen
- The base shrinks come from IntGenerator
- But they're passed through the map
- So the shrinks are the results of mapping 0, 1, 2, etc.
- If mapping 1 throws, that shrink is never created

So the shrinks in baseResult.shrinks are already post-map. They don't include value 1 if mapping 1 throws.

**So why does v1 need to filter shrinks?**

Let me re-read the v1 code:

```kotlin
FilterGenerator(threshold) {
    val result = generate(tree, mode)  // Generate from Int generator + map
    if (predicate(result.value)) Succeeded(result) else Failed()
}
```

Oh! The `generate(tree, mode)` generates from the wrapped generator (Int + map). Then we test the predicate.

And for shrinks:

```kotlin
val validShrinks = genResult.shrinks  // These are IntGenerator's shrinks (ProducerTrees)
    .filter { getResult(GenContext(it, GenMode.Shrinking)) is Succeeded }
// For each shrink tree, we generate from Int + map
// If mapping throws, it fails, so we filter it out
```

So the shrinks are from IntGenerator (which include trees for 0, 1, 2, 3), and we test each one by generating through
the map.

In v2:

```kotlin
val baseResult = intGen.map { ... }.generate(producer)
// baseResult.shrinks are from IntGenerator
// But they're already mapped
// If mapping value 1 threw, we wouldn't have a shrink for it
```

Wait, no. Let me check the v2 map implementation:

Looking at v2 code structure, map would be:

```kotlin
fun <T, R> GenV2<T>.map(f: (T) -> R): GenV2<R> = object : GenV2<R>() {
    override fun generate(): GenResultV2<R> {
        val result = this@map.generate(producer)
        val mappedValue = f(result.value)
        val mappedShrinks = result.shrinks.map { shrinkResult ->
            val shrunkMappedValue = f(shrinkResult.value)
            GenResultV2(shrunkMappedValue, shrinkResult.shrinks.map { ... })
        }
        return GenResultV2(mappedValue, mappedShrinks)
    }
}
```

So when we call `intGen.map { if (it == 1) throw Ex() }.generate()`:

```kotlin
val intResult = intGen.generate()  // GenResultV2(value=3, shrinks=[...2, 1, 0...])
val mappedValue = f(3)  // = 3, doesn't throw
val mappedShrinks = intResult.shrinks.map { shrinkResult ->
    val shrunkMappedValue = f(shrinkResult.value)  // if shrinkResult.value == 1, throws here!
    ...
}
```

So when mapping the shrinks, if `f(1)` throws, the sequence mapping throws.

That's why we need to filter! In the `ignoreExceptions` wrapper:

```kotlin
val baseResult = gen.generate()  // gen is the mapped generator
// If this succeeds, we have a value
// But baseResult.shrinks is a sequence that might throw when consumed
val validShrinks = baseResult.shrinks.mapNotNull { shrinkResult ->
    // Accessing shrinkResult might throw if the map threw during shrink creation
    try {
        shrinkResult  // Just access it to see if it throws
    } catch (e: Exception) {
        if (klass.isInstance(e)) null else throw e
    }
}
```

**YES! This is the answer!**

For v2 `ignoreExceptions`:

```kotlin
fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> {
    return object : GenV2<T>() {
        override fun GenContextV2.generate(): GenResultV2<T> {
            var lastException: Exception? = null

            repeat(threshold) {
                try {
                    val result = this@ignoreExceptions.generate(producer)

                    // Filter shrinks: consume the sequence and catch exceptions
                    val validShrinks = sequence {
                        for (shrinkResult in result.shrinks) {
                            try {
                                // Access shrinkResult to trigger any exceptions from mapping
                                yield(shrinkResult)
                            } catch (e: Exception) {
                                when {
                                    !klass.isInstance(e) -> throw e
                                    // Ignored exception: skip this shrink
                                }
                            }
                        }
                    }

                    return result.copy(shrinks = validShrinks)
                } catch (e: Exception) {
                    when {
                        !klass.isInstance(e) -> throw e
                        else -> lastException = e
                    }
                }
            }

            throw FilterLimitReachedV2(threshold, lastException)
        }
    }
}
```

OK I think I finally understand it! Let me write up the correct implementation plan.

**Correct v2 Implementation Plan**:

1. **PredicateResultV2** - Optional, can be implicit with Boolean
2. **FilterGeneratorV2** for predicates:
   ```kotlin
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
                   // Filter shrinks by predicate
                   val validShrinks = result.shrinks.filter { shrinkResult ->
                       predicate(shrinkResult.value)
                   }
                   return result.copy(shrinks = validShrinks)
               }
               attempts++
           }
           throw FilterLimitReachedV2(threshold, null)
       }
   }
   ```

3. **ignoreExceptions** as a direct function:
   ```kotlin
   fun <T> GenV2<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int = 100): GenV2<T> {
       return object : GenV2<T>() {
           override fun GenContextV2.generate(): GenResultV2<T> {
               var lastException: Exception? = null
               var attempts = 0
               
               while (attempts < threshold) {
                   try {
                       val result = this@ignoreExceptions.generate(producer)
                       
                       // Filter shrinks: catch exceptions when consuming sequence
                       val validShrinks = sequence {
                           for (shrinkResult in result.shrinks) {
                               try {
                                   yield(shrinkResult)
                               } catch (e: Exception) {
                                   when {
                                       !klass.isInstance(e) -> throw e
                                       // Skip ignored exceptions
                                   }
                               }
                           }
                       }
                       
                       return result.copy(shrinks = validShrinks)
                   } catch (e: Exception) {
                       when {
                           !klass.isInstance(e) -> throw e
                           else -> {
                               lastException = e
                               attempts++
                           }
                       }
                   }
               }
               
               throw FilterLimitReachedV2(threshold, lastException)
           }
       }
   }
   ```

Much clearer now!

## Implementation Order

1. **Update BaseGeneratorContract** (Step 1)
    - Add `filterGen()` factory methods
    - Add `ignoreExceptionsGen()` factory method

2. **Create FilterGeneratorTestContract** (Step 1)
    - Create interface with all 8 tests at root level
    - 3 predicate filtering tests
    - 5 exception ignoring tests
    - Port tests from v1, using factory methods from BaseGeneratorContract
    - Use `IGen.map()` directly (already defined on IGen)

3. **Implement factory methods in BaseGenTest** (Step 3 - v1 implementation)
    - Add filterGen implementations
    - Add ignoreExceptionsGen implementation

4. **Update v1 FilterGeneratorTest to implement contract** (Step 2)
    - Remove old tests (they're now in the contract)
    - Implement FilterGeneratorTestContract directly
    - Verify v1 tests still pass

5. **Create empty FilterGeneratorV2Test** (Step 4)
    - Implement FilterGeneratorTestContract directly
    - Will fail until v2 implementation exists

6. **Implement factory methods in BaseGenV2Test** (Step 3 - v2 stub)
    - Add filterGen implementations (throw NotImplementedError temporarily)
    - Add ignoreExceptionsGen implementation (throw NotImplementedError temporarily)

7. **Implement v2 filter generator** (Step 5)
    - Add FilterGeneratorV2 class (for predicates)
    - Add filter() extension functions
    - Add ignoreExceptions() extension function
    - Add FilterLimitReached exception (or reuse v1's)

8. **Update BaseGenV2Test to use actual implementations** (Step 5)
    - Remove NotImplementedError
    - Use actual filter() and ignoreExceptions()

9. **Run all tests** and verify both v1 and v2 pass

## Estimated Complexity

- **Contract creation**: Low-Medium - mostly copying existing tests, making them generic
- **v1 contract implementation**: Very Low - just add wrapper methods
- **v2 implementation**: Medium - need to handle shrink filtering correctly
- **Testing**: Medium - verify both v1 and v2 work correctly

**Total**: Medium complexity, ~2-3 hours

## Success Criteria

- [ ] `FilterGeneratorTestContract` created with all v1 tests
- [ ] v1 `FilterGeneratorTest` implements contract and passes
- [ ] `FilterGeneratorV2Test` created and implements contract
- [ ] v2 `filter()` works correctly
- [ ] v2 `ignoreExceptions()` works correctly
- [ ] Shrink filtering prevents infinite shrinking loops
- [ ] Exception handling uses shared or v2-specific exception class
- [ ] All tests pass for both implementations

## Next Step

Start with **Step 1: Create FilterGeneratorTestContract**.

