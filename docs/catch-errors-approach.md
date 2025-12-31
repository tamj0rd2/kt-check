# Catching and Ignoring Type Mismatch Errors

## The Proposal

Instead of:

1. Using fresh trees to avoid type mismatches, OR
2. Regenerating from seeds when type mismatches occur

Simply **catch the NotImplementedError** and handle it gracefully.

## Implementation Options

### Option 1: Catch in the Generator

```kotlin
// In OneOfGenerator:
override fun generate(tree: ValueTree): GenResult<T> {
    val (index, indexShrinks) = Gen.int(0..<gens.size).generate(tree.left)

    val shrinks = sequence {
        indexShrinks.forEach { shrunkIndexTree ->
            val shrunkTree = tree.withLeft(shrunkIndexTree)

            // Try to generate with new index
            try {
                yield(shrunkTree)
            } catch (e: NotImplementedError) {
                // Type mismatch - skip this shrink
                // Don't yield anything
            }
        }

        valueShrinks.forEach { shrunkValueTree ->
            yield(tree.withRight(shrunkValueTree))
        }
    }

    val (value, valueShrinks) = gens[index].generate(tree.right)
    return GenResult(value, shrinks)
}
```

### Option 2: Catch in the Shrinking Algorithm

```kotlin
// In the test framework's shrinking loop:
fun findSmallerCounterexample(gen: Gen<T>, tree: ValueTree): T? {
    val (_, shrinks) = gen.generate(tree)

    for (shrinkTree in shrinks) {
        try {
            val (value, _) = gen.generate(shrinkTree)
            if (propertyFails(value)) {
                return value  // Found smaller counterexample
            }
        } catch (e: NotImplementedError) {
            // Type mismatch in this shrink - skip it
            continue
        }
    }

    return null  // No smaller counterexample found
}
```

### Option 3: Catch in ValueTree

```kotlin
// In ValueTree.value:
override fun bool(): Boolean = when (choice) {
    is BooleanChoice -> choice.value
    is Undetermined -> Random(choice.seed).nextBoolean()
    is IntChoice -> {
        // Instead of TODO, return a default
        false  // Or throw a special RecoverableTypeError
    }
}
```

## Analysis: Does This Work?

Let me trace through what happens with each option:

### Option 1: Catch in Generator

**Scenario:**

```kotlin
val gen = Gen.oneOf(
    Gen.bool().map { it as Any },
    Gen.int(0..10).map { it as Any }
)

// Generate: index=1, value=Int(7)
// Shrink: index→0, tree.right has IntChoice(7)
```

**What happens:**

```kotlin
indexShrinks.forEach { shrunkIndexTree ->
    val shrunkTree = tree.withLeft(shrunkIndexTree)

    try {
        yield(shrunkTree)  // Just yield the tree
    } catch (e: NotImplementedError) {
        // This won't catch anything!
        // We haven't called generate() yet
    }
}
```

**Problem:** The error doesn't happen during `yield()` - it happens later when someone calls `gen.generate(shrunkTree)`.

**Fix:** Need to eagerly check:

```kotlin
indexShrinks.forEach { shrunkIndexTree ->
    val shrunkTree = tree.withLeft(shrunkIndexTree)

    try {
        // Eagerly test if this tree would work
        gen.generate(shrunkTree)
        yield(shrunkTree)  // Only yield if it worked
    } catch (e: NotImplementedError) {
        // Type mismatch - skip this shrink
    }
}
```

**But wait!** Now you're calling `generate()` just to check if it works. That's expensive and has side effects (it
actually generates the value).

### Option 2: Catch in Shrinking Algorithm

This is more promising! The shrinking code already calls `generate()`:

```kotlin
for (shrinkTree in shrinks) {
    try {
        val (value, _) = gen.generate(shrinkTree)
        if (propertyFails(value)) {
            return value
        }
    } catch (e: NotImplementedError) {
        continue  // Skip this shrink, try next one
    }
}
```

**Analysis:**

- ✅ Simple change - just add try/catch
- ✅ No eager evaluation needed
- ✅ Localized to shrinking loop
- ⚠️ But what if ALL shrinks throw? Then shrinking just stops

Let me trace through a complete example:

```kotlin
// Initial generation
val gen = Gen.oneOf(Gen.bool().map { it as Any }, Gen.int(0..10).map { it as Any })
val (value, shrinks) = gen.generate(tree)
// index=1, value=Int(7)

// Shrinking
shrinks = [
    tree.withLeft(shrunkIndex0),  // index→0, would cause type error
    tree.withRight(shrunkInt0),   // value→0
    tree.withRight(shrunkInt3),   // value→3
    // ...
]

// Try first shrink: index→0
try {
    gen.generate(tree.withLeft(shrunkIndex0))
    // This calls BoolGen.generate(tree.right)
    // Which calls tree.right.value.bool()
    // Which sees IntChoice(7)
    // Which throws NotImplementedError
} catch (e: NotImplementedError) {
    continue  // Skip to next shrink
}

// Try second shrink: value→0
gen.generate(tree.withRight(shrunkInt0))
// index=1 (unchanged), IntGen.generate(tree.right)
// tree.right.value.int(0..10) → IntChoice(0)
// Works! Returns Int(0)
```

**This actually works!**

### Option 3: Return Default Instead of TODO

```kotlin
override fun bool(): Boolean = when (choice) {
    is BooleanChoice -> choice.value
    is Undetermined -> Random(choice.seed).nextBoolean()
    is IntChoice -> false  // Default instead of error
}
```

**Analysis:**

- ⚠️ Silently returns wrong value
- ❌ Loses determinism (always returns false, ignoring the tree)
- ❌ Not actually using the tree structure
- ❌ Terrible for debugging - silent data corruption

**This is the worst option.**

## Detailed Analysis of Option 2 (Catch in Shrinking Loop)

### Advantages

1. ✅ **Simple implementation** - Just wrap generate() in try/catch
2. ✅ **Localized change** - Only affects shrinking code
3. ✅ **No memory overhead** - No extra metadata needed
4. ✅ **No semantic changes** - Trees still mean the same thing
5. ✅ **Works correctly** - Type-mismatched shrinks are simply skipped
6. ✅ **Deterministic** - Same tree always produces same result (or same error)
7. ✅ **Performance** - No eager evaluation, errors only thrown for invalid shrinks

### How It Works

```
Shrinks sequence: [S1, S2, S3, S4, S5, ...]
                   ↓   ↓   ✓   ✓   ↓
Try S1: Type error → skip
Try S2: Type error → skip  
Try S3: Success! → explore nested shrinks of S3
Try S4: Success! → explore nested shrinks of S4
Try S5: Type error → skip
...
```

The shrinking algorithm naturally skips invalid shrinks and continues with valid ones.

### Shrink Quality

**Example:**

```
Initial: index=1 (IntGen), value=Int(100)

Shrinks:
1. index→0 (BoolGen) → TYPE ERROR → skip
2. value→0            → Int(0) ✓
3. value→50           → Int(50) ✓
4. nested from Int(50): value→25 → Int(25) ✓
...

Result: Successfully shrinks the int value, ignores the invalid generator switch
```

**Compared to fresh tree approach:**

```
Initial: index=1 (IntGen), value=Int(100)

Shrinks:
1. index→0 (BoolGen) with fresh tree → Bool(true) ✓
2. value→0                            → Int(0) ✓
3. value→50                           → Int(50) ✓
...

Result: Can switch generators AND shrink values
```

**Trade-off:**

- **Catch errors**: Can't switch generators, but shrinks within same generator work
- **Fresh tree**: Can switch generators, gets fresh values

Which is better depends on your goals!

### Issues with Option 2

#### Issue #1: All Shrinks Might Fail

**Problem:** If all shrinks cause type errors, shrinking stops immediately.

```kotlin
val gen = Gen.oneOf(
    Gen.bool().map { it as Any },
    Gen.int(0..10).map { it as Any }
)

// Contrived scenario where value shrinks are exhausted
// Only index shrinks remain, and they all cause type errors
```

**Analysis:**

- ⚠️ In practice, unlikely - usually have both index and value shrinks
- ⚠️ But could happen in edge cases
- ✅ Better than crashing - at least shrinking tried

#### Issue #2: Errors During Generation (Not Shrinking)

**Problem:** What if type error occurs during initial generation?

```kotlin
// This would only happen with a bug in the generator
val gen = Gen.oneOf(/* some broken setup */)
gen.generate(tree)  // Throws during generation

// Who catches this?
```

**Analysis:**

- ⚠️ If error during generation, test framework should fail (it's a real bug)
- ✅ Only catch during shrinking, not generation
- ✅ Distinguish between "initial error" (bug) and "shrink error" (type mismatch)

#### Issue #3: Lost Information

**Problem:** When shrinks are skipped, you don't know why.

```kotlin
// Shrinking found: Int(42)
// But could there have been a simpler Bool counterexample?
// We'll never know - those shrinks were skipped
```

**Analysis:**

- ⚠️ True, you lose potential shrink paths
- ✅ But those paths were invalid anyway (type mismatches)
- ⚠️ Could log skipped shrinks for debugging

#### Issue #4: Not a General Solution

**Problem:** Only works for type mismatches, not other TODOs.

```kotlin
override fun int(range: IntRange): Int {
    if (choice !is IntChoice) TODO("handle non-int choice $choice")
    if (choice.value !in range) TODO("handle int out of range ${choice.value} not in $range")
    //                           ^^^ This TODO would also be caught!
    return choice.value
}
```

**Analysis:**

- ⚠️ Catching NotImplementedError catches ALL TODOs, not just type mismatches
- ⚠️ Could hide real bugs (out-of-range values)
- ✅ Could catch specific exception instead: `TypeMismatchError`

## Comparison Table

| Aspect                      | Fresh Tree                 | Catch Errors                  | Regeneration             |
|-----------------------------|----------------------------|-------------------------------|--------------------------|
| **Implementation**          | ✅ Moderate (OneOfGen only) | ✅✅ Simple (shrink loop only)  | ❌ Complex (everywhere)   |
| **Memory**                  | ✅ No overhead              | ✅✅ No overhead                | ❌ 3-4x overhead          |
| **Can switch generators**   | ✅ Yes, with fresh values   | ❌ No, skips those shrinks     | ✅ Yes, with regeneration |
| **Shrink within generator** | ✅ Yes                      | ✅ Yes                         | ✅ Yes                    |
| **Determinism**             | ✅ Yes                      | ✅ Yes                         | ✅ Yes                    |
| **Semantics**               | ✅ Clear                    | ✅✅ Very clear                 | ⚠️ Dual meaning          |
| **Performance**             | ✅ Good                     | ✅✅ Best (skip invalid)        | ⚠️ Type checks           |
| **Safety**                  | ✅ TODO catches bugs        | ⚠️ Catches all TODOs          | ❌ Silent                 |
| **Lost shrinks**            | ⚠️ Fresh, not continued    | ⚠️ Generator switches skipped | ⚠️ Meaning lost          |

## Recommendation

**The "catch errors" approach is actually quite good!**

### Refined Implementation

```kotlin
// Define specific exception for type mismatches
class TypeMismatchError(message: String) : Exception(message)

// In ValueTree:
override fun bool(): Boolean = when (choice) {
    is BooleanChoice -> choice.value
    is Undetermined -> Random(choice.seed).nextBoolean()
    else -> throw TypeMismatchError("Expected BooleanChoice but got $choice")
}

// In shrinking loop:
fun <T> shrink(gen: Gen<T>, tree: ValueTree): ValueTree? {
    val (_, shrinks) = gen.generate(tree)

    for (shrinkTree in shrinks) {
        try {
            val (value, _) = gen.generate(shrinkTree)
            if (propertyFails(value)) {
                return shrinkTree  // Found smaller counterexample
            }
        } catch (e: TypeMismatchError) {
            // Type mismatch in this shrink path - skip it
            continue
        }
        // Other exceptions propagate (real bugs)
    }

    return null
}
```

### Advantages of This Approach

1. ✅ **Simplest implementation** - Single try/catch in shrinking loop
2. ✅ **No memory overhead** - No metadata or seed storage
3. ✅ **Clear semantics** - Trees mean what they've always meant
4. ✅ **Safe** - Specific exception type, doesn't hide other bugs
5. ✅ **Efficient** - Invalid shrinks fail fast and are skipped
6. ✅ **OneOf doesn't need changes** - Can just use simple `tree.combineShrinks()`

### Trade-offs

- ❌ **Can't switch generators during shrinking** - Those shrinks are skipped
- ✅ **But this might be acceptable!** - Type-switching is already questionable

### When to Use Each Approach

**Use "catch errors" if:**

- You want the absolute simplest implementation
- Generator switches during shrinking are rare
- You're okay with type-switched shrinks being skipped
- You want zero memory overhead

**Use "fresh tree" if:**

- You want to support generator switching during shrinking
- You want all shrinks to be attempted (none skipped)
- You're okay with slightly more complex OneOf implementation
- You're okay with shrink resets when switching

## Conclusion

**The "catch errors" approach is surprisingly good!**

It's:

- ✅ Simpler than fresh tree approach
- ✅ Much simpler than regeneration approach
- ✅ No memory overhead
- ✅ Clear semantics
- ✅ Just works

**The only downside:** Generator-switching shrinks are skipped. But:

1. Type-switching is already problematic (different shrink structures)
2. Most shrinking happens within the same generator
3. If you really need generator switching, use fresh tree approach

**My recommendation:** Start with "catch errors" approach. It's the simplest, and for most use cases, it works perfectly
well. If you later find you need generator-switching shrinks, you can always upgrade to the fresh tree approach.

## How OneOf Changes

**With catch errors, OneOf can be super simple:**

```kotlin
override fun generate(tree: ValueTree): GenResult<T> {
    val (index, indexShrinks) = Gen.int(0..<gens.size).generate(tree.left)
    val (value, valueShrinks) = gens[index].generate(tree.right)

    // Just use combineShrinks - let the shrinking loop handle errors
    return GenResult(
        value = value,
        shrinks = tree.combineShrinks(indexShrinks, valueShrinks)
    )
}
```

**No fresh trees, no seed tracking, no complexity!**

The shrinking loop will try each shrink, and if it causes a type mismatch, it'll catch the error and skip to the next
one. Simple and effective.

