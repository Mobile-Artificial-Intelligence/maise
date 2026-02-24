package com.danemadsen.maise

import java.util.Locale

data class VoiceInfo(
    val id: String,
    val locale: Locale,
) {
    override fun toString(): String = "$id ($locale)"
}

val ALL_VOICES: List<VoiceInfo> = listOf(
    VoiceInfo("en-US-alloy",       Locale.US),
    VoiceInfo("en-US-aoede",       Locale.US),
    VoiceInfo("en-US-bella",       Locale.US),
    VoiceInfo("en-US-heart",       Locale.US),
    VoiceInfo("en-US-jessica",     Locale.US),
    VoiceInfo("en-US-kore",        Locale.US),
    VoiceInfo("en-US-nicole",      Locale.US),
    VoiceInfo("en-US-nova",        Locale.US),
    VoiceInfo("en-US-river",       Locale.US),
    VoiceInfo("en-US-sarah",       Locale.US),
    VoiceInfo("en-US-sky",         Locale.US),
    VoiceInfo("en-US-adam",        Locale.US),
    VoiceInfo("en-US-echo",        Locale.US),
    VoiceInfo("en-US-eric",        Locale.US),
    VoiceInfo("en-US-fenrir",      Locale.US),
    VoiceInfo("en-US-liam",        Locale.US),
    VoiceInfo("en-US-michael",     Locale.US),
    VoiceInfo("en-US-onyx",        Locale.US),
    VoiceInfo("en-US-puck",        Locale.US),
    VoiceInfo("en-US-santa",       Locale.US),
    VoiceInfo("en-GB-alice",       Locale.UK),
    VoiceInfo("en-GB-emma",        Locale.UK),
    VoiceInfo("en-GB-isabella",    Locale.UK),
    VoiceInfo("en-GB-lily",        Locale.UK),
    VoiceInfo("en-GB-daniel",      Locale.UK),
    VoiceInfo("en-GB-fable",       Locale.UK),
    VoiceInfo("en-GB-george",      Locale.UK),
    VoiceInfo("en-GB-lewis",       Locale.UK),
    VoiceInfo("de-DE-dora",        Locale.GERMANY),
    VoiceInfo("de-DE-alex",        Locale.GERMANY),
    VoiceInfo("de-DE-santa",       Locale.GERMANY),
    VoiceInfo("fr-FR-siwis",       Locale.FRANCE),
    VoiceInfo("el-GR-alpha-f",     Locale("el", "GR")),
    VoiceInfo("el-GR-beta-f",      Locale("el", "GR")),
    VoiceInfo("el-GR-omega-m",     Locale("el", "GR")),
    VoiceInfo("el-GR-psi-m",       Locale("el", "GR")),
    VoiceInfo("it-IT-sara",        Locale.ITALY),
    VoiceInfo("it-IT-nicola",      Locale.ITALY),
    VoiceInfo("ja-JP-alpha-f",     Locale.JAPAN),
    VoiceInfo("ja-JP-gongitsune",  Locale.JAPAN),
    VoiceInfo("ja-JP-nezumi",      Locale.JAPAN),
    VoiceInfo("ja-JP-tebukuro",    Locale.JAPAN),
    VoiceInfo("ja-JP-kumo",        Locale.JAPAN),
    VoiceInfo("pt-BR-dora",        Locale("pt", "BR")),
    VoiceInfo("pt-BR-alex",        Locale("pt", "BR")),
    VoiceInfo("pt-BR-santa",       Locale("pt", "BR")),
    VoiceInfo("zh-CN-xiaobei",     Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoni",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoxiao",    Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoyi",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunjian",     Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxi",       Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxia",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunyang",     Locale.SIMPLIFIED_CHINESE),
)

fun findVoiceById(id: String): VoiceInfo? = ALL_VOICES.find { it.id == id }

const val DEFAULT_VOICE_ID = "en-US-heart"