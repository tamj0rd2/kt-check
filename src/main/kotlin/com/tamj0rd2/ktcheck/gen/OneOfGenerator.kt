package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.contract.GenerationException.OneOfEmpty

/**
 * A generator that chooses between multiple generators using an index. Shrinks towards
 * earlier specified generators.
 *
 * This implementation prevents type mismatches when generators produce different types
 * (cast to a common supertype) by using fresh trees when switching generators:
 * - When shrinking the value within the same generator, we keep the predetermined values
 * - When shrinking the index to switch generators, we use a fresh undetermined tree
 *   to avoid passing predetermined values from one generator type to another
 *
 * Consequently, when switching generators, we lose shrink progress on the value.
 * The new generator starts with a fresh value, not a continuation of shrinks.
 * This is the trade-off: safety (no type mismatches) vs optimal shrinking.
 **/
internal class OneOfGenerator<T>(
    private val gens: List<Gen<T>>,
) : Gen<T>() {
    init {
        if (gens.isEmpty()) throw OneOfEmpty()
    }

    override fun GenContext.generate(): GenResult<T> {
        val (index, indexShrinks) = int(0..<gens.size).generate(tree.left, mode)
        val (value, valueShrinks) = gens[index].generate(tree.right, mode)

        val shrinks = sequence {
            // allows switching between generators by shrinking the index with a fresh (undetermined) right tree
            yieldAll(indexShrinks.map { tree.withLeft(it).withRight(tree.right.right) })

            yieldAll(valueShrinks.map { tree.withRight(it) })
        }

        return GenResult(value = value, shrinks = shrinks)
    }
}
