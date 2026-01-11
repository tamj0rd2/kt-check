package com.tamj0rd2.ktcheck

import java.io.PrintStream

interface TestReporter {
    fun reportSuccess(iterations: Int)

    fun reportFailure(exception: PropertyFalsifiedException)
}

object NoOpTestReporter : TestReporter {
    override fun reportSuccess(iterations: Int) {}
    override fun reportFailure(exception: PropertyFalsifiedException) {}
}

class PrintingTestReporter(
    private val printStream: PrintStream = System.out,
    private val showAllDiagnostics: Boolean = true,
) : TestReporter {
    override fun reportSuccess(iterations: Int) {
        printStream.println("Success: $iterations iterations succeeded\n")
    }

    override fun reportFailure(exception: PropertyFalsifiedException) {
        val shrunkFailure = exception.shrunkResult

        val output = buildString {
            appendLine("Seed: ${exception.seed} - failed on iteration ${exception.iteration}\n")

            if (shrunkFailure != null) {
                appendLine(formatFailure(prefix = "Shrunk ", result = shrunkFailure))
            } else {
                appendLine("Warning - Could not shrink the input arguments")
            }

            if (showAllDiagnostics || shrunkFailure == null) {
                appendLine()
                appendLine(formatFailure(prefix = "Original ", result = exception.originalResult))
                appendLine("-----------------")
            }
        }

        printStream.println(output)
    }

    private fun formatFailure(prefix: String, result: TestResult.Failure<*>): String = buildString {
        appendLine("${prefix}Arguments:")
        appendLine("-----------------")
        when (result.input) {
            is Pair<*, *> -> {
                appendLine("1st -> ${result.input.first}")
                appendLine("2nd -> ${result.input.second}")
            }

            is Triple<*, *, *> -> {
                appendLine("1st -> ${result.input.first}")
                appendLine("2nd -> ${result.input.second}")
                appendLine("3rd -> ${result.input.third}")
            }

            else -> appendLine(result.input)
        }

        appendLine()
        appendLine("${prefix}Failure:")
        appendLine("-----------------")
        appendLine(result.failure)
    }
}
