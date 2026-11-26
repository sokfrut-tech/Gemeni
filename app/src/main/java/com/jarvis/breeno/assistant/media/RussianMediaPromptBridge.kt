package com.jarvis.breeno.assistant.media

/**
 * Offline Russian -> English visual prompt bridge for image and MP4 video Studio generation
 * (adapted from OGAM ImageGenService & MediaPromptBridge).
 * Preserves already-English prompts and boosts quality tags so Russian prompts work cleanly offline
 * even on Local Dream NPU or offline ComfyUI without hitting remote translators.
 */
object RussianMediaPromptBridge {
    private val CYRILLIC_REGEX = Regex("[а-яА-ЯёЁ]")

    private val PREFIX_STRIPPERS = listOf(
        Regex("""^(?:(?:джарвис|пожалуйста|помоги|можешь)[,\s]*)+""", RegexOption.IGNORE_CASE),
        Regex("""^(?:нарисуй|нарисовать|сгенерируй|сгенерировать|создай|создать|сделай|сделать|покажи|изобрази)\s+(?:мне\s+)?(?:пожалуйста\s+)?(?:красивую\s+|крутую\s+|реалистичную\s+)?(?:картинку|изображение|фото|фотографию|арт|рисунок|видео|ролик|клип)\s*(?:с|про|на тему|где)?\s*""", RegexOption.IGNORE_CASE),
        Regex("""^(?:нарисуй|изобрази)\s+(?:мне\s+)?""", RegexOption.IGNORE_CASE),
    )

    // Curated Russian visual lexicon ordered longer-phrases-first where needed.
    private val PHRASE_MAP: List<Pair<String, String>> = listOf(
        "футуристический город" to "futuristic city",
        "футуристичный город" to "futuristic city",
        "ночной город" to "night city",
        "под дождём" to "under rain",
        "под дождем" to "under rain",
        "в космосе" to "in outer space",
        "открытый космос" to "deep outer space",
        "на закате" to "at sunset",
        "на рассвете" to "at sunrise",
        "в стиле киберпанк" to "cyberpunk style",
        "в неоновом свете" to "in neon lighting",
        "летающая машина" to "flying futuristic car",
        "спортивная машина" to "sports car",
        "белый кот" to "white cat",
        "чёрный кот" to "black cat",
        "черный кот" to "black cat",
        "железный человек" to "armored cybernetic hero in red and gold suit",
        "космический корабль" to "spaceship",
        "звездное небо" to "starry night sky",
        "звёздное небо" to "starry night sky",
        "северное сияние" to "aurora borealis northern lights",
    )

    private val WORD_MAP: Map<String, String> = mapOf(
        // Places & Environment
        "город" to "city", "города" to "city", "городе" to "city", "городом" to "city",
        "мегаполис" to "megapolis skyscrapers", "улица" to "street", "улицы" to "streets",
        "лес" to "forest", "лесу" to "forest", "горы" to "mountains", "горах" to "mountains", "гора" to "mountain",
        "море" to "ocean sea", "океан" to "ocean", "берег" to "coastline", "пляж" to "beach",
        "река" to "river", "озеро" to "lake", "водопад" to "waterfall", "остров" to "island",
        "космос" to "outer space", "космоса" to "outer space", "планета" to "planet", "планеты" to "planets",
        "луна" to "moon", "солнце" to "sun", "звезды" to "stars", "звёзды" to "stars", "галактика" to "galaxy",
        "туманность" to "cosmic nebula", "пустыня" to "desert", "снег" to "snow", "зима" to "winter",
        "лето" to "summer", "осень" to "autumn", "весна" to "spring", "замок" to "ancient castle",
        "храм" to "temple", "лаборатория" to "high-tech laboratory", "комната" to "room",

        // Time, Weather & Lighting
        "ночь" to "night", "ночью" to "at night", "ночной" to "night", "ночная" to "night",
        "день" to "daytime", "днем" to "daylight", "днём" to "daylight",
        "закат" to "sunset", "рассвет" to "sunrise", "сумерки" to "twilight",
        "дождь" to "rain", "гроза" to "thunderstorm", "молния" to "lightning", "туман" to "mist fog",
        "огонь" to "fire flames", "пламя" to "flames", "лед" to "ice crystal", "лёд" to "ice crystal",
        "свет" to "cinematic light", "неон" to "neon glow", "неоновый" to "neon", "неоновые" to "neon",
        "тени" to "dramatic shadows", "отражение" to "wet reflections", "отражения" to "reflections",

        // Subjects
        "человек" to "person", "мужчина" to "man", "женщина" to "woman", "девушка" to "young woman portrait",
        "парень" to "young man", "ребенок" to "child", "ребёнок" to "child", "воин" to "warrior",
        "самурай" to "cyber samurai", "рыцарь" to "armored knight", "маг" to "wizard mage",
        "космонавт" to "astronaut", "астронавт" to "astronaut", "пилот" to "pilot", "киборг" to "cyborg",
        "робот" to "futuristic robot", "дроид" to "android", "андроид" to "android",
        "кот" to "cat", "кошка" to "cat", "собака" to "dog", "волк" to "wolf", "волки" to "wolves",
        "дракон" to "majestic dragon", "дракона" to "majestic dragon", "орел" to "eagle", "орёл" to "eagle",
        "феникс" to "phoenix bird", "сова" to "owl", "лошадь" to "horse", "медведь" to "bear", "тигр" to "tiger", "лев" to "lion",

        // Tech & Transport
        "машина" to "car", "автомобиль" to "automobile", "спорткар" to "supercar", "мотоцикл" to "motorcycle",
        "поезд" to "high-speed train", "самолет" to "aircraft jet", "самолёт" to "aircraft jet",
        "корабль" to "ship", "вертолет" to "helicopter", "вертолёт" to "helicopter",
        "сфера" to "glowing holographic energy sphere", "голограмма" to "hologram",

        // Colors
        "красный" to "red", "красная" to "red", "красного" to "red",
        "синий" to "blue", "синяя" to "blue", "голубой" to "cyan", "голубая" to "cyan",
        "зеленый" to "green", "зелёный" to "green", "золотой" to "golden", "золотая" to "golden",
        "серебряный" to "silver", "белый" to "white", "черный" to "black", "чёрный" to "black",
        "фиолетовый" to "purple violet", "оранжевый" to "orange", "розовый" to "pink",

        // Styles & Qualities
        "киберпанк" to "cyberpunk", "фантастика" to "sci-fi", "фэнтези" to "fantasy",
        "аниме" to "anime aesthetic", "портрет" to "close-up portrait", "пейзаж" to "wide landscape",
        "футуристичный" to "futuristic", "футуристический" to "futuristic", "древний" to "ancient",
        "реалистичный" to "photorealistic", "красивый" to "stunning", "огромный" to "epic giant",
        "мрачный" to "dark moody", "яркий" to "vibrant", "сияющий" to "glowing", "летит" to "flying",
        "бежит" to "running", "стоит" to "standing", "плывет" to "floating", "танцует" to "dancing",
    )

    private val TRANSLIT_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun stripRussianMediaPrefix(raw: String): String {
        var current = raw.trim()
        for (regex in PREFIX_STRIPPERS) {
            current = regex.replaceFirst(current, "").trim()
        }
        return current.trim(',', '.', ':', ' ')
    }

    fun toVisualPrompt(rawPrompt: String): String {
        val cleaned = stripRussianMediaPrefix(rawPrompt).ifBlank { rawPrompt.trim() }
        if (cleaned.isEmpty()) return ""
        if (!CYRILLIC_REGEX.containsMatchIn(cleaned)) {
            return cleaned
        }

        var working = cleaned.lowercase()
        for ((ruPhrase, enPhrase) in PHRASE_MAP) {
            if (working.contains(ruPhrase)) {
                working = working.replace(ruPhrase, " $enPhrase ")
            }
        }

        val tokens = working.split(Regex("\\s+")).filter { it.isNotBlank() }
        val translated = mutableListOf<String>()
        for (token in tokens) {
            val bare = token.trim(',', '.', '!', '?', ';', ':')
            if (bare.isEmpty()) continue
            val mapped = WORD_MAP[bare]
            when {
                mapped != null -> translated.add(mapped)
                bare in setOf("в", "на", "с", "у", "из", "и", "к", "по", "под", "над", "для") -> Unit
                !CYRILLIC_REGEX.containsMatchIn(bare) -> translated.add(bare)
                else -> translated.add(transliterate(bare))
            }
        }

        val result = translated.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        if (result.isEmpty()) return cleaned
        return if (result.contains("detail") || result.contains("4k") || result.contains("8k")) {
            result
        } else {
            "$result, high detail, sharp focus"
        }
    }

    private fun transliterate(word: String): String = buildString(word.length) {
        for (ch in word) {
            append(TRANSLIT_MAP[ch] ?: ch)
        }
    }
}
