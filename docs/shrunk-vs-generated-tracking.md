# Distinguishing Shrunk vs Generated Values

## The Proposal

What if we could track whether a predetermined value came from:

1. **Original generation** - Fresh random value from seed
2. **Shrinking** - A simplified version of another value

## Implementation Options

### Option 1: Add Metadata to Choice

```kotlin
sealed interface Choice {
    val seed: Long
    val origin: Origin
    
    enum class Origin {
        GENERATED,  // Created during initial generation
        SHRUNK      // Created during shrinking
    }
    
    data class IntChoice(
        val value: Int,
        override val seed: Long,
        override val origin: Origin = Origin.GENERATED
    ) : Choice
}
```

### Option 2: Separate Value Types

```kotlin
sealed interface Value {
    data class Undetermined(val seed: Long) : Value
    
    data class Generated(val choice: Choice, val seed: Long) : Value
    data class Shrunk(val choice: Choice, val seed: Long) : Value
}
```

### Option 3: Shrink Depth Counter

```kotlin
data class IntChoice(
    val value: Int,
    val seed: Long,
    val shrinkDepth: Int = 0  // 0 = generated, 1+ = shrunk
)
```

## Analysis: Does This Help?

Let's revisit each issue from the regeneration approach analysis:

### Issue #2: Loss of Shrinking Structure

**Before tracking:**

```
Original: Int(10) from seed S1
Shrunk: Int(5) from seed S1 (same seed!)
Switch generator: Regenerate Random(S1) → Bool
Problem: S1 is original seed, doesn't reflect shrinking
```

**With tracking:**

```
Original: Int(10, seed=S1, origin=GENERATED)
Shrunk: Int(5, seed=S1, origin=SHRUNK)
Switch generator: See SHRUNK flag...
```

**Question: Now what?**

**Option A: Use a different seed for shrunk values**

```kotlin
fun shrinkValue(original: IntChoice): IntChoice {
    val shrunkValue = original.value / 2
    val shrunkSeed = deriveSeed(original.seed, shrunkValue)  // Different seed!
    return IntChoice(shrunkValue, shrunkSeed, Origin.SHRUNK)
}
```

This could work! When switching generators:

```kotlin
override fun bool(): Boolean = when (choice) {
    is IntChoice -> {
        if (choice.origin == Origin.SHRUNK) {
            // Use the shrunk seed, which reflects simplification
            Random(choice.seed).nextBoolean()
        } else {
            // Use original seed
            Random(choice.seed).nextBoolean()
        }
    }
    // ...
}
```

**Analysis:**

- ✅ **Helps!** Shrunk values get different seeds
- ✅ Seeds can reflect "simpler" values
- ⚠️ But how do you derive the seed? Hash(value)? That's arbitrary
- ⚠️ Different values might hash to same seed → conflicts
- ⚠️ Still loses the *structure* of shrinking (e.g., binary search path)

**Option B: Don't regenerate shrunk values, use fresh tree instead**

```kotlin
override fun bool(): Boolean = when (choice) {
    is IntChoice -> {
        if (choice.origin == Origin.SHRUNK) {
            // Shrunk values indicate we're mid-shrinking
            // Don't regenerate - use fresh tree approach
            throw ShouldUseFreshTreeException()
        } else {
            // Only regenerate GENERATED values
            Random(choice.seed).nextBoolean()
        }
    }
    // ...
}
```

**Analysis:**

- ✅ Hybrid approach: regenerate only safe cases
- ✅ Avoids the "wrong seed" problem for shrunk values
- ❌ But now you have two different behaviors (complex!)
- ❌ Still need fresh tree mechanism anyway
- ❌ Why not just always use fresh tree then?

### Issue #3: Boolean's Own Shrinks

**Doesn't help.**

Whether the IntChoice was generated or shrunk, the BoolGenerator still needs to:

1. Regenerate a boolean value
2. Create shrinks for that boolean
3. Handle potential cycles

The origin flag doesn't change this.

### Issue #4: Cyclic Regeneration Problem

**Doesn't help.**

```
Step 1: IntGen generates Int(10) [GENERATED]
Step 2: Int shrinks to Int(5) [SHRUNK]
Step 3: Switch to BoolGen, regenerate → Bool(true) [GENERATED]
Step 4: Bool shrinks to Bool(false) [SHRUNK]  
Step 5: Switch back to IntGen, regenerate → Int(7) [GENERATED]
...
```

Knowing which values are shrunk doesn't prevent the cycle. You're still regenerating on each switch.

**Unless** you refuse to regenerate shrunk values (Option B above), which just brings you back to fresh trees.

### Issue #5: Determinism Maintained, But Meaning Lost

**Partially helps if you derive different seeds for shrunk values.**

```
Original: Int(42) from seed S1
Shrink to Int(21): seed = deriveSeed(S1, 21)
Shrink to Int(10): seed = deriveSeed(S1, 10)
Shrink to Int(5): seed = deriveSeed(S1, 5)
```

Now when you switch to BoolGen:

```kotlin
// Encountering IntChoice(5, seed=deriveSeed(S1, 5), origin=SHRUNK)
Random(deriveSeed(S1, 5)).nextBoolean()
```

**Analysis:**

- ✅ The seed reflects the shrunk value (5)
- ✅ Smaller values → different seeds
- ⚠️ But is deriveSeed(S1, 5) actually "simpler" than deriveSeed(S1, 42)?
- ⚠️ Not really - they're just different random seeds
- ⚠️ No guarantee that Random(deriveSeed(S1, 5)).nextBoolean() is simpler than Random(deriveSeed(S1, 42)).nextBoolean()

The seeds are different, but **not meaningfully simpler**.

### Issue #6: Shrink Quality Degradation

**Doesn't really help.**

Even with origin tracking:

```
Initial: Int(75) [GENERATED, seed=S1]
Shrink: Int(37) [SHRUNK, seed=deriveSeed(S1, 37)]
Switch to Bool: Random(deriveSeed(S1, 37)).nextBoolean() → ?

Problem: We don't know if this boolean is "simpler" than one from seed S1
```

The origin flag tells you the int was shrunk, but doesn't help you generate a "shrunk" boolean. Boolean shrinking is
independent of integer shrinking.

### Issue #7: What Does The Tree Represent?

**Makes it more complex!**

Now the tree has **three** interpretations:

1. For the generator that created it: A deterministic record
2. For other generators (GENERATED values): A seed source
3. For other generators (SHRUNK values): Either a different seed source, or a signal to use fresh tree

This doesn't simplify the semantics - it makes them more complex.

### Issue #8: Implementation Complexity

**Increases it!**

Now you need:

- Origin tracking in every Choice
- Logic to set origin correctly during shrinking
- Seed derivation logic for shrunk values
- Conditional behavior based on origin in regeneration
- Documentation of what each origin means

**More complex, not less.**

### Issue #9: Safety Net

**No change.**

Whether a value is GENERATED or SHRUNK, regeneration still hides bugs. The TODO would still be gone.

### Issue #10: Performance/Memory

**Makes it worse!**

```kotlin
// Before: Just the value
data class IntChoice(val value: Int)  // 4 bytes

// With seed: 
data class IntChoice(val value: Int, val seed: Long)  // 12 bytes

// With seed + origin:
data class IntChoice(val value: Int, val seed: Long, val origin: Origin)  // 16 bytes

// Or with shrink depth:
data class IntChoice(val value: Int, val seed: Long, val shrinkDepth: Int)  // 16 bytes
```

**4x memory overhead, plus additional checking logic.**

## New Issues Created

### New Issue #1: Seed Derivation Strategy

**Problem:** How do you derive seeds for shrunk values?

**Options:**

**A. Hash the value:**

```kotlin
val shrunkSeed = value.hashCode().toLong()
```

- ❌ Different values can hash to same seed
- ❌ Depends on hashCode implementation
- ❌ Not stable across platforms/versions

**B. Derive from original seed + value:**

```kotlin
val shrunkSeed = deriveSeed(originalSeed, value.toLong())
```

- ✅ Deterministic
- ✅ Different values → different seeds
- ⚠️ But still arbitrary - no "simpler" relationship

**C. Track shrink path:**

```kotlin
data class IntChoice(
    val value: Int,
    val seed: Long,
    val shrinkPath: List<ShrinkStep>  // Binary search path, etc.
)
```

- ✅ Could reconstruct meaningful shrinking
- ❌ Huge memory overhead
- ❌ Extremely complex
- ❌ Different generator types have incompatible shrink paths

### New Issue #2: Cross-Type Shrink Semantics

**Problem:** What does "shrunk" mean across different types?

```kotlin
Int(5) [SHRUNK] → shrunk from Int(10)
// Switch to Bool
Bool(?) should be... what?

- Shrunk toward false? (arbitrary choice)
- Shrunk toward true? (equally arbitrary)
- Fresh? (then why track origin?)
```

There's no meaningful translation of "shrunk int" to "shrunk bool". The shrink structure is type-specific.

### New Issue #3: Inconsistent Shrink Behavior

**Problem:** Shrinks behave differently depending on whether you switched generators.

**Case 1: No generator switch**

```
Gen.int generates Int(10)
Shrinks to Int(5) [SHRUNK]
Shrinks to Int(2) [SHRUNK]
Normal int shrinking behavior
```

**Case 2: With generator switch**

```
Gen.int generates Int(10)
Shrinks to Int(5) [SHRUNK]
Switch to Gen.bool → Bool(true) [GENERATED] (regenerated)
Shrinks to Bool(false) [SHRUNK]
Switch back to Gen.int → Int(?) [GENERATED] (regenerated)
```

The same tree structure produces different shrink sequences depending on generator switches. This is confusing for users
debugging failures.

## Comprehensive Comparison

| Aspect                   | Fresh Tree           | Regeneration (Basic) | Regeneration + Origin Tracking          |
|--------------------------|----------------------|----------------------|-----------------------------------------|
| **Implementation**       | ✅ Simple             | ❌ Complex            | ❌❌ Very Complex                         |
| **Memory**               | ✅ No overhead        | ❌ 3x (seed)          | ❌❌ 4x (seed + origin)                   |
| **Semantics**            | ✅ Clear              | ⚠️ Dual meaning      | ❌ Triple meaning                        |
| **Shrink Quality**       | ⚠️ Fresh arbitrary   | ⚠️ Original seed     | ⚠️ Derived seed (still arbitrary)       |
| **Determinism**          | ✅ Yes                | ✅ Yes                | ✅ Yes                                   |
| **Safety**               | ✅ TODO catches bugs  | ❌ Silent             | ❌ Silent                                |
| **Consistency**          | ✅ Always fresh       | ✅ Always regenerate  | ❌ Sometimes regenerate, sometimes fresh |
| **Cross-type semantics** | ✅ N/A - always fresh | ⚠️ Reuses seed       | ❌ No meaningful translation             |

## Does Origin Tracking Help?

### What It Could Provide

1. **Information** - You know if a value was shrunk
2. **Conditional behavior** - Could treat shrunk values differently
3. **Debugging** - Could display origin in error messages

### What It Doesn't Solve

1. ❌ **Shrink quality** - Still can't meaningfully translate int shrinking to bool shrinking
2. ❌ **Complexity** - Adds more complexity, doesn't reduce it
3. ❌ **Semantics** - Makes tree meaning more complex, not clearer
4. ❌ **Memory** - Increases overhead
5. ❌ **Cycles** - Doesn't prevent regeneration cycles
6. ❌ **Cross-type shrinking** - No meaningful way to derive "shrunk bool" from "shrunk int"

### The Fundamental Problem

**The issue is not distinguishing generated vs shrunk - it's that shrinking is type-specific.**

An int shrinks toward 0 via binary search.
A bool shrinks from true to false.
A list shrinks by removing elements.

These shrink structures are **incompatible**. Knowing that Int(5) is a shrunk value doesn't tell you how to create a "
shrunk" boolean. There's no universal notion of "shrunk" across types.

## Alternative: What If We Track Shrink Progress Universally?

**Crazy idea:** What if predetermined values tracked a "simplicity score"?

```kotlin
data class IntChoice(
    val value: Int,
    val seed: Long,
    val simplicity: Double  // 0.0 = original, 1.0 = fully shrunk
)
```

Then when switching generators:

```kotlin
override fun bool(): Boolean = when (choice) {
    is IntChoice -> {
        // Use simplicity to bias the random generation
        val biasedSeed = deriveSeed(choice.seed, (choice.simplicity * 1000).toLong())
        val random = Random(biasedSeed)
        
        // If highly simplified, bias toward "simple" bool (false)
        if (choice.simplicity > 0.5 && random.nextFloat() < choice.simplicity) {
            false  // "Simplified" boolean
        } else {
            random.nextBoolean()
        }
    }
}
```

**Analysis:**

- 🤔 Interesting idea!
- ✅ Captures notion of "we've shrunk a lot"
- ❌ Extremely arbitrary (what does 0.5 simplicity mean for a bool?)
- ❌ Complex to implement correctly
- ❌ Each generator needs simplicity → value logic
- ❌ Still loses actual shrink structure

**Verdict:** Too complex, too arbitrary, doesn't actually solve the core problem.

## Conclusion

**Does distinguishing shrunk vs generated values help?**

**Short answer: No, not really.**

**Longer answer:**

1. ✅ **Minor help:** Could derive different seeds for shrunk values
2. ✅ **Debugging:** Could display origin in error messages
3. ❌ **Shrink quality:** Still can't meaningfully translate shrinking across types
4. ❌ **Complexity:** Adds significant complexity without solving core issues
5. ❌ **Memory:** Increases overhead
6. ❌ **Semantics:** Makes tree meaning more complex

**The fundamental problem remains:** Shrinking is type-specific. Int shrinking ≠ Bool shrinking. No amount of metadata
on the int value tells you how to create a "shrunk" bool.

**The fresh tree approach is still simpler** because it accepts this reality:

- When you switch generators, you get a fresh value
- That fresh value is deterministic (from tree.right.left)
- It has its own shrink structure appropriate to its type
- No complex metadata, no arbitrary seed derivation, no dual semantics

**If you want better shrink quality when switching generators,** the solution is not metadata - it's **don't switch
generator types**. Use the type system to prevent it:

```kotlin
// Good - same type, can shrink across branches
Gen.oneOf(Gen.int(0..10), Gen.int(100..200))

// Bad - different types, can't meaningfully shrink across branches  
Gen.oneOf(Gen.int(0..10).map { it as Any }, Gen.bool().map { it as Any })
```

The type mismatch is telling you something: these generators are fundamentally incompatible for shrinking purposes.

