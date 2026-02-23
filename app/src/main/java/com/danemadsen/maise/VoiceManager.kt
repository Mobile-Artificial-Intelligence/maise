package com.danemadsen.maise

import java.util.Locale

data class VoiceInfo(
    val id: String,
    val name: String,
    val nationality: String,
    val gender: String,
    val locale: Locale,
) {
    override fun toString(): String = "$name ($nationality $gender)"
}

val ALL_VOICES: List<VoiceInfo> = listOf(
    VoiceInfo("en-US-alloy",       "Alloy",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-aoede",       "Aoede",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-bella",       "Bella",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-heart",       "Heart",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-jessica",     "Jessica",    "American",   "Female", Locale.US),
    VoiceInfo("en-US-kore",        "Kore",       "American",   "Female", Locale.US),
    VoiceInfo("en-US-nicole",      "Nicole",     "American",   "Female", Locale.US),
    VoiceInfo("en-US-nova",        "Nova",       "American",   "Female", Locale.US),
    VoiceInfo("en-US-river",       "River",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-sarah",       "Sarah",      "American",   "Female", Locale.US),
    VoiceInfo("en-US-sky",         "Sky",        "American",   "Female", Locale.US),
    VoiceInfo("en-US-adam",        "Adam",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-echo",        "Echo",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-eric",        "Eric",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-fenrir",      "Fenrir",     "American",   "Male",   Locale.US),
    VoiceInfo("en-US-liam",        "Liam",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-michael",     "Michael",    "American",   "Male",   Locale.US),
    VoiceInfo("en-US-onyx",        "Onyx",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-puck",        "Puck",       "American",   "Male",   Locale.US),
    VoiceInfo("en-US-santa",       "Santa",      "American",   "Male",   Locale.US),
    VoiceInfo("en-GB-alice",       "Alice",      "British",    "Female", Locale.UK),
    VoiceInfo("en-GB-emma",        "Emma",       "British",    "Female", Locale.UK),
    VoiceInfo("en-GB-isabella",    "Isabella",   "British",    "Female", Locale.UK),
    VoiceInfo("en-GB-lily",        "Lily",       "British",    "Female", Locale.UK),
    VoiceInfo("en-GB-daniel",      "Daniel",     "British",    "Male",   Locale.UK),
    VoiceInfo("en-GB-fable",       "Fable",      "British",    "Male",   Locale.UK),
    VoiceInfo("en-GB-george",      "George",     "British",    "Male",   Locale.UK),
    VoiceInfo("en-GB-lewis",       "Lewis",      "British",    "Male",   Locale.UK),
    VoiceInfo("de-DE-dora",        "Dora",       "German",     "Female", Locale.GERMANY),
    VoiceInfo("de-DE-alex",        "Alex",       "German",     "Male",   Locale.GERMANY),
    VoiceInfo("de-DE-santa",       "Santa",      "German",     "Male",   Locale.GERMANY),
    VoiceInfo("fr-FR-siwis",       "Siwis",      "French",     "Female", Locale.FRANCE),
    VoiceInfo("el-GR-alpha-f",     "Alpha",      "Greek",      "Female", Locale("el", "GR")),
    VoiceInfo("el-GR-beta-f",      "Beta",       "Greek",      "Female", Locale("el", "GR")),
    VoiceInfo("el-GR-omega-m",     "Omega",      "Greek",      "Male",   Locale("el", "GR")),
    VoiceInfo("el-GR-psi-m",       "Psi",        "Greek",      "Male",   Locale("el", "GR")),
    VoiceInfo("it-IT-sara",        "Sara",       "Italian",    "Female", Locale.ITALY),
    VoiceInfo("it-IT-nicola",      "Nicola",     "Italian",    "Male",   Locale.ITALY),
    VoiceInfo("ja-JP-alpha-f",     "Alpha",      "Japanese",   "Female", Locale.JAPAN),
    VoiceInfo("ja-JP-gongitsune",  "Gongitsune", "Japanese",   "Female", Locale.JAPAN),
    VoiceInfo("ja-JP-nezumi",      "Nezumi",     "Japanese",   "Female", Locale.JAPAN),
    VoiceInfo("ja-JP-tebukuro",    "Tebukuro",   "Japanese",   "Female", Locale.JAPAN),
    VoiceInfo("ja-JP-kumo",        "Kumo",       "Japanese",   "Male",   Locale.JAPAN),
    VoiceInfo("pt-BR-dora",        "Dora",       "Portuguese", "Female", Locale("pt", "BR")),
    VoiceInfo("pt-BR-alex",        "Alex",       "Portuguese", "Male",   Locale("pt", "BR")),
    VoiceInfo("pt-BR-santa",       "Santa",      "Portuguese", "Male",   Locale("pt", "BR")),
    VoiceInfo("zh-CN-xiaobei",     "Xiaobei",    "Chinese",    "Female", Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoni",      "Xiaoni",     "Chinese",    "Female", Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoxiao",    "Xiaoxiao",   "Chinese",    "Female", Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoyi",      "Xiaoyi",     "Chinese",    "Female", Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunjian",     "Yunjian",    "Chinese",    "Male",   Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxi",       "Yunxi",      "Chinese",    "Male",   Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxia",      "Yunxia",     "Chinese",    "Male",   Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunyang",     "Yunyang",    "Chinese",    "Male",   Locale.SIMPLIFIED_CHINESE),
)

fun findVoiceById(id: String): VoiceInfo? = ALL_VOICES.find { it.id == id }

const val DEFAULT_VOICE_ID = "en-US-heart"