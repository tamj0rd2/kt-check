package com.tamj0rd2.ktcheck.current

internal class FlatMapGen<T, R>(
    private val gen: GenImpl<T>,
    private val fn: (T) -> GenImpl<R>,
) : GenImpl<R>() {
    override fun generate(tree: RandomTree): GenResultV2<R> {
        val (outerValue, outerShrinks) = gen.generate(tree.left)
        val (innerValue, innerShrinks) = fn(outerValue).generate(tree.right)

        val leftBasedShrinks = outerShrinks.map { tree.withLeft(it) }
        val rightBasedShrinks = innerShrinks.map { tree.withRight(it) }
        val shrinks = leftBasedShrinks + rightBasedShrinks

        return GenResultV2(value = innerValue, shrinks = shrinks)
    }
}
