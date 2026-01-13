package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.contracts.BaseGeneratorContract
import com.tamj0rd2.ktcheck.contracts.RecursiveShrinkNavigator
import com.tamj0rd2.ktcheck.core.ProducerTree

internal abstract class BaseGenV2Test : BaseGeneratorContract, GenBuilder by GenV2.Companion {
    override fun <T : Any> Gen<T>.generateWithShrunkValues(tree: ProducerTree): Pair<T, List<T>> {
        val (value, shrinks) = (this as GenV2<T>).generate(tree)
        return value to shrinks.map { it.value }.toList()
    }

    override fun <T : Any> Gen<T>.navigateRecursiveShrinks(tree: ProducerTree): RecursiveShrinkNavigator<T> {
        val result = (this as GenV2<T>).generate(tree)
        return V2RecursiveShrinkNavigator(result.value, result.shrinks)
    }

    companion object {
        internal fun <T> GenV2<T>.generateWithShrunkValues(tree: ProducerTree): Pair<T, List<T>> {
            val (value, shrinks) = generate(tree)
            return value to shrinks.map { it.value }.toList()
        }
    }
}

private class V2RecursiveShrinkNavigator<T>(
    override val value: T,
    private val shrinkResults: Sequence<GenResultV2<T>>,
) : RecursiveShrinkNavigator<T> {

    override fun getShrinks(limit: Int): List<RecursiveShrinkNavigator<T>> {
        return shrinkResults.take(limit).map { result ->
            V2RecursiveShrinkNavigator(result.value, result.shrinks)
        }.toList()
    }

    override fun findShrinkByValue(value: T, limit: Int): RecursiveShrinkNavigator<T>? {
        return shrinkResults.take(limit).firstNotNullOfOrNull { result ->
            if (result.value == value) {
                V2RecursiveShrinkNavigator(result.value, result.shrinks)
            } else {
                null
            }
        }
    }
}
