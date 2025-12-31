# Selective Applicative Functors and the Type-Switching Problem

## Important Clarification

**The problem we encountered and solved is related to the _motivation_ for Selective Functors in property-based testing,
but our solution is NOT actually implementing Selective Functors.**

### What Selective Functors Actually Are

Selective Applicative Functors sit between Applicative and Monad:

- **Applicative**: `f a -> f b -> f (a, b)` - both effects always execute
- **Monad**: `f a -> (a -> f b) -> f b` - second effect depends on first value
- **Selective**: `f (Either a b) -> f (a -> b) -> f b` - second effect executes **only if needed**

```haskell
class Applicative f => Selective f where
    select :: f (Either a b) -> f (a -> f b) -> f b
    -- If first produces Right b, second is NOT executed (effects skipped)
    -- If first produces Left a, second IS executed to get (a -> b)
```

The key: **conditional effect execution** - you can skip executing the second computation entirely, including not
inspecting its structure.

### What We Actually Did

We implemented a **shrinking strategy** in `oneOf` that:

1. Uses a fresh undetermined tree when switching generators
2. Prevents type mismatches during shrinking
3. Has nothing to do with conditional effect execution

Our `oneOf` is not a Selective instance - it's just a generator combinator that's careful about tree reuse.

---

## Summary of the Well-Typed Article

The article discusses **how Selective Functors help with property-based testing shrinking**. Here's what it actually
means:

The article discusses **Selective Applicative Functors** in the context of property-based testing and shrinking. Here's
what it means:

### The Problem: Generating Values You Don't Use

When you want to conditionally choose between two generators based on a boolean, you might be tempted to do this:

```haskell
choose :: Gen a -> Gen a -> Gen a  -- Bad definition!
choose g g' = do
    x <- g
    y <- g'
    b <- Gen.bool True
    return $ if b then x else y
```

This works fine for **generation** (thanks to laziness, only one generator actually runs), but it shrinks **terribly**.
Here's why:

- If you generate value `y` but don't use it (because `b` is `true`), that part of the sample tree becomes irrelevant
- The library will shrink that unused part of the tree to its minimum (zero everywhere)
- Later, if the boolean shrinks to `false` and you now need `y`, you can **only** get the absolute minimum value from
  generator `g'`
- This means shrinking gets stuck at minimums instead of finding good counter-examples

### The Golden Rule

> **Do not generate values and then discard them: such values will always shrink to their minimum. (Instead, don't
generate the value at all.)**

### The Solution: Selective Functors

**Selective Applicative Functors** solve this by making it visible to the library when a generator is needed and when
it's not. The key operation is:

```haskell
select :: Gen (Either a b) -> Gen (a -> b) -> Gen b
```

The intuition:

1. Run the first generator
2. If it produces `Left a`, run the second generator to get a `b`
3. If it produces `Right b`, **skip the second generator completely**
4. Critically: **we will not try to shrink the right subtree unless that generator is used**

This enables a combinator like:

```haskell
ifS :: Selective f => f Bool -> f a -> f a -> f a
```

Which lets you implement `choose` correctly:

```haskell
choose :: Gen a -> Gen a -> Gen a
choose = ifS (bool True)
```

Now the two generators shrink **independently** because the library doesn't shrink the unused branch.

## How This Relates to Your Test

Your test in `ValueTreeTest.kt` demonstrates **the type-switching problem** that can occur with `flatMap` when the
function returns different generator types.

### The Connection to Selective Functors (in the Falsify library)

The Well-Typed article explains that the **Falsify library makes `Gen` a Selective instance** and provides a `choose`
combinator built on `select`. This allows conditional generation without the type-switching problem because:

1. Falsify's `Gen` implements the `Selective` type class
2. The `select` operation can skip inspecting/shrinking the second branch
3. The library can avoid shrinking parts of the tree that correspond to unused branches

**However, your Kotlin library does NOT have Selective Functors.** Kotlin lacks higher-kinded types, making it difficult
to implement the Selective type class.

### What Your Test Actually Shows

```kotlin
val gen = Gen.bool().flatMap { useInt ->
    if (useInt) Gen.int(0..10) else Gen.bool()
}
```

This `flatMap` returns **different generator types** based on the boolean:

- `true` → `Gen.int(0..10)`
- `false` → `Gen.bool()`

### The Problem in Your Test

1. **First generation**: `bool=true` → use `Gen.int()` → produces an `Int` with `IntChoice` in the tree
2. **Right shrink**: The int shrinks (e.g., `5 → 0`), creating a tree with `right=predetermined(IntChoice(0))`
3. **Nested left shrink**: Now **both** sides shrink:
    - `left=predetermined(BooleanChoice(false))`
    - `right=predetermined(IntChoice(0))` (from step 2)
4. **The collision**: `flatMap` calls `fn(false)` → returns `Gen.bool()` → tries to read from `tree.right` which has an
   `IntChoice`
5. **💥 TODO hit**: "handle non-bool choice IntChoice(value=0)"

### Why This Happens

Your code uses **monadic `flatMap`**, not Selective. With monadic bind:

- Both subtrees are part of the sample tree
- Both get shrunk independently
- When shrinks combine, you can end up with a tree where the left side (the decision) changed, but the right side still
  has predetermined values from the *old* decision

This is precisely the issue the article warns about! The second generator was run and produced a value (with
`IntChoice`), but then the decision changed (to use `Gen.bool()` instead), and now that predetermined `IntChoice` is
still there waiting to cause a type mismatch.

### What Selective Would Do Differently

If you had a Selective version instead of `flatMap`, the library would:

1. Notice that when `useInt=true`, the `Gen.bool()` branch is not used
2. **Not shrink** the right subtree while `Gen.bool()` is inactive
3. When the left side shrinks from `true` to `false`, the right subtree for `Gen.bool()` would still be undetermined (
   just a seed)
4. No type mismatch would occur

## Key Insight

**Monadic `flatMap` (bind)** allows full dependency between computations, which means:

- ✅ Maximum expressiveness
- ❌ Can create "impossible" shrink states (like your type mismatch)
- ❌ Both branches must be shrinkable at all times

**Selective** provides controlled dependency:

- ✅ Conditional computation without running unused branches
- ✅ Only shrinks the branches actually used
- ❌ Less expressive than monadic bind (but safer for most use cases)

Your test beautifully demonstrates why property-based testing libraries need tools like Selective Functors to avoid
these type mismatches during shrinking!

## Bottom Line

The TODO you hit is **not a bug** in your generator library—it's a real issue that occurs when using `flatMap` with
functions that return different generator types.

**What the Falsify library does:** Implements true Selective Functors, which provide conditional effect execution and
solve this problem elegantly through the type system.

**What you can do in Kotlin:** Provide practical combinators like `choose` and `oneOf` that:

- Prevent type mismatches through type constraints or fresh trees
- Solve the same practical problem
- Are NOT actually Selective Functors (which would require higher-kinded types)

The key tradeoff:

- Use `flatMap` when you need full monadic power and can ensure type safety yourself
- Use `choose`/`oneOf` when you need conditional generation between different configurations
- Understand that these are workarounds, not theoretical Selective Functors

**Does anything we've done relate to Selective Functors?**

Only in the sense that:

1. ✅ We encountered the same problem (type-switching during shrinking)
2. ✅ The Falsify library uses Selective Functors to solve this problem
3. ❌ We did NOT implement Selective Functors (impossible without HKTs)
4. ✅ We implemented practical workarounds that solve similar problems
5. ❌ Our solutions don't have the theoretical properties of Selective Functors
6. ❌ We don't have conditional effect execution, just careful tree management

**Selective Functors** are a type class with specific laws and properties. What we've done is create **combinators that
avoid the type-switching problem** through different means (type constraints and fresh tree generation).

---

## Implementing Selective-Like Functionality in Kotlin

While Kotlin doesn't have native support for higher-kinded types (which makes implementing the `Selective` type class
impossible), you can still provide **combinators that solve similar problems** without actually being Selective
Functors.

**Important:** None of the approaches below implement true Selective Functors. They are practical workarounds that:

- Solve the specific problem of conditional generation
- Prevent the type-switching issue
- Don't actually implement the `select` operation or the Selective laws

### Approach 1: Add a `choose` Combinator

A combinator that chooses between two generators without type mismatches:

```kotlin
/**
 * Chooses between two generators based on a boolean generator.
 * The key difference from flatMap is that this combinator ensures only the
 * chosen branch gets shrunk, avoiding the type mismatch issue.
 *
 * Shrinks towards the first generator.
 */
fun <T> Gen.Companion.choose(condition: Gen<Boolean>, ifTrue: Gen<T>, ifFalse: Gen<T>): Gen<T> =
    CombinatorGenerator { tree ->
        val (useFirst, conditionShrinks) = condition.generate(tree.left)
        
        // Generate from the chosen branch using tree.right
        val (value, valueShrinks) = if (useFirst) {
            ifTrue.generate(tree.right)
        } else {
            ifFalse.generate(tree.right)
        }
        
        // Combine shrinks carefully:
        // 1. Shrinks from the condition (which might flip the choice)
        // 2. Shrinks from the chosen generator (but NOT from the unchosen one!)
        val shrinks = sequence {
            // First, try shrinking the condition
            conditionShrinks.forEach { shrunkConditionTree ->
                yield(tree.withLeft(shrunkConditionTree))
            }
            
            // Then, try shrinking the value from the chosen branch
            // These shrinks maintain the current condition
            valueShrinks.forEach { shrunkValueTree ->
                yield(tree.withRight(shrunkValueTree))
            }
        }
        
        GenResult(value, shrinks)
    }
```

**Usage:**

```kotlin
// Instead of:
Gen.bool().flatMap { useInt ->
    if (useInt) Gen.int(0..10) else Gen.bool()  // ❌ Type mismatch during shrinking
}

// Use:
Gen.choose(
    condition = Gen.bool(),
    ifTrue = Gen.int(0..10),
    ifFalse = Gen.int(100..200)  // ✅ Same type, no mismatch possible
)
```

**Key insight:** By restricting both branches to the same type `T`, the type system prevents you from creating the
problematic type-switching scenario.

### Approach 2: Conditional Generation with Lazy Branches

For cases where you need the decision to influence what gets generated (but not the type), you can create a combinator
that defers the generator creation:

```kotlin
/**
 * Conditionally generates based on a boolean, but both branches must return the same type.
 * The generator functions are only called for the branch actually used.
 */
fun <T> Gen<Boolean>.selectGen(ifTrue: () -> Gen<T>, ifFalse: () -> Gen<T>): Gen<T> =
    CombinatorGenerator { tree ->
        val (condition, conditionShrinks) = generate(tree.left)
        
        // Only create and use the generator for the chosen branch
        val chosenGen = if (condition) ifTrue() else ifFalse()
        val (value, valueShrinks) = chosenGen.generate(tree.right)
        
        val shrinks = sequence {
            // Shrink the condition
            conditionShrinks.forEach { yield(tree.withLeft(it)) }
            
            // Shrink the value
            valueShrinks.forEach { yield(tree.withRight(it)) }
        }
        
        GenResult(value, shrinks)
    }
```

**Usage:**

```kotlin
Gen.bool().selectGen(
    ifTrue = { Gen.int(0..10) },
    ifFalse = { Gen.int(100..200) }
)
```

### Approach 3: Multi-Way Selection

For choosing between more than two options, you can use an enum or sealed class:

```kotlin
enum class GeneratorChoice { SMALL, MEDIUM, LARGE }

fun <T> Gen<GeneratorChoice>.select(
    small: Gen<T>,
    medium: Gen<T>,
    large: Gen<T>
): Gen<T> = CombinatorGenerator { tree ->
    val (choice, choiceShrinks) = generate(tree.left)
    
    val chosenGen = when (choice) {
        GeneratorChoice.SMALL -> small
        GeneratorChoice.MEDIUM -> medium
        GeneratorChoice.LARGE -> large
    }
    
    val (value, valueShrinks) = chosenGen.generate(tree.right)
    
    val shrinks = sequence {
        choiceShrinks.forEach { yield(tree.withLeft(it)) }
        valueShrinks.forEach { yield(tree.withRight(it)) }
    }
    
    GenResult(value, shrinks)
}
```

#### The `oneOf` Combinator

A particularly useful variant is `oneOf`, which takes a variable number of generators and picks one uniformly:

```kotlin
/**
 * Chooses uniformly between the provided generators.
 * Shrinks towards the first generator in the list.
 *
 * @param gens Variable number of generators to choose from. Must not be empty.
 * @return A generator that randomly selects one of the provided generators.
 */
fun <T> Gen.Companion.oneOf(vararg gens: Gen<T>): Gen<T> {
    require(gens.isNotEmpty()) { "oneOf requires at least one generator" }
    
    if (gens.size == 1) return gens[0]
    
    return CombinatorGenerator { tree ->
        // Generate an index to choose which generator to use
        val (index, indexShrinks) = Gen.int(0 until gens.size).generate(tree.left)
        
        // Use the chosen generator
        val chosenGen = gens[index]
        val (value, valueShrinks) = chosenGen.generate(tree.right)
        
        // Combine shrinks:
        // 1. Shrinking the index (moves towards 0, i.e., the first generator)
        // 2. Shrinking the value from the chosen generator
        val shrinks = sequence {
            indexShrinks.forEach { shrunkIndexTree ->
                yield(tree.withLeft(shrunkIndexTree))
            }
            
            valueShrinks.forEach { shrunkValueTree ->
                yield(tree.withRight(shrunkValueTree))
            }
        }
        
        GenResult(value, shrinks)
    }
}
```

**Usage:**

```kotlin
// Generate either a small, medium, or large integer
val gen = Gen.oneOf(
    Gen.int(0..10),      // small - shrinks towards this
    Gen.int(50..100),    // medium
    Gen.int(500..1000)   // large
)

// Generate different kinds of strings
val stringGen = Gen.oneOf(
    Gen.constant(""),
    Gen.constant("hello"),
    Gen.constant("world"),
    Gen.constant("a".repeat(100))
)
```

**Key properties of `oneOf`:**

1. **Type safety**: All generators must return the same type `T`
2. **Uniform selection**: Each generator has equal probability of being chosen initially
3. **Shrinks towards first**: The index generator shrinks towards 0, so failures will try to reproduce with the first
   generator
4. **Independent shrinking**:
    - First tries shrinking which generator is chosen (index shrinks)
    - Then tries shrinking the value within the chosen generator
    - Never tries to use shrink values from a different generator

**Why `oneOf` is safe:**

Unlike `flatMap` with type-switching, `oneOf`:

- Ensures all branches produce the same type (enforced at compile time)
- Uses an integer index to choose the generator, not a boolean that could flip types
- The index and value shrink independently but compatibly
- When the index shrinks (changing which generator is chosen), the value tree is reset to undetermined, not reused with
  a predetermined value from a different generator

**Weighted variant:**

You can also implement a weighted version:

```kotlin
/**
 * Chooses between generators with specified weights.
 * Shrinks towards generators with higher weights (earlier in the list).
 */
fun <T> Gen.Companion.frequency(vararg weightedGens: Pair<Int, Gen<T>>): Gen<T> {
    require(weightedGens.isNotEmpty()) { "frequency requires at least one generator" }
    require(weightedGens.all { it.first > 0 }) { "All weights must be positive" }
    
    val totalWeight = weightedGens.sumOf { it.first }
    
    return CombinatorGenerator { tree ->
        // Generate a random value in [0, totalWeight)
        val (randomValue, randomShrinks) = Gen.int(0 until totalWeight).generate(tree.left)
        
        // Find which generator this corresponds to
        var acc = 0
        var chosenIndex = 0
        for ((i, pair) in weightedGens.withIndex()) {
            acc += pair.first
            if (randomValue < acc) {
                chosenIndex = i
                break
            }
        }
        
        val chosenGen = weightedGens[chosenIndex].second
        val (value, valueShrinks) = chosenGen.generate(tree.right)
        
        val shrinks = sequence {
            randomShrinks.forEach { yield(tree.withLeft(it)) }
            valueShrinks.forEach { yield(tree.withRight(it)) }
        }
        
        GenResult(value, shrinks)
    }
}
```

**Usage:**

```kotlin
// Generate mostly small numbers, occasionally large ones
val gen = Gen.frequency(
    10 to Gen.int(0..10),      // 10x more likely
    1 to Gen.int(1000..10000)  // rare
)
```

### Approach 4: Implement a `select`-like Operation

If you want to closely mirror the **API** of Haskell's `select` (but not actually be a Selective Functor):

```kotlin
/**
 * A select-like branching operation (NOT a true Selective Functor instance).
 * 
 * This mimics the API of select but doesn't satisfy the Selective laws or provide
 * true conditional effect execution. It's just a convenient branching combinator.
 */
fun <A, B> Gen<Either<A, B>>.selectLike(handler: Gen<(A) -> B>): Gen<B> =
    CombinatorGenerator { tree ->
        val (either, eitherShrinks) = generate(tree.left)
        
        when (either) {
            is Either.Left -> {
                // Need to run the handler
                val (fn, fnShrinks) = handler.generate(tree.right)
                val result = fn(either.value)
                
                val shrinks = sequence {
                    // Shrink the either (might become Right, skipping handler)
                    eitherShrinks.forEach { yield(tree.withLeft(it)) }
                    
                    // Shrink the handler function
                    fnShrinks.forEach { yield(tree.withRight(it)) }
                }
                
                GenResult(result, shrinks)
            }
            
            is Either.Right -> {
                // Don't run the handler, and don't shrink tree.right!
                val shrinks = sequence {
                    // Only shrink the either (the handler tree remains untouched)
                    eitherShrinks.forEach { yield(tree.withLeft(it)) }
                }
                
                GenResult(either.value, shrinks)
            }
        }
    }

// Helper
sealed class Either<out L, out R> {
    data class Left<L>(val value: L) : Either<L, Nothing>()
    data class Right<R>(val value: R) : Either<Nothing, R>()
}

// Then you can implement ifS on top:
fun <T> Gen.Companion.ifS(condition: Gen<Boolean>, ifTrue: Gen<T>, ifFalse: Gen<T>): Gen<T> =
    condition.map { if (it) Either.Left(Unit) else Either.Right(ifFalse.sample()) }
        .select(Gen.constant { ifTrue.sample() })
```

### What Should You Choose?

**For your current codebase:**

1. **Start with Approach 1 (`choose`)** - It's simple, type-safe, and solves the most common case
2. **Add Approach 2 (`selectGen`)** if you need lazily-created generators
3. **Consider Approach 3 (`oneOf`)** for multi-way choice (which you've already implemented)
4. **Avoid Approach 4** unless you specifically want an Either-based API for aesthetic reasons

### The Key Difference from `flatMap`

All these approaches share a critical property that `flatMap` lacks:

**They prevent type mismatches by ensuring compatible types across branches.**

This is achieved through:

- **Type constraints**: All branches must return the same type `T`
- **Fresh trees**: When switching branches, use fresh undetermined subtrees
- **NOT through Selective Functors** - we don't have true conditional effect execution

With `flatMap`:

- Function can return different generator types → different Choice types in tree → 💥

With `choose`/`oneOf`:

- All branches return same type `T` → compatible Choice types → ✅
- OR fresh trees when switching → no predetermined values from wrong type → ✅

**Note:** These are practical workarounds, not theoretical Selective Functors. The Falsify library in Haskell can
implement true Selective because:

1. Haskell has higher-kinded types
2. Haskell can implement the `Selective` type class
3. The `select` operation can truly skip effects (not just avoid type mismatches)

### Testing Your Implementation

You can verify your selective combinator works by adapting your test:

```kotlin
@Test
fun `choose does not hit type mismatch TODO`() {
    // This SHOULD work without hitting the TODO
    val gen = Gen.choose(
        condition = Gen.bool(),
        ifTrue = Gen.constant(42),      // Both return Int
        ifFalse = Gen.constant(100)
    )
    
    val seed = generateSequence(0L) { it + 1 }
        .first { ValueTree.fromSeed(it).left.value.bool() }
    
    val tree = ValueTree.fromSeed(seed)
    val (_, shrinks) = gen.generate(tree)
    
    val rightShrink = shrinks.drop(1).first()
    val (_, nestedShrinks) = gen.generate(rightShrink)
    
    // This should NOT throw - no type mismatch possible!
    nestedShrinks.forEach { gen.generate(it) }
}
```

### Additional Considerations

**Shrink Direction:** Consider allowing users to specify which branch shrinks toward:

```kotlin
enum class ShrinkDirection { FIRST, SECOND }

fun <T> Gen.Companion.choose(
    condition: Gen<Boolean>,
    ifTrue: Gen<T>,
    ifFalse: Gen<T>,
    shrinkTo: ShrinkDirection = ShrinkDirection.FIRST
): Gen<T> = // ... implementation that biases shrinking
```

**Performance:** The selective approach may be slightly slower during shrinking (more careful bookkeeping), but it's
safer and more predictable.

**Documentation:** Make it clear that:

- Use `flatMap` when you need full monadic power and can ensure type safety
- Use `choose`/`select` when you need conditional generation with different configurations
- Never use `flatMap` with functions that return different generator types (or expect TODOs)

By providing these selective-style combinators alongside your existing `flatMap`, you give users:

1. **Safety** when they need conditional generation
2. **Power** when they need true monadic dependencies
3. **Clear guidance** on when to use which

