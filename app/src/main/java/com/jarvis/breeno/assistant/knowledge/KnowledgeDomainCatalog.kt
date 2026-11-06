package com.jarvis.breeno.assistant.knowledge

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Read-only metadata describing the knowledge areas supported by Jarvis.
 * This is a domain index, not a factual knowledge corpus or a trained model.
 */
data class KnowledgeDomain(
    val id: String,
    val title: String,
    val topics: List<String>,
    val epistemicStatus: String?,
    val safetyProfile: String?
)

data class KnowledgeDomainCatalog(
    val schemaVersion: Int,
    val catalogId: String,
    val language: String,
    val storagePolicy: String,
    val networkPolicy: String,
    val implementationStatus: String,
    val domains: List<KnowledgeDomain>,
    val sourceRecordRequired: List<String>,
    val answerQualityRequired: List<String>,
    val noImplicitTrainingFromUserData: Boolean,
    val noBackgroundNetworkRequests: Boolean
) {
    fun domain(id: String): KnowledgeDomain? = domains.firstOrNull { it.id == id }
}

/**
 * Loads only the bundled asset. No HTTP client, downloader, or implicit sync exists here.
 */
class KnowledgeDomainCatalogRepository(context: Context) {
    private val appContext = context.applicationContext

    fun load(): KnowledgeDomainCatalog =
        appContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { reader ->
            KnowledgeDomainCatalogParser.parse(reader.readText())
        }

    companion object {
        const val ASSET_PATH = "knowledge/domain_catalog.json"
    }
}

object KnowledgeDomainCatalogParser {
    fun parse(json: String): KnowledgeDomainCatalog {
        val root = JsonParser.parseString(json)
        require(root.isJsonObject) { "Каталог должен быть JSON-объектом" }
        val obj = root.asJsonObject

        val schemaVersion = obj.requiredInt("schema_version")
        require(schemaVersion == 1) { "Неподдерживаемая версия каталога: $schemaVersion" }

        val storagePolicy = obj.requiredString("storage_policy")
        require(storagePolicy == "local_only") { "Каталог должен храниться только локально" }

        val networkPolicy = obj.requiredString("network_policy")
        require(networkPolicy == "explicit_user_consent_per_research_or_download") {
            "Сетевые исследования и загрузки требуют отдельного согласия пользователя"
        }

        val domains = obj.requiredArray("domains").map { element ->
            require(element.isJsonObject) { "Каждая область должна быть объектом" }
            val domain = element.asJsonObject
            KnowledgeDomain(
                id = domain.requiredString("id"),
                title = domain.requiredString("title"),
                topics = domain.requiredStringArray("topics"),
                epistemicStatus = domain.optionalString("epistemic_status"),
                safetyProfile = domain.optionalString("safety_profile")
            )
        }
        require(domains.isNotEmpty()) { "Каталог областей знаний пуст" }
        require(domains.map { it.id }.distinct().size == domains.size) {
            "Идентификаторы областей должны быть уникальными"
        }

        val sourceFields = obj.requiredStringArray("source_record_required")
        val answerFields = obj.requiredStringArray("answer_quality_required")
        require(sourceFields.containsAll(listOf("publisher_or_author", "title", "license", "sha256"))) {
            "В каталоге отсутствуют обязательные поля происхождения источников"
        }
        require(answerFields.containsAll(listOf("assumptions", "inputs_and_units", "source_citations"))) {
            "В каталоге отсутствуют обязательные требования к качеству ответа"
        }

        val noImplicitTraining = obj.requiredBoolean("no_implicit_training_from_user_data")
        val noBackgroundNetwork = obj.requiredBoolean("no_background_network_requests")
        require(noImplicitTraining) { "Неявное обучение на пользовательских данных запрещено" }
        require(noBackgroundNetwork) { "Фоновые сетевые запросы запрещены" }

        return KnowledgeDomainCatalog(
            schemaVersion = schemaVersion,
            catalogId = obj.requiredString("catalog_id"),
            language = obj.requiredString("language"),
            storagePolicy = storagePolicy,
            networkPolicy = networkPolicy,
            implementationStatus = obj.requiredString("implementation_status"),
            domains = domains,
            sourceRecordRequired = sourceFields,
            answerQualityRequired = answerFields,
            noImplicitTrainingFromUserData = noImplicitTraining,
            noBackgroundNetworkRequests = noBackgroundNetwork
        )
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Поле '$name' должно быть строкой"
        }
        return value.asString.also { require(it.isNotBlank()) { "Поле '$name' не должно быть пустым" } }
    }

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "Поле '$name' должно быть строкой"
        }
        return value.asString
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            "Поле '$name' должно быть целым числом"
        }
        return value.asInt
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val value = get(name)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) {
            "Поле '$name' должно быть логическим значением"
        }
        return value.asBoolean
    }

    private fun JsonObject.requiredArray(name: String): JsonArray {
        val value = get(name)
        require(value != null && value.isJsonArray) { "Поле '$name' должно быть массивом" }
        return value.asJsonArray
    }

    private fun JsonObject.requiredStringArray(name: String): List<String> =
        requiredArray(name).map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                "Массив '$name' должен содержать только строки"
            }
            value.asString.also { require(it.isNotBlank()) { "Массив '$name' содержит пустую строку" } }
        }
}
