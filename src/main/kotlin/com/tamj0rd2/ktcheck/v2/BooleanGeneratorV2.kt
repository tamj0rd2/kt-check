package com.tamj0rd2.ktcheck.v2

import com.tamj0rd2.ktcheck.v1.ProducerTree

internal object BooleanGeneratorV2 : GenV2<Boolean>() {
    override fun generate(tree: ProducerTree): GenResultV2<Boolean> {
        val value = tree.producer.bool()
        return GenResultV2(
            value = value,
            shrinks = if (value) {
                sequenceOf(GenResultV2(false, emptySequence()))
            } else {
                emptySequence()
            },
        )
    }
}
