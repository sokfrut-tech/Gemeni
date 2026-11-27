package com.jarvis.breeno.assistant.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun buildsPonyClipSkip2AndWan22I2vSingleWindowWorkflowAndParsesVideoOutput() {
        val ponyOnly = JarvisMediaGenerator.buildComfyUiWorkflowJson(
            prompt = "киберпанк пилот",
            negativePrompt = "",
            style = "Фото",
            width = 1024,
            height = 1024,
            steps = 30,
            cfgScale = 5.5f,
            seed = 42,
            checkpointName = JarvisMediaGenerator.DEFAULT_PONY_CHECKPOINT,
            usePonyClipSkip2 = true,
        )
        assertTrue(ponyOnly.contains("\"class_type\":\"CLIPSetLastLayer\""))
        assertTrue(ponyOnly.contains("\"stop_at_clip_layer\":-2"))
        assertTrue(ponyOnly.contains("score_9, score_8_up, score_7_up"))
        assertTrue(ponyOnly.contains("\"sampler_name\":\"dpmpp_2m\""))
        assertTrue(ponyOnly.contains("\"scheduler\":\"karras\""))

        val turboPipe = JarvisMediaGenerator.buildComfyUiPonyToWan22I2vWorkflowJson(
            imagePrompt = "неоновый мегаполис под дождём",
            motionPrompt = "slow camera push-in, glowing rain",
            videoWidth = 650,
            videoHeight = 490,
            frames = 50,
            seed = 101,
            turboLora = true,
        )
        assertTrue(turboPipe.contains("\"class_type\":\"CLIPSetLastLayer\""))
        assertTrue(turboPipe.contains("\"class_type\":\"LoraLoaderModelOnly\""))
        assertTrue(turboPipe.contains("\"class_type\":\"ModelSamplingSD3\""))
        assertTrue(turboPipe.contains("\"class_type\":\"WanImageToVideo\""))
        assertTrue(turboPipe.contains("\"class_type\":\"KSamplerAdvanced\""))
        assertTrue(turboPipe.contains("\"class_type\":\"CreateVideo\""))
        assertTrue(turboPipe.contains("\"class_type\":\"SaveVideo\""))
        // 650->656, 490->496 (multiples of 16), 50->49 (4k+1)
        assertTrue(turboPipe.contains("\"width\":656"))
        assertTrue(turboPipe.contains("\"height\":496"))
        assertTrue(turboPipe.contains("\"length\":49"))
        assertTrue(turboPipe.contains("\"steps\":4"))

        val basePipe = JarvisMediaGenerator.buildComfyUiPonyToWan22I2vWorkflowJson(
            imagePrompt = "космос",
            turboLora = false,
        )
        assertFalse(basePipe.contains("LoraLoaderModelOnly"))
        assertTrue(basePipe.contains("\"steps\":20"))
        assertTrue(basePipe.contains("\"end_at_step\":10"))

        val mixedHistory = """
            {
              "vid-1": {
                "outputs": {
                  "17": {"images": [{"filename": "JarvisPonyKeyframe_00001_.png", "subfolder": "", "type": "output"}]},
                  "35": {"images": [{"filename": "JarvisWan22_00001.mp4", "subfolder": "video", "type": "output"}]}
                }
              }
            }
        """.trimIndent()
        val videoRef = JarvisMediaGenerator.parseComfyHistoryMediaRef(mixedHistory, "vid-1", preferVideo = true)
        assertNotNull(videoRef)
        assertEquals("JarvisWan22_00001.mp4", videoRef!!.filename)
        assertEquals("video", videoRef.subfolder)
    }

    @Test
    fun buildsLocalDreamRequestAndParsesSseEvents() {
        assertEquals(
            "http://127.0.0.1:8081/generate",
            JarvisMediaGenerator.normalizeLocalDreamGenerateUrl(""),
        )
        assertEquals(
            "http://127.0.0.1:8081/generate",
            JarvisMediaGenerator.normalizeLocalDreamGenerateUrl("http://127.0.0.1:8081/"),
        )
        assertEquals(
            "http://127.0.0.1:8081/generate",
            JarvisMediaGenerator.normalizeLocalDreamGenerateUrl("http://127.0.0.1:8081/generate"),
        )

        val req = JarvisMediaGenerator.buildLocalDreamRequestJson(
            prompt = "cyberpunk night street",
            negativePrompt = "bad anatomy, extra fingers",
            style = "Фото",
            width = 512,
            height = 768,
            steps = 24,
            cfgScale = 7.0f,
            seed = 77,
        )
        assertTrue(req.contains("\"width\":512"))
        assertTrue(req.contains("\"height\":768"))
        assertTrue(req.contains("\"steps\":24"))
        assertTrue(req.contains("\"scheduler\":\"dpm\""))
        assertTrue(req.contains("\"output_format\":\"jpeg\""))
        assertTrue(req.contains("\"negative_prompt\":\"bad anatomy, extra fingers\""))

        val prog = JarvisMediaGenerator.parseLocalDreamSseEvent("""data: {"type":"progress","step":6,"total_steps":24}""")
        assertEquals(LocalDreamSseEvent.Progress(6, 24), prog)

        val completeJpeg = JarvisMediaGenerator.parseLocalDreamSseEvent(
            """data: {"type":"complete","image":"AQIDBA==","format":"jpeg","width":512,"height":512}"""
        )
        assertEquals(LocalDreamSseEvent.Complete("AQIDBA==", "jpeg", 512, 512, 3), completeJpeg)

        val completeRaw = JarvisMediaGenerator.parseLocalDreamSseEvent(
            """data: {"type":"complete","image":"////","format":"raw","width":512,"height":512,"channels":4}"""
        )
        assertEquals(LocalDreamSseEvent.Complete("////", "raw", 512, 512, 4), completeRaw)

        val err = JarvisMediaGenerator.parseLocalDreamSseEvent("""data: {"type":"error","message":"out of memory"}""")
        assertEquals(LocalDreamSseEvent.Error("out of memory"), err)

        assertEquals(LocalDreamSseEvent.Done, JarvisMediaGenerator.parseLocalDreamSseEvent("data: [DONE]"))
        assertNull(JarvisMediaGenerator.parseLocalDreamSseEvent(": keepalive"))
    }

    @Test
    fun buildsAuthenticatedPollinationsUrlAndParsesRetryAfter() {
        val authUrl = JarvisMediaGenerator.buildPollinationsAuthenticatedUrl(
            prompt = "Космонавт в шлеме",
            style = "Фото",
            width = 768,
            height = 768,
            seed = 12,
        )
        assertTrue(authUrl.startsWith("https://gen.pollinations.ai/image/"))
        assertTrue(authUrl.contains("width=768&height=768&seed=12&nologo=true"))
        assertFalse(authUrl.contains("&model="))

        val authModelUrl = JarvisMediaGenerator.buildPollinationsAuthenticatedUrl(
            prompt = "Космос",
            model = "flux",
        )
        assertTrue(authModelUrl.contains("&model=flux"))

        assertEquals(43, JarvisMediaGenerator.parseRetryAfterSeconds("43"))
        assertEquals(12, JarvisMediaGenerator.parseRetryAfterSeconds(" 12 "))
        assertNull(JarvisMediaGenerator.parseRetryAfterSeconds(""))
        assertNull(JarvisMediaGenerator.parseRetryAfterSeconds("abc"))
        assertNull(JarvisMediaGenerator.parseRetryAfterSeconds("-5"))
        assertNull(JarvisMediaGenerator.parseRetryAfterSeconds("0"))
    }

    @Test
    fun buildsAiHordeRequestAndParsesStatusAndTranslation() {
        assertEquals(512 to 512, JarvisMediaGenerator.computeAiHordeDimensions(768, 768))
        assertEquals(640 to 384, JarvisMediaGenerator.computeAiHordeDimensions(768, 432))
        assertEquals(384 to 640, JarvisMediaGenerator.computeAiHordeDimensions(432, 768))

        val photoJson = JarvisMediaGenerator.buildAiHordeRequestJson(
            prompt = "sunset mountains",
            negativePrompt = "blurry, watermark",
            style = "Фото",
            width = 512,
            height = 512,
            steps = 20,
            cfgScale = 7.0f,
            seed = 99,
        )
        assertTrue(photoJson.contains("sunset mountains, photorealistic"))
        assertTrue(photoJson.contains(" ### blurry, watermark"))
        assertTrue(photoJson.contains("\"r2\":true"))
        assertTrue(photoJson.contains("\"nsfw\":false"))
        assertTrue(photoJson.contains("\"Deliberate\""))

        val animeJson = JarvisMediaGenerator.buildAiHordeRequestJson(
            prompt = "mecha pilot",
            style = "Аниме",
            width = 640,
            height = 384,
        )
        assertTrue(animeJson.contains("\"Flat-2D Animerge\""))
        assertTrue(animeJson.contains("\"width\":640"))
        assertTrue(animeJson.contains("\"height\":384"))

        val customJson = JarvisMediaGenerator.buildAiHordeRequestJson(
            prompt = "portrait",
            customModel = "AbsoluteReality",
        )
        assertTrue(customJson.contains("\"models\":[\"AbsoluteReality\"]"))

        val noModelsJson = JarvisMediaGenerator.buildAiHordeRequestJson(
            prompt = "portrait",
            includeModels = false,
        )
        assertFalse(noModelsJson.contains("\"models\""))

        assertEquals("job-xyz-1", JarvisMediaGenerator.parseAiHordeSubmitId("""{"id":"job-xyz-1","kudos":6.0}"""))

        val check = JarvisMediaGenerator.parseAiHordeCheckStatus(
            """{"done":false,"faulted":false,"is_possible":true,"wait_time":14,"queue_position":2,"processing":1}"""
        )
        assertFalse(check.done)
        assertFalse(check.faulted)
        assertTrue(check.isPossible)
        assertEquals(14, check.waitTime)
        assertEquals(2, check.queuePosition)
        assertEquals(1, check.processing)

        val r2Payload = JarvisMediaGenerator.parseAiHordeCompletedImage(
            """{"done":true,"generations":[{"img":"https://example.r2.cloudflarestorage.com/img.webp"}]}"""
        )
        assertTrue(r2Payload is AiHordeImagePayload.RemoteUrl)
        assertEquals(
            "https://example.r2.cloudflarestorage.com/img.webp",
            (r2Payload as AiHordeImagePayload.RemoteUrl).url,
        )

        val b64Payload = JarvisMediaGenerator.parseAiHordeCompletedImage(
            """{"done":true,"generations":[{"img":"data:image/webp;base64,UklGRg=="}]}"""
        )
        assertTrue(b64Payload is AiHordeImagePayload.Base64Data)
        assertEquals("UklGRg==", (b64Payload as AiHordeImagePayload.Base64Data).base64)

        assertEquals(
            "An orange cat at sunset by the sea",
            JarvisMediaGenerator.parseMyMemoryTranslation(
                """{"responseData":{"translatedText":"An orange cat at sunset by the sea","match":0.85},"responseStatus":200}"""
            ),
        )
        assertNull(
            JarvisMediaGenerator.parseMyMemoryTranslation(
                """{"responseData":{"translatedText":"MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS"},"responseStatus":429}"""
            )
        )
        assertNull(
            JarvisMediaGenerator.parseMyMemoryTranslation(
                """{"responseData":{"translatedText":"MYMEMORY WARNING: limit exceeded"},"responseStatus":200}"""
            )
        )
    }

    @Test
    fun autoConfiguresComfyGgufVaeClipAndUncensoredHorde() {
        val fluxWire = JarvisMediaGenerator.autoConfigureComfyModel(
            modelName = "flux1-dev-Q4_K_S.gguf",
            availableVaes = listOf("sdxl_vae.safetensors", "ae.safetensors"),
            availableClips = listOf("clip_l.safetensors", "t5xxl_fp8_e4m3fn.safetensors"),
        )
        assertEquals("FLUX_GGUF", fluxWire.architecture)
        assertEquals("ae.safetensors", fluxWire.vaeName)
        assertEquals("clip_l.safetensors", fluxWire.clip1Name)
        assertEquals("t5xxl_fp8_e4m3fn.safetensors", fluxWire.clip2Name)
        assertEquals(1.0f, fluxWire.cfgScale)
        assertFalse(fluxWire.usePonyClipSkip2)

        val ggufWorkflow = JarvisMediaGenerator.buildComfyUiWorkflowJson(
            prompt = "cyberpunk samurai",
            negativePrompt = "",
            style = "Фото",
            width = 1024,
            height = 1024,
            steps = fluxWire.steps,
            cfgScale = fluxWire.cfgScale,
            seed = 77,
            checkpointName = fluxWire.modelName,
            usePonyClipSkip2 = fluxWire.usePonyClipSkip2,
            vaeName = fluxWire.vaeName,
            clip1Name = fluxWire.clip1Name,
            clip2Name = fluxWire.clip2Name,
        )
        assertTrue(ggufWorkflow.contains("\"class_type\":\"UnetLoaderGGUF\""))
        assertTrue(ggufWorkflow.contains("\"unet_name\":\"flux1-dev-Q4_K_S.gguf\""))
        assertTrue(ggufWorkflow.contains("\"class_type\":\"VAELoader\""))
        assertTrue(ggufWorkflow.contains("\"vae_name\":\"ae.safetensors\""))
        assertTrue(ggufWorkflow.contains("\"class_type\":\"DualCLIPLoader\""))

        val uncensoredHorde = JarvisMediaGenerator.buildAiHordeRequestJson(
            prompt = "artistic portrait",
            uncensoredMode = true,
        )
        assertTrue(uncensoredHorde.contains("\"nsfw\":true"))
        assertTrue(uncensoredHorde.contains("\"censor_nsfw\":false"))

        val localFlux = StableDiffusionNativeBridge.resolveLocalModelSpec(
            modelPath = "/data/user/0/app/files/studio_models/model/flux1-dev-Q4_K_S.gguf",
            vaePath = "/data/user/0/app/files/studio_models/vae/ae.safetensors",
            clip1Path = "/data/user/0/app/files/studio_models/clip1/clip_l.safetensors",
            clip2Path = "/data/user/0/app/files/studio_models/clip2/t5xxl_fp8_e4m3fn.safetensors",
        )
        assertTrue(localFlux.isStandaloneDiffusion)
        assertEquals("/data/user/0/app/files/studio_models/clip1/clip_l.safetensors", localFlux.clipLPath)
        assertEquals("/data/user/0/app/files/studio_models/clip2/t5xxl_fp8_e4m3fn.safetensors", localFlux.t5xxlPath)
        assertEquals(1.0f, localFlux.recommendedCfg)

        val localPony = StableDiffusionNativeBridge.resolveLocalModelSpec(
            modelPath = "/data/user/0/app/files/studio_models/model/CyberRealisticPony_V18.safetensors",
        )
        assertFalse(localPony.isStandaloneDiffusion)
        assertEquals(2, localPony.recommendedClipSkip)
        assertEquals(5.5f, localPony.recommendedCfg)
    }
}
