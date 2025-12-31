# What We Actually Did vs. Selective Functors

## TL;DR

**We did NOT implement Selective Functors.** We implemented a shrinking strategy that avoids type mismatches when
switching between generators of different types.

## What Are Selective Functors?

Selective Functors are a type class between Applicative and Monad:

```haskell
class Applicative f => Selective f where
    select :: f (Either a b) -> f (a -> b) -> f b
```

**Key properties:**

1. If the first computation produces `Right b`, the second computation **is not executed at all**
2. This is about **conditional effect execution**, not shrinking
3. They satisfy specific laws (identity, distributivity, associativity, etc.)
4. They enable static analysis - you can inspect what effects might occur without running the computation

**Example:**

```haskell
-- If condition is Right b, skip the expensive computation entirely
ifS :: Selective f => f Bool -> f a -> f a -> f a
ifS condition thenBranch elseBranch = 
    select (fmap selector condition) (fmap selector' (thenBranch, elseBranch))
```

## What We Actually Did

### Problem: Type-Switching in `flatMap`

```kotlin
Gen.bool().flatMap { useInt ->
    if (useInt) Gen.int(0..10) else Gen.bool()
}
```

During shrinking:

- Original: bool=true, Gen.int generates Int(7), creates tree with IntChoice(7)
- Shrink: bool→false, Gen.bool tries to read tree with IntChoice → 💥 TODO

### Solution 1: Type Constraints (in `oneOf`)

```kotlin
fun <T> Gen.Companion.oneOf(vararg gens: Gen<T>): Gen<T>
```

All generators must return the same type `T` → compiler prevents type mismatches.

### Solution 2: Fresh Trees (in `oneOf` implementation)

When switching generators, use a fresh undetermined tree:

```kotlin
indexShrinks.forEach { shrunkIndexTree ->
    val freshRightTree = tree.right.left  // Fresh, undetermined
    yield(shrunkIndexTree.withRight(freshRightTree))
}
```

This prevents passing IntChoice to a BoolGenerator.

## Comparison Table

| Aspect                     | Selective Functors                    | What We Did                           |
|----------------------------|---------------------------------------|---------------------------------------|
| **Type Class**             | Yes - requires HKTs                   | No - just regular functions           |
| **Laws**                   | Has formal laws                       | No formal laws                        |
| **Conditional Effects**    | Can skip effects entirely             | Always execute, just use fresh trees  |
| **Static Analysis**        | Enables inspection without execution  | No static analysis capability         |
| **Problem Solved**         | General conditional computation       | Specific: type-switching in shrinking |
| **Implementation**         | Requires language support (Haskell)   | Practical workaround in Kotlin        |
| **Theoretical Foundation** | Category theory, applicative functors | Ad-hoc solution to practical problem  |

## The Falsify Library Connection

The [Well-Typed article](https://www.well-typed.com/blog/2023/04/falsify/) discusses how the **Falsify library uses
Selective Functors** for property-based testing:

1. Falsify's `Gen` is an instance of `Selective`
2. It provides `choose` built on the `select` operation
3. When the boolean is `False`, the unused generator is **not inspected for shrinking**
4. This is true conditional execution - the library literally skips processing the unused branch

**This is fundamentally different from what we did:**

- We always execute both generators during generation
- We just use fresh trees to avoid type mismatches
- We don't have true conditional execution or the ability to skip branches

## Why The Confusion?

The confusion arose because:

1. **Same motivation**: Both Selective Functors (in Falsify) and our solution address problems with conditional
   generation and shrinking
2. **Similar-sounding goal**: "Don't use shrink state from inactive branches" sounds like what Selective does
3. **Terminology**: I incorrectly called our approach "selective semantics"

But the mechanisms are completely different:

- **Selective Functors**: Skip executing/inspecting the second computation when not needed (deep, theoretical)
- **Our Solution**: Always execute, but use type constraints and fresh trees (shallow, practical)

## What We Should Call It

Our implementation is better described as:

> **A fresh-tree strategy for multi-generator shrinking**
>
> When switching between generators (via index shrinking), we use an unused subtree to ensure the new generator receives
> an undetermined tree rather than predetermined values from a different type.

Or simply:

> **Type-safe generator switching through fresh trees**

## Can We Implement Selective in Kotlin?

**Short answer: No, not really.**

Selective requires:

1. Higher-kinded types (to define `class Selective f where ...`)
2. The ability to define type classes
3. Language support for effect skipping

Kotlin has none of these. We can:

- ✅ Create functions with similar signatures
- ✅ Solve similar practical problems
- ❌ Implement the actual Selective type class
- ❌ Get the theoretical benefits (laws, static analysis, etc.)

## Conclusion

**Does anything we've done actually relate to Selective Functors?**

**Tangentially, but not directly:**

- ✅ We encountered a problem that Selective Functors elegantly solve (in Haskell)
- ✅ We read about Selective Functors in the context of property-based testing
- ✅ We implemented a practical workaround for a similar problem
- ❌ We did NOT implement Selective Functors
- ❌ Our solution doesn't have the properties of Selective Functors
- ❌ Our solution is not theoretically grounded in the same way

It's like seeing someone use a backhoe to dig a hole, then digging your own hole with a shovel. You solved the same
problem (making a hole), but you're using completely different tools with different capabilities and limitations.

**The relationship:** Inspired by the same problem domain, implemented with different techniques.

