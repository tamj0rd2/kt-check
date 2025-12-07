package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.constant
import com.tamj0rd2.ktcheck.gen.flatMap
import com.tamj0rd2.ktcheck.util.Tuple

// todo: merge this in with Test
fun <T> checkAll(gen: Gen<T>, test: (T) -> Unit): Property =
    gen.flatMap {
        val args = when (it) {
            is Tuple -> it.values
            else -> listOf(it)
        }

        val testResult =
            try {
                test(it)
                TestResult.Success(args)
            } catch (e: AssertionError) {
                TestResult.Failure(args, e)
            }

        Gen.constant(testResult)
    }
