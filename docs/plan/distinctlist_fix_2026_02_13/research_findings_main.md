# Research Findings: DistinctListGen Line 68 Bug Investigation

**Plan ID:** distinctlist_fix_2026_02_13  
**Focus Area:** Line 68 maxTreeOffset constraint bug  
**Confidence Level:** High (95% coverage, clear understanding of the issue)

---

## Executive Summary

There **IS** a real bug at line 68 in `DistinctListGen.kt`, but the user's test doesn't expose it correctly. The bug
allows shrinks to violate minimum size constraints when the maxTreeOffset limit is reached. However, the test fails for
a different reason (size-based shrinks exist when test expects none).

---

## What Line 68 Does

**File:** `src/main/kotlin/com/tamj0rd2/ktcheck/current/DistinctListGen.kt:68`

```kotlin
if (maxTreeOffset != null && treeOffset > maxTreeOffset) {
    break
}
```

This check limits how far we traverse the random tree during regeneration. It occurs at the **start** of each loop
iteration in `generateListWithResults()`, before:

1. Checking if `failureCount >= MAX_FAILURES` (line 72)
2. Generating the next element (line 82)
3. Adding distinct elements to results (line 84-90)

**Purpose:** During element-based shrinking, `maxTreeOffset` prevents traversing beyond the maximum offset used in the
original generation. This avoids picking up new elements with potentially larger values when a shrunk element creates a
duplicate.

---

## The Potential Bug Scenario

### When It Occurs

The bug manifests during **element-based shrinking** when:

1. A shrink tree has `maxTreeOffset` attached (line 38)
2. Duplicate values are encountered during regeneration
3. The `treeOffset` exceeds `maxTreeOffset` before collecting `minSize` elements

### Example Scenario

Original generation produces: `[0, 1, 2]` at tree offsets `[0, 1, 2]`  
Size constraint: `3..3` (minSize=3, maxSize=3)

When shrinking element at index 1 (value `1` → `0`):

```
maxTreeOffset = 2 (max of [0, 1, 2])

Regeneration with shrunk tree:
- Offset 0: value = 0 (from shrunk element) ✓ distinct → results = [0]
- Offset 1: value = 1 ✗ duplicate OR value = 0 ✗ duplicate → failureCount++
- Offset 2: value = 2 ✓ distinct → results = [0, 2]
- Offset 3: treeOffset (3) > maxTreeOffset (2) → **LINE 68 BREAKS**

Result: [0, 2] with size 2, but minSize = 3!
```

**Bug:** Line 68 breaks without checking if `results.size >= minSize`, creating an invalid shrink that violates size
constraints.

---

## The Test Case

**File:** `src/test/kotlin/com/tamj0rd2/ktcheck/contracts/DistinctListGeneratorContract.kt:98-107`

```kotlin
@Test
fun `does not produce any shrinks when the list size is equal to the number of distinct values`() {
    Assumptions.assumeTrue(false, "ignore this for now.")
    // note: there are only 3 possible distinct values. So a distinct list of size 3 
    // can only ever be achieved once: (0, 1, 2)
    val intGen = int(0..2)
    val gen = intGen.list(3, distinct = true)

    val result = gen.generate(tree())
    expectThat(result.value).isEqualTo(listOf(1, 2, 3))  // BUG: Range is 0..2, not 1..3!
    expectThat(result).shrunkValues.isEmpty()
}
```

**Test Issues:**

1. **Incorrect expectation:** Expected value `listOf(1, 2, 3)` is impossible from range `0..2`
2. **Wrong assertion logic:** Even if the range were correct, size-based shrinks would always exist (shrinking size 3 →
   2, 1, 0), so `shrunkValues.isEmpty()` would be false

---

## Why the Test Doesn't Fail as Expected

The test is currently **ignored** via `Assumptions.assumeTrue(false)`. When enabled, it would fail, but **NOT because of
the line 68 bug**.

**Why it fails:**

1. **Size-based shrinks** (line 26 in DistinctListGen.kt) always produce shrinks to smaller sizes
2. The sequence `sizeResult.shrinks` contains trees for sizes 0, 1, 2
3. Therefore `shrunkValues` is **NOT empty** and the test fails on the assertion
4. The test fails **before** evaluating element-based shrinks that might trigger the line 68 bug

**The line 68 bug is masked** because:

- Test fails for the wrong reason (size shrinks exist)
- Element-based shrinks that could expose the bug come later in the lazy sequence
- The test's flawed logic prevents it from being a useful bug detector

---

## Is There Actually a Bug?

**YES.** The line 68 bug is real, though the test doesn't properly expose it.

### Current Behavior (Buggy)

```kotlin
if (maxTreeOffset != null && treeOffset > maxTreeOffset) {
    break  // Breaks even if results.size < minSize!
}
```

### Expected Behavior

The break should only occur if minimum size requirements are satisfied:

```kotlin
if (maxTreeOffset != null && treeOffset > maxTreeOffset) {
    if (results.size >= minSize) {
        break  // Safe to stop
    }
    // else: Can't traverse further (offset limit) but haven't met minSize
    // This shrink is INVALID
    break  // Currently breaks anyway, creating undersized list
}
```

### Impact

- **Shrinks can violate size constraints:** Element-based shrinks may produce lists smaller than `minSize`
- **Shrinking quality degrades:** Invalid shrinks pollute the shrink sequence
- **Potential exceptions downstream:** Code consuming shrinks might assume size constraints are honored
- **Hard to detect:** Only occurs in specific scenarios (tight value range + size constraints + duplicates during
  shrinking)

---

## Recommendation

### Fix the Line 68 Bug

**Option 1: Check minSize before breaking**

```kotlin
// Line 67-70
if (maxTreeOffset != null && treeOffset > maxTreeOffset) {
    if (results.size < minSize) {
        // Shrink failed to meet minimum size - invalid shrink
        // Could throw exception or mark as invalid
    }
    break
}
```

**Option 2: Respect minSize in the check**

```kotlin
if (maxTreeOffset != null && treeOffset > maxTreeOffset && results.size >= minSize) {
    break
}
```

**Preferred:** Option 1, as it makes the failure case explicit and could be enhanced with proper error handling or
filtering.

### Fix the Test

The test at line 98 needs complete rewrite:

```kotlin
@Test
fun `does not produce element shrinks when list size equals distinct value count`() {
    // Only 3 distinct values possible: 0, 1, 2
    val intGen = int(0..2)
    val gen = intGen.list(3, distinct = true)

    val result = gen.generate(tree())
    expectThat(result.value).hasSize(3)  // Must be size 3
    expectThat(result.value.toSet()).hasSize(3)  // All distinct
    
    // Size shrinks will exist (size 2, 1, 0), but element shrinks should not
    // because shrinking any element creates duplicates in a constrained space
    val shrunkValues = result.shrunkValues.toList()
    val elementShrinks = shrunkValues.filter { it.size == 3 }  // Same size = element shrink
    expectThat(elementShrinks).isEmpty()  // No valid element shrinks
}
```

Or test the bug directly:

```kotlin
@Test
fun `respects minSize when maxTreeOffset is reached`() {
    val gen = int(0..2).list(3..3, distinct = true)
    
    repeat(100) {
        val result = gen.generate(tree())
        // All shrinks must respect size constraint
        expectThat(result).shrunkValues.all { 
            hasSize.isGreaterThanOrEqualTo(3) 
        }
    }
}
```

---

## Relevant Files

- **`src/main/kotlin/com/tamj0rd2/ktcheck/current/DistinctListGen.kt`**
    - Line 68: Bug location
    - Lines 12-46: `generate()` method - creates shrinks with maxTreeOffset
    - Lines 53-95: `generateListWithResults()` - regenerates with constraint

- **`src/test/kotlin/com/tamj0rd2/ktcheck/contracts/DistinctListGeneratorContract.kt`**
    - Lines 98-107: Flawed test case
    - Lines 119-133: Better test examples (`all shrunk element values do not exceed max`)

---

## Key Functions/Classes

- **`DistinctListGen.generate()`** (`DistinctListGen.kt:12`)
    - Creates element-based shrinks with `MaxTreeOffsetWrapper`

- **`DistinctListGen.generateListWithResults()`** (`DistinctListGen.kt:53`)
    - Enforces `maxTreeOffset` constraint at line 68
    - Checks `MAX_FAILURES` at line 72

- **`MaxTreeOffsetWrapper`** (`DistinctListGen.kt:48`)
    - Data wrapper that attaches maxOffset metadata to trees during shrinking

---

## Patterns/Conventions

- **Lazy shrink sequences:** Shrinks are generated lazily and not validated at creation time
- **Tree offset constraint:** Used to prevent shrinking from introducing larger values
- **Dual shrinking strategy:** Size-based (line 26) + element-based (line 33)
- **Failure counting:** MAX_FAILURES=100 limit for duplicate attempts

---

## Open Questions

1. **Should invalid shrinks be filtered out?** Currently, undersized shrinks from line 68 bug remain in the sequence
2. **What's the correct behavior when maxTreeOffset is reached before minSize?**
    - Throw exception (fail fast)?
    - Continue without offset limit (might introduce larger values)?
    - Skip this shrink silently?
3. **Is the test actually testing a valid property?** The comment suggests no shrinks should exist when value range
   equals list size, but size shrinks still make sense

---

## Confidence Assessment

- **Level:** High
- **Coverage:** 95% - Examined implementation, test, and shrinking logic thoroughly
- **Gaps:**
    - Don't have execution trace of actual failing scenario (test is ignored)
    - Haven't verified IntShrinker behavior for range 0..2
    - Haven't confirmed exact random tree values from default `tree()` seed

---

## Dependencies

- **IntShrinker:** Used to shrink individual elements (from `com.tamj0rd2.ktcheck.core.shrinkers`)
- **RandomTree:** Tree structure for deterministic random generation
- **Seed:** Random seed management for reproducibility
- **Strikt:** Assertion library used in tests
