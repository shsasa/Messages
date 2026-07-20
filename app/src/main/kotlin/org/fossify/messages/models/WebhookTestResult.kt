package org.fossify.messages.models

data class WebhookTestResult(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val requestBody: String?,
    val queryParameters: Map<String, String>? = null,
    val responseCode: Int? = null,
    val responseHeaders: Map<String, String> = emptyMap(),
    val responseBody: String? = null,
    val errorMessage: String? = null,
    val durationMs: Long = 0,
    val eventType: String,
    val isSuccess: Boolean = false,
)
