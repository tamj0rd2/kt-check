package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenerationException
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.asResultOr
import dev.forkhandles.result4k.valueOrNull
import kotlin.reflect.KClass

internal class IgnoreExceptionGen<T>(
    private val wrappedGen: Generator<T>,
    private val klass: KClass<out Exception>,
    private val threshold: Int,
) : Generator<T> {
    override fun generate(root: RandomTree): Result4k<GeneratedValue<T>, GenerationException> {
        var latestError: Exception? = null

        return generateSequence(root) { it.right }
            .take(threshold)
            .mapNotNull {
                try {
                    wrappedGen.generate(it.left).valueOrNull()
                } catch (e: Exception) {
                    if (!klass.isInstance(e)) throw e
                    latestError = e
                    null
                }
            }
            .firstOrNull()
            .asResultOr { GenerationException.FilterLimitReached(threshold, latestError) }
    }

    override fun edgeCases(root: RandomTree): List<GeneratedValue<T>> {
        return emptyList()
    }
}
