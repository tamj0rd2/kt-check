package com.tamj0rd2.ktcheck.current

import com.tamj0rd2.ktcheck.GenBuilders
import com.tamj0rd2.ktcheck.contracts.ShrinkingChallengeContract
import org.junit.platform.commons.annotation.Testable

@Testable
class ShrinkingChallengeTest : ShrinkingChallengeContract, GenBuilders by Gen.Companion
