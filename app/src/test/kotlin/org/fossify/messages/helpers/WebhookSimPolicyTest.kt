package org.fossify.messages.helpers

import org.fossify.messages.helpers.WebhookSimPolicy.Action
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

    /** The decision made on arrival and again before every delivery attempt. */
    private fun action(selected: Int, paused: Boolean, messageSim: Int, active: Collection<Int>?): Action {
        return WebhookSimPolicy.action(
            state = WebhookSimPolicy.state(selected, paused, active),
            matches = WebhookSimPolicy.isFromSelectedSim(selected, messageSim, active)
        )
    }

    @Test
    fun nothingIsForwardedUntilASimIsSelected() {
        assertEquals(State.NOT_SELECTED, WebhookSimPolicy.state(NO_SIM, false, both))
        assertEquals(Action.SKIP, action(NO_SIM, false, sim1, both))
        assertEquals(Action.SKIP, action(NO_SIM, false, NO_SIM, listOf(sim1)))
    }

    @Test
    fun onlyMessagesFromTheSelectedSimAreForwarded() {
        assertEquals(Action.FORWARD, action(sim1, false, sim1, both))
        assertEquals(Action.SKIP, action(sim1, false, sim2, both))

        assertEquals(Action.FORWARD, action(sim2, false, sim2, both))
        assertEquals(Action.SKIP, action(sim2, false, sim1, both))
    }

    @Test
    fun messageWithUnknownSubscriptionIdIsSkippedEvenWithOneActiveSim() {
        assertEquals(Action.SKIP, action(sim1, false, NO_SIM, listOf(sim1)))
        assertEquals(Action.SKIP, action(sim1, false, NO_SIM, both))
        assertEquals(Action.SKIP, action(sim1, false, 99, both))
    }

    @Test
    fun removedSimPauses() {
        assertEquals(State.MISSING, WebhookSimPolicy.state(sim1, false, listOf(sim2)))
        assertEquals(Action.PAUSE, action(sim1, false, sim1, listOf(sim2)))
        assertEquals(Action.PAUSE, action(sim1, false, sim1, emptyList()))
    }

    @Test
    fun replacedSimPausesAndItsMessagesAreNotForwarded() {
        val afterReplacement = listOf(replacement)

        assertEquals(Action.PAUSE, action(sim1, false, replacement, afterReplacement))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, replacement, afterReplacement))
    }

    @Test
    fun unreadableSimsPause() {
        assertEquals(Action.PAUSE, action(sim1, false, sim1, null))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, sim1, null))
    }

    @Test
    fun retryStaysBlockedAfterAPauseUntilTheUserSelectsTheSimAgain() {
        // The queued message arrived on SIM 1. The SIM goes missing: the retry pauses forwarding.
        assertEquals(Action.PAUSE, action(sim1, paused = false, messageSim = sim1, active = listOf(sim2)))

        // The same SIM comes back, but the pause is still set: every further retry is blocked.
        assertEquals(State.RESELECT_REQUIRED, WebhookSimPolicy.state(sim1, true, both))
        assertEquals(Action.BLOCKED, action(sim1, paused = true, messageSim = sim1, active = both))
        assertEquals(Action.BLOCKED, action(sim1, paused = true, messageSim = sim1, active = both))

        // Selecting the SIM again clears the pause, and only then is the retry sent.
        assertEquals(Action.FORWARD, action(sim1, paused = false, messageSim = sim1, active = both))
    }

    @Test
    fun retryIsDroppedIfTheUserSelectsADifferentSimAfterAPause() {
        assertEquals(Action.SKIP, action(sim2, paused = false, messageSim = sim1, active = both))
    }

    @Test
    fun matchRequiresTheSelectedSimToBeActive() {
        assertTrue(WebhookSimPolicy.isFromSelectedSim(sim1, sim1, both))
        assertFalse(WebhookSimPolicy.isFromSelectedSim(sim1, sim1, listOf(sim2)))
    }
}
