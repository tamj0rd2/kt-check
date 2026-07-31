package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.Falsification
import com.tamj0rd2.ktcheck.Property
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraint
import com.tamj0rd2.ktcheck.TestConfig
import kotlin.random.Random

internal fun <T> test(config: TestConfig, gen: GenV2<T>, property: Property<T>) {
    TestRunner(config = config, gen = gen, property = property).run()
}

private class TestRunner<T>(
    private val config: TestConfig,
    private val gen: GenV2<T>,
    private val property: Property<T>,
) {
    fun run() {
        // todo: could do edge cases for the first x % of iterations, then gradually reduce from there.
        val startingIteration = (config.replayIteration ?: 1)
        repeat(config.effectiveIterations) {
            val iteration = startingIteration + it

            val result = runIteration(iteration)
            if (result !is TestIterationResult.DidFalsify<*>) return@repeat

            throw PropertyFalsifiedException(
                seed = config.seed.value,
                iteration = iteration,
                original = result.originalFalsification,
                shrunk = result.shrinking.falsification.takeIf { f -> f.input != result.originalFalsification.input },
                shrinkSteps = result.shrinking.steps,
                shrinkingConstrained = result.shrinking.wasConstrained,
            )
        }
    }

    private fun runIteration(iteration: Int): TestIterationResult {
        val seed = config.seed.next(iteration)
        val context = RandomChoices(Random(seed.value))
        val originalValue = gen.generate(context)
        val originalError = property.falsify(originalValue)
            .also { printGeneratedValue(it, iteration, originalValue) }
            ?: return TestIterationResult.DidNotFalsify

        val originalFalsification = Falsification(originalValue, originalError.error)

        val shrinking = config.shrinkingConstraintFactory.new().use {
            findSimplestFalsification(
                originalContext = context,
                originalFalsification = originalFalsification,
                shrinkingConstraint = it,
            )
        }

        return TestIterationResult.DidFalsify(
            originalFalsification = originalFalsification,
            shrinking = shrinking,
        )
    }

    private data class ShrinkingResult<T>(
        val falsification: Falsification<T>,
        val steps: Int,
        val wasConstrained: Boolean,
    )

    private fun findSimplestFalsification(
        originalContext: Choices,
        originalFalsification: Falsification<T>,
        shrinkingConstraint: ShrinkingConstraint,
    ): ShrinkingResult<T> {
        shrinkingConstraint.onStart()

        var simplestFalsification = originalFalsification
        var shrinkCandidates = originalContext.shrinks().iterator()

        val seenValues = mutableSetOf<T>()

        while (shrinkCandidates.hasNext()) {
            if (shrinkingConstraint.shouldStopShrinking()) return ShrinkingResult(
                falsification = simplestFalsification,
                steps = seenValues.size,
                wasConstrained = true
            )

            val shrunkContext = shrinkCandidates.next()
            // todo: if generation fails, skip to next shrink.
            val shrunkInput = gen.generate(shrunkContext)

            if (!seenValues.add(shrunkInput)) continue

            shrinkingConstraint.onStep()

            val testResult = property.falsify(shrunkInput)
                .also { printShrinkStep(it, seenValues.size, shrunkInput) }
                ?: continue

            simplestFalsification = Falsification(shrunkInput, testResult.error)
            shrinkCandidates = shrunkContext.shrinks().iterator()
        }

        return ShrinkingResult(
            falsification = simplestFalsification,
            steps = seenValues.size,
            wasConstrained = false
        )
    }

    private fun printGeneratedValue(falsified: Property.Falsified?, iteration: Int, input: T) {
        if (config.printShrinkSteps) {
            val tag = if (falsified == null) "not" else "was"
            println("iteration $iteration ($tag falsified): $input")
        }
    }

    private fun printShrinkStep(falsified: Property.Falsified?, step: Int, input: T) {
        if (config.printShrinkSteps) {
            val tag = if (falsified == null) "not" else "was"
            println("step $step ($tag falsified): $input")
        }
    }

    private sealed interface TestIterationResult {
        data class DidFalsify<T>(
            val originalFalsification: Falsification<T>,
            val shrinking: ShrinkingResult<T>,
        ) : TestIterationResult

        data object DidNotFalsify : TestIterationResult
    }
}
