package org.fossify.messages.helpers

enum class WebhookEventType(val value: String) {
    INCOMING_SMS("incoming_sms"),
    OUTGOING_SMS("outgoing_sms"),
    INCOMING_MMS("incoming_mms"),
    OUTGOING_MMS("outgoing_mms"),
    TEST("test"),
}
