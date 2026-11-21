package com.jarvis.breeno.assistant.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMediaGeneratorTest {
    @Test
    fun buildsEncodedPollinationsUrlWithStyleAndDimensions() {
        val url = JarvisMediaGenerator.buildPollinationsImageUrl(
            prompt = "Красный дракон над горами",
            style = "Киберпанк",
            width = 768,
            height = 432,
            seed = 777,
            model = "flux",
        )
        assertTrue(url.startsWith("https://image.pollinations.ai/prompt/"))
        assertTrue(url.contains("width=768&height=432&seed=777&model=flux&nologo=true"))
        assertTrue(!url.contains(" "))
    }

    @Test
    fun buildsThreeProgressiveStoryboardScenes() {
        val scenes = JarvisMediaGenerator.buildStoryboardPrompts("корабль летит в космосе", "Кино")
        assertEquals(3, scenes.size)
        assertTrue(scenes[0].contains("корабль летит в космосе"))
        assertTrue(scenes[0] != scenes[1] && scenes[1] != scenes[2])
    }

    @Test
    fun buildsAndParsesA1111AndOpenAiImagePayloads() {
        val a1111Req = JarvisMediaGenerator.buildA1111RequestJson("кот в космосе", "Фото", 512, 512)
        assertTrue(a1111Req.contains("\"width\":512"))
        val a1111B64 = JarvisMediaGenerator.parseA1111Base64Image("""{"images":["data:image/png;base64,QUJDREVGRw=="]}""")
        assertEquals("QUJDREVGRw==", a1111B64)

        val openAiReq = JarvisMediaGenerator.buildOpenAiImageRequestJson("gpt-image-1", "робот", "3D Арт", 768, 432)
        assertTrue(openAiReq.contains("\"size\":\"1536x1024\""))

        val b64Payload = JarvisMediaGenerator.parseOpenAiImageResult("""{"data":[{"b64_json":"SGVsbG8="}]}""")
        assertTrue(b64Payload is OpenAiImagePayload.Base64Data)
        assertEquals("SGVsbG8=", (b64Payload as OpenAiImagePayload.Base64Data).base64)

        val urlPayload = JarvisMediaGenerator.parseOpenAiImageResult("""{"data":[{"url":"https://cdn.example.com/img.png"}]}""")
        assertTrue(urlPayload is OpenAiImagePayload.RemoteUrl)
        assertEquals("https://cdn.example.com/img.png", (urlPayload as OpenAiImagePayload.RemoteUrl).url)
    }
}
