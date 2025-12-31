# Understanding the Selective Implementation in OneOfGenerator (Lines 28-31)

## The Code in Question

```kotlin
indexShrinks.forEach { shrunkIndexTree ->
    val freshRightTree = tree.right.left
    yield(shrunkIndexTree.withRight(freshRightTree))
}
```

## What This Does: A Step-by-Step Trace

Let's trace through a concrete example with the test case where we have two generators of different types:

- `gens[0]` = `Gen.bool().map { it as Any }`  (produces Boolean)
- `gens[1]` = `Gen.int(0..10).map { it as Any }` (produces Int)

### Initial Generation

**Starting tree structure:**

```
tree
├─ left (used for index)
│  └─ value: Undetermined(seed=123)
└─ right (used for value generation)
   ├─ left: Undetermined(seed=456)
   └─ right: Undetermined(seed=789)
```

**Step 1: Generate index**

```kotlin
val (index, indexShrinks) = Gen.int(0..<gens.size).generate(tree.left)
// Suppose this generates: index = 1 (choosing Gen.int)
```

**Step 2: Generate value with gens[1] (the int generator)**

```kotlin
val (value, valueShrinks) = gens[1].generate(tree.right)
// This generates: value = 7 (an Int)
// And creates shrinks: valueShrinks with predetermined IntChoice
```

**After generation, we have:**

- Generated value: `7` (Int)
- Index shrinks: Trees where index shrinks towards 0
- Value shrinks: Trees where the int value shrinks (e.g., 7 → 0, 7 → 3, etc.)

### The Shrinking Process - Value Shrinks (Lines 34-36)

These are straightforward:

```kotlin
valueShrinks.forEach { shrunkValueTree ->
    yield(tree.withRight(shrunkValueTree))
}
```

This creates shrink trees like:

```
shrunkTree
├─ left: Undetermined(seed=123)  // Same as original - index unchanged
└─ right: Predetermined(IntChoice(3))  // Shrunk value
```

When we regenerate with this tree:

1. `tree.left.value.int(0..1)` → still gives index=1 (same generator)
2. `gens[1].generate(tree.right)` → reads IntChoice(3) ✅ Works fine!

### The Shrinking Process - Index Shrinks (Lines 28-31) - THE PROBLEMATIC CASE

Now let's look at what happens when the **index** shrinks.

**Without the selective fix (old code):**

```kotlin
// OLD CODE (problematic):
indexShrinks.forEach { shrunkIndexTree ->
    yield(tree.withLeft(shrunkIndexTree))
}
```

This would create:

```
badShrunkTree
├─ left: Predetermined(IntChoice(0))  // Index shrunk to 0 - choosing Gen.bool now!
└─ right: Undetermined(seed=789)  // Original tree.right - STILL HAS STRUCTURE FROM INT GEN
```

Wait, that's not quite right. Let me trace more carefully...

Actually, if we had FIRST done value shrinks, we'd have:

```
afterValueShrink
├─ left: Undetermined(seed=123)
└─ right: Predetermined(IntChoice(3))  // From value shrink
```

Then if we NEST another shrink and shrink the index:

```
nestedShrunk
├─ left: Predetermined(IntChoice(0))  // Index changed to 0!
└─ right: Predetermined(IntChoice(3))  // Still has IntChoice from before!
```

Now when we regenerate:

1. `tree.left.value.int(0..1)` → reads index=0 (choose gens[0])
2. `gens[0].generate(tree.right)` → Gen.bool tries to read from tree.right
3. `tree.right.value.bool()` → but tree.right has Predetermined(IntChoice(3))!
4. **💥 TODO: "handle non-bool choice IntChoice"**

**With the selective fix (new code):**

```kotlin
// NEW CODE (selective):
indexShrinks.forEach { shrunkIndexTree ->
    val freshRightTree = tree.right.left  // Get a fresh undetermined tree
    yield(shrunkIndexTree.withRight(freshRightTree))
}
```

## What is `tree.right.left`?

This is the key! Let's visualize the full tree structure:

```
tree
├─ left (used for index generation)
│  ├─ left: Undetermined(seed=...)
│  └─ right: Undetermined(seed=...)
└─ right (used for value generation)
   ├─ left: Undetermined(seed=456) ← THIS IS tree.right.left!
   └─ right: Undetermined(seed=789)
```

**`tree.right.left` is an undetermined subtree that hasn't been used yet!**

## What Happens With The Fix

When the index shrinks and we use the selective implementation:

```kotlin
val freshRightTree = tree.right.left  // Undetermined(seed=456)
yield(shrunkIndexTree.withRight(freshRightTree))
```

This creates:

```
selectiveShrunk
├─ left: Predetermined(IntChoice(0))  // Index changed to 0
└─ right: Undetermined(seed=456)  // FRESH tree from tree.right.left!
```

Now when we regenerate:

1. `tree.left.value.int(0..1)` → reads index=0 (choose gens[0])
2. `gens[0].generate(tree.right)` → Gen.bool generates from fresh tree
3. `tree.right.value.bool()` → reads from Undetermined(seed=456)
4. **✅ No TODO! Successfully generates a boolean!**

## The Consequences

### 1. **Prevents Type Mismatches**

By using a fresh undetermined tree when switching generators, we ensure that each generator gets a tree it can work
with, not predetermined values from a different generator type.

### 2. **Loses Shrink History When Switching Generators**

When the index shrinks (changing generators), we can't keep the shrunk value from the previous generator. We start fresh
with the new generator.

**Example:**

- Original: gens[1] generated Int(7)
- Value shrunk to Int(3)
- Index shrinks to 0 (switch to gens[0] for Bool)
- **Result: We get a fresh Bool, not a shrunk Bool**

This means we lose the shrinking progress on the value when we switch generators.

### 3. **Shrinking Still Works, Just Differently**

The shrinking process becomes:

- **Depth-first within same generator**: First shrink the value with the current generator
- **Then try different generators**: Once value shrinking is exhausted, try switching to other generators (which shrink
  towards gens[0])
- **Each generator starts fresh**: When you switch generators, you get a fresh value, not a continuation of shrinks

### 4. **Maintains Determinism**

By using `tree.right.left` (which is derived from the original seed), we maintain determinism:

- Same seed + same shrink path = same values
- The fresh tree is not random, it's derived from the tree structure

### 5. **Trade-off: Safety vs. Shrink Quality**

**With selective (current implementation):**

- ✅ Safe: No type mismatches
- ❌ Less optimal shrinking: Switching generators loses shrink progress

**With monadic flatMap:**

- ✅ Better shrinking: Can maintain values across generator switches
- ❌ Unsafe: Can hit type mismatches

## Visual Summary

```
BEFORE (Problematic):
Generation:    index=1 → Int(7)
Value shrink:  index=1 → Int(3)  
Index shrink:  index=0 → Bool generator tries to read Int(3) → 💥 TODO

AFTER (Selective):
Generation:    index=1 → Int(7)
Value shrink:  index=1 → Int(3)
Index shrink:  index=0 → Bool generator reads fresh tree → ✅ Bool(true)
```

## Key Insight

**Lines 28-31 implement a workaround for type safety in multi-type generators:**

> When switching generators (changing which generator is active based on an index), don't reuse predetermined values
> from the previous generator. Instead, use a fresh undetermined subtree for the new generator.

This prevents `Gen.bool()` from trying to read a tree with `IntChoice`, which would throw the TODO.

**This is NOT the same as Selective Functors**, which are about conditional effect execution, not shrinking strategies.

