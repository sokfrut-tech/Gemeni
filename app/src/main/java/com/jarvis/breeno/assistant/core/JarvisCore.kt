package com.jarvis.breeno.assistant.core

import android.content.Context
import com.jarvis.breeno.assistant.knowledge.KnowledgeDomainCatalogRepository

class JarvisCore(private val context: Context) {
    val tools = JarvisToolRegistry()

    /** Bundled local metadata only; this does not download or index factual documents. */
    val knowledgeCatalog by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        KnowledgeDomainCatalogRepository(context).load()
    }

    suspend fun handle(text: String) = tools.dispatch(text)
}
