    @Test fun guadeloupeNationalNumberUses590NotMetropolitan33() {
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("0690 12 34 56"))
        assertEquals("+590690123456", CallRuleEngine.normalizeNumber("+590 690 12 34 56"))
        assertEquals("+33612345678", CallRuleEngine.normalizeNumber("06 12 34 56 78"))
    }

    @Test fun guadeloupeNationalAndE164MatchTheSameExactRule() {
        val blocked = "a".repeat(64)
        val fingerprint: (String) -> Set<String> = {
            setOf(if (it == "0690123456") blocked else "b".repeat(64))
        }
        val engine = CallRuleEngine(setOf(blocked), fingerprintsForNumber = fingerprint)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+590690123456").action)
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("0690123456").action)
    }

    @Test fun numberOutsideDirectoryIsSilencedOnlyWhenDirectoryIsReady() {
        val known = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { it == "+590690123456" }
        )
        assertEquals(CallRuleEngine.Action.ALLOW, known.evaluate("0690 12 34 56").action)
        val unknown = CallRuleEngine(
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { false }
        )
        val silenced = unknown.evaluate("+33 6 12 34 56 78")
        assertEquals(CallRuleEngine.Action.SILENCE, silenced.action)
        assertEquals("NOT_IN_DIRECTORY", silenced.reason)
        assertEquals(
            CallRuleEngine.Action.ALLOW,
            CallRuleEngine(silenceOutsideDirectory = true, directoryReady = false).evaluate("+33612345678").action
        )
    }

    @Test fun unavailableNumberIsSilencedOnlyAfterDirectoryIsReady() {
        val armed = CallRuleEngine(silenceOutsideDirectory = true, directoryReady = true)
        assertEquals(CallRuleEngine.Action.SILENCE, armed.evaluate(null).action)
        assertEquals(CallRuleEngine.Action.ALLOW, CallRuleEngine().evaluate(null).action)
    }

    @Test fun explicitBlockStillWinsOverDirectorySilence() {
        val fingerprint = "a".repeat(64)
        val engine = CallRuleEngine(
            blockedNumberHashes = setOf(fingerprint),
            fingerprintsForNumber = { setOf(fingerprint) },
            silenceOutsideDirectory = true,
            directoryReady = true,
            directoryContains = { false }
        )
        assertEquals(CallRuleEngine.Action.BLOCK, engine.evaluate("+33612345678").action)
    }
