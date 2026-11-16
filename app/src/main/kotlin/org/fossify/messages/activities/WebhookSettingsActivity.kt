package org.fossify.messages.activities

import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityWebhookSettingsBinding
import org.fossify.messages.dialogs.WebhookInputDialog
import org.fossify.messages.dialogs.WebhookTestPreviewDialog
import org.fossify.messages.dialogs.WebhookTestResultDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.WebhookMessageFilter
import org.fossify.messages.helpers.WebhookSender
import org.fossify.messages.helpers.WebhookSimPolicy
import org.fossify.messages.helpers.WebhookSims

/** All webhook forwarding controls: forwarding and SIM, message filter, connection and test. */
class WebhookSettingsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityWebhookSettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.webhookSettingsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.webhookSettingsNestedScrollview,
            topAppBar = binding.webhookSettingsAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.webhookSettingsAppbar, NavigationIcon.Arrow)
        setupWebhook()
        updateTextColors(binding.webhookSettingsNestedScrollview)

        arrayOf(
            binding.webhookSectionForwardingLabel,
            binding.webhookSectionFilterLabel,
            binding.webhookSectionConnectionLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupWebhook() {
        setupWebhookSwitches()
        setupWebhookSim()
        setupWebhookFilter()
        setupWebhookTextRows()
        setupWebhookTest()
        updateWebhookSettingVisibility()
    }

    private fun updateWebhookSettingVisibility() = binding.apply {
        val filterMode = config.webhookFilterMode
        settingsWebhookTestHolder.beVisibleIf(config.webhookEnabled)
        settingsWebhookApprovedSendersHolder.beVisibleIf(WebhookMessageFilter.usesSenders(filterMode))
        settingsWebhookKeywordsHolder.beVisibleIf(WebhookMessageFilter.usesKeywords(filterMode))
    }

    private fun setupWebhookSim(): ActivityWebhookSettingsBinding = binding.apply {
        val activeSims = WebhookSims.active(this@WebhookSettingsActivity)
        val state = WebhookSims.state(this@WebhookSettingsActivity, activeSims?.map { it.subscriptionId })
        if (state == WebhookSimPolicy.State.MISSING) {
            WebhookSims.requireReselect(this@WebhookSettingsActivity)
        }

        val selectedLabel = WebhookSims.selectedLabel(this@WebhookSettingsActivity)
        settingsWebhookSim.text = when (state) {
            WebhookSimPolicy.State.READY -> selectedLabel
            WebhookSimPolicy.State.NOT_SELECTED -> getString(R.string.webhook_sim_not_selected)
            else -> "$selectedLabel: ${getString(R.string.webhook_sim_missing)}"
        }

        settingsWebhookSimHolder.setOnClickListener {
            val sims = WebhookSims.active(this@WebhookSettingsActivity).orEmpty()
            if (sims.isEmpty()) {
                toast(R.string.webhook_sim_none_active)
                return@setOnClickListener
            }

            // After a pause nothing is preselected, so the user has to choose the SIM again.
            val selectedId = if (state == WebhookSimPolicy.State.READY) {
                WebhookSims.selectedId(this@WebhookSettingsActivity)
            } else {
                WebhookSimPolicy.NO_SIM
            }
            val items = ArrayList(
                sims.map { RadioItem(it.subscriptionId, WebhookSims.label(this@WebhookSettingsActivity, it)) }
            )
            RadioGroupDialog(this@WebhookSettingsActivity, items, selectedId) { chosen ->
                sims.firstOrNull { it.subscriptionId == chosen as Int }?.let {
                    WebhookSims.select(this@WebhookSettingsActivity, it)
                }
                setupWebhookSim()
            }
        }
    }

    private fun setupWebhookFilter() = binding.apply {
        val items = arrayListOf(
            RadioItem(WebhookMessageFilter.MODE_NONE, getString(R.string.webhook_filter_none)),
            RadioItem(WebhookMessageFilter.MODE_SENDERS, getString(R.string.webhook_filter_senders)),
            RadioItem(WebhookMessageFilter.MODE_KEYWORDS, getString(R.string.webhook_filter_keywords)),
            RadioItem(
                WebhookMessageFilter.MODE_SENDERS_AND_KEYWORDS,
                getString(R.string.webhook_filter_senders_and_keywords)
            ),
        )
        settingsWebhookFilter.text = items.first { it.id == config.webhookFilterMode }.title
        settingsWebhookFilterHolder.setOnClickListener {
            RadioGroupDialog(this@WebhookSettingsActivity, items, config.webhookFilterMode) {
                config.webhookFilterMode = it as Int
                settingsWebhookFilter.text = items.first { item -> item.id == it }.title
                updateWebhookSettingVisibility()
            }
        }
    }

    private fun setupWebhookSwitches() = binding.apply {
        settingsWebhookEnabled.isChecked = config.webhookEnabled
        settingsWebhookEnabledHolder.setOnClickListener {
            settingsWebhookEnabled.toggle()
            config.webhookEnabled = settingsWebhookEnabled.isChecked
            updateWebhookSettingVisibility()
        }

        settingsWebhookForwardIncoming.isChecked = config.webhookForwardIncoming
        settingsWebhookForwardIncomingHolder.setOnClickListener {
            settingsWebhookForwardIncoming.toggle()
            config.webhookForwardIncoming = settingsWebhookForwardIncoming.isChecked
        }
    }

    private fun setupWebhookTextRows() = binding.apply {
        setupTextRow(
            holder = settingsWebhookApprovedSendersHolder,
            value = settingsWebhookApprovedSenders,
            setting = TextSetting(
                titleId = R.string.webhook_approved_senders,
                hintId = R.string.webhook_approved_senders_hint,
                read = { WebhookMessageFilter.displayList(config.webhookApprovedSenders) },
                write = { config.webhookApprovedSenders = it }
            )
        )
        setupTextRow(
            holder = settingsWebhookKeywordsHolder,
            value = settingsWebhookKeywords,
            setting = TextSetting(
                titleId = R.string.webhook_keywords,
                hintId = R.string.webhook_keywords_hint,
                read = { WebhookMessageFilter.displayList(config.webhookFilterKeywords) },
                write = { config.webhookFilterKeywords = it }
            )
        )
        setupTextRow(
            holder = settingsWebhookUrlHolder,
            value = settingsWebhookUrl,
            setting = TextSetting(
                titleId = R.string.webhook_url,
                hintId = R.string.webhook_url,
                isUrl = true,
                read = { config.webhookUrl.trim() },
                write = { config.webhookUrl = it.trim() }
            )
        )
        // The saved token is never shown again: the dialog starts empty and saving replaces the token.
        setupTextRow(
            holder = settingsWebhookBearerTokenHolder,
            value = settingsWebhookBearerToken,
            setting = TextSetting(
                titleId = R.string.webhook_bearer_token,
                hintId = R.string.webhook_bearer_token_hint,
                isSecret = true,
                read = { config.webhookBearerToken },
                write = { config.webhookBearerToken = it }
            )
        )
    }

    private fun setupTextRow(holder: View, value: TextView, setting: TextSetting) {
        fun show() {
            val current = setting.read()
            value.text = if (setting.isSecret && current.isNotEmpty()) SECRET_MASK else current
        }

        show()
        holder.setOnClickListener {
            WebhookInputDialog(
                activity = this,
                titleId = setting.titleId,
                hintId = setting.hintId,
                prefill = if (setting.isSecret) "" else setting.read(),
                isUrl = setting.isUrl
            ) { newValue ->
                setting.write(newValue)
                show()
            }
        }
    }

    private fun setupWebhookTest() = binding.apply {
        settingsWebhookTestHolder.setOnClickListener {
            when {
                !config.webhookEnabled -> return@setOnClickListener
                config.webhookUrl.trim().isBlank() -> toast(R.string.webhook_url_required)
                else -> {
                    val preview = WebhookSender.previewTest(this@WebhookSettingsActivity)
                    WebhookTestPreviewDialog(
                        activity = this@WebhookSettingsActivity,
                        result = preview
                    ) { request ->
                        ensureBackgroundThread {
                            val result = WebhookSender.executeTest(this@WebhookSettingsActivity, request)
                            runOnUiThread {
                                WebhookTestResultDialog(this@WebhookSettingsActivity, result)
                            }
                        }
                    }
                }
            }
        }
    }

    private class TextSetting(
        val titleId: Int,
        val hintId: Int,
        val isUrl: Boolean = false,
        val isSecret: Boolean = false,
        val read: () -> String,
        val write: (String) -> Unit,
    )

    private companion object {
        const val SECRET_MASK = "••••"
    }
}
