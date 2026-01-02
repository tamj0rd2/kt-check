package com.tamj0rd2.ktcheck.gen

private class ConstantGenerator<T>(private val value: T) : Gen<T>() {
    override fun generate(tree: ValueTree): GenResult<T> = GenResult(value, emptySequence())
}

fun <T> Gen.Companion.constant(value: T): Gen<T> = ConstantGenerator(value)
