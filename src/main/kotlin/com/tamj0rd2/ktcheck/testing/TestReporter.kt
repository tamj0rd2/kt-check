package com.tamj0rd2.ktcheck.testing

import java.io.PrintStream

interface TestReporter {
    fun reportSuccess(seed: Long, iterations: Int)

    fun reportFailure(
        seed: Long,
        failedIteration: Int,
        originalFailure: TestResult.Failure,
        shrunkFailure: TestResult.Failure?,
    )
}

class PrintingTestReporter(
    private val printStream: PrintStream = System.out,
    private val showAllDiagnostics: Boolean = true,
) : TestReporter {
    override fun reportSuccess(seed: Long, iterations: Int) {
        printStream.println("Success: $iterations iterations succeeded")
    }

    override fun reportFailure(
        seed: Long,
        failedIteration: Int,
        originalFailure: TestResult.Failure,
        shrunkFailure: TestResult.Failure?
    ) {
        val output = buildString {
            appendLine("Seed: $seed - failed on iteration $failedIteration\n")

            if (shrunkFailure != null) {
                appendLine(formatFailure(prefix = "Shrunk ", result = shrunkFailure))
            } else {
                appendLine("Warning - Could not shrink the input arguments")
            }

            if (showAllDiagnostics || shrunkFailure == null) {
                appendLine()
                appendLine(formatFailure(prefix = "Original ", result = originalFailure))
                appendLine("-----------------")
            }
        }

        printStream.println(output)
    }

    private fun formatFailure(prefix: String, result: TestResult.Failure): String = buildString {
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
}
