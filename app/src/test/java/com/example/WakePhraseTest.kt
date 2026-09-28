package com.example

import com.example.core.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakePhraseTest {

    @Test
    fun `plain and hey max are wake phrases`() {
        assertTrue(WakePhrase.startsWithWake("Max"))
        assertTrue(WakePhrase.startsWithWake("hey max"))
        assertTrue(WakePhrase.startsWithWake("Hey Max, open YouTube"))
        assertTrue(WakePhrase.startsWithWake("  ok max what time is it"))
    }

    @Test
    fun `words that merely contain max do not trigger`() {
        assertFalse(WakePhrase.containsWake("what is the maximum speed"))
        assertFalse(WakePhrase.containsWake("Maxwell called me"))
        assertFalse(WakePhrase.containsWake("that was the climax"))
        assertFalse(WakePhrase.containsWake("what is the max speed of a cheetah"))
    }

    @Test
    fun `hey max in the middle of an utterance is detected by the background detector`() {
        assertTrue(WakePhrase.containsWake("okay hey max what time is it"))
        assertFalse(WakePhrase.startsWithWake("okay so anyway"))
    }

    @Test
    fun `stripWake removes only the leading phrase`() {
        assertEquals("open YouTube", WakePhrase.stripWake("Hey Max open YouTube"))
        assertEquals("open YouTube", WakePhrase.stripWake("max, open YouTube"))
        assertEquals("what is the max speed", WakePhrase.stripWake("what is the max speed"))
    }

    @Test
    fun `wake only utterances are recognised`() {
        assertTrue(WakePhrase.isWakeOnly("Max"))
        assertTrue(WakePhrase.isWakeOnly("hey max!"))
        assertTrue(WakePhrase.isWakeOnly("Max,"))
        assertFalse(WakePhrase.isWakeOnly("max open youtube"))
        assertFalse(WakePhrase.isWakeOnly("open youtube"))
    }
}

class VoskWakeParsingTest {
    @Test
    fun `extracts text and partial from vosk json`() {
        assertEquals("hey max", WakePhrase.extractVoskText("{\n  \"text\" : \"hey max\"\n}"))
        assertEquals("max", WakePhrase.extractVoskText("{\"partial\" : \"max\"}"))
        assertEquals("", WakePhrase.extractVoskText("{\"partial\" : \"\"}"))
        assertEquals("", WakePhrase.extractVoskText("garbage"))
    }

    @Test
    fun `restricted grammar matches only when max was heard`() {
        assertTrue(WakePhrase.matchesRestrictedGrammar("max"))
        assertTrue(WakePhrase.matchesRestrictedGrammar("hey max"))
        assertTrue(WakePhrase.matchesRestrictedGrammar("[unk] max"))
        assertFalse(WakePhrase.matchesRestrictedGrammar("[unk]"))
        assertFalse(WakePhrase.matchesRestrictedGrammar(""))
        assertFalse(WakePhrase.matchesRestrictedGrammar("maximum"))
    }
}

class HelloMaxTest {
    @Test
    fun `hello max is a wake phrase and is stripped from the command`() {
        assertTrue(WakePhrase.startsWithWake("Hello Max"))
        assertTrue(WakePhrase.isWakeOnly("hello max"))
        assertEquals("open YouTube", WakePhrase.stripWake("Hello Max open YouTube"))
    }

    @Test
    fun `salam greeting only for messages that start with hello`() {
        assertTrue(WakePhrase.startsWithHello("Hello"))
        assertTrue(WakePhrase.startsWithHello("hello max what time is it"))
        assertFalse(WakePhrase.startsWithHello("hey max hello"))
        assertFalse(WakePhrase.startsWithHello("open youtube"))
        assertFalse(WakePhrase.startsWithHello("helloworld app"))
    }
}
