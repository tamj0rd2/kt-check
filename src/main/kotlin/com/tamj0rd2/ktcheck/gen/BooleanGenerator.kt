package com.tamj0rd2.ktcheck.gen

internal class BooleanGenerator : Gen<Boolean>() {
    override fun GenContext.generate(): GenResult<Boolean> {
        val value = tree.producer.bool()
        return GenResult(
            value = value,
            shrinks = if (value) sequenceOf(tree.withValue(false)) else emptySequence()
        )
    }
}
