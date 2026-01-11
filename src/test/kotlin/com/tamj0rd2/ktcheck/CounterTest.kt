package com.tamj0rd2.ktcheck

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan

@Suppress("ClassName")
class CounterTest {

    @Nested
    inner class `collecting and checking unlabelled values` {
        @Test
        fun `can collect and verify percentage of single value`() {
            val counter = Counter()

            repeat(100) { counter.collect("value1") }

            counter.checkPercentages(mapOf("value1" to 100.0))
        }

        @Test
        fun `can collect and verify percentage of multiple values`() {
            val counter = Counter()

            repeat(50) { counter.collect("value1") }
            repeat(30) { counter.collect("value2") }
            repeat(20) { counter.collect("value3") }

            counter.checkPercentages(
                mapOf(
                    "value1" to 50.0,
                    "value2" to 30.0,
                    "value3" to 20.0
                )
            )
        }

        @Test
        fun `checkPercentages accepts minimum percentages not exact matches`() {
            val counter = Counter()

            repeat(50) { counter.collect("value1") }
            repeat(50) { counter.collect("value2") }

            counter.checkPercentages(
                mapOf(
                    "value1" to 40.0,  // actual is 50%, so 40% min should pass
                    "value2" to 40.0
                )
            )
        }

        @Test
        fun `checkPercentages throws when percentage is below expected minimum`() {
            val counter = Counter()

            repeat(30) { counter.collect("value1") }
            repeat(70) { counter.collect("value2") }

            expectThrows<AssertionError> {
                counter.checkPercentages(mapOf("value1" to 50.0))
            }.get { message }.isEqualTo(
                "expected the recorded percentage for 'value1' under label 'unlabelled' to be at least 50.0% but was 30.0%"
            )
        }

        @Test
        fun `checkPercentages throws when value was never recorded`() {
            val counter = Counter()
            repeat(100) { counter.collect("value1") }

            expectThrows<AssertionError> {
                counter.checkPercentages(mapOf("nonexistent" to 1.0))
            }.get { message }.isEqualTo("'unlabelled' has no recorded statistics for the value 'nonexistent'")
        }

        @Test
        fun `can collect null values`() {
            val counter = Counter()

            repeat(50) { counter.collect(null) }
            repeat(50) { counter.collect("value1") }

            counter.checkPercentages(
                mapOf(
                    null to 50.0,
                    "value1" to 50.0
                )
            )
        }
    }

    @Nested
    inner class `collecting and checking labelled values` {
        @Test
        fun `can collect and verify percentage with labels`() {
            val counter = Counter()

            repeat(60) { counter.collect("label1", "value1") }
            repeat(40) { counter.collect("label1", "value2") }

            counter.checkPercentages("label1", mapOf("value1" to 60.0, "value2" to 40.0))
        }

        @Test
        fun `different labels maintain separate statistics`() {
            val counter = Counter()

            repeat(80) { counter.collect("label1", "value1") }
            repeat(20) { counter.collect("label1", "value2") }

            repeat(30) { counter.collect("label2", "value1") }
            repeat(70) { counter.collect("label2", "value2") }

            counter.checkPercentages("label1", mapOf("value1" to 80.0, "value2" to 20.0))
            counter.checkPercentages("label2", mapOf("value1" to 30.0, "value2" to 70.0))
        }

        @Test
        fun `labelled and unlabelled collections are separate`() {
            val counter = Counter()

            repeat(100) { counter.collect("value1") }
            repeat(100) { counter.collect("label1", "value2") }

            counter.checkPercentages(mapOf("value1" to 100.0))
            counter.checkPercentages("label1", mapOf("value2" to 100.0))
        }

        @Test
        fun `checkPercentages with label throws when percentage is below minimum`() {
            val counter = Counter()

            repeat(25) { counter.collect("myLabel", "value1") }
            repeat(75) { counter.collect("myLabel", "value2") }

            expectThrows<AssertionError> {
                counter.checkPercentages("myLabel", mapOf("value1" to 50.0))
            }.get { message }.isEqualTo(
                "expected the recorded percentage for 'value1' under label 'myLabel' to be at least 50.0% but was 25.0%"
            )
        }
    }

    @Nested
    inner class `toString output` {
        @Test
        fun `formats unlabelled statistics correctly`() {
            val counter = Counter()

            repeat(50) { counter.collect("value1") }
            repeat(30) { counter.collect("value2") }
            repeat(20) { counter.collect("value3") }

            val output = counter.toString()

            expectThat(output).contains("Stats (unlabelled):")
            expectThat(output).contains("value1")
            expectThat(output).contains("value2")
            expectThat(output).contains("value3")
            expectThat(output).contains("50%")
            expectThat(output).contains("30%")
            expectThat(output).contains("20%")
        }

        @Test
        fun `formats labelled statistics correctly`() {
            val counter = Counter()

            repeat(70) { counter.collect("myLabel", "value1") }
            repeat(30) { counter.collect("myLabel", "value2") }

            val output = counter.toString()

            expectThat(output).contains("Stats (myLabel):")
            expectThat(output).contains("70%")
            expectThat(output).contains("30%")
        }

        @Test
        fun `formats multiple labelled sections`() {
            val counter = Counter()

            repeat(50) { counter.collect("label1", "value1") }
            repeat(50) { counter.collect("label2", "value2") }

            val output = counter.toString()

            expectThat(output).contains("Stats (label1):")
            expectThat(output).contains("Stats (label2):")
        }

        @Test
        fun `sorts entries by count descending`() {
            val counter = Counter()

            repeat(10) { counter.collect("low") }
            repeat(50) { counter.collect("high") }
            repeat(30) { counter.collect("medium") }

            val output = counter.toString()
            val lowIndex = output.indexOf("low")
            val mediumIndex = output.indexOf("medium")
            val highIndex = output.indexOf("high")

            expectThat(highIndex).isLessThan(mediumIndex)
            expectThat(mediumIndex).isLessThan(lowIndex)
        }
    }
}
