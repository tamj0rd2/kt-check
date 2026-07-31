package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import kotlin.random.Random
import kotlin.reflect.KClass

@ConsistentCopyVisibility
data class GenV2<T> internal constructor(
    internal val generate: (Choices) -> T,
) : Gen<T> {
    override fun sample(seed: Long): T {
        return generate(RandomChoices(Random(seed)))
    }

    override fun <T2, R> combineWith(nextGen: Gen<T2>, combine: (T, T2) -> R): Gen<R> {
        TODO("Not yet implemented")
    }

    override fun distinctList(size: IntRange): Gen<List<T>> {
        TODO("Not yet implemented")
    }

    override fun filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> {
        TODO("Not yet implemented")
    }

    override fun <R> flatMap(fn: (T) -> Gen<R>): Gen<R> {
        TODO("Not yet implemented")
    }

    override fun ignoreExceptions(klass: KClass<out Exception>, threshold: Int): Gen<T> {
        TODO("Not yet implemented")
    }

    override fun list(size: IntRange): Gen<List<T>> {
        TODO("Not yet implemented")
    }

    override fun <R> map(fn: (T) -> R): Gen<R> = GenV2 { fn(generate(it)) }

    companion object : GenBuilders {
        override fun <T> constant(value: T): Gen<T> {
            TODO("Not yet implemented")
        }

        override fun int(
            range: IntRange,
            shrinkTarget: Int,
        ): Gen<Int> {
            require(shrinkTarget in range) { "shrinkTarget $shrinkTarget not in range $range" }
            return GenV2 { it.int(IntegerConstraints(range, shrinkTarget)) }
        }

        override fun long(): Gen<Long> {
            TODO("Not yet implemented")
        }

        fun <T> builder(block: GenBuilderContext.() -> T): Gen<T> = GenV2 { block(GenBuilderContext(it)) }
    }
}

class GenBuilderContext internal constructor(private val choices: Choices) {
    fun <T> Gen<T>.bind(): T {
        check(this is GenV2) { "${this@bind::class.java} incompatible with ${choices::class.java}" }
        return generate(choices)
    }
}

internal data class IntegerConstraints(
    val range: IntRange,
    val shrinkTarget: Int,
) {
    init {
        require(shrinkTarget in range) { "shrinkTarget $shrinkTarget not in range $range" }
    }
}

fun main() {
    // example user defined gen
    data class Account(val age: Int)

    val accountGen = GenV2.builder {
        Account(
            age = GenV2.int().bind()
        )
    }

    println(accountGen.sample())
}
