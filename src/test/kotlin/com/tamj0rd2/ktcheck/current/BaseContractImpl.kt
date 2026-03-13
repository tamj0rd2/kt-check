package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.BaseContract
import com.tamj0rd2.ktcheck.contracts.GenResults
import com.tamj0rd2.ktcheck.contracts.repeatTest
import com.tamj0rd2.ktcheck.contracts.shrunkValues
import com.tamj0rd2.ktcheck.contracts.skipIteration
import com.tamj0rd2.ktcheck.contracts.value
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.Tree
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow
import dev.forkhandles.result4k.valueOrNull
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import kotlin.random.Random
import com.tamj0rd2.ktcheck.Gen as IGen

internal abstract class BaseContractImpl : BaseContract, GenBuilders by GenV2Builders {
    @Test
    fun `generated values are reproducible via their returned tree`() {
        repeatTest { seed ->
            val gen = getGenIfDefined() as Gen
            val originalResult = gen.generate(tree(seed), GenerationMode.Random).orThrow()
            val regenerated = gen.generate(originalResult.usedTree as Tree<*>)

            expectThat(regenerated).value.isEqualTo(originalResult.value)
        }
    }

    @Test
    open fun `shrinks of generated values are reproducible via their returned tree`() {
        runIfGenSupportsShrinking()

        repeatTest { seed ->
            val gen = getGenIfDefined() as Gen
            val originalResult = gen.generate(tree(seed), GenerationMode.Random).orThrow()
            val originalShrunkValues = originalResult.getShrinks(gen)
            if (originalShrunkValues.isEmpty()) skipIteration()

            val regenerated = gen.generate(originalResult.usedTree as Tree<*>)

            expectThat(regenerated).shrunkValues.isEqualTo(originalShrunkValues)
        }
    }

    @Test
    open fun `edge cases are reproducible via their returned tree`() {
        runIfGenSupportsEdgeCases()

        repeatTest { seed ->
            val gen = getGenIfDefined() as Gen
            val edgeCases = gen.edgeCases(tree(seed))

            val anEdgeCase = edgeCases.random(Random(seed.value))
            val originalShrunkValues = anEdgeCase.getShrinks(gen)
            val regenerated = gen.generate(anEdgeCase.usedTree as Tree<*>)

            expectThat(regenerated).value.isEqualTo(anEdgeCase.value)
            expectThat(regenerated).shrunkValues.isEqualTo(originalShrunkValues)
        }
    }

    @Test
    open fun `shrinks of edge cases are reproducible via their returned tree`() {
        runIfGenSupportsShrinking()
        runIfGenSupportsEdgeCases()

        repeatTest { seed ->
            val gen = getGenIfDefined() as Gen
            val edgeCases = gen.edgeCases(tree(seed))

            val anEdgeCase = edgeCases.random(Random(seed.value))
            val originalShrunkValues = anEdgeCase.getShrinks(gen)
            val regenerated = gen.generate(anEdgeCase.usedTree as Tree<*>)

            expectThat(regenerated).value.isEqualTo(anEdgeCase.value)
            expectThat(regenerated).shrunkValues.isEqualTo(originalShrunkValues)
        }
    }

    private fun <T> GeneratedValue<T>.getShrinks(
        gen: Gen<T>,
    ): List<T> = shrinks
        .mapNotNull { gen.generate(it, GenerationMode.Random).valueOrNull()?.value }
        .distinct()
        .toList()

    //=== Wiring ===//
    override fun tree(seed: Seed) = ProviderTree.new(seed)
    override fun Tree<*>.withLeft(left: Tree<*>) = (this as ProviderTree).withLeft(left as ProviderTree)
    override fun Tree<*>.withRight(right: Tree<*>) = (this as ProviderTree).withRight(right as ProviderTree)

    @Suppress("UNCHECKED_CAST")
    override fun <T> IGen<T>.generate(tree: Tree<*>): GenResults<T> {
        val result = (this as Gen).generate(tree as ProviderTree, GenerationMode.Random).orThrow()
        return GenResults(result.value, collectShrinksRecursively(result.shrinks))
    }

    override fun <T> IGen<T>.edgeCases(seed: Seed): List<GenResults<T>> {
        val result = (this as Gen).edgeCases((tree(seed)))
        return result.map { GenResults(it.value, collectShrinksRecursively(it.shrinks)) }
    }

    private fun <T> Gen<T>.collectShrinksRecursively(shrinks: Sequence<ProviderTree>): Sequence<GenResults<T>> =
        sequence {
            for (shrink in shrinks) {
                val result = generate(shrink, GenerationMode.Random).onFailure { continue }
                yield(GenResults(result.value, collectShrinksRecursively(result.shrinks)))
            }
        }
}
