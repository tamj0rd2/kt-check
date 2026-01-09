# Plan: v2 Set Generator Implementation

## Context

We need to implement set generation for v2 to achieve feature parity with v1. The v1 implementation supports:

- Generating sets with distinct elements
- Handling duplicates during generation (retries)
- Handling duplicates during shrinking (accepting smaller sizes)
- Throwing `DistinctCollectionSizeImpossible` when unable to generate enough distinct elements

## Landmark Steps

### Step 1: Create SetGeneratorTestContract ✅

**Goal**: Define the contract that specifies expected set generator behavior

**What to include**:

1. Basic set generation tests
    - Can generate sets
    - Sets have distinct elements
    - Can specify size ranges
    - Can generate fixed-size sets

2. Duplicate handling tests
    - Throws error when unable to generate enough distinct elements
    - Handles limited value space (e.g., `Gen.int(0..10).set(100)`)

3. Shrinking tests
    - Shrinks set size
    - Shrinks element values
    - Shrinks produce valid sets (still distinct)

4. Edge cases
    - Empty sets
    - Single-element sets
    - Sets where all elements are minimal

**Contract methods needed**:

```kotlin
interface SetGeneratorTestContract : BaseGeneratorContract {
    fun <T : Any> IGen<T>.generateWithShrunkValuesForSetGen(rngValues: List<Any>): Pair<T, List<T>>
}
```

**File**: `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/SetGeneratorTestContract.kt`

### Step 2: Move v1 Tests into Contract ✅

**Goal**: Extract existing v1 set tests and make them implementation-agnostic

**Current v1 tests** (from `SetGeneratorTest.kt`):

- `can generate a long set without stack overflow`
- `generates sets with distinct elements`
- `throws when unable to generate enough distinct elements`
- `shrinks a set of 1 element`
- `shrinks a set of 2 elements`
- `shrinks a set of 3 elements`
- `handles duplicates during shrinking by accepting smaller size when in range`
- `handles duplicates during shrinking by rejecting smaller size when not in range`

**Challenges**:

1. v1 tests use `Gen.int().set()` directly
2. Tests use `ProducerTree` for setup
3. Need to abstract these details

**Solution**: Similar to `ListGeneratorTestContract`, create abstract methods:

```kotlin
interface SetGeneratorTestContract : BaseGeneratorContract {
    fun <T : Any> IGen<T>.generateWithShrunkValuesForSetGen(rngValues: List<Any>): Pair<Set<T>, List<Set<T>>>

    @Test
    fun `generates sets with distinct elements`() {
        val gen = intGen(0..100).setGen(5)
        // ... test implementation
    }
}
```

**Action items**:

- Copy tests from `gen/SetGeneratorTest.kt`
- Replace `Gen.int().set()` with `intGen().setGen()`
- Replace `ProducerTree` setup with `generateWithShrunkValuesForSetGen(rngValues)`
- Keep test assertions the same

### Step 3: Make Contract Generic for v1 and v2 ✅

**Goal**: Ensure contract can be implemented by both v1 and v2

**Key differences to handle**:

1. **ProducerTree (v1) vs ValueProducer (v2)**
    - Contract uses `rngValues: List<Any>`
    - Each implementation converts to its own internal representation

2. **Gen (v1) vs GenV2 (v2)**
    - Contract returns `IGen` (common interface)
    - Implementations cast to their specific type

3. **Result extraction**
    - v1: `Gen<Set<T>>.generateWithShrunkValues(tree)` → `Pair<Set<T>, List<Set<T>>>`
    - v2: `GenV2<Set<T>>.generate(producer)` → extract values from `GenResultV2`

**Contract structure**:

```kotlin
interface SetGeneratorTestContract : BaseGeneratorContract {
    // Implementation-specific method for generating sets with test data
    fun <T : Any> IGen<T>.generateWithShrunkValuesForSetGen(rngValues: List<Any>): Pair<Set<T>, List<Set<T>>>

    // Factory methods - already exist in BaseGeneratorContract
    // fun intGen(range: IntRange): IGen<Int>

    // Add set-specific factory
    fun <T> IGen<T>.setGen(size: IntRange = 0..100): IGen<Set<T>>
    fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>>

    @Test
    fun `generates sets with distinct elements`() {
        ...
    }
    // ... more tests
}
```

**Implementation in v1** (`gen/BaseGenTest.kt`):

```kotlin
abstract class BaseGenTest : BaseGeneratorContract {
    // Add set-specific methods
    override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> {
        return (this as Gen<T>).set(size)
    }

    override fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>> {
        return (this as Gen<T>).set(size)
    }
}
```

**Implementation in v2** (`v2/BaseGenV2Test.kt`):

```kotlin
abstract class BaseGenV2Test : BaseGeneratorContract {
    // Add set-specific methods (to be implemented)
    override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> {
        return (this as GenV2<T>).set(size)
    }

    override fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>> {
        return (this as GenV2<T>).set(size)
    }
}
```

### Step 4: Create SetGeneratorV2Test ✅

**Goal**: Create test class that implements the contract for v2

**File**: `src/test/kotlin/com/tamj0rd2/ktcheck/v2/SetGeneratorV2Test.kt`

**Implementation**:

```kotlin
package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.SetGeneratorTestContract

internal class SetGeneratorV2Test : BaseGenV2Test(), SetGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForSetGen(rngValues: List<Any>): Pair<Set<T>, List<Set<T>>> {
        val gen = this as GenV2<Set<T>>
        val result = gen.generate(StubValueProducer(rngValues))
        return result.value to result.shrinks.map { it.value }.toList()
    }
}
```

**Dependencies**:

- Requires `GenV2.set()` extension functions to exist
- Requires `SetGeneratorV2` implementation

### Step 5: Implement SetGeneratorV2 ✅

**Goal**: Port v1's set generation logic to v2 architecture

**v1 Implementation Analysis**:

Looking at v1's `ListGenerator`:

```kotlin
internal class ListGenerator<T>(
    private val sizeRange: IntRange,
    private val distinct: Boolean,  // ← Key parameter!
    private val gen: Gen<T>,
) : Gen<List<T>>()

// Sets are created by:
fun <T> Gen<T>.set(size: IntRange = 0..100): Gen<Set<T>> =
    ListGenerator(sizeRange = size, distinct = true, gen = this).map { it.toSet() }
```

**Key insight**: v1 reuses `ListGenerator` with `distinct=true` and maps to set!

**v2 Options**:

**Option A: Reuse ListGeneratorV2 (like v1)**

```kotlin
fun <T> GenV2<T>.set(size: IntRange = 0..100): GenV2<Set<T>> =
    ListGeneratorV2(sizeRange = size, distinct = true, gen = this).map { it.toSet() }
```

**Pros**:

- ✅ Minimal code duplication
- ✅ Consistent with v1 approach
- ✅ Shrinking behavior automatically correct

**Cons**:

- ❌ Requires adding `distinct` parameter to `ListGeneratorV2`
- ❌ Adds complexity to `ListGeneratorV2`
- ❌ List generation tests would need to handle distinct mode

**Option B: Create separate SetGeneratorV2**

```kotlin
private class SetGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val sizeRange: IntRange,
) : GenV2<Set<T>>() {
    // Dedicated set generation logic
}
```

**Pros**:

- ✅ Separation of concerns
- ✅ Keeps `ListGeneratorV2` simple
- ✅ Easier to test independently

**Cons**:

- ❌ Code duplication with `ListGeneratorV2`
- ❌ Need to maintain two similar implementations
- ❌ Shrinking logic duplicated

**Recommendation**: **Option A - Reuse ListGeneratorV2 with distinct parameter**

**Why**:

- v1 proves this approach works well
- Most of the logic is identical (generation, shrinking)
- Only difference is duplicate handling
- Less code to maintain
- Shrinking behavior is automatically correct

**Implementation Plan**:

1. **Add `distinct` parameter to `ListGeneratorV2`**:

```kotlin
private class ListGeneratorV2<T>(
    private val gen: GenV2<T>,
    private val sizeRange: IntRange,
    private val distinct: Boolean = false,  // New parameter
) : GenV2<List<T>>()
```

2. **Add duplicate detection during generation**:

```kotlin
override fun GenContextV2.generate(): GenResultV2<List<T>> {
    val sizeResult = sizeGen.generate(producer)
    val size = sizeResult.value

    val elementResults = mutableListOf<GenResultV2<T>>()
    val seenValues = mutableSetOf<T>()
    var retriesRemaining = MAX_DISTINCT_ATTEMPTS

    while (elementResults.size < size) {
        val elemResult = gen.generate(producer)

        if (distinct && elemResult.value in seenValues) {
            if (retriesRemaining <= 0) {
                throw DistinctCollectionSizeImpossibleV2(
                    targetSize = size,
                    achievedSize = elementResults.size,
                    attempts = MAX_DISTINCT_ATTEMPTS,
                )
            }
            retriesRemaining--
            continue
        }

        elementResults.add(elemResult)
        if (distinct) seenValues.add(elemResult.value)
    }

    return GenResultV2(
        value = elementResults.map { it.value },
        shrinks = generateListShrinks(size, elementResults),
    )
}
```

3. **Handle duplicates during shrinking**:

```kotlin
private fun generateListShrinks(
    size: Int,
    elementResults: List<GenResultV2<T>>,
): Sequence<GenResultV2<List<T>>> = sequence {
    // ... size shrinks ...

    // Element shrinks - with duplicate handling
    elementResults.indices.forEach { index ->
        elementResults[index].shrinks.forEach { shrunkElementResult ->
            val newElementResults = elementResults.mapIndexed { i, elemResult ->
                if (i == index) shrunkElementResult else elemResult
            }

            // Check for duplicates if distinct mode
            if (distinct) {
                val values = newElementResults.map { it.value }
                val hasDuplicates = values.size != values.toSet().size

                if (hasDuplicates) {
                    // Accept smaller size if in range
                    val uniqueResults = newElementResults.distinctBy { it.value }
                    if (uniqueResults.size in sizeRange) {
                        yield(
                            GenResultV2(
                                value = uniqueResults.map { it.value },
                                shrinks = generateListShrinks(uniqueResults.size, uniqueResults)
                            )
                        )
                    }
                    return@forEach  // Skip this shrink
                }
            }

            yield(
                GenResultV2(
                    value = newElementResults.map { it.value },
                    shrinks = generateListShrinks(size, newElementResults)
                )
            )
        }
    }
}
```

4. **Add set extension functions**:

```kotlin
fun <T> GenV2<T>.set(size: IntRange = 0..100): GenV2<Set<T>> =
    ListGeneratorV2(gen = this, sizeRange = size, distinct = true).map { it.toSet() }

fun <T> GenV2<T>.set(size: Int): GenV2<Set<T>> = set(size..size)
```

5. **Add exception class**:

```kotlin
class DistinctCollectionSizeImpossibleV2 internal constructor(
    targetSize: Int,
    achievedSize: Int,
    attempts: Int
) : GenerationException(
    "Failed to generate a set of size $targetSize with distinct elements after $attempts attempts. Only achieved size $achievedSize."
)
```

6. **Update `BaseGeneratorContract`**:

```kotlin
interface BaseGeneratorContract {
    // ... existing methods ...

    fun <T> IGen<T>.setGen(size: IntRange = 0..100): IGen<Set<T>>
    fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>>
}
```

7. **Implement in base test classes**:

```kotlin
// v1/BaseGenTest.kt
override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> =
    (this as Gen<T>).set(size)

override fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>> =
    (this as Gen<T>).set(size)

// v2/BaseGenV2Test.kt
override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> =
    (this as GenV2<T>).set(size)

override fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>> =
    (this as GenV2<T>).set(size)
```

## Testing Strategy

### Phase 1: Contract Tests (Before Implementation)

1. Create `SetGeneratorTestContract`
2. Move v1 tests into contract
3. Verify v1 still passes all tests
4. Create empty `SetGeneratorV2Test` implementing contract
5. Tests will fail (expected - no implementation yet)

### Phase 2: Implementation

1. Add `distinct` parameter to `ListGeneratorV2`
2. Add duplicate detection during generation
3. Add duplicate handling during shrinking
4. Add set extension functions
5. Add exception class
6. Update contracts and base classes

### Phase 3: Verification

1. Run v2 set tests - should pass
2. Run v1 set tests - should still pass
3. Run v2 list tests - should still pass (distinct=false by default)
4. Run v1 list tests - should still pass

## Open Questions

### 1. Should we test recursive shrinking for sets?

Sets will inherit recursive shrinking from lists automatically since we're reusing `ListGeneratorV2`.

**Options**:

- A) Add recursive shrinking tests to `SetGeneratorTestContract`
- B) Trust that list recursive shrinking covers it
- C) Add basic smoke test but not full coverage

**Recommendation**: **Option B** - Trust list tests. Sets are just lists with distinct=true and .toSet().

### 2. How should duplicate handling work during shrinking?

**v1 approach** (from the code):

```kotlin
if (distinct && value in values) {
    // If the current list size is within the acceptable range, accept the smaller list
    if (mode == GenMode.Shrinking || values.size in sizeRange) {
        return GenResult(value = values, shrinks = ...)
    }
    // Otherwise retry
}
```

**Key insight**: During shrinking, v1 accepts smaller sizes when duplicates appear.

**v2 should do the same**: If shrinking produces a duplicate, emit a shrink with the duplicate removed (smaller size) if
that size is in range.

### 3. Should we add a `SetGeneratorTestContract` or reuse `ListGeneratorTestContract`?

**Recommendation**: Create separate `SetGeneratorTestContract`.

**Why**:

- Sets have specific behavior (distinct elements)
- Different error cases (impossible distinct sizes)
- Clearer separation of concerns
- Each contract focuses on one thing

## File Structure

```
src/
├── main/kotlin/com/tamj0rd2/ktcheck/
│   ├── gen/
│   │   └── ListGenerator.kt (already has distinct support)
│   └── v2/
│       └── ListGeneratorV2.kt (will add distinct support)
│
└── test/kotlin/com/tamj0rd2/ktcheck/
    ├── contracts/
    │   ├── BaseGeneratorContract.kt (add setGen methods)
    │   └── SetGeneratorTestContract.kt (NEW - create this)
    ├── gen/
    │   ├── BaseGenTest.kt (add setGen implementation)
    │   └── SetGeneratorTest.kt (implement contract)
    └── v2/
        ├── BaseGenV2Test.kt (add setGen implementation)
        └── SetGeneratorV2Test.kt (NEW - create this)
```

## Success Criteria

- [ ] `SetGeneratorTestContract` created with all v1 tests
- [ ] v1 `SetGeneratorTest` implements contract and passes all tests
- [ ] `SetGeneratorV2Test` created and implements contract
- [ ] `ListGeneratorV2` supports `distinct` parameter
- [ ] `GenV2.set()` extension functions work correctly
- [ ] All v2 set tests pass
- [ ] All v1 tests still pass (backward compatibility)
- [ ] All v2 list tests still pass (distinct=false by default)
- [ ] Exception handling works (`DistinctCollectionSizeImpossibleV2`)

## Implementation Order

1. **Create SetGeneratorTestContract** (Step 1)
2. **Move v1 tests into contract** (Step 2)
3. **Update BaseGeneratorContract** with setGen methods (Step 3)
4. **Implement setGen in BaseGenTest** (v1 implementation)
5. **Verify v1 SetGeneratorTest implements contract and passes**
6. **Create empty SetGeneratorV2Test** (Step 4)
7. **Add distinct parameter to ListGeneratorV2** (Step 5a)
8. **Add duplicate detection during generation** (Step 5b)
9. **Add duplicate handling during shrinking** (Step 5c)
10. **Add set extension functions** (Step 5d)
11. **Add exception class** (Step 5e)
12. **Implement setGen in BaseGenV2Test** (Step 5f)
13. **Run all tests** and verify

## Estimated Complexity

- **Contract creation**: Low - mostly copying existing tests
- **v1 contract implementation**: Very Low - already works, just needs interface implementation
- **ListGeneratorV2 modifications**: Medium - need careful duplicate handling
- **Shrinking logic**: Medium-High - most complex part, need to handle duplicates correctly
- **Testing**: Medium - need to verify both v1 and v2 work correctly

**Total**: Medium complexity, ~2-3 hours of implementation + testing

## Next Step

Start with **Step 1: Create SetGeneratorTestContract** - this will clarify requirements and make the rest easier.

