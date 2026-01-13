package com.tamj0rd2.ktcheck.v1

import com.tamj0rd2.ktcheck.Gen
import com.tamj0rd2.ktcheck.GenBuilder
import com.tamj0rd2.ktcheck.core.ProducerTree
import com.tamj0rd2.ktcheck.core.Seed
import java.util.*
import kotlin.reflect.KClass

internal data class GenContext(
    val tree: ProducerTree,
    val mode: GenMode,
)

internal enum class GenMode {
    Initial,
    Shrinking,
}

/**
 * A generator that can produce values of type T.
 *
 * Generators can be transformed and combined using various methods such as [map], [flatMap], and [combineWith].
 *
 * @param T The type of values produced by this generator.
 */
internal sealed class GenV1<T> : Gen<T> {
    internal abstract fun GenContext.generate(): GenResult<T>

    internal fun generate(tree: ProducerTree, mode: GenMode): GenResult<T> =
        GenContext(tree, mode).generate()

    override fun <R> map(fn: (T) -> R): GenV1<R> = CombinatorGenerator {
        val (value, shrinks) = generate(tree, mode)
        GenResult(fn(value), shrinks)
    }

    override fun <R> flatMap(fn: (T) -> Gen<R>): Gen<R> = CombinatorGenerator {
        val (leftValue, leftShrinks) = generate(tree.left, mode)
        val (rightValue, rightShrinks) = (fn(leftValue) as GenV1<R>).generate(tree.right, mode)
        GenResult(
            value = rightValue,
            shrinks = tree.combineShrinks(leftShrinks, rightShrinks)
        )
    }

    /**
     * Combines two independent generators using the provided combining function.
     *
     * The shrinks from both generators are combined to provide a comprehensive set of shrinks for the final value.
     *
     * Use this when you want to create a new generator that produces values based on two independent generators. i.e
     * the value from one generator does not influence the value from the other generator.
     *
     * @param nextGen The second generator to combine with this generator.
     * @param combine A function that takes values from both generators and combines them into a value of type R.
     * @return A new generator that produces values of type R.
     */
    fun <T2, R> combineWith(nextGen: GenV1<T2>, combine: (T, T2) -> R): GenV1<R> =
        CombinatorGenerator {
            val (thisValue, thisShrinks) = generate(tree.left, mode)
            val (nextValue, nextShrinks) = nextGen.generate(tree.right, mode)
            GenResult(
                value = combine(thisValue, nextValue),
                shrinks = tree.combineShrinks(thisShrinks, nextShrinks)
            )
        }

    override fun sample(seed: Long): T = generate(
        tree = ProducerTree.new(Seed(seed)),
        mode = GenMode.Initial
    ).value

    internal companion object : GenBuilder {
        override fun <T> constant(value: T): GenV1<T> {
            return ConstantGenerator(value)
        }

        override fun bool(): GenV1<Boolean> {
            return BooleanGenerator()
        }

        override fun int(range: IntRange): GenV1<Int> {
            return IntGenerator(range)
        }

        // todo: implement this properly
        override fun long(range: IntRange): GenV1<Long> {
            return int(range).map { it.toLong() }
        }

        override fun uuid(): GenV1<UUID> {
            return (long() + long()).map { UUID(it.first, it.second) }
        }

        override fun <T> oneOf(gens: Collection<Gen<T>>): GenV1<T> {
            require(gens.isNotEmpty()) { "oneOf requires at least one generator" }
            val genList = gens.map { it as GenV1<T> }
            return OneOfGenerator(genList)
        }

        override fun <T> Gen<T>.list(
            size: IntRange,
            distinct: Boolean,
        ): GenV1<List<T>> = ListGenerator(sizeRange = size, distinct = distinct, gen = this as GenV1<T>)

        override fun Gen<Char>.string(size: IntRange): GenV1<String> {
            return list(size).map { it.joinToString("") }
        }

        override fun Gen<Char>.string(size: Int): GenV1<String> {
            return string(size..size)
        }

        /**
         * Filters generated values using the given [predicate]. Although this generator supports shrinking, it is very
         * inefficient. Instead of using this generator, consider using generators that do not throw exceptions.
         */
        override fun <T> Gen<T>.filter(threshold: Int, predicate: (T) -> Boolean): GenV1<T> {
            return PredicateFilterGenerator(
                gen = this as GenV1<T>,
                threshold = threshold,
                predicate = predicate
            )
        }

        /**
         * Ignores exceptions of type [klass] thrown during generation. Although this generator supports shrinking, it is very
         * inefficient. Instead of using this generator, consider using generators that do not throw exceptions.
         */
        override fun <T> Gen<T>.ignoreExceptions(klass: KClass<out Exception>, threshold: Int): GenV1<T> =
            ExceptionIgnoringGenerator(
                gen = this as GenV1<T>,
                threshold = threshold,
                klass = klass
            )

        override fun <T> combine(block: CombinerContext.() -> T): GenV1<T> {
            return CombinerGenerator(block)
        }

        override fun <T1, T2> Gen<T1>.plus(nextGen: Gen<T2>): GenV1<Pair<T1, T2>> {
            // todo: fix all this casting nonsense?
            val first = this as GenV1<T1>
            val second = nextGen as GenV1<T2>
            return first.combineWith(second, ::Pair)
        }
    }
}

/**
 * The result of generating a value from a generator, including the generated value and its shrinks.
 * Shrinks are represented as a sequence of [ProducerTree]s, allowing for lazy evaluation and efficient traversal.
 */
internal data class GenResult<T>(val value: T, val shrinks: Sequence<ProducerTree>)

private class CombinatorGenerator<T>(private val generator: GenContext.() -> GenResult<T>) : GenV1<T>() {
    override fun GenContext.generate(): GenResult<T> = generator()
}
