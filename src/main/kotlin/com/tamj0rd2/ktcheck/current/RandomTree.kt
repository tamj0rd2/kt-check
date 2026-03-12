package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.Tree
import com.tamj0rd2.ktcheck.current.GenerationMode.*
import kotlin.random.nextInt

internal data class TreeData(
    val provider: ValueProvider,
    val mode: GenerationMode,
)

@ConsistentCopyVisibility
internal data class RandomTree private constructor(
    override val data: TreeData,
    override val lazyLeft: Lazy<RandomTree>,
    override val lazyRight: Lazy<RandomTree>,
) : Tree<TreeData>() {
    override fun toString(): String = visualise(maxDepth = 10)

    val provider = data.provider
    val generationMode = data.mode

    override val left: RandomTree get() = lazyLeft.value
    override val right: RandomTree get() = lazyRight.value

    fun traversingRight() = generateSequence(this) { it.right }

    // todo: make this a tree type of its own, rather than a provider?
    val isTerminator: Boolean get() = provider is TerminalValueProvider

    fun withoutEdgeCases(): RandomTree = RandomTree(
        data = when (data.mode) {
            Random,
            Shrinking,
                -> data

            EdgeCase -> data.copy(mode = Random)
        },
        lazyLeft = lazy { left.withoutEdgeCases() },
        lazyRight = lazy { right.withoutEdgeCases() }
    )

    fun withEdgeCasesOnly(): RandomTree = RandomTree(
        data = when (data.mode) {
            Shrinking,
            EdgeCase,
                -> data

            Random -> data.copy(mode = EdgeCase)
        },
        lazyLeft = lazy { left.withoutEdgeCases() },
        lazyRight = lazy { right.withoutEdgeCases() }
    )

    fun withShrunkValue(value: Any): RandomTree =
        copy(
            data = data.copy(
                provider = PredeterminedValueProvider(value, provider),
                mode = Shrinking,
            )
        )

    fun withLeft(left: RandomTree): RandomTree = copy(lazyLeft = lazyOf(left))
    fun withRight(right: RandomTree): RandomTree = copy(lazyRight = lazyOf(right))

    fun skipRight(amount: Int) = walkRight(this, amount)

    companion object {
        fun new(
            seed: Seed = Seed.random(),
            probability: EdgeCaseProbability = EdgeCaseProbability.default,
        ): RandomTree {
            val produceEdgeCase = probability.shouldProduceEdgeCase(seed)

            return RandomTree(
                data = TreeData(
                    provider = RandomValueProvider(seed),
                    mode = if (produceEdgeCase) EdgeCase else Random
                ),
                lazyLeft = lazy { new(seed.next(1), probability) },
                lazyRight = lazy { new(seed.next(2), probability) },
            )
        }

        val terminal
            get(): RandomTree = RandomTree(
                data = TreeData(
                    provider = TerminalValueProvider,
                    mode = Random
                ),
                lazyLeft = lazy { terminal },
                lazyRight = lazy { terminal },
            )

        private tailrec fun walkRight(tree: RandomTree, amount: Int): RandomTree = when (amount) {
            0 -> tree
            else -> walkRight(tree.right, amount - 1)
        }
    }
}

internal sealed interface ValueProvider {
    fun int(range: IntRange): Int

    fun bool() = int(0..1) == 1
}

internal interface DecoratedValueProvider : ValueProvider {
    val delegate: ValueProvider
}

data object TerminalValueProvider : ValueProvider {
    override fun int(range: IntRange): Int {
        error("${TerminalValueProvider::class.simpleName} cannot produce values")
    }
}

internal data class RandomValueProvider(private val seed: Seed) : ValueProvider {
    val random get() = kotlin.random.Random(seed.value)

    override fun int(range: IntRange): Int {
        return random.nextInt(range)
    }
}

internal data class PredeterminedValueProvider(
    private val value: Any,
    override val delegate: ValueProvider,
) : DecoratedValueProvider {
    init {
        val valueIsSupported = when (value) {
            is Int -> true
            else -> false
        }
        require(valueIsSupported) { "internal error - predetermined value was not a supported primitive" }
    }

    override fun int(range: IntRange): Int =
        when (value) {
            !is Int,
            !in range,
                -> delegate.int(range)

            else -> value
        }
}

internal sealed interface EdgeCaseProbability {
    fun shouldProduceEdgeCase(seed: Seed): Boolean

    companion object {
        val default = BasedOnCoinFlips(4)
    }

    /**
     * Deciding whether to use edge case mode based on simple probability of nextDouble doesn't work because it ends
     * up skewing the values produced by the generator. e.g if the edge case probability is 2% so we generate an edge
     * case, and the generator's edge case logic needs to pick a number between 0..99, it'll end up picking 2. Because
     * it's using the same random source.
     *
     * So to prevent that, I derive the seed, and also determine the probability based on multiple coin flips.
     */
    data class BasedOnCoinFlips(private val requiredFlips: Int) : EdgeCaseProbability {
        override fun shouldProduceEdgeCase(seed: Seed): Boolean {
            val decider = kotlin.random.Random(seed.next(0).value)
            return List(requiredFlips) { decider.nextBoolean() }.all { it }
        }
    }
}
