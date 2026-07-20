package org.fossify.messages.dialogs

import android.text.InputType
import android.util.Patterns
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogWebhookInputBinding

class WebhookInputDialog(
    private val activity: BaseSimpleActivity,
    private val titleId: Int,
    private val hintId: Int,
    private val prefill: String,
    private val isUrl: Boolean,
    private val callback: (String) -> Unit,
) {
    init {
        val binding = DialogWebhookInputBinding.inflate(activity.layoutInflater).apply {
            webhookInputLayout.hint = activity.getString(hintId)
            webhookInput.setText(prefill)
            if (isUrl) {
                webhookInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, titleId) { alertDialog ->
                    alertDialog.showKeyboard(binding.webhookInput)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val value = binding.webhookInput.value.let { if (isUrl) it.trim() else it }
                        if (isUrl && value.isNotEmpty() && !Patterns.WEB_URL.matcher(value).matches()) {
                            activity.toast(R.string.webhook_url_invalid)
                            return@setOnClickListener
                        }

                        if (isUrl && value.startsWith("http://", ignoreCase = true)) {
                            activity.toast(R.string.webhook_http_insecure)
                        }

                        callback(value)
                        alertDialog.dismiss()
                    }
                }
            }
    }
}
