package com.example

import com.example.core.MicArbiter
import com.example.core.MicOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MicArbiterTest {

    @Before
    fun reset() = MicArbiter.resetForTest()

    @Test
    fun `wake can take a free mic`() {
        assertTrue(MicArbiter.acquire(MicOwner.WAKE))
        assertEquals(MicOwner.WAKE, MicArbiter.owner.value)
    }

    @Test
    fun `assistant and live pre-empt the wake detector`() {
        assertTrue(MicArbiter.acquire(MicOwner.WAKE))
        assertTrue(MicArbiter.acquire(MicOwner.ASSISTANT))
        assertEquals(MicOwner.ASSISTANT, MicArbiter.owner.value)

        MicArbiter.release(MicOwner.ASSISTANT)
        assertTrue(MicArbiter.acquire(MicOwner.WAKE))
        assertTrue(MicArbiter.acquire(MicOwner.LIVE))
        assertEquals(MicOwner.LIVE, MicArbiter.owner.value)
    }

    @Test
    fun `wake cannot steal the mic from an active session`() {
        assertTrue(MicArbiter.acquire(MicOwner.ASSISTANT))
        assertFalse(MicArbiter.acquire(MicOwner.WAKE))
        assertEquals(MicOwner.ASSISTANT, MicArbiter.owner.value)
    }

    @Test
    fun `assistant and live never pre-empt each other`() {
        assertTrue(MicArbiter.acquire(MicOwner.LIVE))
        assertFalse(MicArbiter.acquire(MicOwner.ASSISTANT))
        assertEquals(MicOwner.LIVE, MicArbiter.owner.value)
    }

    @Test
    fun `stray release by a non-owner is ignored`() {
        assertTrue(MicArbiter.acquire(MicOwner.ASSISTANT))
        MicArbiter.release(MicOwner.WAKE)
        assertEquals(MicOwner.ASSISTANT, MicArbiter.owner.value)
        MicArbiter.release(MicOwner.ASSISTANT)
        assertEquals(MicOwner.NONE, MicArbiter.owner.value)
    }
}
