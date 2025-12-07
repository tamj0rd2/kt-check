package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.int
import com.tamj0rd2.ktcheck.gen.list
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import strikt.assertions.isLessThanOrEqualTo
import java.io.ByteArrayOutputStream
import java.io.PrintStream

// based on https://github.com/jlink/shrinking-challenge/tree/main/challenges
class ShrinkingChallenge {
    @Test
    fun reverse() = expectShrunkOutput("Arg 0 -> [0, 1]") { printStream ->
        val gen = Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list(0..10000)
        test(
            checkAll(gen) { initial -> expectThat(initial.reversed()).isEqualTo(initial) },
            showAllDiagnostics = false, printStream = printStream
        )
    }

    @Test
    fun nestedLists() = expectShrunkOutput("Arg 0 -> [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]]") { printStream ->
        test(checkAll(Gen.int(Int.MIN_VALUE..Int.MAX_VALUE).list().list()) { ls ->
            expectThat(ls.sumOf { it.size }).isLessThanOrEqualTo(10)
        }, showAllDiagnostics = false, printStream = printStream)
    }

    @Test
    @Disabled("The shrinker does produce a much smaller failing case, but not the minimal one.")
    fun lengthList() = expectShrunkOutput("Arg 0 -> [900]") { printStream ->
        val gen = Gen.int(0..1000).list(1..100)
        test(checkAll(gen) { ls ->
            expectThat(ls.max()).isLessThan(900)
        }, showAllDiagnostics = false, printStream = printStream)
    }

    private fun expectShrunkOutput(expected: String, block: (PrintStream) -> Unit) {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        expectThrows<AssertionError> { block(printStream) }

        val output = outputStream.toString()
        expectThat(output).contains(expected)
    }
}
