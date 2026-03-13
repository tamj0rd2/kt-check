package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.Property
import com.tamj0rd2.ktcheck.PropertyFalsifiedException
import com.tamj0rd2.ktcheck.ShrinkingConstraint
import com.tamj0rd2.ktcheck.TestConfig
import com.tamj0rd2.ktcheck.core.Seed
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
    private val edgeCases = gen.edgeCases(ProviderTree.new(Seed(0)))

    fun run() {
        val startingIteration = (config.replayIteration ?: 1)
        repeat(config.iterations) {
            val iteration = startingIteration + it

            val result = runIteration(iteration - 1)
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

    private fun runIteration(iterationIdx: Int): TestIterationResult {
        val (input, mode) = if (iterationIdx in edgeCases.indices) {
            edgeCases.elementAt(iterationIdx) to GenerationMode.EdgeCase
        } else {
            gen.generate(ProviderTree.new(config.seed.next(iterationIdx)), GenerationMode.Random)
                .orThrow() to GenerationMode.Random
        }

        val originalFalsification = property.test(input.value) ?: return TestIterationResult.DidNotFalsify

        val (shrunkFalsification, shrinkSteps) = config.shrinkingConstraintFactory.new().use {
            findSimplestFalsification(
                originalGeneratedValue = input,
                originalFalsification = originalFalsification,
                shrinkingConstraint = it,
                mode = mode,
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
        originalFalsification: Property.Falsification<T>,
        shrinkingConstraint: ShrinkingConstraint,
        mode: GenerationMode,
    ): Pair<Property.Falsification<T>, Int> {
        shrinkingConstraint.onStart()

        // todo: these 2 values are entirely coupled. They should probably be a single thing.
        var simplestFalsification = originalFalsification
        var candidates = originalGeneratedValue.shrinks.iterator()

        val seenValues = mutableSetOf<T>()

        while (shrinkingConstraint.shouldKeepShrinking() && candidates.hasNext()) {
            val shrunkInput = gen.generate(candidates.next(), mode).onFailure { continue }
            if (!seenValues.add(shrunkInput.value)) continue

            shrinkingConstraint.onStep()

            val testResult = property.test(shrunkInput.value) ?: continue

            simplestFalsification = testResult
            candidates = shrunkInput.shrinks.iterator()
        }

        return simplestFalsification to seenValues.size
    }

    private sealed interface TestIterationResult {
        data class DidFalsify<T>(
            val originalFalsification: Property.Falsification<T>,
            val shrunkFalsification: Property.Falsification<T>,
            val shrinkSteps: Int,
        ) : TestIterationResult

        data object DidNotFalsify : TestIterationResult
    }
}
