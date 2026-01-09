# Plan: Abstract Recursive Shrinking Tests into ListGeneratorTestContract

## Problem Statement

We now have identical recursive shrinking tests implemented separately for v1 and v2:

- **v1**: `/src/test/kotlin/com/tamj0rd2/ktcheck/gen/ListGeneratorTest.kt` - Uses ProducerTree
- **v2**: `/src/test/kotlin/com/tamj0rd2/ktcheck/v2/ListGeneratorTest.kt` - Uses GenResult

Both implementations pass the same 6 test scenarios, proving they support the same recursive shrinking behavior.
However, the tests are duplicated and use different patterns due to the architectural differences between v1 and v2.

**Goal**: Abstract these tests into `ListGeneratorTestContract` so both implementations can share the same test logic
without duplication.

## Core Challenge

### v1 Pattern (ProducerTree-based)

```kotlin
val (value, level1Trees) = gen.generate(tree, GenMode.Initial)
val shrinkTree = gen.findShrinkTreeByValue(level1Trees, targetValue)
val (shrunkValue, level2Trees) = gen.generate(shrinkTree!!, GenMode.Shrinking)
```

### v2 Pattern (GenResult-based)

```kotlin
val result = gen.generate(StubValueProducer(rngValues))
val firstLevel = result.shrinks.take(10).toList()
val secondLevel = firstLevel[0].shrinks.take(5).toList()
```

**Key Difference**:

- v1 requires **regeneration** - each shrink tree must be used to generate a value (and its shrinks)
- v2 has **direct access** - shrinks are nested directly in the result structure

## Previous Attempt (Failed)

We previously tried to create an `IGenResult<T>` abstraction:

```kotlin
interface IGenResult<T> {
   val value: T
   val shrinks: Sequence<IGenResult<T>>
}
```

**Why it failed**: This abstraction doesn't naturally fit v1's ProducerTree model. In v1, shrinks aren't "results with
nested results" - they're trees that need to be regenerated to get their shrinks.

## Solution: Abstract the Navigation Pattern

Instead of trying to unify the data structures, we can abstract the **navigation operations** - the actions we perform
when testing recursive shrinking.

### Key Insight

When testing recursive shrinking, we perform these operations:

1. **Generate initial value** from test data
2. **Get first-level shrinks** as a list
3. **Find a specific shrink** by its value
4. **Navigate to second level** by getting that shrink's shrinks
5. **Continue navigating** to deeper levels

These operations can be abstracted!

## Proposed Solution: Navigation Abstraction

### Step 1: Define Navigation Interface

```kotlin
// In ListGeneratorTestContract.kt
interface RecursiveShrinkNavigator<T> {
   /** The generated value */
   val value: T

   /** Get shrinks as a list (materializes up to the limit) */
   fun getShrinks(limit: Int = 10): List<RecursiveShrinkNavigator<T>>

   /** Find a shrink by its value */
   fun findShrinkByValue(value: T, limit: Int = 15): RecursiveShrinkNavigator<T>?
}
```

### Step 2: Implement for v1 (ProducerTree-based)

```kotlin
// In BaseGenTest.kt (v1)
private class V1RecursiveShrinkNavigator<T>(
   private val gen: Gen<T>,
   override val value: T,
   private val shrinkTrees: Sequence<ProducerTree>
) : RecursiveShrinkNavigator<T> {

   override fun getShrinks(limit: Int): List<RecursiveShrinkNavigator<T>> {
      return shrinkTrees.take(limit).map { tree ->
         val (shrunkValue, nestedShrinks) = gen.generate(tree, GenMode.Shrinking)
         V1RecursiveShrinkNavigator(gen, shrunkValue, nestedShrinks)
      }.toList()
   }

   override fun findShrinkByValue(value: T, limit: Int): RecursiveShrinkNavigator<T>? {
      return shrinkTrees.take(limit).firstNotNullOfOrNull { tree ->
         val (shrunkValue, nestedShrinks) = gen.generate(tree, GenMode.Shrinking)
         if (shrunkValue == value) {
            V1RecursiveShrinkNavigator(gen, shrunkValue, nestedShrinks)
         } else {
            null
         }
      }
   }
}

// Factory method
fun <T> Gen<T>.navigateRecursiveShrinks(tree: ProducerTree): RecursiveShrinkNavigator<T> {
   val (value, shrinks) = generate(tree, GenMode.Initial)
   return V1RecursiveShrinkNavigator(this, value, shrinks)
}
```

### Step 3: Implement for v2 (GenResult-based)

```kotlin
// In BaseGenV2Test.kt (v2)
private class V2RecursiveShrinkNavigator<T>(
   override val value: T,
   private val shrinkResults: Sequence<GenResult<T>>
) : RecursiveShrinkNavigator<T> {

   override fun getShrinks(limit: Int): List<RecursiveShrinkNavigator<T>> {
      return shrinkResults.take(limit).map { result ->
         V2RecursiveShrinkNavigator(result.value, result.shrinks)
      }.toList()
   }

   override fun findShrinkByValue(value: T, limit: Int): RecursiveShrinkNavigator<T>? {
      return shrinkResults.take(limit).firstNotNullOfOrNull { result ->
         if (result.value == value) {
            V2RecursiveShrinkNavigator(result.value, result.shrinks)
         } else {
            null
         }
      }
   }
}

// Factory method
fun <T> Gen<T>.navigateRecursiveShrinks(producer: ValueProducer): RecursiveShrinkNavigator<T> {
   val result = generate(producer)
   return V2RecursiveShrinkNavigator(result.value, result.shrinks)
}
```

### Step 4: Add Contract Method

```kotlin
// In ListGeneratorTestContract.kt
interface ListGeneratorTestContract : BaseGeneratorContract {
   // Existing methods...

   /** 
    * Create a navigator for testing recursive shrinking.
    * Implementations provide this based on their architecture (ProducerTree or GenResult).
    */
   fun <T : Any> IGen<T>.navigateRecursiveShrinks(rngValues: List<Any>): RecursiveShrinkNavigator<T>
}
```

### Step 5: Implement in Base Classes

```kotlin
// In BaseGenTest.kt (v1)
override fun <T : Any> IGen<T>.navigateRecursiveShrinks(rngValues: List<Any>): RecursiveShrinkNavigator<T> {
   val tree = buildListTree(rngValues)
   return (this as Gen<T>).navigateRecursiveShrinks(tree)
}

// In BaseGenV2Test.kt (v2)
override fun <T : Any> IGen<T>.navigateRecursiveShrinks(rngValues: List<Any>): RecursiveShrinkNavigator<T> {
   return (this as Gen<T>).navigateRecursiveShrinks(StubValueProducer(rngValues))
}
```

### Step 6: Write Contract Tests

```kotlin
// In ListGeneratorTestContract.kt
interface `Recursive Shrinking Contract` : ListGeneratorTestContract {
   @Test
   fun `empty list has no shrinks`() {
      val gen = intGen(0..5).listGen()
      val nav = gen.navigateRecursiveShrinks(listOf(0))  // size = 0

      expectThat(nav.value).isEmpty()
      expectThat(nav.getShrinks()).isEmpty()
   }

   @Test
   fun `single minimal element shrinks recursively`() {
      val gen = intGen(0..5).listGen()
      val nav = gen.navigateRecursiveShrinks(listOf(1, 0))  // [0]

      expectThat(nav.value).isEqualTo(listOf(0))

      // Find the empty list shrink
      val emptyListNav = nav.findShrinkByValue(emptyList())
      expectThat(emptyListNav).isNotNull()

      // Verify [] has no further shrinks
      expectThat(emptyListNav!!.value).isEmpty()
      expectThat(emptyListNav.getShrinks()).isEmpty()
   }

   @Test
   fun `single non-minimal element shrinks recursively`() {
      val gen = intGen(0..5).listGen()
      val nav = gen.navigateRecursiveShrinks(listOf(1, 4))  // [4]

      expectThat(nav.value).isEqualTo(listOf(4))

      // Find the [0] shrink
      val listOf0Nav = nav.findShrinkByValue(listOf(0))
      expectThat(listOf0Nav).isNotNull()

      // Level 2: [0] should shrink to []
      val listOf0Shrinks = listOf0Nav!!.getShrinks(limit = 5)
      expectThat(listOf0Shrinks.map { it.value }).contains(listOf(emptyList()))

      // Find the [2] shrink
      val listOf2Nav = nav.findShrinkByValue(listOf(2))
      expectThat(listOf2Nav).isNotNull()

      // Level 2: [2] should shrink to [], [0], [1]
      val listOf2Shrinks = listOf2Nav!!.getShrinks(limit = 5)
      expectThat(listOf2Shrinks.map { it.value }).contains(
         emptyList(),
         listOf(0),
         listOf(1),
      )
   }

   // ... similar patterns for remaining tests
}
```

### Step 7: Implement in Test Classes

```kotlin
// In gen/ListGeneratorTest.kt (v1)
@Nested
inner class `Recursive Shrinking` : ListGeneratorTestContract.`Recursive Shrinking Contract`, BaseGenTest()

// In v2/ListGeneratorTest.kt (v2)
@Nested
inner class `Recursive Shrinking` : ListGeneratorTestContract.`Recursive Shrinking Contract`, BaseGenV2Test()
```

## Benefits of This Approach

✅ **Eliminates duplication** - Tests written once in the contract
✅ **Architecture-agnostic** - Each implementation provides its own navigator
✅ **Type-safe** - No casting or generic workarounds needed
✅ **Lazy evaluation preserved** - Navigation only materializes what's needed
✅ **Clean API** - Test code reads naturally: `nav.findShrinkByValue(target)`
✅ **Extensible** - Easy to add more navigation operations if needed

## Alternative: Extension Function Approach

Instead of a navigator interface, we could use extension functions:

```kotlin
// In contract
interface ListGeneratorTestContract : BaseGeneratorContract {
   fun <T : Any> IGen<T>.getInitialValue(rngValues: List<Any>): T
   fun <T : Any> IGen<T>.getFirstLevelShrinks(rngValues: List<Any>, limit: Int = 10): List<T>
   fun <T : Any> IGen<T>.getSecondLevelShrinks(
      rngValues: List<Any>,
      firstLevelValue: T,
      limit: Int = 10
   ): List<T>
}
```

**Cons of this approach**:

- Less composable - hard to navigate to level 3, 4, etc.
- Test code becomes more verbose
- Doesn't capture the "navigation" concept as clearly

**Verdict**: Navigator interface is superior.

## Implementation Plan

### Phase 1: Define Abstraction ✅

1. Add `RecursiveShrinkNavigator<T>` interface to `ListGeneratorTestContract.kt`
2. Add `navigateRecursiveShrinks()` method to contract
3. Define nested `Recursive Shrinking Contract` interface in contract

### Phase 2: Implement for v1 ✅

1. Add `V1RecursiveShrinkNavigator` to `BaseGenTest.kt`
2. Implement `navigateRecursiveShrinks()` override in `BaseGenTest`
3. Keep `buildListTree()` and `findShrinkTreeByValue()` as internal helpers

### Phase 3: Implement for v2 ✅

1. Add `V2RecursiveShrinkNavigator` to `BaseGenV2Test.kt`
2. Implement `navigateRecursiveShrinks()` override in `BaseGenV2Test`

### Phase 4: Write Contract Tests ✅

1. Move all 6 test scenarios into `Recursive Shrinking Contract` interface
2. Rewrite tests to use the navigator API
3. Keep existing helper assertions (like `.contains(listOf(...))`)

### Phase 5: Update Test Implementations ✅

1. Change v1 `ListGeneratorTest` to implement the contract
2. Change v2 `ListGeneratorTest` to implement the contract
3. Delete the duplicate test code from both files

### Phase 6: Verify ✅

1. Run v1 tests - should pass
2. Run v2 tests - should pass
3. Delete any leftover duplicate code

## Success Criteria

- [ ] `RecursiveShrinkNavigator` interface defined in contract
- [ ] v1 implementation working with ProducerTree
- [ ] v2 implementation working with GenResult
- [ ] All 6 test scenarios in contract
- [ ] v1 ListGeneratorTest implements contract (no duplicate tests)
- [ ] v2 ListGeneratorTest implements contract (no duplicate tests)
- [ ] All tests passing for both implementations
- [ ] No code duplication between v1 and v2 test files

## Files to Modify

1. **`src/test/kotlin/com/tamj0rd2/ktcheck/contracts/ListGeneratorTestContract.kt`**
   - Add `RecursiveShrinkNavigator<T>` interface
   - Add `navigateRecursiveShrinks()` method
   - Add nested `Recursive Shrinking Contract` interface with 6 tests

2. **`src/test/kotlin/com/tamj0rd2/ktcheck/gen/BaseGenTest.kt`**
   - Add `V1RecursiveShrinkNavigator` class
   - Implement `navigateRecursiveShrinks()` override
   - Add extension function for Gen<T>

3. **`src/test/kotlin/com/tamj0rd2/ktcheck/v2/BaseGenV2Test.kt`**
   - Add `V2RecursiveShrinkNavigator` class
   - Implement `navigateRecursiveShrinks()` override
   - Add extension function for Gen<T>

4. **`src/test/kotlin/com/tamj0rd2/ktcheck/gen/ListGeneratorTest.kt`**
   - Change nested class to implement contract
   - Delete duplicate test code

5. **`src/test/kotlin/com/tamj0rd2/ktcheck/v2/ListGeneratorTest.kt`**
   - Change nested class to implement contract
   - Delete duplicate test code

## Notes

- The navigator pattern is commonly used in testing frameworks (e.g., Selenium WebDriver)
- This abstraction is specific to recursive shrinking tests - other tests don't need it
- We preserve lazy evaluation by using `Sequence` internally and only materializing with `limit`
- The `findShrinkByValue()` method internally limits how many shrinks it checks (prevents infinite loops)
- Both implementations can use the same assertion patterns with Strikt

## Risk Mitigation

**Risk**: Navigator abstraction is too complex
**Mitigation**: Keep interface minimal (just 3 methods), hide implementation details in private classes

**Risk**: Performance impact from wrapping
**Mitigation**: Navigators are only used in tests, performance is not critical

**Risk**: Hard to debug when tests fail
**Mitigation**: Navigator methods should have clear names, provide good error messages

## Future Extensions

Once this works, we could extend the pattern to other generators:

- `SetGeneratorTestContract` - if we add recursive shrinking for sets
- `MapGeneratorTestContract` - if we add recursive shrinking for maps
- Any generator that supports recursive shrinking

This establishes a pattern for testing recursive behavior across different architectures.

