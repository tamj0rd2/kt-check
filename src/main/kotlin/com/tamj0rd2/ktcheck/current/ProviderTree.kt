package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.Tree
import kotlin.random.Random
import kotlin.random.nextInt

@ConsistentCopyVisibility
internal data class ProviderTree private constructor(
    val provider: ValueProvider,
    override val lazyLeft: Lazy<ProviderTree>,
    override val lazyRight: Lazy<ProviderTree>,
) : Tree<ValueProvider>() {
    override fun toString(): String = visualise(maxDepth = 10)

    override val data = provider

    override val left: ProviderTree get() = lazyLeft.value
    override val right: ProviderTree get() = lazyRight.value

    fun traversingRight() = generateSequence(this) { it.right }

    // todo: make this a tree type of its own, rather than a provider?
    val isTerminator: Boolean get() = provider is TerminalValueProvider

    fun withPredeterminedValue(value: Int): ProviderTree =
        copy(provider = PredeterminedValueProvider(value, provider))

    fun withLeft(left: ProviderTree): ProviderTree = copy(lazyLeft = lazyOf(left))
    fun withRight(right: ProviderTree): ProviderTree = copy(lazyRight = lazyOf(right))

    companion object {
        fun new(seed: Seed = Seed.random()): ProviderTree = ProviderTree(
            provider = RandomValueProvider(seed),
            lazyLeft = lazy { new(seed.next(1)) },
            lazyRight = lazy { new(seed.next(2)) },
        )

        val terminal
            get(): ProviderTree = ProviderTree(
                provider = TerminalValueProvider,
                lazyLeft = lazy { terminal },
                lazyRight = lazy { terminal },
            )
    }
}

internal sealed interface ValueProvider {
    fun int(range: IntRange): Int
}

internal interface DecoratedValueProvider : ValueProvider {
    val delegate: ValueProvider
}

data object TerminalValueProvider : ValueProvider {
    override fun int(range: IntRange): Int {
        error("${TerminalValueProvider::class.simpleName} cannot produce values")
    }
}

private data class RandomValueProvider(private val seed: Seed) : ValueProvider {
    private val random get() = Random(seed.value)

    override fun int(range: IntRange): Int {
        return random.nextInt(range)
    }
}

@ConsistentCopyVisibility
private data class PredeterminedValueProvider private constructor(
    private val value: Any,
    override val delegate: ValueProvider,
) : DecoratedValueProvider {
    constructor(value: Int, fallback: ValueProvider) : this(value as Any, fallback)

    override fun int(range: IntRange): Int =
        when (value) {
            !is Int,
            !in range,
                -> delegate.int(range)

            else -> value
        }
}
