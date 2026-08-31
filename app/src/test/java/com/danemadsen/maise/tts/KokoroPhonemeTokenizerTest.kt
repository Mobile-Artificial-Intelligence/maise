package com.danemadsen.maise.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
            assertTrue("char U+%04X must not map to pad (0)".format(ch.code), ids[1] != 0)
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
        // # and @ are in neither Kokoro's vocab nor the G2P output alphabet —
        // they must be dropped, never padded (ID 0) mid-sequence.
        val expected = KokoroPhonemeTokenizer.encode("abc").toList()
        assertEquals(expected, KokoroPhonemeTokenizer.encode("a#b@c").toList())
        assertNotEquals(0, expected[1]) // sanity: "a" is a real token
    }
}