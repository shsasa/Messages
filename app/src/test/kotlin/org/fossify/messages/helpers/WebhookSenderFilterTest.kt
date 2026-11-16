package org.fossify.messages.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookSenderFilterTest {
    private val list = "GitHub, GoDaddy;+973 1234-5678\n 5550001 "

    @Test
    fun emptyListApprovesNobody() {
        assertFalse(WebhookSenderFilter.isApproved("GitHub", ""))
        assertFalse(WebhookSenderFilter.isApproved("GitHub", " , ;\n"))
    }

    @Test
    fun emptySenderIsNeverApproved() {
        assertFalse(WebhookSenderFilter.isApproved("", list))
        assertFalse(WebhookSenderFilter.isApproved("   ", list))
    }

    @Test
    fun alphanumericSendersMatchIgnoringCase() {
        assertTrue(WebhookSenderFilter.isApproved("GITHUB", list))
        assertTrue(WebhookSenderFilter.isApproved(" godaddy ", list))
    }

    @Test
    fun phoneNumbersMatchIgnoringFormatting() {
        assertTrue(WebhookSenderFilter.isApproved("+97312345678", list))
        assertTrue(WebhookSenderFilter.isApproved("5550001", list))
    }

    @Test
    fun unknownAndPartialSendersAreRejected() {
        assertFalse(WebhookSenderFilter.isApproved("5550002", list))
        assertFalse(WebhookSenderFilter.isApproved("12345678", list))
        assertFalse(WebhookSenderFilter.isApproved("GitHub2", list))
        assertFalse(WebhookSenderFilter.isApproved("Git", list))
    }

    @Test
    fun parseNormalizesAndDeduplicates() {
        assertEquals(
            setOf("github", "97312345678"),
            WebhookSenderFilter.parse("GitHub,github\n+973 1234 5678;97312345678"),
        )
    }
}
