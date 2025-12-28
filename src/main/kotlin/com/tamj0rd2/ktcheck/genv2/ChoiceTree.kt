package com.tamj0rd2.ktcheck.genv2

import com.tamj0rd2.ktcheck.gen.deriveSeed
import kotlin.random.Random

internal sealed interface ChoiceTree {
    val left: ChoiceTree
    val right: ChoiceTree

    fun int(range: IntRange): Int

    companion object {
        internal fun from(seed: Long): ChoiceTree = RandomTree(
            seed = seed,
            lazyLeft = lazy { from(deriveSeed(seed, 1)) },
            lazyRight = lazy { from(deriveSeed(seed, 2)) },
        )
    }
}

internal data class RandomTree(
    private val seed: Long,
    internal val lazyLeft: Lazy<ChoiceTree>,
    internal val lazyRight: Lazy<ChoiceTree>,
) : ChoiceTree {
    private val random get() = Random(seed)

    override val left: ChoiceTree by lazyLeft
    override val right: ChoiceTree by lazyRight

    override fun int(range: IntRange) = range.random(random)
}

internal data class RecordedChoiceTree<out T>(
    private val choice: T,
    internal val lazyLeft: Lazy<ChoiceTree>,
    internal val lazyRight: Lazy<ChoiceTree>,
) : ChoiceTree {
    override val left: ChoiceTree by lazyLeft
    override val right: ChoiceTree by lazyRight

    override fun int(range: IntRange): Int {
        if (choice !is Int) TODO("handle this")
        if (choice !in range) TODO("handle int out of range")
        return choice
    }
}

internal fun combineShrinks(
    tree: ChoiceTree,
    leftShrinks: Sequence<ChoiceTree>,
    rightShrinks: Sequence<ChoiceTree>,
): Sequence<ChoiceTree> {
    val leftChoices = leftShrinks.map { tree.withLeft(it) }
    val rightChoices = rightShrinks.map { tree.withRight(it) }
    return leftChoices + rightChoices
}

internal fun <T> ChoiceTree.withChoice(value: T): ChoiceTree = when (this) {
    is RandomTree -> RecordedChoiceTree(
        choice = value,
        lazyLeft = lazyLeft,
        lazyRight = lazyRight,
    )

    is RecordedChoiceTree<*> -> RecordedChoiceTree(
        choice = value,
        lazyLeft = lazyLeft,
        lazyRight = lazyRight,
    )
}

private fun ChoiceTree.withLeft(left: ChoiceTree): ChoiceTree = when (this) {
    is RandomTree -> copy(lazyLeft = lazyOf(left))
    is RecordedChoiceTree<*> -> copy(lazyLeft = lazyOf(left))
}

private fun ChoiceTree.withRight(right: ChoiceTree): ChoiceTree = when (this) {
    is RandomTree -> copy(lazyRight = lazyOf(right))
    is RecordedChoiceTree<*> -> copy(lazyRight = lazyOf(right))
}
