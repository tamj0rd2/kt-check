package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.Falsification
import com.tamj0rd2.ktcheck.Property
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraint
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.asPercentageOf
import com.tamj0rd2.ktcheck.stats.Percentage.Companion.percent
import dev.forkhandles.result4k.onFailure
import dev.forkhandles.result4k.orThrow

internal fun <T> test(config: TestConfig, gen: Gen<T>, property: Property<T>) {
    TestRunner(config = config, gen = gen, property = property).run()
}

private class TestRunner<T>(
    private val config: TestConfig,
    private val gen: Gen<T>,
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
                shrunk = result.shrinking.falsification.takeIf { it.input != result.originalFalsification.input },
                shrinkSteps = result.shrinking.steps,
                shrinkingConstrained = result.shrinking.wasConstrained,
            )
        }
    }

    private fun runIteration(iteration: Int): TestIterationResult {
        val seed = config.seed.next(iteration)
        val ctx = GenContext.new(
            seed = seed,
            influenceEdgeCases = let {
                // todo: can I get rid of this yet?
                val progress = iteration asPercentageOf config.iterations
                val edgeCaseChance = (100.percent - progress).coerceIn(5.percent..15.percent)
                InfluenceGeneration.BasedOnRng(edgeCaseChance)
            }
        )

        val genResult = gen.generate(ctx).orThrow()
        val originalError = property.falsify(genResult.value)
            .also { printGeneratedValue(it, iteration, genResult.value) }
            ?: return TestIterationResult.DidNotFalsify

        val originalFalsification = Falsification(genResult.value, originalError.error)

        val shrinking = config.shrinkingConstraintFactory.new().use {
            findSimplestFalsification(
                originalGeneratedValue = genResult,
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
        originalGeneratedValue: GeneratedValue<T>,
        originalFalsification: Falsification<T>,
        shrinkingConstraint: ShrinkingConstraint,
    ): ShrinkingResult<T> {
        shrinkingConstraint.onStart()

        // todo: these 2 values are entirely coupled. They should probably be a single thing.
        var simplestFalsification = originalFalsification
        var shrinkCandidates = originalGeneratedValue.shrinks.iterator()

        val seenValues = mutableSetOf<T>()

        while (shrinkCandidates.hasNext()) {
            if (shrinkingConstraint.shouldStopShrinking()) return ShrinkingResult(
                falsification = simplestFalsification,
                steps = seenValues.size,
                wasConstrained = true
            )

            val shrunkInput = gen.generate(shrinkCandidates.next()).onFailure { continue }

            if (!seenValues.add(shrunkInput.value)) continue

            shrinkingConstraint.onStep()

            val testResult = property.falsify(shrunkInput.value)
                .also { printShrinkStep(it, seenValues.size, shrunkInput.value) }
                ?: continue

            simplestFalsification = Falsification(shrunkInput.value, testResult.error)
            shrinkCandidates = shrunkInput.shrinks.iterator()
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
