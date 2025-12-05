package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.constant
import com.tamj0rd2.ktcheck.gen.flatMap
import com.tamj0rd2.ktcheck.util.Tuple

fun <T> checkAll(gen: Gen<T>, test: (T) -> Unit): Property {
    return gen.flatMap {
        val outcome =
            try {
                test(it)
                null
            } catch (e: AssertionError) {
                e
            }

        val args = when (it) {
            is Tuple -> it.values
            else -> listOf(it)
        }

        Gen.constant(TestResult(failure = outcome, args = args))
    }
}
