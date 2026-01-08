# The Problem with Monadic Bind in Property-Based Testing

## Overview
When implementing property-based testing libraries that unify random generation and shrinking, the monadic `bind` operation creates a fundamental tension: while it works well for random generation, it significantly degrades shrinking quality.

## The Core Issue

### Why `map` Works
For simpler operations like `map`, we can maintain the structure of the value tree:
```kotlin
// map maintains shrink structure
map(func: Callable[[T], U], gen: Gen[T]) -> Gen[U]
```

The shrink tree is preserved, and values can be transformed while keeping shrinks intact.

### Why `bind` Fails: The Information Loss Problem

`bind` has this signature:
```kotlin
bind(func: Callable[[T], Gen[U]], gen: Gen[T]) -> Gen[U]
```

The fundamental problem: **`func` returns a new generator (`Gen[U]`), which itself contains random generation and a shrink tree. This shrink tree is *dependent on the specific T value* that was used to create it. When shrinking, we only have the final `U` value, not the original `T` that created it.**

#### During Generation (works fine)
1. Generate a `T` value from `gen`
2. Pass that `T` to `func` → get back `Gen[U]`
3. Generate a `U` value from that returned generator
4. Store the shrink tree along with `U`

#### During Shrinking (the problem)
You want to shrink the `U` value that failed. But to do so intelligently, you'd need:
- The original `T` value that created the generator
- The shrink tree from that specific generator

**You have neither.** You only have the final `U` value.

The `U` value was created by calling `func(some_original_T)`, but that original `T` is lost. You can't recover it from `U` — the transformation may not be reversible.

#### Why Regeneration Happens

Since you can't recover the original `T`, the only option is:

```kotlin
def bind(func: Callable[[T], Gen[U]], gen: Gen[T]) -> Gen[U]:
    def inner_bind(value: T) -> CandidateTree[U]:
        random_tree = func(value)
        return random_tree.generate()  // ← Must regenerate because the old tree is lost!
    return random_map(lambda tree: tree_bind(inner_bind, tree), gen)
```

When the outer value shrinks:
1. Shrink the `T` value tree
2. For each shrunk `T'`, call `func(T')` again to get a new generator
3. Generate a fresh `U` from that new generator — **this is random, not deterministic**

You can't reuse the original shrink tree because that tree was created in the context of the original `T`, and you've now changed the `T` to `T'`.

#### Comparison: Why `map` Works

With `map`, there's no new generator created:
```kotlin
map(f: T -> U, gen: Gen[T])
```

The shrink tree stays the same — you just transform each `T` value in the tree by applying `f`. You still have all the original `T` values, so you can intelligently shrink the `T` values, then apply `f` to get shrunk `U` values.

With `bind`, each `U` value carries its own separate shrink tree that's locked into a specific `T` context, and that context is lost once you have the final `U`.

## Consequences

### 1. Loss of Information
- Start with a list of 4 persons that fails the test
- When shrinking the list length to 3, a completely **new random list of 3 persons** is generated
- This new list might pass the test (by chance), breaking the shrinking process
- The original failing values that were specifically problematic are discarded

### 2. Inefficient Shrinking
- Binary search shrinking would work in ~15 steps, but with `bind` it takes ~127 steps
- Cannot use sophisticated algorithms (like binary chop for lists) when inner values are regenerated randomly
- Shrinking becomes partly non-deterministic

### 3. Constraint Violations
In the example with `simple_names = map("".join, list_of_length(6, letters))`:
- Generator respects the 6-letter constraint during generation
- If we used `bind` for list generation, shrinking might violate this invariant
- The shrunk names could become different lengths, leading to invalid test cases

## Why This Happens

The underlying issue is **mismatch between interfaces**:
- `map` transforms values in a way that preserves structure
- `bind` creates *entirely new* value-generation contexts
- Shrinking expects to have access to the original intermediate values, but `bind` only offers random re-generation

## Known Mitigations

Since this problem is well-known in the property-based testing community, libraries use:

1. **Avoid `bind` for common types**: Collections typically have bespoke, hand-written shrink functions optimized for their structure
2. **Prefer `map`/`mapN`**: Users are encouraged to use composition rather than dependent generation when possible
3. **Manual shrink overrides**: Allow users to implement custom shrink functions for specific types that use `bind`
4. **Education**: Document the trade-offs and help users understand when `bind` is problematic

## Conclusion

`bind` creates a fundamental limitation in integrated shrinking APIs: **randomly regenerating values during shrinking sacrifices the quality and speed of the shrinking process**. This is why production property-based testing libraries heavily favor bespoke shrinking strategies for complex types over relying on monadic bind.
