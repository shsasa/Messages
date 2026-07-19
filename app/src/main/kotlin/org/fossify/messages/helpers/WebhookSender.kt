package org.fossify.messages.helpers

import android.content.Context
import android.net.Uri
import android.telephony.SubscriptionManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.json.Json
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.getNameAndPhotoFromPhoneNumber
import org.fossify.messages.models.Message
import org.fossify.messages.models.WebhookPayload

object WebhookSender {
    private const val TIMEOUT_MS = 15000

    private val json = Json { explicitNulls = false }

    fun send(context: Context, eventType: WebhookEventType, message: Message) {
        val address = message.senderPhoneNumber.ifEmpty {
            message.participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        } ?: ""
        val senderName = message.senderName.ifEmpty {
            message.participants.firstOrNull()?.name
        } ?: ""
        val contactName = senderName.takeIf { it.isNotEmpty() && it != address }
        send(
            context,
            WebhookPayload(
                eventType = eventType.value,
                address = address,
                contactName = contactName,
                body = message.body.takeIf { it.isNotEmpty() },
                timestamp = message.millis(),
                threadId = message.threadId.takeIf { it != 0L },
                subscriptionId = message.subscriptionId
                    .takeIf { it != -1 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                messageId = message.id.takeIf { it != 0L },
                appPackage = context.packageName
            )
        )
    }

    fun sendOutgoingSms(
        context: Context,
        address: String,
        body: String,
        threadId: Long,
        subId: Int,
        messageId: Long,
    ) {
        ensureBackgroundThread {
            val contactName = context.getNameAndPhotoFromPhoneNumber(address).name
                .takeIf { it != address }
            val payload = WebhookPayload(
                eventType = WebhookEventType.OUTGOING_SMS.value,
                address = address,
                contactName = contactName,
                body = body.takeIf { it.isNotEmpty() },
                timestamp = System.currentTimeMillis(),
                threadId = threadId.takeIf { it != 0L },
                subscriptionId = subId
                    .takeIf { it != -1 && it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
                messageId = messageId.takeIf { it != 0L },
                appPackage = context.packageName
            )
            sendInternal(context, payload)
        }
    }

    fun sendTest(context: Context): Boolean {
        val payload = WebhookPayload(
            eventType = WebhookEventType.TEST.value,
            address = "+1234567890",
            body = "This is a test webhook from Fossify Messages.",
            timestamp = System.currentTimeMillis(),
            appPackage = context.packageName
        )
        return sendInternal(context, payload)
    }

    fun send(context: Context, payload: WebhookPayload) {
        ensureBackgroundThread {
            sendInternal(context, payload)
        }
    }

    @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
    private fun sendInternal(context: Context, payload: WebhookPayload): Boolean {
        val config = context.config
        val urlString = config.webhookUrl.trim()
        if (!config.webhookEnabled || urlString.isBlank() || !shouldSendEvent(config, payload)) {
            return false
        }

        val body = json.encodeToString(WebhookPayload.serializer(), payload)
        return try {
            when (config.webhookHttpMethod) {
                WEBHOOK_METHOD_GET -> executeRequest(context, buildGetUrl(urlString, payload), "GET", null)
                WEBHOOK_METHOD_PUT -> executeRequest(context, URL(urlString), "PUT", body)
                else -> executeRequest(context, URL(urlString), "POST", body)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun shouldSendEvent(config: Config, payload: WebhookPayload): Boolean {
        return when (payload.eventType) {
            WebhookEventType.INCOMING_SMS.value, WebhookEventType.INCOMING_MMS.value -> {
                config.webhookForwardIncoming
            }
            WebhookEventType.OUTGOING_SMS.value, WebhookEventType.OUTGOING_MMS.value -> {
                config.webhookForwardOutgoing
            }
            else -> true
        }
    }

    private fun buildGetUrl(baseUrl: String, payload: WebhookPayload): URL {
        val builder = Uri.parse(baseUrl).buildUpon()
        payload.toMap().forEach { (key, value) ->
            if (value != null) {
                builder.appendQueryParameter(key, value.toString())
            }
        }
        return URL(builder.build().toString())
    }

    @Suppress("TooGenericExceptionCaught", "PrintStackTrace")
    private fun executeRequest(context: Context, url: URL, method: String, body: String?): Boolean {
        val config = context.config
        var connection: HttpURLConnection? = null
        return try {
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")

            val token = config.webhookBearerToken
            if (token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val responseCode = connection.responseCode
            responseCode in HttpURLConnection.HTTP_OK until HttpURLConnection.HTTP_MULT_CHOICE
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun WebhookPayload.toMap(): Map<String, Any?> {
        return mapOf(
            "event_type" to eventType,
            "address" to address,
            "contact_name" to contactName,
            "body" to body,
            "timestamp" to timestamp,
            "thread_id" to threadId,
            "subscription_id" to subscriptionId,
            "message_id" to messageId,
            "app_package" to appPackage,
        )
    }
}
