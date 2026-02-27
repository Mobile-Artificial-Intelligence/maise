package com.danemadsen.maise.tts

import java.util.Locale

data class VoiceInfo(
    val id: String,
    val locale: Locale,
) {
    override fun toString(): String = "$id ($locale)"
}

val ALL_VOICES: List<VoiceInfo> = listOf(
    // ── Kokoro voices ─────────────────────────────────────────────────────────
    VoiceInfo("en-US-alloy-kokoro",       Locale.US),
    VoiceInfo("en-US-aoede-kokoro",       Locale.US),
    VoiceInfo("en-US-bella-kokoro",       Locale.US),
    VoiceInfo("en-US-heart-kokoro",       Locale.US),
    VoiceInfo("en-US-jessica-kokoro",     Locale.US),
    VoiceInfo("en-US-kore-kokoro",        Locale.US),
    VoiceInfo("en-US-nicole-kokoro",      Locale.US),
    VoiceInfo("en-US-nova-kokoro",        Locale.US),
    VoiceInfo("en-US-river-kokoro",       Locale.US),
    VoiceInfo("en-US-sarah-kokoro",       Locale.US),
    VoiceInfo("en-US-sky-kokoro",         Locale.US),
    VoiceInfo("en-US-adam-kokoro",        Locale.US),
    VoiceInfo("en-US-echo-kokoro",        Locale.US),
    VoiceInfo("en-US-eric-kokoro",        Locale.US),
    VoiceInfo("en-US-fenrir-kokoro",      Locale.US),
    VoiceInfo("en-US-liam-kokoro",        Locale.US),
    VoiceInfo("en-US-michael-kokoro",     Locale.US),
    VoiceInfo("en-US-onyx-kokoro",        Locale.US),
    VoiceInfo("en-US-puck-kokoro",        Locale.US),
    VoiceInfo("en-US-santa-kokoro",       Locale.US),
    VoiceInfo("en-GB-alice-kokoro",       Locale.UK),
    VoiceInfo("en-GB-emma-kokoro",        Locale.UK),
    VoiceInfo("en-GB-isabella-kokoro",    Locale.UK),
    VoiceInfo("en-GB-lily-kokoro",        Locale.UK),
    VoiceInfo("en-GB-daniel-kokoro",      Locale.UK),
    VoiceInfo("en-GB-fable-kokoro",       Locale.UK),
    VoiceInfo("en-GB-george-kokoro",      Locale.UK),
    VoiceInfo("en-GB-lewis-kokoro",       Locale.UK),
    VoiceInfo("de-DE-dora-kokoro",        Locale.GERMANY),
    VoiceInfo("de-DE-alex-kokoro",        Locale.GERMANY),
    VoiceInfo("de-DE-santa-kokoro",       Locale.GERMANY),
    VoiceInfo("fr-FR-siwis-kokoro",       Locale.FRANCE),
    VoiceInfo("el-GR-alpha-f-kokoro",     Locale("el", "GR")),
    VoiceInfo("el-GR-beta-f-kokoro",      Locale("el", "GR")),
    VoiceInfo("el-GR-omega-m-kokoro",     Locale("el", "GR")),
    VoiceInfo("el-GR-psi-m-kokoro",       Locale("el", "GR")),
    VoiceInfo("it-IT-sara-kokoro",        Locale.ITALY),
    VoiceInfo("it-IT-nicola-kokoro",      Locale.ITALY),
    VoiceInfo("ja-JP-alpha-f-kokoro",     Locale.JAPAN),
    VoiceInfo("ja-JP-gongitsune-kokoro",  Locale.JAPAN),
    VoiceInfo("ja-JP-nezumi-kokoro",      Locale.JAPAN),
    VoiceInfo("ja-JP-tebukuro-kokoro",    Locale.JAPAN),
    VoiceInfo("ja-JP-kumo-kokoro",        Locale.JAPAN),
    VoiceInfo("pt-BR-dora-kokoro",        Locale("pt", "BR")),
    VoiceInfo("pt-BR-alex-kokoro",        Locale("pt", "BR")),
    VoiceInfo("pt-BR-santa-kokoro",       Locale("pt", "BR")),
    VoiceInfo("zh-CN-xiaobei-kokoro",     Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoni-kokoro",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoxiao-kokoro",    Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-xiaoyi-kokoro",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunjian-kokoro",     Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxi-kokoro",       Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunxia-kokoro",      Locale.SIMPLIFIED_CHINESE),
    VoiceInfo("zh-CN-yunyang-kokoro",     Locale.SIMPLIFIED_CHINESE),

)

fun findVoiceById(id: String): VoiceInfo? = ALL_VOICES.find { it.id == id }

const val DEFAULT_VOICE_ID = "en-US-heart-kokoro"
