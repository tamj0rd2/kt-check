package com.tamj0rd2.ktcheck.testing

import com.tamj0rd2.ktcheck.testing.TestConfig.Companion.SYSTEM_PROPERTY_TEST_ITERATIONS
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class TestConfigTest {
    @Test
    fun `defaults to the iterations specified via the system property`() {
        System.setProperty(SYSTEM_PROPERTY_TEST_ITERATIONS, "50")
        expectThat(TestConfig()).get { iterations }.isEqualTo(50)
    }

    @Test
    fun `defaults to 1000 iterations when no value is specified and no system property has been set`() {
        System.clearProperty(SYSTEM_PROPERTY_TEST_ITERATIONS)
        expectThat(TestConfig()).get { iterations }.isEqualTo(1000)
    }

    @Test
    fun `can overwrite the default iterations via the constructor`() {
        expectThat(TestConfig().withIterations(iterations = 123)).get { iterations }.isEqualTo(123)
    }
}
