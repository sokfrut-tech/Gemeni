package com.jarvis.breeno.assistant.core.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalOnlyJarvisModelProviderTest {
    private val provider = LocalOnlyJarvisModelProvider()

    @Test
    fun searchCommandProducesValidatedOfflineProposal() = runBlocking {
        assertTrue(provider.isLocalOnly)
        val result = provider.propose(JarvisModelRequest("Джарвис, найди погоду завтра"))
        assertTrue(result is JarvisModelResult.Proposal)
        val proposal = (result as JarvisModelResult.Proposal)
        assertEquals("local-command-parser", proposal.source)
        val validated = ToolProposalCodec.validate(proposal.json)
        assertEquals("web_search", validated?.proposal?.tool)
        assertEquals("погоду завтра", validated?.proposal?.arguments?.get("query"))
        assertEquals(ProposalRisk.SAFE, validated?.risk)
    }

    @Test
    fun dialCommandIsTypedAsConfirmationRequired() = runBlocking {
        val result = provider.propose(JarvisModelRequest("Джарвис, позвони +49 151 23456789"))
        assertTrue(result is JarvisModelResult.Proposal)
        val validated = ToolProposalCodec.validate((result as JarvisModelResult.Proposal).json)
        assertEquals(ProposalRisk.CONFIRM, validated?.risk)
    }

    @Test
    fun unsupportedSentenceDoesNotInventAnAction() = runBlocking {
        val result = provider.propose(JarvisModelRequest("Расскажи интересную историю"))
        assertTrue(result is JarvisModelResult.NoAction)
    }

    @Test
    fun rejectsEmptyOrMalformedDialerNumbers() {
        assertEquals(null, ToolProposalCodec.validate("""{"tool":"open_dialer","arguments":{"number":""}}"""))
        assertEquals(null, ToolProposalCodec.validate("""{"tool":"open_dialer","arguments":{"number":"++49 151 23456789"}}"""))
        assertEquals(null, ToolProposalCodec.validate("""{"tool":"open_dialer","arguments":{"number":"49+15123456789"}}"""))
    }
}
