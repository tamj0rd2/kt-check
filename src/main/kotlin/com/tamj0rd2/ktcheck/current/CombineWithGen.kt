package com.tamj0rd2.ktcheck.current

internal class CombineWithGen<T1, T2, R>(
    private val gen1: GenImpl<T1>,
    private val gen2: GenImpl<T2>,
    private val combine: (T1, T2) -> R,
) : GenImpl<R>() {
    override fun generate(tree: RandomTree): GenResultV2<R> {
        return combine(
            tree = tree,
            left = gen1.generate(tree.left),
            right = gen2.generate(tree.right)
        )
    }

    override fun edgeCases(): List<GenResultV2<R>> {
        val leftEdgeCases = gen1.edgeCases()
        val rightEdgeCases = gen2.edgeCases()

        return leftEdgeCases.flatMap { left ->
            rightEdgeCases.map { right ->
                combine(edgeCaseTree, left, right)
            }
        }
    }

    private fun combine(
        tree: RandomTree,
        left: GenResultV2<T1>,
        right: GenResultV2<T2>,
    ): GenResultV2<R> {
        val leftBasedShrinks = left.shrinks.map { tree.withLeft(it) }
        val rightBasedShrinks = right.shrinks.map { tree.withRight(it) }
        val shrinks = leftBasedShrinks + rightBasedShrinks

        return GenResultV2(
            value = combine(left.value, right.value),
            shrinks = shrinks,
        )
    }
}
