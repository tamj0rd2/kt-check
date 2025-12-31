package com.tamj0rd2.ktcheck.genv2

/**
 * A generator that chooses between multiple generators using an index.
 *
 * This implementation prevents type mismatches when generators produce different types
 * (cast to a common supertype) by using fresh trees when switching generators:
 * - When shrinking the value within the same generator, we keep the predetermined values
 * - When shrinking the index to switch generators, we use a fresh undetermined tree
 *   to avoid passing predetermined values from one generator type to another
 *
 * Tree structure visualization:
 * ```
 * tree
 * ├─ left (used for: index generation)
 * │  ├─ left: Undetermined(seed=A)
 * │  └─ right: Undetermined(seed=B)
 * └─ right (used for: value generation)
 *    ├─ left: Undetermined(seed=C) ← tree.right.left - FRESH, UNUSED!
 *    └─ right: Undetermined(seed=D)
 * ```
 *
 * When index shrinks (switching generators), we use tree.right.left as a fresh tree
 * for the new generator, ensuring it doesn't encounter predetermined values from the
 * previous generator (which could be a different type).
 *
 * Note: This is NOT an implementation of Selective Functors (which would require
 * higher-kinded types). It's a practical workaround for the type-switching problem.
 */
private class OneOfGenerator<T>(
    private val gens: List<Gen<T>>,
) : Gen<T>() {
    init {
        require(gens.isNotEmpty()) { "oneOf requires at least one generator" }
    }

    override fun generate(tree: ValueTree): GenResult<T> {
        val (index, indexShrinks) = Gen.int(0..<gens.size).generate(tree.left)
        val (value, valueShrinks) = gens[index].generate(tree.right)

        val shrinks = sequence {
            // FRESH TREE STRATEGY: When the index shrinks (switching generators)
            //
            // Example scenario:
            // - Original: index=1 (Gen.int) generates Int(7)
            // - Value shrinks: Int(7) → Int(3), tree.right has Predetermined(IntChoice(3))
            // - Index shrinks: index=1 → index=0 (switching to Gen.bool)
            //
            // THE PROBLEM if we just do tree.withLeft(shrunkIndexTree):
            // - New tree has: left=IntChoice(0), right=IntChoice(3)
            // - Gen.bool tries to read from right which has IntChoice → 💥 TODO!
            //
            // THE SOLUTION using tree.right.left:
            // - tree.right.left is an undetermined subtree that hasn't been touched
            // - It's a "fresh" tree derived from the original seed structure
            // - Gen.bool can safely read from it (it's Undetermined, not IntChoice)
            //
            // CONSEQUENCE: When switching generators, we lose shrink progress on the value.
            // The new generator starts with a fresh value, not a continuation of shrinks.
            // This is the trade-off: safety (no type mismatches) vs optimal shrinking.
            //
            // NOTE: This is NOT implementing Selective Functors - it's a practical workaround.
            indexShrinks.forEach { shrunkIndexTree ->
                val freshRightTree = tree.right.left
                yield(shrunkIndexTree.withRight(freshRightTree))
            }

            // Value shrinks keep the same index (same generator) so we can safely
            // keep the predetermined values in the right tree
            valueShrinks.forEach { shrunkValueTree ->
                yield(tree.withRight(shrunkValueTree))
            }
        }

        return GenResult(value = value, shrinks = shrinks)
    }
}

fun <T> Gen.Companion.oneOf(vararg gens: Gen<T>): Gen<T> = OneOfGenerator(gens.toList())
