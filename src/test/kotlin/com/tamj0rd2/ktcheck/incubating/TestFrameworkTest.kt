package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.TestFrameworkContract
import org.junit.platform.commons.annotation.Testable

@Testable
class TestFrameworkTest : TestFrameworkContract, GenBuilders by Gen.Companion
