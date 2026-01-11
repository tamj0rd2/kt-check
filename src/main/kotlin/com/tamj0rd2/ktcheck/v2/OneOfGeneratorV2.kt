package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.core.GenerationException.OneOfEmpty

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
internal data class OneOfGeneratorV2<T>(
    private val gens: List<GenV2<T>>,
) : GenV2<T>() {
    init {
        if (gens.isEmpty()) throw OneOfEmpty()
    }

    private val indexGen = int(0..<gens.size)

    override fun GenContextV2.generate(): GenResultV2<T> {
        val indexResult = indexGen.generate(producer)

        // generate this first so that it matches the way V1 OneOfGenerator works. temporary hack.
        val selectedResult = gens[indexResult.value].generate(producer)

        // Generate from ALL generators upfront and cache the results
        // This prevents value explosion during shrinking - when we switch generators,
        // we use the cached value that was generated with the original randomness,
        // rather than regenerating with potentially exhausted/different randomness
        val allResults = gens.mapIndexed { index, gen ->
            if (index == indexResult.value) selectedResult
            else gen.generate(producer)
        }

        // Build shrinks: first try switching generators (index shrinks), then shrink within generator (value shrinks)
        // Index shrinks use cached results to maintain determinism and prevent value explosion
        val indexShrinks = indexResult.shrinks.map { shrunkIndexResult -> allResults[shrunkIndexResult.value] }

        return GenResultV2(
            value = selectedResult.value,
            shrinks = indexShrinks + selectedResult.shrinks,
        )
    }
}
