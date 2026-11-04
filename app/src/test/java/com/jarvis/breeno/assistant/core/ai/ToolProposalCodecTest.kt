package com.jarvis.breeno.assistant.core.ai

import org.junit.Assert.*
import org.junit.Test

class ToolProposalCodecTest {
    @Test fun acceptsSafeSearchProposal() {
        val result = ToolProposalCodec.validate("""{"tool":"web_search","arguments":{"query":"погода"}}""")
        assertNotNull(result)
        assertEquals(ProposalRisk.SAFE, result?.risk)
    }

    @Test fun dialRequiresConfirmation() {
        val result = ToolProposalCodec.validate("""{"tool":"open_dialer","arguments":{"number":"+7 999 123-45-67"}}""")
        assertEquals(ProposalRisk.CONFIRM, result?.risk)
    }

    @Test fun rejectsUnknownToolAndExtraFields() {
        assertNull(ToolProposalCodec.validate("""{"tool":"shell","arguments":{"command":"whoami"}}"""))
        assertNull(ToolProposalCodec.validate("""{"tool":"tell_time","arguments":{},"execute":true}"""))
    }

    @Test fun rejectsMissingAndNonStringArguments() {
        assertNull(ToolProposalCodec.validate("""{"tool":"web_search","arguments":{}}"""))
        assertNull(ToolProposalCodec.validate("""{"tool":"web_search","arguments":{"query":123}}"""))
    }

    @Test fun rejectsOversizedAndMalformedInput() {
        assertNull(ToolProposalCodec.validate("""{"tool":"web_search","arguments":{"query":"${"x".repeat(241)}"}}"""))
        assertNull(ToolProposalCodec.validate("{not-json"))
    }

    @Test fun rejectsDialInjectionCharacters() {
        assertNull(ToolProposalCodec.validate("""{"tool":"open_dialer","arguments":{"number":"123;ACTION_CALL"}}"""))
    }

    @Test fun rejectsUnknownArgumentKey() {
        assertNull(ToolProposalCodec.validate("""{"tool":"navigate","arguments":{"destination":"Berlin","url":"https://evil"}}"""))
    }
}
