# Test Plan: Recursive List Shrinking

## Context

We're implementing recursive shrinking for the v2 ListGenerator. The current implementation only produces first-level
shrinks (each shrink has `emptySequence()` for its own shrinks). With recursive shrinking, each shrunk value can itself
be shrunk further.

**Testing Approach:**

- ✅ Tests will be added directly to `ListGeneratorTest` in the v2 package
- ✅ Use production APIs: `Gen.int().list()` instead of contract helper methods
- ✅ This reduces complexity and keeps tests straightforward

**Current test coverage**:

- ✅ First-level shrink values are correct
- ✅ Shrink ordering is correct
- ✅ Basic behaviors (size shrinks, element shrinks)

**What's missing**:

- ❌ Verification that shrinks have their own shrinks (recursive structure)
- ❌ Verification that deep shrinking terminates properly
- ❌ Verification that the shrink tree structure enables depth-first traversal to find minimal counterexamples

## Testing Principles

### DO NOT Materialize Sequences Unnecessarily

```kotlin
// ❌ WRONG - Don't do this!
val allShrinks = result.shrinks.toList()
expectThat(allShrinks).hasSize(100)  // Forces evaluation of entire shrink tree!

// ✅ CORRECT - Verify structure by spot-checking
val firstShrink = result.shrinks.first()
val secondLevelShrinks = firstShrink.shrinks.take(3).toList()
expectThat(secondLevelShrinks.map { it.value }).contains(expectedValue)
```

**Key principle**: We want to verify the shrink tree *exists* and has the right *structure*, not enumerate every single
shrink.

### Test Strategy

1. **Spot-check key paths** - Don't enumerate all shrinks, just verify critical ones exist
2. **Use `.take(n)`** - Limit how many shrinks we evaluate
3. **Verify termination** - Check that deep shrinks eventually produce empty sequences
4. **Test realistic scenarios** - Focus on cases that matter for finding counterexamples

## Test Scenarios

### Scenario 1: Empty List Has No Shrinks (Baseline)

**Purpose**: Verify the termination case - empty list is minimal.

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(0)))  // size = 0
```

**Assertions**:

```kotlin
expectThat(result.value).isEmpty()
expectThat(result.shrinks.toList()).isEmpty()  // OK to materialize - should be empty
```

**What this proves**: Base case works correctly.

---

### Scenario 2: Single Minimal Element Shrinks Recursively

**Purpose**: Verify that a list with one minimal element can still shrink its size, and that size shrink has no further
shrinks.

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(1, 0)))  // [0]
```

**Assertions**:

```kotlin
expectThat(result.value).isEqualTo(listOf(0))

// First level: Should have size shrink to []
val firstLevelShrinks = result.shrinks.take(5).toList()
val emptyListShrink = firstLevelShrinks.find { it.value.isEmpty() }
expectThat(emptyListShrink).isNotNull()

// Second level: [] should have no shrinks
expectThat(emptyListShrink!!.shrinks.toList()).isEmpty()
```

**What this proves**:

- Size shrinking works recursively
- Empty list (the terminal case) has no shrinks
- We don't invent shrinks for minimal elements

---

### Scenario 3: Single Non-Minimal Element Shrinks Recursively (Critical Test)

**Purpose**: Verify that after shrinking list size, we can still shrink the element.

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(1, 4)))  // [4]
```

**Expected shrink tree**:

```
[4]
├─ [] (size shrink)
├─ [0] (element shrink)
│  └─ [] (size shrink of [0])
├─ [2] (element shrink)
│  ├─ [] (size shrink of [2])
│  └─ [0] (element shrink of [2])
│  └─ [1] (element shrink of [2])
└─ [3] (element shrink)
   └─ ...
```

**Assertions**:

```kotlin
expectThat(result.value).isEqualTo(listOf(4))

// First level shrinks
val firstLevel = result.shrinks.take(10).toList()

// Find the [0] shrink (element shrink)
val listOf0 = firstLevel.find { it.value == listOf(0) }
expectThat(listOf0).isNotNull()

// Second level: [0] should shrink to []
val secondLevel = listOf0!!.shrinks.take(5).toList()
expectThat(secondLevel.map { it.value }).contains(emptyList())

// Find the [2] shrink (another element shrink)
val listOf2 = firstLevel.find { it.value == listOf(2) }
expectThat(listOf2).isNotNull()

// Second level: [2] should shrink to [] (size) and [0], [1] (element)
val listOf2Shrinks = listOf2!!.shrinks.take(5).toList()
expectThat(listOf2Shrinks.map { it.value }).contains(
    emptyList(),  // size shrink
    listOf(0),    // element shrink
    listOf(1),    // element shrink
)
```

**What this proves**:

- Element shrinks are recursive
- Each shrunk list can be shrunk further
- Both size and element shrinking work at multiple levels

---

### Scenario 4: Two-Element List Shrinks Recursively

**Purpose**: Verify that size-shrunk lists (with elements removed) can be further shrunk.

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(2, 1, 4)))  // [1, 4]
```

**Expected key shrinks**:

```
[1, 4]
├─ [] (size shrink - minimal)
├─ [1] (size shrink - tail removal)
│  ├─ [] (size shrink of [1])
│  └─ [0] (element shrink of [1])
├─ [4] (size shrink - head removal)
│  ├─ [] (size shrink of [4])
│  ├─ [0] (element shrink of [4])
│  ├─ [2] (element shrink of [4])
│  └─ [3] (element shrink of [4])
└─ [0, 4] (element shrink at index 0)
   ├─ [] (size shrink)
   ├─ [0] (tail removal)
   ├─ [4] (head removal)  
   └─ [0, 0] (element shrink at index 1)
```

**Assertions**:

```kotlin
expectThat(result.value).isEqualTo(listOf(1, 4))

val firstLevel = result.shrinks.take(15).toList()

// Check that [1] (tail removal) has recursive shrinks
val listOf1 = firstLevel.find { it.value == listOf(1) }
expectThat(listOf1).isNotNull()

val listOf1Shrinks = listOf1!!.shrinks.take(5).toList()
expectThat(listOf1Shrinks.map { it.value }).contains(
    emptyList(),  // size shrink
    listOf(0),    // element shrink
)

// Check that [4] (head removal) has recursive shrinks
val listOf4 = firstLevel.find { it.value == listOf(4) }
expectThat(listOf4).isNotNull()

val listOf4Shrinks = listOf4!!.shrinks.take(10).toList()
expectThat(listOf4Shrinks.map { it.value }).contains(
    emptyList(),  // size shrink
    listOf(0),    // element shrink
    listOf(2),    // element shrink
)

// Check that [0, 4] (element shrink) has recursive shrinks
val listOf0_4 = firstLevel.find { it.value == listOf(0, 4) }
expectThat(listOf0_4).isNotNull()

val listOf0_4Shrinks = listOf0_4!!.shrinks.take(10).toList()
expectThat(listOf0_4Shrinks.map { it.value }).contains(
    emptyList(),  // size shrink
    listOf(0),    // size shrink (tail removal)
    listOf(4),    // size shrink (head removal)
    listOf(0, 0), // element shrink
)
```

**What this proves**:

- Tail removal produces recursively shrinkable lists
- Head removal produces recursively shrinkable lists
- Element-shrunk lists can be further shrunk
- Complex shrink trees are navigable

---

### Scenario 5: Three-Level Depth Test

**Purpose**: Verify shrinking works at depth 3 (shrink of a shrink of a shrink).

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(2, 2, 4)))  // [2, 4]
```

**Shrink path to verify**:

```
[2, 4] → [2] → [1] → [0]
```

**Assertions**:

```kotlin
expectThat(result.value).isEqualTo(listOf(2, 4))

// Level 1: Find [2]
val level1 = result.shrinks.take(15).toList()
val listOf2 = level1.find { it.value == listOf(2) }
expectThat(listOf2).isNotNull()

// Level 2: [2] should shrink to [1] (among others)
val level2 = listOf2!!.shrinks.take(10).toList()
val listOf1 = level2.find { it.value == listOf(1) }
expectThat(listOf1).isNotNull()

// Level 3: [1] should shrink to [0]
val level3 = listOf1!!.shrinks.take(10).toList()
expectThat(level3.map { it.value }).contains(listOf(0))

// Level 4: [0] should shrink to []
val listOf0 = level3.find { it.value == listOf(0) }
expectThat(listOf0).isNotNull()
val level4 = listOf0!!.shrinks.take(5).toList()
expectThat(level4.map { it.value }).contains(emptyList())
```

**What this proves**:

- Deep recursion works (at least 4 levels)
- No infinite loops
- Paths eventually terminate at minimal values

---

### Scenario 6: All Elements Minimal - Only Size Shrinks Recursively

**Purpose**: Verify that when elements are minimal, we still get recursive size shrinking.

**Setup**:

```kotlin
val gen = Gen.int(0..5).list()
val result = gen.generate(StubValueProducer(listOf(3, 0, 0, 0)))  // [0, 0, 0]
```

**Expected behavior**:

- No element shrinks (all elements are 0, which is minimal)
- Size shrinks: `[]`, `[0, 0]` (tail), `[0, 0]` (head)
- `[0, 0]` shrinks to: `[]`, `[0]` (tail), `[0]` (head)
- `[0]` shrinks to: `[]`

**Assertions**:

```kotlin
expectThat(result.value).isEqualTo(listOf(0, 0, 0))

val level1 = result.shrinks.take(10).toList()

// Should have size shrinks but no element shrinks
// We can identify this by checking all shrinks are shorter lists of 0s
expectThat(level1.map { it.value }).all {
    get { all { it == 0 } }.isEqualTo(true)
}

// Find [0, 0] and verify it has recursive shrinks
val listOf0_0 = level1.find { it.value == listOf(0, 0) }
expectThat(listOf0_0).isNotNull()

val level2 = listOf0_0!!.shrinks.take(5).toList()
expectThat(level2.map { it.value }).contains(
    emptyList(),
    listOf(0),
)
```

**What this proves**:

- We don't invent element shrinks where none exist
- Size shrinking still works recursively
- Minimal element handling is correct

---

## Summary of Test Coverage

| Scenario                   | What It Tests                             | Depth |
|----------------------------|-------------------------------------------|-------|
| Empty list                 | Base case / termination                   | 0     |
| Single minimal element     | Size shrinking with no element shrinks    | 2     |
| Single non-minimal element | Element shrinking recursively             | 2-3   |
| Two-element list           | Both size and element shrinks recursive   | 2-3   |
| Three-level depth          | Deep recursion works, terminates properly | 4     |
| All elements minimal       | No invented shrinks, size-only recursion  | 2-3   |

**Total scenarios**: 6 tests
**Coverage**: Base cases, typical cases, edge cases, deep recursion, termination

## Implementation Location

Add these tests to: `src/test/kotlin/com/tamj0rd2/ktcheck/v2/ListGeneratorTest.kt`

**Important**:

- Tests go in the v2 `ListGeneratorTest` class directly, NOT in the contract
- Use production APIs directly: `Gen.int().list()` instead of contract helper methods like `intGen().listGen()`
- This reduces complexity and makes tests more straightforward

The tests should be in a nested test class:

```kotlin
@Nested
inner class `Recursive Shrinking` {
    @Test
    fun `empty list has no shrinks`() {
        val gen = Gen.int(0..5).list()
        val result = gen.generate(StubValueProducer(listOf(0)))
        // ... assertions
    }

    @Test
    fun `single minimal element shrinks recursively`() {
        ...
    }

    // ... etc
}
```

## Next Steps

1. ✅ Review this test plan - does it cover the right scenarios?
2. ❓ Implement the recursive shrinking in `ListGenerator.kt`
3. ❓ Implement these tests
4. ❓ Run tests and verify they fail (red)
5. ❓ Verify implementation makes tests pass (green)

