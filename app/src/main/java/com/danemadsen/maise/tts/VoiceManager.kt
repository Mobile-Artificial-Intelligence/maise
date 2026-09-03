package com.danemadsen.maise.tts

import java.util.Locale

data class VoiceInfo(
    val id: String,
    val locale: Locale,
) {
    override fun toString(): String = "$id ($locale)"
}

// Every voice is en_US: the shipped phonemizer is en_us-only, so all Kokoro
// voice styles (including IDs like de-DE-* or ja-JP-*) speak American English.
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
    VoiceInfo("en-GB-alice-kokoro",       Locale.US),
    VoiceInfo("en-GB-emma-kokoro",        Locale.US),
    VoiceInfo("en-GB-isabella-kokoro",    Locale.US),
    VoiceInfo("en-GB-lily-kokoro",        Locale.US),
    VoiceInfo("en-GB-daniel-kokoro",      Locale.US),
    VoiceInfo("en-GB-fable-kokoro",       Locale.US),
    VoiceInfo("en-GB-george-kokoro",      Locale.US),
    VoiceInfo("en-GB-lewis-kokoro",       Locale.US),
    VoiceInfo("de-DE-dora-kokoro",        Locale.US),
    VoiceInfo("de-DE-alex-kokoro",        Locale.US),
    VoiceInfo("de-DE-santa-kokoro",       Locale.US),
    VoiceInfo("fr-FR-siwis-kokoro",       Locale.US),
    VoiceInfo("el-GR-alpha-f-kokoro",     Locale.US),
    VoiceInfo("el-GR-beta-f-kokoro",      Locale.US),
    VoiceInfo("el-GR-omega-m-kokoro",     Locale.US),
    VoiceInfo("el-GR-psi-m-kokoro",       Locale.US),
    VoiceInfo("it-IT-sara-kokoro",        Locale.US),
    VoiceInfo("it-IT-nicola-kokoro",      Locale.US),
    VoiceInfo("ja-JP-alpha-f-kokoro",     Locale.US),
    VoiceInfo("ja-JP-gongitsune-kokoro",  Locale.JAPAN),
    VoiceInfo("ja-JP-nezumi-kokoro",      Locale.US),
    VoiceInfo("ja-JP-tebukuro-kokoro",    Locale.US),
    VoiceInfo("ja-JP-kumo-kokoro",        Locale.US),
    VoiceInfo("pt-BR-dora-kokoro",        Locale.US),
    VoiceInfo("pt-BR-alex-kokoro",        Locale.US),
    VoiceInfo("pt-BR-santa-kokoro",       Locale.US),
    VoiceInfo("zh-CN-xiaobei-kokoro",     Locale.US),
    VoiceInfo("zh-CN-xiaoni-kokoro",      Locale.US),
    VoiceInfo("zh-CN-xiaoxiao-kokoro",    Locale.US),
    VoiceInfo("zh-CN-xiaoyi-kokoro",      Locale.US),
    VoiceInfo("zh-CN-yunjian-kokoro",     Locale.US),
    VoiceInfo("zh-CN-yunxi-kokoro",       Locale.US),
    VoiceInfo("zh-CN-yunxia-kokoro",      Locale.US),
    VoiceInfo("zh-CN-yunyang-kokoro",     Locale.US),

)

fun findVoiceById(id: String): VoiceInfo? = ALL_VOICES.find { it.id == id }

const val DEFAULT_VOICE_ID = "en-US-heart-kokoro"
