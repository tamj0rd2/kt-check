package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator
import com.tamj0rd2.ktcheck.core.ProducerTree

internal abstract class BaseGenTest : BaseGeneratorContract, GenBuilder by GenV1.Companion {
    override fun <T : Any> Gen<T>.generateWithShrunkValues(tree: ProducerTree): Pair<T, List<T>> {
        val (value, shrinks) = (this as GenV1<T>).generate(tree, GenMode.Initial)
        return value to shrinks.map { generate(it, GenMode.Shrinking).value }.toList()
    }

    override fun <T : Any> Gen<T>.navigateRecursiveShrinks(tree: ProducerTree): RecursiveShrinkNavigator<T> {
        val (value, shrinks) = (this as GenV1<T>).generate(tree, GenMode.Initial)
        return V1RecursiveShrinkNavigator(this, value, shrinks)
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
