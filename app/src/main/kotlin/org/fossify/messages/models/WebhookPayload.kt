package org.fossify.messages.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebhookPayload(
    @SerialName("event_type")
    val eventType: String,

    val address: String,

    @SerialName("contact_name")
    val contactName: String? = null,

    val body: String? = null,

    val timestamp: Long,

    @SerialName("thread_id")
    val threadId: Long? = null,

    @SerialName("subscription_id")
    val subscriptionId: Int? = null,

    @SerialName("message_id")
    val messageId: Long? = null,

    @SerialName("app_package")
    val appPackage: String,
)
