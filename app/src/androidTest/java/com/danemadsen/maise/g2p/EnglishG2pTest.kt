package com.danemadsen.maise.g2p

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danemadsen.maise.tts.KokoroPhonemeTokenizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end G2P ↔ Kokoro compatibility gate: phonemize a representative corpus
 * and assert every output character is in the Kokoro vocab (nothing dropped).
 */
@RunWith(AndroidJUnit4::class)
class EnglishG2pTest {

    private lateinit var g2p: EnglishG2p

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        g2p = EnglishG2p(context)
    }

    @After
    fun tearDown() {
        g2p.close()
    }

    private val corpus = listOf(
        "Hello world.",
        "The quick brown fox jumps over the lazy dog.",
        "One hundred dollars, or $3.50 exactly.",
        "In 1999, everything cost 21st-century money.",
        "\"Judge church,\" she said — twice!",
        "A wonderful serenity has taken possession of my entire soul.",
        "florbimicate zyzzycki quux",       // OOV — BART fallback network
        "e.g. i.e. vs. Mr. Dr. St.",        // abbreviations
    )

    @Test
    fun everyPhonemizedCharacter_isInKokoroVocab() {
        for (text in corpus) {
            val phonemes = g2p.phonemize(text)
            assertTrue("no phonemes for \"$text\"", phonemes.isNotEmpty())
            val ids = KokoroPhonemeTokenizer.encode(phonemes)
            assertEquals(
                "G2P output has characters outside the Kokoro vocab: \"$phonemes\"",
                phonemes.length + 2,
                ids.size,
            )
        }
    }

    @Test
    fun outputIsFullyTokenizable_endToEnd() {
        // KokoroTTS consumes: n_tokens = tokens.size - 2; must never be negative or capped unexpectedly
        for (text in corpus) {
            val phonemes = g2p.phonemize(text)
            val ids = KokoroPhonemeTokenizer.encode(phonemes)
            assertTrue(ids.size > 2)
            assertFalse("inner token must never be pad", ids.slice(1 until ids.size - 1).contains(0))
        }
    }

    @Test
    fun postProcessing_removesRawFlapAndGlottalSymbols() {
        for (text in corpus) {
            val phonemes = g2p.phonemize(text)
            assertFalse("ɾ must be post-processed to T: \"$phonemes\"", phonemes.contains('ɾ'))
            assertFalse("ʔ must be post-processed to t: \"$phonemes\"", phonemes.contains('ʔ'))
        }
    }

    @Test
    fun helloWorld_matchesExpectedPhonemes() {
        assertEquals("həlˈO wˈɜɹld", g2p.phonemize("hello world"))
    }
}