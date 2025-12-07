package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.gen.ChoiceSequence
import com.tamj0rd2.ktcheck.gen.ChoiceSequence.Companion.shrink
import com.tamj0rd2.ktcheck.gen.Gen
import com.tamj0rd2.ktcheck.gen.InvalidReplay
import com.tamj0rd2.ktcheck.gen.WritableChoiceSequence
import java.io.PrintStream
import kotlin.random.Random

data class TestResult(val failure: Throwable?, val args: List<Any?>) {
    val didSucceed = failure == null
}

typealias Property = Gen<TestResult>

fun test(
    arb: Property,
    iterations: Int = 1000,
    seed: Long = Random.nextLong(),
    showAllDiagnostics: Boolean = true,
    printStream: PrintStream = System.out,
) {
    val rand = Random(seed)

    fun getSmallestCounterExample(choices: ChoiceSequence): TestResult? {
        for (candidate in choices.shrink()) {
            val result =
                try {
                    arb.generate(candidate)
                } catch (_: InvalidReplay) {
                    continue
                }

            if (!result.didSucceed) {
                return getSmallestCounterExample(candidate) ?: result
            }
        }

        return null
    }

    fun formatResults(prefix: String, result: TestResult): String = buildString {
        appendLine("${prefix}Arguments:")
        appendLine("-----------------")
        result.args.forEachIndexed { index, arg -> appendLine("Arg $index -> $arg") }

        if (showAllDiagnostics) {
            appendLine()
            appendLine("${prefix}Failure:")
            appendLine("-----------------")
            appendLine(result.failure)
        }
    }

    repeat(iterations) {
        val cs = WritableChoiceSequence(rand)
        val result = arb.generate(cs)
        if (result.didSucceed) return@repeat
        val shrunkResult = getSmallestCounterExample(cs)

        buildString {
                appendLine("Seed: $seed\n")

                if (shrunkResult != null) {
                    appendLine(formatResults(prefix = "", result = shrunkResult))
                } else {
                    appendLine("Warning - Could not shrink the input arguments")
                }

                if (showAllDiagnostics || shrunkResult == null) {
                    appendLine()
                    appendLine(formatResults(prefix = "Original ", result = result))
                    appendLine("-----------------")
                }
            }
            .also(printStream::println)

        throw (shrunkResult ?: result).failure!!
    }

    printStream.println("Success: $iterations iterations succeeded")
}
