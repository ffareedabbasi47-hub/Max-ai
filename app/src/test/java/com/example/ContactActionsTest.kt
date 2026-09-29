package com.example

import com.example.tools.ConfirmationWords
import com.example.tools.ConfirmationWords.Answer
import com.example.tools.ContactActions
import com.example.tools.OfflineCommandRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactActionsTest {

    @Test
    fun `phone numbers get the SIM country code when it is missing`() {
        assertEquals("919876543210", ContactActions.normalizePhone("98765 43210", "in"))
        assertEquals("919876543210", ContactActions.normalizePhone("09876543210", "in"))
        assertEquals("919876543210", ContactActions.normalizePhone("+91 98765-43210", "in"))
        assertEquals("919876543210", ContactActions.normalizePhone("919876543210", "in"))
        assertEquals("923001234567", ContactActions.normalizePhone("0300 1234567", "pk"))
        assertEquals("447911123456", ContactActions.normalizePhone("0044 7911 123456", "in"))
        assertEquals("9876543210", ContactActions.normalizePhone("9876543210", "xx"))
        assertEquals("", ContactActions.normalizePhone("abc", "in"))
    }

    @Test
    fun `call commands are parsed`() {
        assertEquals("Rahul", OfflineCommandRouter.parseCallTarget("call Rahul"))
        assertEquals("Rahul", OfflineCommandRouter.parseCallTarget("Rahul ko call karo"))
        assertEquals("Ammi", OfflineCommandRouter.parseCallTarget("Ammi ko phone laga do"))
        assertNull(OfflineCommandRouter.parseCallTarget("call Rahul and message Amit"))
        assertNull(OfflineCommandRouter.parseCallTarget("open youtube"))
    }

    @Test
    fun `whatsapp commands give name and message`() {
        assertEquals("Rahul" to "I am late", OfflineCommandRouter.parseWhatsApp("send whatsapp to Rahul saying I am late"))
        assertEquals("Rahul" to "main late hoon", OfflineCommandRouter.parseWhatsApp("Rahul ko whatsapp karo ki main late hoon"))
        assertEquals("Amit" to "call me", OfflineCommandRouter.parseWhatsApp("send a message to Amit on whatsapp saying call me"))
        assertNull(OfflineCommandRouter.parseWhatsApp("whatsapp Rahul"))
        assertNull(OfflineCommandRouter.parseWhatsApp("open whatsapp"))
    }

    @Test
    fun `yes and no answers are understood`() {
        assertEquals(Answer.YES, ConfirmationWords.classify("haan"))
        assertEquals(Answer.YES, ConfirmationWords.classify("Yes, send it"))
        assertEquals(Answer.YES, ConfirmationWords.classify("haan kar do"))
        assertEquals(Answer.NO, ConfirmationWords.classify("nahi"))
        assertEquals(Answer.NO, ConfirmationWords.classify("no don't send"))
        assertEquals(Answer.NO, ConfirmationWords.classify("haan nahi rehne do"))
        assertEquals(Answer.OTHER, ConfirmationWords.classify("what is the weather in delhi today please"))
        assertEquals(Answer.OTHER, ConfirmationWords.classify(""))
    }
}
