package com.tamj0rd2.ktcheck.gen


import com.tamj0rd2.ktcheck.contract.IGen
import com.tamj0rd2.ktcheck.contracts.OneOfGeneratorTestContract
import com.tamj0rd2.ktcheck.gen.GenTests.Companion.generateWithShrunkValues
import com.tamj0rd2.ktcheck.producer.ProducerTree

internal class OneOfGeneratorTest : BaseGenTest(), OneOfGeneratorTestContract {
    override fun <T : Any> IGen<T>.generateWithShrunkValuesForOneOfGens(rngValues: List<Any>): Pair<T, List<T>> {
        val tree = ProducerTree.new()
            .run {
                withLeft(left.withValue(rngValues.first()))
            }
            .run {
                // note: trees are difficult to manipulate.
                val root = this
                val remainingValues = rngValues.drop(1)
                if (remainingValues.isEmpty()) return@run root

                val lastAffectedNode = traverseRight(rngValues.size - 1)
                remainingValues.foldRightIndexed(lastAffectedNode) { index, value, acc ->
                    val updatedNode = acc.withValue(value)
                    val updatedParentNode = root.traverseRight(index).withRight(updatedNode)
                    updatedParentNode
                }
            }

        return (this as Gen<T>).generateWithShrunkValues(tree)
    }
}
