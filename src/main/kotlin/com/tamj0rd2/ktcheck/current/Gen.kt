package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.GenerationException
import com.tamj0rd2.ktcheck.core.Seed
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.orThrow
import kotlin.reflect.KClass
import com.tamj0rd2.ktcheck.Gen as IGen

// todo: one thing on my mind is that each generator should do a final check to make sure the constraints are upheld
//  post generation/edge case creation. put that in a contract somewhere.
internal sealed interface Generator<T> {
    fun generate(root: RandomTree): Result4k<GeneratedValue<T>, GenerationException>

    fun edgeCases(root: RandomTree): List<GeneratedValue<T>>
}

internal data class Gen<T>(
    private val generator: Generator<T>,
) : IGen<T>, Generator<T> by generator {
    override fun sample(seed: Long) = generate(RandomTree.new(Seed(seed))).orThrow().value

    override fun withoutDefaultEdgeCases() = Gen(EdgeCasesDisabledGen(this))

    override fun <R> map(fn: (T) -> R) = Gen(MapGen(this, fn))

    @Suppress("UNCHECKED_CAST")
    override fun <R> flatMap(fn: (T) -> IGen<R>) = Gen(FlatMapGen(this, fn as (T) -> Gen<R>))

    override fun <T2, R> combineWith(
        nextGen: IGen<T2>,
        combine: (T, T2) -> R,
    ) = Gen(CombineWithGen(this, nextGen as Gen<T2>, combine))

    override fun filter(
        threshold: Int,
        predicate: (T) -> Boolean,
    ) = Gen(FilterGen(this, threshold, predicate))

    override fun ignoreExceptions(
        klass: KClass<out Exception>,
        threshold: Int,
    ) = Gen(IgnoreExceptionGen(this, klass, threshold))

    override fun list(size: IntRange) = Gen(ListGen(GenV2Builders.int(size), this))

    override fun distinctList(size: IntRange) = Gen(DistinctListGen(GenV2Builders.int(size), this))
}

internal data class GeneratedValue<T>(
    val value: T,
    val shrinks: Sequence<RandomTree>,
    val usedTree: RandomTree,
)

internal object GenV2Builders : GenBuilders {

    override fun <T> constant(value: T) = Gen(ConstantGen(value))

    override fun int(range: IntRange, shrinkTarget: Int) = Gen(IntGen(range, shrinkTarget))

    // todo: implement properly
    override fun long() = int().map { it.toLong() }
}
