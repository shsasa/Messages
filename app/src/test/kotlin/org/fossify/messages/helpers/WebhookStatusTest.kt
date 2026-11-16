package org.fossify.messages.helpers

import org.fossify.messages.helpers.WebhookMessageFilter.MODE_KEYWORDS
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_NONE
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_SENDERS
import org.fossify.messages.helpers.WebhookMessageFilter.MODE_SENDERS_AND_KEYWORDS
import org.fossify.messages.helpers.WebhookSimPolicy.State
import org.fossify.messages.helpers.WebhookStatus.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookStatusTest {
    @Test
    fun offWinsOverEverything() {
        State.entries.forEach {
            assertEquals(Status.OFF, WebhookStatus.of(enabled = false, setupComplete = true, simState = it))
        }
    }

    @Test
    fun setupRequiredWithoutASimOrACompleteSetup() {
        assertEquals(Status.SETUP_REQUIRED, WebhookStatus.of(true, true, State.NOT_SELECTED))
        assertEquals(Status.SETUP_REQUIRED, WebhookStatus.of(true, false, State.NOT_SELECTED))
        assertEquals(Status.SETUP_REQUIRED, WebhookStatus.of(true, false, State.READY))
    }

    @Test
    fun pausedWhenTheSelectedSimIsMissingOrMustBeSelectedAgain() {
        assertEquals(Status.PAUSED, WebhookStatus.of(true, true, State.MISSING))
        assertEquals(Status.PAUSED, WebhookStatus.of(true, true, State.RESELECT_REQUIRED))
        assertEquals(Status.PAUSED, WebhookStatus.of(true, false, State.RESELECT_REQUIRED))
    }

    @Test
    fun onOnlyWhenEverythingIsReady() {
        assertEquals(Status.ON, WebhookStatus.of(true, true, State.READY))
    }

    @Test
    fun filterIsUsableOnlyWhenTheListsItNeedsAreFilled() {
        assertTrue(WebhookMessageFilter.isUsable(MODE_NONE, "", ""))
        assertTrue(WebhookMessageFilter.isUsable(MODE_SENDERS, "GitHub", ""))
        assertFalse(WebhookMessageFilter.isUsable(MODE_SENDERS, " , ", "otp"))
        assertTrue(WebhookMessageFilter.isUsable(MODE_KEYWORDS, "", "otp"))
        assertFalse(WebhookMessageFilter.isUsable(MODE_KEYWORDS, "GitHub", ""))
        assertTrue(WebhookMessageFilter.isUsable(MODE_SENDERS_AND_KEYWORDS, "GitHub", "otp"))
        assertFalse(WebhookMessageFilter.isUsable(MODE_SENDERS_AND_KEYWORDS, "GitHub", ""))
    }
}
