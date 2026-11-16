package org.fossify.messages.helpers

import org.fossify.messages.helpers.WebhookSimPolicy.NO_SIM
import org.fossify.messages.helpers.WebhookSimPolicy.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebhookSimPolicyTest {
    private val sim1 = 1
    private val sim2 = 2
    private val replacement = 7
    private val both = listOf(sim1, sim2)

    @Test
    fun nothingIsForwardedUntilASimIsSelected() {
        assertEquals(State.NOT_SELECTED, WebhookSimPolicy.state(NO_SIM, false, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(NO_SIM, sim1, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(NO_SIM, NO_SIM, listOf(sim1)))
    }

    @Test
    fun onlyMessagesFromTheSelectedSimMatch() {
        assertEquals(State.READY, WebhookSimPolicy.state(sim1, false, both))
        assertTrue(WebhookSimPolicy.isFromSelectedSim(sim1, sim1, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, sim2, both))

        assertTrue(WebhookSimPolicy.isFromSelectedSim(sim2, sim2, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim2, sim1, both))
    }

    @Test
    fun messageFromAnUnknownSimNeverMatchesWithTwoSims() {
        assertEquals(NO_SIM, WebhookSimPolicy.messageSim(NO_SIM, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, NO_SIM, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, 99, both))
    }

    @Test
    fun messageWithoutSubscriptionIdBelongsToTheOnlyActiveSim() {
        assertTrue(WebhookSimPolicy.isFromSelectedSim(sim1, NO_SIM, listOf(sim1)))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, NO_SIM, listOf(sim2)))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, NO_SIM, emptyList()))
    }

    @Test
    fun removedSimIsMissing() {
        assertEquals(State.MISSING, WebhookSimPolicy.state(sim1, false, listOf(sim2)))
        assertEquals(State.MISSING, WebhookSimPolicy.state(sim1, false, emptyList()))
    }

    @Test
    fun replacedSimIsMissingAndItsMessagesDoNotMatch() {
        val afterReplacement = listOf(replacement)

        assertEquals(State.MISSING, WebhookSimPolicy.state(sim1, false, afterReplacement))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, replacement, afterReplacement))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, NO_SIM, afterReplacement))
    }

    @Test
    fun unreadableSimsAreMissing() {
        assertEquals(State.MISSING, WebhookSimPolicy.state(sim1, false, null))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, sim1, null))
    }

    @Test
    fun pauseStaysUntilTheUserSelectsAgainEvenIfTheSimReturns() {
        assertEquals(State.RESELECT_REQUIRED, WebhookSimPolicy.state(sim1, true, both))
        assertEquals(State.READY, WebhookSimPolicy.state(sim1, false, both))
    }
}
