package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.core.Seed
import kotlin.reflect.KClass

internal sealed class GenImpl<T> : Gen<T> {
    abstract fun generate(tree: RandomTree): GenResultV2<T>

    open fun edgeCases(): List<GenResultV2<T>> = emptyList()

    override fun sample(seed: Long): T {
        return generate(randomTree(Seed(seed))).value ?: error("sample(seed) only supported for GenV2")
    }

    override fun <R> map(fn: (T) -> R): GenImpl<R> {
        return MapGen(this, fn)
    }

    override fun <R> flatMap(fn: (T) -> Gen<R>): GenImpl<R> {
        @Suppress("UNCHECKED_CAST")
        return FlatMapGen(this, fn as (T) -> GenImpl<R>)
    }

    override fun <T2, R> combineWith(
        nextGen: Gen<T2>,
        combine: (T, T2) -> R,
    ): GenImpl<R> {
        return CombineWithGen(this, nextGen as GenImpl, combine)
    }

    override fun filter(
        threshold: Int,
        predicate: (T) -> Boolean,
    ): GenImpl<T> {
        return PredicateFilterGen(this, threshold, predicate)
    }

    override fun ignoreExceptions(
        klass: KClass<out Exception>,
        threshold: Int,
    ): GenImpl<T> {
        return ExceptionIgnoringGen(this, threshold, klass)
    }

    override fun list(
        size: IntRange,
        distinct: Boolean,
    ): GenImpl<List<T>> = if (distinct) {
        DistinctListGen(this, size)
    } else {
        ListGen(this, size)
    }
}

internal data class GenResultV2<T>(
    val value: T,
    val shrinks: Sequence<RandomTree>,
) {
    fun <R> map(fn: (T) -> R): GenResultV2<R> = GenResultV2(
        value = fn(value),
        shrinks = shrinks,
    )
}

internal object GenV2Builders : GenBuilders {

    override fun <T> constant(value: T): Gen<T> {
        return ConstantGen(value)
    }

    override fun bool(shrinkTarget: Boolean): Gen<Boolean> = int(
        range = 0..1,
        shrinkTarget = if (shrinkTarget) 1 else 0
    ).map { it == 1 }

    override fun int(range: IntRange, shrinkTarget: Int): Gen<Int> {
        return IntGen(range, shrinkTarget)
    }

    override fun long(): Gen<Long> {
        // todo: implement this properly
        return int().map { it.toLong() }
    }

    override fun <T> oneOf(gens: Collection<Gen<T>>): Gen<T> {
        val gensList = gens.map { it as GenImpl<T> }
        return int(gensList.indices).flatMap { gensList[it] }
    }
}
