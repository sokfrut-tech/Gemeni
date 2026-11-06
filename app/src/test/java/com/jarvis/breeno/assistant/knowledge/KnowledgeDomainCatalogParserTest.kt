package com.jarvis.breeno.assistant.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeDomainCatalogParserTest {
    @Test
    fun parsesCatalogAndKeepsScienceAndAstrologyEpistemicallySeparate() {
        val catalog = KnowledgeDomainCatalogParser.parse(validCatalog())

        assertEquals("ru", catalog.language)
        assertEquals("local_only", catalog.storagePolicy)
        assertEquals(2, catalog.domains.size)
        assertEquals("empirical_science", catalog.domain("astronomy")?.epistemicStatus)
        assertEquals("non_empirical_cultural_system", catalog.domain("astrology")?.epistemicStatus)
        assertTrue(catalog.noImplicitTrainingFromUserData)
        assertTrue(catalog.noBackgroundNetworkRequests)
    }

    @Test
    fun rejectsRemoteStoragePolicy() {
        val json = validCatalog().replace("\"local_only\"", "\"cloud_sync\"")
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDomainCatalogParser.parse(json)
        }
    }

    @Test
    fun rejectsDuplicateDomainIds() {
        val json = validCatalog().replace(
            "\"domains\":[",
            "\"domains\":[{\"id\":\"astronomy\",\"title\":\"Дубликат\",\"topics\":[\"x\"]},"
        )
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDomainCatalogParser.parse(json)
        }
    }

    @Test
    fun rejectsImplicitTrainingAndBackgroundNetworkAccess() {
        val training = validCatalog().replace(
            "\"no_implicit_training_from_user_data\":true",
            "\"no_implicit_training_from_user_data\":false"
        )
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDomainCatalogParser.parse(training)
        }

        val network = validCatalog().replace(
            "\"no_background_network_requests\":true",
            "\"no_background_network_requests\":false"
        )
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDomainCatalogParser.parse(network)
        }
    }

    @Test
    fun lookupReturnsNullForUnknownDomain() {
        val catalog = KnowledgeDomainCatalogParser.parse(validCatalog())
        assertNotNull(catalog.domain("astronomy"))
        assertFalse(catalog.domains.any { it.id == "unknown" })
        assertEquals(null, catalog.domain("unknown"))
    }

    private fun validCatalog() = """
        {
          "schema_version": 1,
          "catalog_id": "test",
          "language": "ru",
          "storage_policy": "local_only",
          "network_policy": "explicit_user_consent_per_research_or_download",
          "implementation_status": "catalog_only_not_a_knowledge_corpus",
          "domains": [
            {"id":"astronomy","title":"Астрономия","topics":["звёзды"],"epistemic_status":"empirical_science"},
            {"id":"astrology","title":"Астрология","topics":["история"],"epistemic_status":"non_empirical_cultural_system"}
          ],
          "source_record_required": ["publisher_or_author","title","license","sha256"],
          "answer_quality_required": ["assumptions","inputs_and_units","source_citations"],
          "no_implicit_training_from_user_data": true,
          "no_background_network_requests": true
        }
    """.trimIndent()
}
