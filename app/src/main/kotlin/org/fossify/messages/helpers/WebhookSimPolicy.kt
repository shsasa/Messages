package org.fossify.messages.helpers

/** One active SIM. Android gives every SIM card or eSIM profile its own subscription ID. */
data class WebhookSim(val subscriptionId: Int, val slotIndex: Int, val carrier: String)

/**
 * Decides whether the selected webhook SIM is usable and whether a message arrived on it.
 * Has no Android dependencies.
 *
 * The SIM is identified by its subscription ID. A different card in the same slot has a
 * different ID, so a replaced SIM is never treated as the selected one.
 */
object WebhookSimPolicy {
    const val NO_SIM = -1

    enum class State {
        /** No SIM has been selected yet. Nothing is forwarded. */
        NOT_SELECTED,

        /** The selected SIM went missing earlier. Paused until the user selects a SIM again. */
        RESELECT_REQUIRED,

        /** The selected SIM is not among the active SIMs, or the active SIMs cannot be read. */
        MISSING,

        READY,
    }

    enum class Action {
        FORWARD,

        /** Not for the webhook: wrong SIM, filtered out, or no SIM selected. */
        SKIP,

        /** The selected SIM has just been found missing: pause, and do not send. */
        PAUSE,

        /** Already paused: do not send until the user selects a SIM again. */
        BLOCKED,
    }

    /** [activeIds] is null when the active SIMs cannot be read. */
    fun state(selectedId: Int, reselectRequired: Boolean, activeIds: Collection<Int>?): State {
        return when {
            selectedId == NO_SIM -> State.NOT_SELECTED
            reselectRequired -> State.RESELECT_REQUIRED
            activeIds == null || selectedId !in activeIds -> State.MISSING
            else -> State.READY
        }
    }

    /**
     * True only if the message carries the subscription ID of the selected, active SIM.
     * A message without a subscription ID never matches, even when only one SIM is active.
     */
    fun isFromSelectedSim(selectedId: Int, messageSubscriptionId: Int, activeIds: Collection<Int>?): Boolean {
        return selectedId != NO_SIM &&
            messageSubscriptionId == selectedId &&
            activeIds != null &&
            selectedId in activeIds
    }

    /** What to do with a message, on arrival and again before every delivery attempt. */
    fun action(state: State, matches: Boolean): Action {
        return when (state) {
            State.READY -> if (matches) Action.FORWARD else Action.SKIP
            State.MISSING -> Action.PAUSE
            State.RESELECT_REQUIRED -> Action.BLOCKED
            State.NOT_SELECTED -> Action.SKIP
        }
    }
}
