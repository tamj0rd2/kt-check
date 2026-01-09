package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.gen.OneOfEmpty
import com.tamj0rd2.ktcheck.v2.IntGenerator.Companion.int


/**
 * A generator that chooses between multiple generators using an index. Shrinks towards
 * earlier specified generators.
 *
 * Implementation uses eager caching: all generators are evaluated upfront during generation,
 * and their results are cached. When shrinking changes the generator choice (index shrinks),
 * we use the cached result rather than regenerating.
 *
 * This allows deterministic shrinking and preserves shrink progress across generator switches.
 * This design prioritises correctness and debuggability over performance, which is appropriate
 * for a testing library where reliability of shrinking is critical.
 **/
private class OneOfGenerator<T>(
    private val gens: List<Gen<T>>,
) : Gen<T>() {
    init {
        if (gens.isEmpty()) throw OneOfEmpty()
    }

    override fun GenContext.generate(): GenResult<T> {
        // Generate index to select which generator to use
        val indexGen = Gen.int(0..<gens.size)
        val indexResult = indexGen.generate(producer)

        // Generate from ALL generators upfront and cache the results
        // This prevents value explosion during shrinking - when we switch generators,
        // we use the cached value that was generated with the original randomness,
        // rather than regenerating with potentially exhausted/different randomness
        val allResults = gens.map { it.generate(producer) }

        // Select the result for the chosen index
        val selectedResult = allResults[indexResult.value]

        // Build shrinks: first try switching generators (index shrinks), then shrink within generator (value shrinks)
        // Index shrinks use cached results to maintain determinism and prevent value explosion
        val indexShrinks = indexResult.shrinks.map { shrunkIndexResult ->
            allResults[shrunkIndexResult.value]
        }

        return GenResult(
            value = selectedResult.value,
            shrinks = indexShrinks + selectedResult.shrinks,
        )
    }
}

/** Shrinks towards the first generator */
fun <T> Gen.Companion.oneOf(vararg gens: Gen<T>): Gen<T> = oneOf(gens.toList())

/** Shrinks toward the first generator */
fun <T> Gen.Companion.oneOf(gens: Collection<Gen<T>>): Gen<T> = OneOfGenerator(gens.toList())

/** Shrinks toward the first value. Individual values will not be shrunk. */
@JvmName("oneOfValues")
fun <T> Gen.Companion.oneOf(values: Iterable<T>): Gen<T> {
    val options = values.toList()
    if (options.isEmpty()) throw OneOfEmpty()
    return Gen.int(0..<options.size).map { options[it] }
}
