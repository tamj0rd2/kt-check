package com.tamj0rd2.ktcheck.gen

import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTree
import com.tamj0rd2.ktcheck.producer.ProducerTreeDsl.Companion.copy
import com.tamj0rd2.ktcheck.producer.Seed

internal abstract class BaseGenTest : BaseGeneratorContract, GenBuilder by GenV1.Companion {
    override fun <T : Any> Gen<T>.generateWithShrunkValues(rngValues: List<Any>): Pair<T, List<T>> {
        return (this as GenV1<T>).generateWithShrunkValues(ProducerTree.new().withValue(rngValues.single()))
    }

    override fun <T : Any> Gen<T>.generateWithShrunkValues(seed: Seed): Pair<T, List<T>> {
        return (this as GenV1<T>).generateWithShrunkValues(ProducerTree.new(seed))
    }

    override fun <T : Any> Gen<T>.navigateRecursiveShrinks(
        rngValues: List<Any>,
    ): RecursiveShrinkNavigator<T> {
        val tree = buildListTree(rngValues)
        return (this as GenV1<T>).navigateRecursiveShrinks(tree)
    }

    private fun <T> GenV1<T>.navigateRecursiveShrinks(tree: ProducerTree): RecursiveShrinkNavigator<T> {
        val (value, shrinks) = generate(tree, GenMode.Initial)
        return V1RecursiveShrinkNavigator(this, value, shrinks)
    }

    protected fun buildListTree(rngValues: List<Any>): ProducerTree = ProducerTree.new()
        .run {
            withLeft(left.withValue(rngValues.first()))
        }
        .run {
            val root = this
            val remainingValues = rngValues.drop(1)
            if (remainingValues.isEmpty()) return@run root

            val lastAffectedNode = root.traverseRight(rngValues.size - 1)
            remainingValues.foldRightIndexed(lastAffectedNode) { index, value, acc ->
                val updatedNode = acc.copy { left(value) }
                val updatedParentNode = root.traverseRight(index).withRight(updatedNode)
                updatedParentNode
            }
        }
}

private class V1RecursiveShrinkNavigator<T>(
    private val gen: GenV1<T>,
    override val value: T,
    private val shrinkTrees: Sequence<ProducerTree>,
) : RecursiveShrinkNavigator<T> {

    override fun getShrinks(limit: Int): List<RecursiveShrinkNavigator<T>> {
        return shrinkTrees.take(limit).map { tree ->
            val (shrunkValue, nestedShrinks) = gen.generate(tree, GenMode.Shrinking)
            V1RecursiveShrinkNavigator(gen, shrunkValue, nestedShrinks)
        }.toList()
    }

    override fun findShrinkByValue(value: T, limit: Int): RecursiveShrinkNavigator<T>? {
        return shrinkTrees.take(limit).firstNotNullOfOrNull { tree ->
            val (shrunkValue, nestedShrinks) = gen.generate(tree, GenMode.Shrinking)
            if (shrunkValue == value) {
                V1RecursiveShrinkNavigator(gen, shrunkValue, nestedShrinks)
            } else {
                null
            }
        }
    }
}
