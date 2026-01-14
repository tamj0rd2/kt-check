package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.CombinerContext
import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.core.ProducerTree

internal data class CombinerGeneratorV2<T>(
    private val block: (CombinerContext) -> T,
) : GenV2<T>() {
    override fun generate(tree: ProducerTree): GenResultV2<T> {
        val context = CombinerContextV2(tree)
        val value = block(context)
        val shrinks = combineShrinks(context.results)
        return GenResultV2(value, shrinks)
    }

    private inner class CombinerContextV2(
        private var tree: ProducerTree,
    ) : CombinerContext {
        val results = mutableListOf<GenResultV2<*>>()

        override fun <T> Gen<T>.bind(): T {
            val result = (this as GenV2<T>).generate(tree.left)
            tree = tree.right
            results.add(result)
            return result.value
        }

    }

    private class ShrinkingCombinerContext(
        private val originalResults: List<GenResultV2<*>>,
        private val replacementIndex: Int,
        private val replacementResult: GenResultV2<*>,
    ) : CombinerContext {
        private var currentIndex = 0
        val results = mutableListOf<GenResultV2<*>>()

        override fun <T> Gen<T>.bind(): T {
            @Suppress("UNCHECKED_CAST")
            val result = if (currentIndex == replacementIndex) {
                replacementResult as GenResultV2<T>
            } else {
                originalResults[currentIndex] as GenResultV2<T>
            }

            results.add(result)
            currentIndex++
            return result.value
        }
    }

    private fun combineShrinks(results: MutableList<GenResultV2<*>>): Sequence<GenResultV2<T>> =
        results.indices.asSequence().flatMap { index ->
            results[index].shrinks.map { shrunkResult ->
                val context = ShrinkingCombinerContext(
                    originalResults = results,
                    replacementIndex = index,
                    replacementResult = shrunkResult
                )

                GenResultV2(
                    value = block(context),
                    shrinks = combineShrinks(context.results)
                )
            }
        }
}
