package com.tamj0rd2.ktcheck.genv2

import kotlin.random.Random
import kotlin.random.nextULong

sealed interface Sample {
    val value: ULong

    @JvmInline
    value class Original(override val value: ULong) : Sample

    @JvmInline
    value class Shrunk(override val value: ULong) : Sample
}

sealed interface SampleTree {
    val sample: Sample
    val left: SampleTree
    val right: SampleTree

    companion object {
        internal fun from(seed: Long): SampleTree = LazySampleTree(
            sample = Sample.Original(Random(seed).nextULong()),
            lazyLeft = lazy { from(deriveSeed(seed, 1)) },
            lazyRight = lazy { from(deriveSeed(seed, 2)) },
        )

        internal fun constant(value: ULong): SampleTree = LazySampleTree(
            sample = Sample.Original(value),
            lazyLeft = lazy { constant(value) },
            lazyRight = lazy { constant(value) },
        )

        private const val SPLIT_MIX_64_MULTIPLIER = 6364136223846793005L

        private fun deriveSeed(parentSeed: Long, offset: Int): Long =
            parentSeed * SPLIT_MIX_64_MULTIPLIER + offset
    }
}

internal data class LazySampleTree(
    override val sample: Sample,
    private val lazyLeft: Lazy<SampleTree>,
    private val lazyRight: Lazy<SampleTree>,
) : SampleTree {
    override val left: SampleTree by lazyLeft
    override val right: SampleTree by lazyRight
}

internal data object MinimalSampleTree : SampleTree {
    override val sample = Sample.Shrunk(0UL)
    override val left = this
    override val right = this
}

internal fun combineShrinks(
    tree: SampleTree,
    leftShrinks: Sequence<SampleTree>,
    rightShrinks: Sequence<SampleTree>,
): Sequence<SampleTree> = when (tree) {
    MinimalSampleTree -> emptySequence()

    is LazySampleTree -> {
        fun Sequence<SampleTree>.orEmptyIfMinimal(tree: SampleTree) =
            if (tree is MinimalSampleTree) emptySequence() else this

        val derivedLeftShrinks = leftShrinks.orEmptyIfMinimal(tree.left).map { tree.withLeft(it) }
        val derivedRightShrinks = rightShrinks.orEmptyIfMinimal(tree.right).map { tree.withRight(it) }
        val derivedShrinks = derivedLeftShrinks + derivedRightShrinks

        if (derivedShrinks.any()) sequenceOf(MinimalSampleTree) + derivedShrinks else derivedShrinks
    }
}

internal fun SampleTree.withSample(sample: Sample): SampleTree = when (this) {
    is MinimalSampleTree -> this
    is LazySampleTree -> copy(sample = sample)
}

private fun SampleTree.withLeft(left: SampleTree): SampleTree = when (this) {
    is MinimalSampleTree -> this
    is LazySampleTree -> copy(lazyLeft = lazyOf(left))
}

private fun SampleTree.withRight(right: SampleTree): SampleTree = when (this) {
    is MinimalSampleTree -> this
    is LazySampleTree -> copy(lazyRight = lazyOf(right))
}
