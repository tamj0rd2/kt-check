package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import kotlin.reflect.KClass

internal class ExceptionIgnoringGen<T>(
    private val gen: GenImpl<T>,
    private val threshold: Int,
    private val klass: KClass<out Throwable>,
) : GenImpl<T>() {
    override fun generate(tree: RandomTree): GenResultV2<T> {
        var lastFailure: Throwable? = null

        return generateSequence(tree) { it.right }
            .take(threshold)
            .mapIndexedNotNull { index, offsetTree ->
                val result = catchException { gen.generate(offsetTree.left) }.getOrElse {
                    lastFailure = it
                    return@mapIndexedNotNull null
                }

                GenResultV2(
                    value = result.value,
                    shrinks = result.shrinks.map { tree.replaceLeftAtOffset(index, it) },
                )
            }
            .firstOrNull()
            ?: throw GenerationException.FilterLimitReached(threshold, lastFailure)
    }

    private fun catchException(block: () -> GenResultV2<T>): Result<GenResultV2<T>> {
        return runCatching(block).onFailure { if (!klass.isInstance(it)) throw it }
    }
}
