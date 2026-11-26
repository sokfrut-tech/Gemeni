package com.jarvis.breeno.assistant.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceGgufBrowserTest {
    @Test
    fun parsesSearchReposAndRepoGgufFiles() {
        val searchJson = """
            [
              {"id":"IlyaGusev/saiga_llama3_8b_gguf","downloads":12500,"likes":84,"tags":["gguf","ru"]},
              {"id":"bartowski/Dolphin3.0-Qwen2.5-3b-GGUF","downloads":54000,"likes":210,"tags":["gguf"]}
            ]
        """.trimIndent()
        val repos = HuggingFaceGgufBrowser.parseSearchResults(searchJson)
        assertEquals(2, repos.size)
        assertEquals("IlyaGusev/saiga_llama3_8b_gguf", repos[0].repoId)
        assertEquals(12500L, repos[0].downloads)

        val treeJson = """
            [
              {"type":"file","path":"README.md","size":1024},
              {"type":"file","path":"model-q8_0.gguf","size":8000000000},
              {"type":"file","path":"model-q4_k_m.gguf","size":4000000000}
            ]
        """.trimIndent()
        val files = HuggingFaceGgufBrowser.parseRepoGgufFiles("IlyaGusev/saiga_llama3_8b_gguf", treeJson)
        assertEquals(2, files.size)
        // Sorted ascending by sizeBytes
        assertEquals("model-q4_k_m.gguf", files[0].fileName)
        assertTrue(files[0].downloadUrl.startsWith("https://huggingface.co/IlyaGusev/saiga_llama3_8b_gguf/resolve/main/"))
    }
}
