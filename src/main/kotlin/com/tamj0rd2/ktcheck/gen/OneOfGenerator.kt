package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.producer.ProducerTree

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
private class OneOfGenerator<T>(
    private val gens: List<Gen<T>>,
) : Gen<T>() {
    init {
        if (gens.isEmpty()) throw OneOfEmpty()
    }

    override fun generate(tree: ProducerTree): GenResult<T> {
        val (index, indexShrinks) = Gen.int(0..<gens.size).generate(tree.left)
        val (value, valueShrinks) = gens[index].generate(tree.right)

        val shrinks = sequence {
            // allows switching between generators by shrinking the index with a fresh (undetermined) right tree
            yieldAll(indexShrinks.map { tree.withLeft(it).withRight(tree.right.right) })

            yieldAll(valueShrinks.map { tree.withRight(it) })
        }

        return GenResult(value = value, shrinks = shrinks)
    }
}

/** Shrinks towards the first generator */
fun <T> Gen.Companion.oneOf(vararg gens: Gen<T>): Gen<T> = oneOf(gens.toList())

/** Shrinks toward the first generator */
fun <T> Gen.Companion.oneOf(gens: Collection<Gen<T>>): Gen<T> = OneOfGenerator(gens.toList())

/** Shrinks toward the first value. Individual values will not be shrunk. */
@JvmName("oneOfValues")
fun <T> Gen.Companion.oneOfValues(values: Iterable<T>): Gen<T> {
    val options = values.toList()
    if (options.isEmpty()) throw OneOfEmpty()
    return Gen.int(0..<options.size).map { options[it] }
}

@Suppress("unused")
class OneOfEmpty : GenerationException("Gen.oneOf() called with no generators")
