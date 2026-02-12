package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.core.Seed
import com.tamj0rd2.ktcheck.core.Tree
import kotlin.random.Random
import kotlin.random.nextInt

internal sealed interface ValueProvider {
    fun int(range: IntRange): Int

    data class Random(val seed: Seed) : ValueProvider {
        private val random get() = Random(seed.value)

        override fun toString(): String {
            return seed.toString()
        }

        override fun int(range: IntRange): Int {
            return random.nextInt(range)
        }
    }

    data class Shrunk(val value: Any?) : ValueProvider {
        override fun int(range: IntRange): Int {
            if (value is Int && value in range) return value
            TODO("do I ever get here? if it does, I could fallback to using Random as a delegate")
        }
    }
}

internal typealias RandomTree = Tree<ValueProvider>

internal fun randomTree(seed: Seed = Seed.random()): RandomTree = Tree(
    data = ValueProvider.Random(seed),
    lazyLeft = lazy { randomTree(seed.next(1)) },
    lazyRight = lazy { randomTree(seed.next(2)) },
)

// todo: maybe just pass it in? makes things less awkward/annoying to deal with
internal val edgeCaseTree = randomTree(Seed(0))

/**
 * Walks right [offset] times, then replaces the left tree at that position.
 */
internal fun RandomTree.replaceLeftAtOffset(offset: Int, shrunkLeft: RandomTree): RandomTree =
    replaceAtOffset(offset) { it.withLeft(shrunkLeft) }

/**
 * Walks right [offset] times, then replaces the tree at that position.
 */
private fun RandomTree.replaceAtOffset(offset: Int, fn: (RandomTree) -> RandomTree): RandomTree = when (offset) {
    0 -> fn(this)
    else -> withRight(right.replaceAtOffset(offset - 1, fn))
}
