package org.fossify.messages.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.BasePropertiesDialog
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R
import org.fossify.messages.models.WebhookTestResult

class WebhookTestResultDialog(
    val activity: BaseSimpleActivity,
    val result: WebhookTestResult,
) : BasePropertiesDialog(activity) {

    init {
        addProperty(R.string.webhook_url, result.url)
        addProperty(R.string.webhook_http_method, result.method)
        addProperty(R.string.webhook_response_code, result.responseCode?.toString() ?: activity.getString(R.string.none))
        addProperty(R.string.webhook_duration, activity.getString(R.string.webhook_duration, result.durationMs))
        addProperty(R.string.webhook_response_headers, formatMap(result.responseHeaders) ?: activity.getString(R.string.none))
        addProperty(R.string.webhook_response_body, result.responseBody ?: activity.getString(R.string.none))
        if (!result.errorMessage.isNullOrBlank()) {
            addProperty(R.string.webhook_error, result.errorMessage)
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNeutralButton(org.fossify.commons.R.string.copy) { _, _ -> activity.copyToClipboard(buildFullText()) }
            .apply {
                activity.setupDialogStuff(mDialogView.root, this, R.string.webhook_test_result)
            }
    }

    private fun formatMap(map: Map<String, String>): String? {
        return map.takeIf { it.isNotEmpty() }?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }
    }

    private fun buildFullText(): String {
        return buildString {
            appendLine("${activity.getString(R.string.webhook_url)}: ${result.url}")
            appendLine("${activity.getString(R.string.webhook_http_method)}: ${result.method}")
            appendLine("${activity.getString(R.string.webhook_event_type)}: ${result.eventType}")
            appendLine()
            appendLine("${activity.getString(R.string.webhook_headers)}:")
            appendLine(formatMap(result.headers) ?: activity.getString(R.string.none))
            appendLine()
            if (result.queryParameters != null) {
                appendLine("${activity.getString(R.string.webhook_query_parameters)}:")
                appendLine(formatMap(result.queryParameters) ?: activity.getString(R.string.none))
                appendLine()
            }
            appendLine("${activity.getString(R.string.webhook_request_body)}:")
            appendLine(result.requestBody ?: activity.getString(R.string.none))
            appendLine()
            appendLine("${activity.getString(R.string.webhook_response_code)}: ${result.responseCode ?: activity.getString(R.string.none)}")
            appendLine("${activity.getString(R.string.webhook_duration, result.durationMs)}")
            appendLine()
            appendLine("${activity.getString(R.string.webhook_response_headers)}:")
            appendLine(formatMap(result.responseHeaders) ?: activity.getString(R.string.none))
            appendLine()
            appendLine("${activity.getString(R.string.webhook_response_body)}:")
            appendLine(result.responseBody ?: activity.getString(R.string.none))
            if (!result.errorMessage.isNullOrBlank()) {
                appendLine()
                appendLine("${activity.getString(R.string.webhook_error)}: ${result.errorMessage}")
            }
        }
    }
}
