package com.tamj0rd2.ktcheck

import com.tamj0rd2.ktcheck.core.Tuple

sealed interface Property<in T> {
    fun falsify(input: T): Falsified?

    data class Falsified(val error: AssertionError?)
}

/** Runs the test on the given input. Should throw an AssertionError if the property fails. */
fun interface ThrowingProperty<T> : Property<T> {
    operator fun invoke(input: T)

    override fun falsify(input: T): Property.Falsified? = try {
        invoke(input)
        null
    } catch (e: AssertionError) {
        Property.Falsified(e)
    }
}

/** Runs the test on the given input. Should return false if the property fails */
fun interface BooleanProperty<T> : Property<T> {
    operator fun invoke(input: T): Boolean

    override fun falsify(input: T): Property.Falsified? =
        if (invoke(input)) null else Property.Falsified(null)
}

fun <T> forAll(gen: Gen<T>, property: BooleanProperty<T>) = forAll(TestConfig(), gen, property)

fun <T> forAll(config: TestConfig, gen: Gen<T>, property: BooleanProperty<T>) =
    runPropertyTest(config, gen, property as Property<T>)

fun <T> checkAll(gen: Gen<T>, property: ThrowingProperty<T>) = checkAll(TestConfig(), gen, property)

fun <T> checkAll(config: TestConfig, gen: Gen<T>, property: ThrowingProperty<T>) =
    runPropertyTest(config, gen, property as Property<T>)

private fun <T> runPropertyTest(config: TestConfig, gen: Gen<T>, property: Property<T>) {
    when (gen) {
        is com.tamj0rd2.ktcheck.incubating.Gen -> com.tamj0rd2.ktcheck.incubating.test(config, gen, property)
        else -> throw IllegalArgumentException("Unsupported Gen implementation: ${gen::class}")
    }

    config.reportingPrintStream.println("Success: ${config.effectiveIterations} iterations succeeded")
}

data class Falsification<T>(
    val input: T,
    val error: AssertionError?,
)

class PropertyFalsifiedException internal constructor(
    val seed: Long,
    val iteration: Int,
    val original: Falsification<*>,
    val shrunk: Falsification<*>?,
    val shrinkSteps: Int,
    // todo: remove default value when I remove the old impl
    val shrinkingConstrained: Boolean = false,
) : AssertionError() {
    internal val smallest = shrunk ?: original
    override val cause = smallest.error

    override val message = buildString {
        appendLine("Property falsified on iteration ${iteration}, seed $seed\n")

        if (shrunk != null) {
            appendLine(
                formatFalsification(prefix = "Shrunk ", result = shrunk, showShrinkWarning = shrinkingConstrained)
            )
        } else {
            appendLine("Warning - Could not shrink the input arguments")
        }

        appendLine(formatFalsification(prefix = "Original ", result = original, showShrinkWarning = false))
    }

    private fun formatFalsification(
        prefix: String,
        result: Falsification<*>,
        showShrinkWarning: Boolean,
    ) = buildString {
        appendLine("${prefix}Arguments:")
        appendLine("--------------------")
        when (result.input) {
            is Tuple -> {
                result.input.values.forEachIndexed { index, value ->
                    appendLine("Arg ${index + 1} -> $value")
                }
            }

            else -> appendLine(result.input)
        }

        if (showShrinkWarning) {
            appendLine("Warning - The shrinking process was stopped before completion. Did $shrinkSteps steps.")
        }

        if (result.error != null) {
            appendLine()
            appendLine("${prefix}Failure:")
            appendLine("--------------------")
            appendLine(result.error)
        }
    }
}
