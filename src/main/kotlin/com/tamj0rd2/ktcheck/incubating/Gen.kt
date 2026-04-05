package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.orThrow
import kotlin.reflect.KClass
import com.tamj0rd2.ktcheck.Gen as IGen

internal class Gen<T> private constructor(
    provider: GenProvider<T>,
) : IGen<T>, GenProvider<T> by provider {
    override fun sample(seed: Long): T = generate(GenContext.new(Seed(seed))).orThrow().value

    override fun <R> map(fn: (T) -> R): Gen<R> = Gen(MappingGen(this, fn))

    override fun <R> flatMap(fn: (T) -> IGen<R>): Gen<R> = Gen(
        FlatMappingGen(
            gen = this,
            fn = let {
                @Suppress("UNCHECKED_CAST")
                fn as (T) -> Gen<R>
            }
        )
    )

    override fun <T2, R> combineWith(
        nextGen: IGen<T2>,
        combine: (T, T2) -> R,
    ): Gen<R> = Gen(CombineGen(this, nextGen as Gen<T2>, combine))

    override fun filter(
        threshold: Int,
        predicate: (T) -> Boolean,
    ): Gen<T> =
        Gen(FilterGen(this, threshold, predicate))

    override fun ignoreExceptions(
        klass: KClass<out Exception>,
        threshold: Int,
    ): Gen<T> = Gen(IgnoreExceptionsGen(this, threshold, klass))

    override fun list(size: IntRange): Gen<List<T>> = Gen(ListGen(this, size))

    override fun distinctList(size: IntRange): Gen<List<T>> = Gen(DistinctListGen(this, size))

    companion object : GenBuilders {
        override fun <T> constant(value: T): Gen<T> = Gen(ConstantGen(value))

        override fun int(
            range: IntRange,
            shrinkTarget: Int,
        ) = Gen(IntGen(range, shrinkTarget))

        override fun long(): Gen<Long> {
            TODO("Not yet implemented")
        }
    }
}
