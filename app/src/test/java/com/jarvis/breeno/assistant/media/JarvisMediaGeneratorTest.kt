package com.jarvis.breeno.assistant.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JarvisMediaGeneratorTest {
    @Test
    fun buildsEncodedPollinationsUrlWithStyleAndDimensionsWithoutPaidModelByDefault() {
        val defaultUrl = JarvisMediaGenerator.buildPollinationsImageUrl(
            prompt = "Красный дракон над горами",
            style = "Киберпанк",
            width = 768,
            height = 432,
            seed = 777,
        )
        assertTrue(defaultUrl.startsWith("https://image.pollinations.ai/prompt/"))
        assertTrue(defaultUrl.contains("width=768&height=432&seed=777&nologo=true"))
        assertTrue(!defaultUrl.contains("&model="))
        assertTrue(!defaultUrl.contains(" "))

        val customModelUrl = JarvisMediaGenerator.buildPollinationsImageUrl(
            prompt = "Космос",
            style = "Фото",
            width = 512,
            height = 512,
            seed = 1,
            model = "turbo",
        )
        assertTrue(customModelUrl.contains("&model=turbo"))
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
        val a1111Req = JarvisMediaGenerator.buildA1111RequestJson(
            prompt = "кот в космосе",
            style = "Фото",
            width = 512,
            height = 512,
            negativePrompt = "blurry, watermark",
            steps = 30,
            cfgScale = 6.5f,
            seed = 12345,
        )
        assertTrue(a1111Req.contains("\"width\":512"))
        assertTrue(a1111Req.contains("\"negative_prompt\":\"blurry, watermark\""))
        assertTrue(a1111Req.contains("\"steps\":30"))
        assertTrue(a1111Req.contains("\"cfg_scale\":6.5"))
        assertTrue(a1111Req.contains("\"seed\":12345"))
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

    @Test
    fun normalizesServerUrlsAndHandlesComfyUiWorkflowAndHistory() {
        assertEquals("http://192.168.1.50:8188", JarvisMediaGenerator.normalizeComfyBaseUrl("http://192.168.1.50:8188/prompt/"))
        assertEquals("https://gpu.example.com/comfy", JarvisMediaGenerator.normalizeComfyBaseUrl("https://gpu.example.com/comfy/view"))
        assertEquals("http://192.168.1.50:7860/sdapi/v1/txt2img", JarvisMediaGenerator.normalizeA1111Txt2ImgUrl("http://192.168.1.50:7860/"))
        assertEquals("http://192.168.1.50:7860/sdapi/v1/txt2img", JarvisMediaGenerator.normalizeA1111Txt2ImgUrl("http://192.168.1.50:7860/sdapi/v1/txt2img"))

        val objectInfo = """{"CheckpointLoaderSimple":{"input":{"required":{"ckpt_name":[["sd_xl_base_1.0.safetensors","dreamshaper.safetensors"]]}}}}"""
        assertEquals("sd_xl_base_1.0.safetensors", JarvisMediaGenerator.parseComfyFirstCheckpoint(objectInfo))
        assertNull(JarvisMediaGenerator.parseComfyFirstCheckpoint("""{}"""))

        val workflow = JarvisMediaGenerator.buildComfyUiWorkflowJson(
            prompt = "неон и дождь",
            negativePrompt = "lowres",
            style = "Киберпанк",
            width = 768,
            height = 432,
            steps = 28,
            cfgScale = 7.5f,
            seed = 987,
            checkpointName = "sd_xl_base_1.0.safetensors",
        )
        assertTrue(workflow.contains("\"ckpt_name\":\"sd_xl_base_1.0.safetensors\""))
        assertTrue(workflow.contains("\"steps\":28"))
        assertTrue(workflow.contains("\"client_id\":\"jarvis-android\""))

        val promptId = JarvisMediaGenerator.parseComfyPromptId("""{"prompt_id":"abc-123","number":1}""")
        assertEquals("abc-123", promptId)

        val historyJson = """
            {"abc-123":{"outputs":{"9":{"images":[{"filename":"JarvisStudio_00001_.png","subfolder":"sub","type":"output"}]}}}}
        """.trimIndent()
        val imgRef = JarvisMediaGenerator.parseComfyHistoryImageRef(historyJson, "abc-123")
        assertNotNull(imgRef)
        assertEquals("JarvisStudio_00001_.png", imgRef!!.filename)
        assertEquals("sub", imgRef.subfolder)
        assertEquals("output", imgRef.type)
    }
}
