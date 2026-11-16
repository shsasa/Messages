package org.fossify.messages.helpers

import android.content.Context
import org.fossify.messages.R
import org.fossify.messages.extensions.config

/** The one-line forwarding status shown on the Webhook entry in the main settings. */
object WebhookStatus {
    enum class Status { OFF, SETUP_REQUIRED, PAUSED, ON }

    /** [setupComplete] means a valid URL, a token, and a usable filter. Has no Android dependencies. */
    fun of(enabled: Boolean, setupComplete: Boolean, simState: WebhookSimPolicy.State): Status {
        return when {
            !enabled -> Status.OFF
            simState == WebhookSimPolicy.State.MISSING -> Status.PAUSED
            simState == WebhookSimPolicy.State.RESELECT_REQUIRED -> Status.PAUSED
            !setupComplete || simState == WebhookSimPolicy.State.NOT_SELECTED -> Status.SETUP_REQUIRED
            else -> Status.ON
        }
    }

    fun current(context: Context): Status {
        val config = context.config
        val setupComplete = WebhookSender.isUrlAllowed(config.webhookUrl) &&
            config.webhookBearerToken.isNotBlank() &&
            WebhookMessageFilter.isUsable(
                mode = config.webhookFilterMode,
                senders = config.webhookApprovedSenders,
                keywords = config.webhookFilterKeywords
            )
        val activeIds = WebhookSims.active(context)?.map { it.subscriptionId }
        return of(
            enabled = config.webhookEnabled && config.webhookForwardIncoming,
            setupComplete = setupComplete,
            simState = WebhookSims.state(context, activeIds)
        )
    }

    fun text(context: Context): String {
        return when (current(context)) {
            Status.OFF -> context.getString(R.string.webhook_status_off)
            Status.SETUP_REQUIRED -> context.getString(R.string.webhook_status_setup_required)
            Status.PAUSED -> context.getString(R.string.webhook_status_paused)
            Status.ON -> context.getString(R.string.webhook_status_on, WebhookSims.selectedLabel(context))
        }
    }
}
