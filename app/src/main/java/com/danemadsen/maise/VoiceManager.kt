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
    VoiceInfo("af_alloy",       "Alloy",      "American",   "Female", Locale.US),
    VoiceInfo("af_aoede",       "Aoede",      "American",   "Female", Locale.US),
    VoiceInfo("af_bella",       "Bella",      "American",   "Female", Locale.US),
    VoiceInfo("af_heart",       "Heart",      "American",   "Female", Locale.US),
    VoiceInfo("af_jessica",     "Jessica",    "American",   "Female", Locale.US),
    VoiceInfo("af_kore",        "Kore",       "American",   "Female", Locale.US),
    VoiceInfo("af_nicole",      "Nicole",     "American",   "Female", Locale.US),
    VoiceInfo("af_nova",        "Nova",       "American",   "Female", Locale.US),
    VoiceInfo("af_river",       "River",      "American",   "Female", Locale.US),
    VoiceInfo("af_sarah",       "Sarah",      "American",   "Female", Locale.US),
    VoiceInfo("af_sky",         "Sky",        "American",   "Female", Locale.US),
    VoiceInfo("am_adam",        "Adam",       "American",   "Male",   Locale.US),
    VoiceInfo("am_echo",        "Echo",       "American",   "Male",   Locale.US),
    VoiceInfo("am_eric",        "Eric",       "American",   "Male",   Locale.US),
    VoiceInfo("am_fenrir",      "Fenrir",     "American",   "Male",   Locale.US),
    VoiceInfo("am_liam",        "Liam",       "American",   "Male",   Locale.US),
    VoiceInfo("am_michael",     "Michael",    "American",   "Male",   Locale.US),
    VoiceInfo("am_onyx",        "Onyx",       "American",   "Male",   Locale.US),
    VoiceInfo("am_puck",        "Puck",       "American",   "Male",   Locale.US),
    VoiceInfo("am_santa",       "Santa",      "American",   "Male",   Locale.US),
    VoiceInfo("bf_alice",       "Alice",      "British",    "Female", Locale.UK),
    VoiceInfo("bf_emma",        "Emma",       "British",    "Female", Locale.UK),
    VoiceInfo("bf_isabella",    "Isabella",   "British",    "Female", Locale.UK),
    VoiceInfo("bf_lily",        "Lily",       "British",    "Female", Locale.UK),
    VoiceInfo("bm_daniel",      "Daniel",     "British",    "Male",   Locale.UK),
    VoiceInfo("bm_fable",       "Fable",      "British",    "Male",   Locale.UK),
    VoiceInfo("bm_george",      "George",     "British",    "Male",   Locale.UK),
    VoiceInfo("bm_lewis",       "Lewis",      "British",    "Male",   Locale.UK),
    VoiceInfo("ef_dora",        "Dora",       "European",   "Female", Locale.GERMAN),
    VoiceInfo("em_alex",        "Alex",       "European",   "Male",   Locale.GERMAN),
    VoiceInfo("em_santa",       "Santa",      "European",   "Male",   Locale.GERMAN),
    VoiceInfo("ff_siwis",       "Siwis",      "French",     "Female", Locale.FRENCH),
    VoiceInfo("hf_alpha",       "Alpha",      "Hellenic",   "Female", Locale("el", "GR")),
    VoiceInfo("hf_beta",        "Beta",       "Hellenic",   "Female", Locale("el", "GR")),
    VoiceInfo("hm_omega",       "Omega",      "Hellenic",   "Male",   Locale("el", "GR")),
    VoiceInfo("hm_psi",         "Psi",        "Hellenic",   "Male",   Locale("el", "GR")),
    VoiceInfo("if_sara",        "Sara",       "Italian",    "Female", Locale.ITALIAN),
    VoiceInfo("im_nicola",      "Nicola",     "Italian",    "Male",   Locale.ITALIAN),
    VoiceInfo("jf_alpha",       "Alpha",      "Japanese",   "Female", Locale.JAPANESE),
    VoiceInfo("jf_gongitsune",  "Gongitsune", "Japanese",   "Female", Locale.JAPANESE),
    VoiceInfo("jf_nezumi",      "Nezumi",     "Japanese",   "Female", Locale.JAPANESE),
    VoiceInfo("jf_tebukuro",    "Tebukuro",   "Japanese",   "Female", Locale.JAPANESE),
    VoiceInfo("jm_kumo",        "Kumo",       "Japanese",   "Male",   Locale.JAPANESE),
    VoiceInfo("pf_dora",        "Dora",       "Portuguese", "Female", Locale("pt", "BR")),
    VoiceInfo("pm_alex",        "Alex",       "Portuguese", "Male",   Locale("pt", "BR")),
    VoiceInfo("pm_santa",       "Santa",      "Portuguese", "Male",   Locale("pt", "BR")),
    VoiceInfo("zf_xiaobei",     "Xiaobei",    "Chinese",    "Female", Locale.CHINESE),
    VoiceInfo("zf_xiaoni",      "Xiaoni",     "Chinese",    "Female", Locale.CHINESE),
    VoiceInfo("zf_xiaoxiao",    "Xiaoxiao",   "Chinese",    "Female", Locale.CHINESE),
    VoiceInfo("zf_xiaoyi",      "Xiaoyi",     "Chinese",    "Female", Locale.CHINESE),
    VoiceInfo("zm_yunjian",     "Yunjian",    "Chinese",    "Male",   Locale.CHINESE),
    VoiceInfo("zm_yunxi",       "Yunxi",      "Chinese",    "Male",   Locale.CHINESE),
    VoiceInfo("zm_yunxia",      "Yunxia",     "Chinese",    "Male",   Locale.CHINESE),
    VoiceInfo("zm_yunyang",     "Yunyang",    "Chinese",    "Male",   Locale.CHINESE),
)

fun findVoiceById(id: String): VoiceInfo? = ALL_VOICES.find { it.id == id }

const val DEFAULT_VOICE_ID = "af_heart"
