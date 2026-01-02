package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.genv2.PropertyFalsifiedException
import java.io.PrintStream

interface TestReporter {
    fun reportSuccess(iterations: Int)

    fun reportFailure(exception: PropertyFalsifiedException)
}

class PrintingTestReporter(
    private val printStream: PrintStream = System.out,
    private val showAllDiagnostics: Boolean = true,
) : TestReporter {
    override fun reportSuccess(iterations: Int) {
        printStream.println("Success: $iterations iterations succeeded\n")
    }

    override fun reportFailure(exception: PropertyFalsifiedException) {
        val shrunkFailure = exception.shrunkResult.takeIf { it != exception.originalResult }

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
        result.args.forEachIndexed { index, arg -> appendLine("Arg $index -> $arg") }

        if (showAllDiagnostics) {
            appendLine()
            appendLine("${prefix}Failure:")
            appendLine("-----------------")
            appendLine(result.failure)
        }
    }
}
