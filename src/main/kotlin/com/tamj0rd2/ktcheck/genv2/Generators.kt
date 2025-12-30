package com.tamj0rd2.ktcheck.genv2

private class ConstantGenerator<T>(private val value: T) : Gen<T>() {
    override fun generate(tree: ChoiceTree): GenResult<T> = GenResult(value, emptySequence())
}

fun <T> Gen.Companion.constant(value: T): Gen<T> = ConstantGenerator(value)
