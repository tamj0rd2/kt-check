package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asSuccess
import dev.forkhandles.result4k.onFailure

internal sealed class AbstractListGen<T>(
    protected val sizeGen: Generator<Int>,
    protected val elementGen: Generator<T>,
) : Generator<List<T>> {
    final override fun generate(root: ProviderTree): Result4k<GeneratedValue<List<T>>, GenerationException> {
        val sizeResult = sizeGen.generate(root.left).onFailure { return it }
        val listElementResults = generateElements(root.right, sizeResult.value).onFailure { return it }
        return buildResult(root, sizeResult, listElementResults).asSuccess()
    }

    protected fun buildResult(
        root: ProviderTree,
        sizeResult: GeneratedValue<Int>,
        listElementResults: List<GeneratedValue<T>>,
    ): GeneratedValue<List<T>> {
        val sizeShrinks = sizeResult.shrinks.flatMap { sizeShrink ->
            sequence {
                val removeElementsFromTail = root.withSizeTree(sizeShrink)
                yield(removeElementsFromTail)

                val newSize = sizeGen.generate(sizeShrink).onFailure { return@sequence }.value
                val removeElementsFromHead = root
                    .withSizeTree(sizeShrink)
                    .withElementTrees(listElementResults.takeLast(newSize))
                yield(removeElementsFromHead)
            }
        }

        val elementBasedShrinks = listElementResults.asSequence().flatMapIndexed { index, elementResult ->
            elementResult.shrinks.map { shrink ->
                root.withElementTrees(listElementResults.map { it.usedTree }.replaceAtIndex(index, shrink))
            }
        }

        return GeneratedValue(
            value = listElementResults.map { it.value },
            shrinks = sizeShrinks + elementBasedShrinks,
            usedTree = root,
        )
    }

    protected abstract fun generateElements(
        initialTree: ProviderTree,
        size: Int,
    ): Result4k<List<GeneratedValue<T>>, GenerationException>

    protected fun ProviderTree.withSizeTree(sizeShrink: ProviderTree) = withLeft(sizeShrink)

    @JvmName("withElementResults")
    protected fun ProviderTree.withElementTrees(elementResults: List<GeneratedValue<T>>) =
        withElementTrees(elementResults.map { it.usedTree })

    protected fun ProviderTree.withElementTrees(elementTrees: List<ProviderTree>): ProviderTree {
        if (elementTrees.isEmpty()) return this

        // todo: make this tail recursive.
        fun ProviderTree.replaceLeftTree(index: Int): ProviderTree = when {
            index >= elementTrees.size -> {
                ProviderTree.terminal
            }

            else -> {
                val newTree = elementTrees[index]
                withLeft(newTree).withRight(right.replaceLeftTree(index + 1))
            }
        }

        return withRight(replaceLeftTree(0))
    }

    protected fun <T> List<T>.replaceAtIndex(index: Int, replacement: T): List<T> =
        toMutableList().apply { set(index, replacement) }
}
