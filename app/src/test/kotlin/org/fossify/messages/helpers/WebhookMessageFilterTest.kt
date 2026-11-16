package org.fossify.messages.helpers

import org.fossify.messages.helpers.WebhookMessageFilter.MODE_KEYWORDS
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_NONE
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_SENDERS
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_SENDERS_AND_KEYWORDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookMessageFilterTest {
    private val senders = "GitHub, +973 1234-5678"
    private val keywords = "code, OTP"
    private val body = "Your synthetic Code is 000000"

    private fun matches(mode: Int, address: String, body: String = this.body): Boolean {
        return WebhookMessageFilter.matches(mode, address, body, senders, keywords)
    }

    @Test
    fun newConfigurationsAreUnfilteredAndExistingOnesKeepSenderRules() {
        assertEquals(MODE_NONE, WebhookMessageFilter.defaultMode(hasExistingConfiguration = false))
        assertEquals(MODE_SENDERS, WebhookMessageFilter.defaultMode(hasExistingConfiguration = true))
    }

    @Test
    fun noFilterForwardsEverySenderAndBody() {
        assertTrue(matches(MODE_NONE, "5550001"))
        assertTrue(matches(MODE_NONE, "UnknownBank", "anything"))
        assertTrue(WebhookMessageFilter.matches(MODE_NONE, "5550001", "", "", ""))
    }

    @Test
    fun senderFilterForwardsOnlyApprovedSenders() {
        assertTrue(matches(MODE_SENDERS, "github", "no keyword here"))
        assertTrue(matches(MODE_SENDERS, "97312345678"))
        assertFalse(matches(MODE_SENDERS, "5550001"))
    }

    @Test
    fun keywordFilterIgnoresCaseAndSender() {
        assertTrue(matches(MODE_KEYWORDS, "5550001"))
        assertTrue(matches(MODE_KEYWORDS, "5550001", "otp 000000"))
        assertFalse(matches(MODE_KEYWORDS, "GitHub", "Hello there"))
    }

    @Test
    fun combinedFilterNeedsBothSenderAndKeyword() {
        assertTrue(matches(MODE_SENDERS_AND_KEYWORDS, "GitHub"))
        assertFalse(matches(MODE_SENDERS_AND_KEYWORDS, "5550001"))
        assertFalse(matches(MODE_SENDERS_AND_KEYWORDS, "GitHub", "Hello there"))
    }

    @Test
    fun activeFilterWithEmptyListForwardsNothing() {
        assertFalse(WebhookMessageFilter.matches(MODE_SENDERS, "GitHub", body, "", keywords))
        assertFalse(WebhookMessageFilter.matches(MODE_KEYWORDS, "GitHub", body, senders, " , "))
        assertFalse(WebhookMessageFilter.matches(MODE_SENDERS_AND_KEYWORDS, "GitHub", body, senders, ""))
    }

    @Test
    fun unknownModeForwardsNothing() {
        assertFalse(matches(42, "GitHub"))
    }
}
