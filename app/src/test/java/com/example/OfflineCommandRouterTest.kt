package com.example

import com.example.tools.OfflineCommandRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCommandRouterTest {

    @Test
    fun `english open commands give the app name`() {
        assertEquals("YouTube", OfflineCommandRouter.parseOpenTarget("open YouTube"))
        assertEquals("WhatsApp", OfflineCommandRouter.parseOpenTarget("Please launch the WhatsApp app"))
        assertEquals("chrome", OfflineCommandRouter.parseOpenTarget("start chrome."))
    }

    @Test
    fun `hinglish open commands work`() {
        assertEquals("youtube", OfflineCommandRouter.parseOpenTarget("youtube kholo"))
        assertEquals("whatsapp", OfflineCommandRouter.parseOpenTarget("whatsapp khol do"))
        assertEquals("camera", OfflineCommandRouter.parseOpenTarget("camera chalu karo"))
    }

    @Test
    fun `compound or unrelated sentences are left to the AI`() {
        assertNull(OfflineCommandRouter.parseOpenTarget("open youtube and search cats"))
        assertNull(OfflineCommandRouter.parseOpenTarget("youtube kholo aur gaana chalao"))
        assertNull(OfflineCommandRouter.parseOpenTarget("what is the weather in Delhi"))
        assertNull(OfflineCommandRouter.parseOpenTarget("open"))
    }

    @Test
    fun `time and date questions are recognised`() {
        assertTrue(OfflineCommandRouter.isTimeQuery("what time is it"))
        assertTrue(OfflineCommandRouter.isTimeQuery("kitne baje hain"))
        assertTrue(OfflineCommandRouter.isDateQuery("aaj ki date kya hai"))
        assertFalse(OfflineCommandRouter.isTimeQuery("open clock"))
    }
}
