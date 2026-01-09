# Plan: Add Distinct Support to ListGeneratorV2

## Context

Instead of creating a separate SetGenerator, we should add `distinct` support to `ListGeneratorV2` (just like v1's
`ListGenerator` has). Sets will then be a simple mapping: `list(distinct=true).map { it.toSet() }`.

This is a better approach because:

- ✅ Matches v1's design
- ✅ Single implementation to maintain
- ✅ Recursive shrinking automatically works for distinct lists and sets
- ✅ Can test distinct behavior directly on lists

## Revised Landmark Steps

### Step 1: Add `distinct` Parameter to ListGeneratorTestContract ✅

**Goal**: Extend the list contract to cover distinct behavior

**Add to `ListGeneratorTestContract`**:

```kotlin
interface ListGeneratorTestContract : BaseGeneratorContract {
   // Existing methods...

   // Add factory method for distinct lists
   fun <T> IGen<T>.listGen(size: IntRange = 0..100, distinct: Boolean = false): IGen<List<T>>

   @Test
   fun `generates lists with distinct elements when distinct=true`() {
      val gen = intGen(0..100).listGen(size = 5, distinct = true)
      // Test that all elements are unique
   }

   @Test
   fun `throws when unable to generate enough distinct elements`() {
      val gen = intGen(0..10).listGen(size = 100, distinct = true)
      // Should throw DistinctCollectionSizeImpossible
   }

   @Test
   fun `handles duplicates during shrinking by accepting smaller size when in range`() {
      // When shrinking produces a duplicate, accept the smaller list if size in range
   }

   @Test
   fun `handles duplicates during shrinking by rejecting smaller size when not in range`() {
      // When shrinking produces a duplicate, reject if size not in range
   }
}
```

**Why add to ListGeneratorTestContract**:

- Distinct lists are still lists
- Tests the core behavior before we map to Set
- Can reuse recursive shrinking tests with `distinct=true`

### Step 2: Add Distinct Tests from v1 SetGeneratorTest ✅

**Goal**: Port relevant tests from v1's `SetGeneratorTest` to the list contract

**Tests to port**:
From `gen/SetGeneratorTest.kt`:

- `generates sets with distinct elements` → becomes `generates lists with distinct elements`
- `throws when unable to generate enough distinct elements` → same for lists
- `handles duplicates during shrinking by accepting smaller size when in range` → same for lists
- `handles duplicates during shrinking by rejecting smaller size when not in range` → same for lists

**New generic shrinking test** (instead of separate 1/2/3 element tests):

```kotlin
@Test
fun `distinct list shrinks maintain distinctness`() {
   // Generic test that works for any list size
   checkAll(
      TestConfig().withIterations(100),
      intGen(0..4).listGen(distinct = true),
   ) { list ->
      val (_, shrinks) = intGen(0..4).listGen(distinct = true)
         .generateWithShrunkValuesForListGen(rngValues = /* from list */)

      // All shrinks should also be distinct
      shrinks.forEach { shrunkList ->
         expectThat(shrunkList.toSet().size).isEqualTo(shrunkList.size)
      }
   }
}
```

**Why this is better**:

- ✅ Tests the invariant directly: "shrinks maintain distinctness"
- ✅ Works for any size list (not just 1, 2, 3)
- ✅ Less test code to maintain
- ✅ More comprehensive coverage via property-based testing

**Alternative simpler version** (if we can't use property-based testing in contracts):

```kotlin
@Test
fun `distinct list shrinks maintain distinctness`() {
   val gen = intGen(0..10).listGen(distinct = true)

   // Test with a few specific examples of different sizes
   listOf(
      listOf(1, 4),           // 1-element: size=1
      listOf(2, 1, 4),        // 2-element: size=2  
      listOf(3, 1, 4, 7),     // 3-element: size=3
   ).forEach { rngValues ->
      val (value, shrinks) = gen.generateWithShrunkValuesForListGen(rngValues)

      // Original value should be distinct
      expectThat(value.toSet().size).isEqualTo(value.size)

      // All shrinks should also be distinct
      shrinks.forEach { shrunkList ->
         expectThat(shrunkList.toSet().size).isEqualTo(shrunkList.size)
      }
   }
}
```

### Step 3: Implement Distinct Support in ListGeneratorV2 ✅

**Goal**: Add the actual distinct logic to the v2 implementation

**Changes to `ListGeneratorV2.kt`**:

1. **Add parameter**:

```kotlin
private class ListGeneratorV2<T>(
   private val gen: GenV2<T>,
   private val sizeRange: IntRange,
   private val distinct: Boolean = false,  // NEW
) : GenV2<List<T>>()
```

2. **Add duplicate detection during generation**:

```kotlin
override fun GenContextV2.generate(): GenResultV2<List<T>> {
   val sizeResult = sizeGen.generate(producer)
   val size = sizeResult.value

   val elementResults = mutableListOf<GenResultV2<T>>()
   val seenValues = if (distinct) mutableSetOf<T>() else null
   var retriesRemaining = MAX_DISTINCT_ATTEMPTS

   while (elementResults.size < size) {
      val elemResult = gen.generate(producer)

      // Check for duplicates if distinct mode
      if (distinct && seenValues != null && elemResult.value in seenValues) {
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
      seenValues?.add(elemResult.value)
   }

   return GenResultV2(
      value = elementResults.map { it.value },
      shrinks = generateListShrinks(size, elementResults),
   )
}

companion object {
   private const val MAX_DISTINCT_ATTEMPTS = 1000
}
```

3. **Add duplicate handling during shrinking**:

```kotlin
private fun generateListShrinks(
   size: Int,
   elementResults: List<GenResultV2<T>>,
): Sequence<GenResultV2<List<T>>> = sequence {
   // ... existing size shrinks ...

   // Element shrinks
   elementResults.indices.forEach { index ->
      elementResults[index].shrinks.forEach { shrunkElementResult ->
         val newElementResults = elementResults.mapIndexed { i, elemResult ->
            if (i == index) shrunkElementResult else elemResult
         }

         // Handle duplicates if distinct mode
         if (distinct) {
            val newValues = newElementResults.map { it.value }
            val hasDuplicates = newValues.size != newValues.toSet().size

            if (hasDuplicates) {
               // Remove duplicates and accept if size still in range
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

4. **Update list() extension functions**:

```kotlin
fun <T> GenV2<T>.list(size: IntRange = 0..100, distinct: Boolean = false): GenV2<List<T>> =
   ListGeneratorV2(gen = this, sizeRange = size, distinct = distinct)

fun <T> GenV2<T>.list(size: Int, distinct: Boolean = false): GenV2<List<T>> =
   list(size..size, distinct)
```

5. **Add exception class**:

```kotlin
class DistinctCollectionSizeImpossibleV2 internal constructor(
   targetSize: Int,
   achievedSize: Int,
   attempts: Int
) : GenerationException(
   "Failed to generate a list of size $targetSize with distinct elements after $attempts attempts. Only achieved size $achievedSize."
)
```

### Step 4: Update v1 to Use Same Contract ✅

**Goal**: Make v1 also implement the extended list contract

**Changes to `gen/ListGenerator.kt`**:
Currently has:

```kotlin
fun <T> Gen<T>.list(size: IntRange = 0..100): Gen<List<T>> =
   ListGenerator(sizeRange = size, distinct = false, gen = this)
```

Update to:

```kotlin
fun <T> Gen<T>.list(size: IntRange = 0..100, distinct: Boolean = false): Gen<List<T>> =
   ListGenerator(sizeRange = size, distinct = distinct, gen = this)

fun <T> Gen<T>.list(size: Int, distinct: Boolean = false): Gen<List<T>> =
   list(size..size, distinct)
```

This makes the API consistent between v1 and v2!

### Step 5: Add Set Generation (Easy Win) ✅

**Goal**: Once distinct lists work, sets are trivial

**Add to both v1 and v2**:

```kotlin
// v1: gen/ListGenerator.kt
fun <T> Gen<T>.set(size: IntRange = 0..100): Gen<Set<T>> =
   list(size, distinct = true).map { it.toSet() }

fun <T> Gen<T>.set(size: Int): Gen<Set<T>> =
   set(size..size)

// v2: v2/ListGeneratorV2.kt
fun <T> GenV2<T>.set(size: IntRange = 0..100): GenV2<Set<T>> =
   list(size, distinct = true).map { it.toSet() }

fun <T> GenV2<T>.set(size: Int): GenV2<Set<T>> =
   set(size..size)
```

**Note**: v1 already has these, but they'll be updated to use the `distinct` parameter explicitly.

### Step 6: Create SetGeneratorTestContract (Optional) ✅

**Goal**: Contract specifically for set behavior (wraps list contract)

**Why have a separate contract**:

- Sets have `.toSet()` semantics
- Type is `Set<T>` not `List<T>`
- Clear separation of concerns
- Can add set-specific tests (e.g., `.contains()` semantics)

**Implementation**:

```kotlin
interface SetGeneratorTestContract : BaseGeneratorContract {
   // Factory methods
   fun <T> IGen<T>.setGen(size: IntRange = 0..100): IGen<Set<T>>
   fun <T> IGen<T>.setGen(size: Int): IGen<Set<T>>

   @Test
   fun `generates sets with distinct elements`() {
      // Uses setGen() which returns Set<T>
   }

   @Test
   fun `set shrinks maintain distinctness`() {
      // Verify shrinks are still valid sets
   }

   // All the tests from ListGeneratorTestContract's distinct tests,
   // but working with Set<T> instead of List<T>
}
```

**Implementation in base classes**:

```kotlin
// v1
override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> =
   (this as Gen<T>).set(size)

// v2
override fun <T> IGen<T>.setGen(size: IntRange): IGen<Set<T>> =
   (this as GenV2<T>).set(size)
```

This gives us the best of both worlds:

- Distinct list tests in `ListGeneratorTestContract`
- Set-specific tests in `SetGeneratorTestContract`
- Both use the same underlying implementation

## Testing Strategy

### Phase 1: Add Distinct Tests to ListGeneratorTestContract

1. Add distinct parameter to `listGen()` factory methods
2. Add tests for distinct list generation
3. Add tests for duplicate handling during generation
4. Add tests for duplicate handling during shrinking
5. Verify v1 passes (already has distinct support)
6. v2 tests will fail (expected - no implementation yet)

### Phase 2: Implement Distinct Support in v2

1. Add `distinct` parameter to `ListGeneratorV2`
2. Add duplicate detection logic
3. Add duplicate handling during shrinking
4. Add exception class
5. Update `list()` extension functions
6. Run tests - v2 should now pass

### Phase 3: Add Set Generation

1. Add `set()` extension functions to v1 and v2
2. Update v1's existing `set()` to use `distinct` parameter explicitly
3. Create `SetGeneratorTestContract` (optional but recommended)
4. Create `SetGeneratorV2Test` implementing contract
5. Verify all tests pass

### Phase 4: Recursive Shrinking for Distinct Lists

**Question**: Should we test recursive shrinking with `distinct=true`?

**Answer**: Add a few smoke tests to `RecursiveListShrinkingContract`:

```kotlin
interface RecursiveListShrinkingContract : ListGeneratorTestContract {
   // ... existing tests ...

   @Test
   fun `distinct list shrinks recursively and maintains distinctness`() {
      val gen = intGen(0..5).listGen(distinct = true)
      val nav = gen.navigateRecursiveShrinks(listOf(2, 1, 4))  // [1, 4]

      expectThat(nav.value).isEqualTo(listOf(1, 4))
      expectThat(nav.value.toSet().size).isEqualTo(2)  // Distinct

      // Navigate to a shrink
      val listOf1Nav = nav.findShrinkByValue(listOf(1))
      expectThat(listOf1Nav).isNotNull()

      // Shrink should still be distinct
      expectThat(listOf1Nav!!.value.toSet().size).isEqualTo(1)
   }
}
```

But don't duplicate all 6 recursive shrinking tests - just verify distinctness is maintained.

## Implementation Order

1. **Update `ListGeneratorTestContract`**
   - Add `distinct` parameter to `listGen()` methods
   - Add tests for distinct list generation
   - Add tests for duplicate handling

2. **Update v1's `list()` extension functions**
   - Add `distinct` parameter (for API consistency)
   - Verify v1 tests still pass

3. **Implement distinct support in `ListGeneratorV2`**
   - Add `distinct` parameter
   - Add duplicate detection during generation
   - Add duplicate handling during shrinking
   - Add exception class
   - Update `list()` extensions

4. **Run v2 tests** - should now pass

5. **Add set generation**
   - Update v1's `set()` to use `distinct` parameter
   - Add v2's `set()` extension functions
   - Create `SetGeneratorTestContract` (optional)
   - Create `SetGeneratorV2Test`

6. **Final verification**
   - All v1 tests pass
   - All v2 tests pass
   - Both support distinct lists and sets

## Benefits of This Approach

✅ **Single source of truth**: One implementation for distinct behavior
✅ **Automatic recursive shrinking**: Distinct lists and sets get it for free
✅ **Consistent API**: Both v1 and v2 have same `list(distinct=true)` API
✅ **Easier to test**: Test distinct behavior directly on lists
✅ **Easier to maintain**: One implementation instead of two
✅ **Natural progression**: Lists → Distinct Lists → Sets

## Key Differences from Original Plan

| Original Plan                         | Revised Plan                                |
|---------------------------------------|---------------------------------------------|
| Focus on Set generation               | Focus on distinct List generation           |
| Create SetGeneratorTestContract first | Extend ListGeneratorTestContract first      |
| Separate Set implementation           | Reuse List with distinct parameter          |
| Set tests first                       | Distinct list tests first, set tests second |

## File Changes

### New Files

- None! (We're extending existing files)

### Modified Files

1. `contracts/ListGeneratorTestContract.kt` - Add distinct tests
2. `gen/ListGenerator.kt` - Add `distinct` parameter to API
3. `v2/ListGeneratorV2.kt` - Implement distinct support
4. `gen/BaseGenTest.kt` - Implement extended contract
5. `v2/BaseGenV2Test.kt` - Implement extended contract

### Optional New Files

- `contracts/SetGeneratorTestContract.kt` - Set-specific contract
- `v2/SetGeneratorV2Test.kt` - v2 set tests

## Success Criteria

- [ ] `ListGeneratorTestContract` includes distinct tests
- [ ] v1 `list()` has `distinct` parameter in API
- [ ] v2 `ListGeneratorV2` supports `distinct` parameter
- [ ] v2 handles duplicates during generation (retries)
- [ ] v2 handles duplicates during shrinking (accepts smaller size if in range)
- [ ] v2 throws `DistinctCollectionSizeImpossibleV2` when needed
- [ ] Both v1 and v2 have `set()` extension functions
- [ ] All v1 tests pass
- [ ] All v2 tests pass
- [ ] Distinct lists maintain distinctness when shrinking
- [ ] Recursive shrinking works for distinct lists

## Next Step

Start with **Step 1: Update ListGeneratorTestContract** to add distinct parameter and tests.

