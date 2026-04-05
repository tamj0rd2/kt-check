package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.core.GenerationContext
import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.shrinkers.IntShrinker
import kotlin.random.Random
import kotlin.reflect.KClass
import com.tamj0rd2.ktcheck.Gen as IGen

data class SplittableRandom(
    private val seed: Seed,
) {
    val random get() = Random(seed.value)
    val left by lazy { SplittableRandom(seed.next(1)) }
    val right by lazy { SplittableRandom(seed.next(2)) }
}

@ConsistentCopyVisibility
internal data class GenContext private constructor(
    val seed: Seed,
    val generateEdgeCase: Boolean,
) : GenerationContext {
    val random get() = Random(seed.value)
    val left by lazy { new(seed.next(1)) }
    val right by lazy { new(seed.next(2)) }

    companion object {
        fun new(seed: Seed): GenContext = new(seed, ShouldGenerateEdgeCase.BasedOnRng)

        fun new(seed: Seed, shouldGenerateEdgeCase: ShouldGenerateEdgeCase): GenContext = GenContext(
            seed = seed,
            generateEdgeCase = shouldGenerateEdgeCase(seed.next(3)),
        )
    }
}

internal fun interface ShouldGenerateEdgeCase {
    operator fun invoke(seed: Seed): Boolean

    data object BasedOnRng : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean {
            return Random(seed.value).nextBoolean()
        }
    }

    data object Always : ShouldGenerateEdgeCase {
        override fun invoke(seed: Seed): Boolean = true
    }
}

internal data class GenResult<T>(
    val value: T,
    val shrinks: Sequence<GenResult<T>>,
) {
    fun <R> map(fn: (T) -> R): GenResult<R> = GenResult(
        value = fn(value),
        shrinks = shrinks.map { it.map(fn) },
    )
}

internal sealed interface GenProvider<T> {
    fun generate(ctx: GenContext): GenResult<T>
}

internal data class IntGen(
    private val range: IntRange,
    private val shrinkTarget: Int,
) : GenProvider<Int> {
    init {
        require(shrinkTarget in range) { "shrinkTarget $shrinkTarget not in range $range" }
    }

    private val edgeCases = setOf(range.first, range.first + 1, -1, 0, 1, range.last - 1, range.last)
        .filter { it in range }
        .distinct()

    override fun generate(ctx: GenContext): GenResult<Int> {
        val value = if (ctx.generateEdgeCase) {
            edgeCases.random(ctx.random)
        } else {
            range.random(ctx.random)
        }

        return buildResult(value)
    }

    private fun buildResult(value: Int): GenResult<Int> = GenResult(
        value = value,
        shrinks = IntShrinker.shrink(value, range, shrinkTarget).map { buildResult(it) }
    )
}

internal data class MappingGen<T, R>(
    private val provider: GenProvider<T>,
    private val fn: (T) -> R,
) : GenProvider<R> {
    override fun generate(ctx: GenContext): GenResult<R> = provider.generate(ctx).map(fn)
}

internal data class Gen<T>(
    private val provider: GenProvider<T>,
) : IGen<T>, GenProvider<T> by provider {
    override fun sample(seed: Long): T = provider.generate(GenContext.new(Seed(seed))).value

    override fun <R> map(fn: (T) -> R): Gen<R> = Gen(MappingGen(provider, fn))

    override fun <R> flatMap(fn: (T) -> IGen<R>): Gen<R> {
        TODO("Not yet implemented")
    }

    override fun <T2, R> combineWith(
        nextGen: IGen<T2>,
        combine: (T, T2) -> R,
    ): Gen<R> {
        TODO("Not yet implemented")
    }

    override fun filter(threshold: Int, predicate: (T) -> Boolean): Gen<T> {
        TODO("Not yet implemented")
    }

    override fun ignoreExceptions(
        klass: KClass<out Exception>,
        threshold: Int,
    ): Gen<T> {
        TODO("Not yet implemented")
    }

    override fun list(size: IntRange): Gen<List<T>> {
        TODO("Not yet implemented")
    }

    override fun distinctList(size: IntRange): Gen<List<T>> {
        TODO("Not yet implemented")
    }

    companion object : GenBuilders {
        override fun <T> constant(value: T): Gen<T> {
            TODO("Not yet implemented")
        }

        override fun int(
            range: IntRange,
            shrinkTarget: Int,
        ) = Gen(IntGen(range, shrinkTarget))

        override fun long(): Gen<Long> {
            TODO("Not yet implemented")
        }
    }
}

