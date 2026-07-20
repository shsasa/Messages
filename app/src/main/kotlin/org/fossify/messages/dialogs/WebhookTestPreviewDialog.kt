package org.fossify.messages.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.BasePropertiesDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R
import org.fossify.messages.models.WebhookTestResult

class WebhookTestPreviewDialog(
    val activity: BaseSimpleActivity,
    val result: WebhookTestResult,
    val sendCallback: (WebhookTestResult) -> Unit,
) : BasePropertiesDialog(activity) {

    init {
        addProperty(R.string.webhook_url, result.url)
        addProperty(R.string.webhook_http_method, result.method)
        addProperty(R.string.webhook_event_type, result.eventType)
        addProperty(R.string.webhook_headers, formatMap(result.headers) ?: activity.getString(R.string.none))
        if (result.queryParameters != null) {
            addProperty(R.string.webhook_query_parameters, formatMap(result.queryParameters) ?: activity.getString(R.string.none))
        }
        addProperty(R.string.webhook_request_body, result.requestBody ?: activity.getString(R.string.none))

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.send) { _, _ -> sendCallback(result) }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(mDialogView.root, this, R.string.webhook_test_preview)
            }
    }

    private fun formatMap(map: Map<String, String>): String? {
        return map.takeIf { it.isNotEmpty() }?.entries?.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}
