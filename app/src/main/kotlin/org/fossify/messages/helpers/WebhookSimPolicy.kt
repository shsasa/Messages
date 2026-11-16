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
     * The active SIM a message arrived on, or [NO_SIM] if that is unknown. A message without
     * a subscription ID is attributed to the only active SIM when there is exactly one.
     */
    fun messageSim(messageSubscriptionId: Int, activeIds: Collection<Int>?): Int {
        return when {
            activeIds == null -> NO_SIM
            messageSubscriptionId in activeIds -> messageSubscriptionId
            messageSubscriptionId < 0 && activeIds.size == 1 -> activeIds.first()
            else -> NO_SIM
        }
    }

    fun isFromSelectedSim(selectedId: Int, messageSubscriptionId: Int, activeIds: Collection<Int>?): Boolean {
        return selectedId != NO_SIM && messageSim(messageSubscriptionId, activeIds) == selectedId
    }
}
