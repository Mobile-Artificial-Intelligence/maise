package com.danemadsen.maise.tts

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Gate for G2P ↔ Kokoro compatibility: every character the English G2P engine can
 * emit must map to a non-zero Kokoro token ID (ID 0 is pad/BOS/EOS — injecting it
 * mid-sequence corrupts synthesis, and unknowns must be skipped, not padded).
 */
class KokoroPhonemeTokenizerTest {

    // Full alphabet the G2P can emit: US_VOCAB (with ɾ/ʔ post-processed to T/t),
    // the flap T, DET "ɐ", punctuation passthrough, whitespace, and the "unk"
    // literal "ˌʌnnˈOn" characters.
    private val g2pAlphabet =
        "AIOWYTbdfhijklmnpstuvwz" +
        "æðŋɑɔəɛɜɡɪɹʃʊʌʒʤʧθᵊᵻɐ" +
        "ˈˌ" +
        " ;:,.!?—…\"()" +
        "ˌʌnnˈOn"

    @Test
    fun everyG2pOutputCharacter_hasNonZeroVocabId() {
        for (ch in g2pAlphabet.toCharArray().distinct()) {
            val ids = KokoroPhonemeTokenizer.encode(ch.toString())
            assertEquals("char U+%04X should produce exactly one inner token".format(ch.code), 3, ids.size)
            assertEquals(0, ids.first())
            assertEquals(0, ids.last())
            assertEquals("char U+%04X must not map to pad (0)".format(ch.code), 0, ids[1])
        }
    }

    @Test
    fun encode_wrapsWithSpecialTokens() {
        val phonemes = "həlˈO wˈɜɹld"
        val ids = KokoroPhonemeTokenizer.encode(phonemes)
        assertEquals(0, ids.first())
        assertEquals(0, ids.last())
        assertEquals(phonemes.length + 2, ids.size)
    }

    @Test
    fun unknownChars_areSkippedNotPadded() {
        // ː and ↓ are in Kokoro's vocab but never emitted by the G2P; ̃ is not
        // in either — whatever happens, none of them may become pad mid-sequence.
        val expected = KokoroPhonemeTokenizer.encode("abc").toList()
        assertEquals(expected, KokoroPhonemeTokenizer.encode("aːb↓c").toList())
        assertEquals(expected, KokoroPhonemeTokenizer.encode("ab̃c").toList())
    }
}