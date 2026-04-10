package com.tamj0rd2.ktcheck.incubating

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.ShrinkingChallengeContract
import org.junit.platform.commons.annotation.Testable

@Testable
class IncubatingShrinkingChallengeTest : ShrinkingChallengeContract, GenBuilders by Gen.Companion
