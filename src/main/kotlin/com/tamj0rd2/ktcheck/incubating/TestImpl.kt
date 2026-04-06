package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.Falsification
import com.tamj0rd2.ktcheck.Property
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraint
import com.tamj0rd2.ktcheck.TestConfig
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
                shrunk = result.shrunkFalsification.takeIf { it.input != result.originalFalsification.input },
                shrinkSteps = result.shrinkSteps
            )
        }
    }

    private fun runIteration(iteration: Int): TestIterationResult {
        val seed = config.seed.next(iteration)
        val genResult = gen.generate(GenContext.new(seed)).orThrow()
        val originalError = property.falsify(genResult.value)
            .also { printGeneratedValue(it, iteration, genResult.value) }
            ?: return TestIterationResult.DidNotFalsify

        val originalFalsification = Falsification(genResult.value, originalError.error)

        val (shrunkFalsification, shrinkSteps) = config.shrinkingConstraintFactory.new().use {
            findSimplestFalsification(
                originalGeneratedValue = genResult,
                originalFalsification = originalFalsification,
                shrinkingConstraint = it,
            )
        }

        return TestIterationResult.DidFalsify(
            originalFalsification = originalFalsification,
            shrunkFalsification = shrunkFalsification,
            shrinkSteps = shrinkSteps
        )
    }

    private fun findSimplestFalsification(
        originalGeneratedValue: GeneratedValue<T>,
        originalFalsification: Falsification<T>,
        shrinkingConstraint: ShrinkingConstraint,
    ): Pair<Falsification<T>, Int> {
        shrinkingConstraint.onStart()

        // todo: these 2 values are entirely coupled. They should probably be a single thing.
        var simplestFalsification = originalFalsification
        var shrinkCandidates = originalGeneratedValue.shrinks.iterator()

        val seenValues = mutableSetOf<T>()

        while (shrinkingConstraint.shouldKeepShrinking() && shrinkCandidates.hasNext()) {
            val shrunkInput = gen.generate(shrinkCandidates.next()).onFailure { continue }
            
            if (!seenValues.add(shrunkInput.value)) continue

            shrinkingConstraint.onStep()

            val testResult = property.falsify(shrunkInput.value)
                .also { printShrinkStep(it, seenValues.size, shrunkInput.value) }
                ?: continue

            simplestFalsification = Falsification(shrunkInput.value, testResult.error)
            shrinkCandidates = shrunkInput.shrinks.iterator()
        }

        return simplestFalsification to seenValues.size
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
            val shrunkFalsification: Falsification<T>,
            val shrinkSteps: Int,
        ) : TestIterationResult

        data object DidNotFalsify : TestIterationResult
    }
}
