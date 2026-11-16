package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.activities.ManageBlockedNumbersActivity
import org.fossify.commons.dialogs.ChangeDateTimeFormatDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatWithDeprecatedBadge
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.getFontSizeText
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE
import org.fossify.commons.helpers.FONT_SIZE_LARGE
import org.fossify.commons.helpers.FONT_SIZE_MEDIUM
import org.fossify.commons.helpers.FONT_SIZE_SMALL
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PROTECTION_FINGERPRINT
import org.fossify.commons.helpers.SHOW_ALL_TABS
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.helpers.isTiramisuPlus
import org.fossify.commons.models.RadioItem
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivitySettingsBinding
import org.fossify.messages.dialogs.ExportMessagesDialog
import org.fossify.messages.dialogs.WebhookInputDialog
import org.fossify.messages.dialogs.WebhookTestPreviewDialog
import org.fossify.messages.dialogs.WebhookTestResultDialog
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.emptyMessagesRecycleBin
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.helpers.FILE_SIZE_100_KB
import org.fossify.messages.helpers.FILE_SIZE_1_MB
import org.fossify.messages.helpers.FILE_SIZE_200_KB
import org.fossify.messages.helpers.FILE_SIZE_2_MB
import org.fossify.messages.helpers.FILE_SIZE_300_KB
import org.fossify.messages.helpers.FILE_SIZE_600_KB
import org.fossify.messages.helpers.FILE_SIZE_NONE
import org.fossify.messages.helpers.LOCK_SCREEN_NOTHING
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER
import org.fossify.messages.helpers.LOCK_SCREEN_SENDER_MESSAGE
import org.fossify.messages.helpers.MessagesImporter
import org.fossify.messages.helpers.WebhookMessageFilter
import org.fossify.messages.helpers.WebhookSender
import org.fossify.messages.helpers.WebhookSimPolicy
import org.fossify.messages.helpers.WebhookSims
import org.fossify.messages.helpers.refreshConversations
import java.util.Locale
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    private var blockedNumbersAtPause = -1
    private var recycleBinMessages = 0
    private val messagesFileType = "application/json"
    private val messageImportFileTypes = buildList {
        add("application/json")
        add("application/xml")
        add("text/xml")
        if (!isQPlus()) {
            add("application/octet-stream")
        }
    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                MessagesImporter(this).importMessages(uri)
            }
        }

    private var exportMessagesDialog: ExportMessagesDialog? = null

    private val saveDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument(messagesFileType)) { uri ->
            if (uri != null) {
                toast(org.fossify.commons.R.string.exporting)
                exportMessagesDialog?.exportMessages(uri)
            }
        }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.settingsNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.settingsNestedScrollview,
            topAppBar = binding.settingsAppbar
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupCustomizeNotifications()
        setupUseEnglish()
        setupLanguage()
        setupManageBlockedNumbers()
        setupManageBlockedKeywords()
        setupChangeDateTimeFormat()
        setupFontSize()
        setupShowCharacterCounter()
        setupUseSimpleCharacters()
        setupSendOnEnter()
        setupEnableDeliveryReports()
        setupSendLongMessageAsMMS()
        setupGroupMessageAsMMS()
        setupKeepConversationsArchived()
        setupLockScreenVisibility()
        setupMMSFileSizeLimit()
        setupUseRecycleBin()
        setupEmptyRecycleBin()
        setupAppPasswordProtection()
        setupMessagesExport()
        setupMessagesImport()
        setupWebhook()
        updateTextColors(binding.settingsNestedScrollview)

        if (
            blockedNumbersAtPause != -1 && blockedNumbersAtPause != getBlockedNumbers().hashCode()
        ) {
            refreshConversations()
        }

        arrayOf(
            binding.settingsColorCustomizationSectionLabel,
            binding.settingsGeneralSettingsLabel,
            binding.settingsOutgoingMessagesLabel,
            binding.settingsNotificationsLabel,
            binding.settingsArchivedMessagesLabel,
            binding.settingsRecycleBinLabel,
            binding.settingsSecurityLabel,
            binding.settingsMigratingLabel,
            binding.settingsWebhookLabel
        ).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupMessagesExport() {
        binding.settingsExportMessagesHolder.setOnClickListener {
            exportMessagesDialog = ExportMessagesDialog(this) { fileName ->
                saveDocument.launch("$fileName.json")
            }
        }
    }

    private fun setupMessagesImport() {
        binding.settingsImportMessagesHolder.setOnClickListener {
            getContent.launch(messageImportFileTypes.toTypedArray())
        }
    }

    override fun onPause() {
        super.onPause()
        blockedNumbersAtPause = getBlockedNumbers().hashCode()
    }

    private fun setupCustomizeColors() = binding.apply {
        settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupCustomizeNotifications() = binding.apply {
        settingsCustomizeNotificationsHolder.setOnClickListener {
            launchCustomizeNotificationsIntent()
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf(
            (config.wasUseEnglishToggled || Locale.getDefault().language != "en")
                    && !isTiramisuPlus()
        )
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        if (isTiramisuPlus()) {
            settingsLanguageHolder.beVisible()
            settingsLanguageHolder.setOnClickListener {
                launchChangeAppLanguageIntent()
            }
        } else {
            settingsLanguageHolder.beGone()
        }
    }

    private fun setupManageBlockedNumbers() = binding.apply {
        settingsManageBlockedNumbers.text =
            getString(org.fossify.commons.R.string.manage_blocked_numbers)
        settingsManageBlockedNumbersHolder.beVisible()
        settingsManageBlockedNumbersHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupManageBlockedKeywords() = binding.apply {
        settingsManageBlockedKeywords.text =
            getString(R.string.manage_blocked_keywords)

        settingsManageBlockedKeywordsHolder.setOnClickListener {
            Intent(this@SettingsActivity, ManageBlockedKeywordsActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun setupChangeDateTimeFormat() = binding.apply {
        settingsChangeDateTimeFormatHolder.setOnClickListener {
            ChangeDateTimeFormatDialog(this@SettingsActivity) {
                refreshConversations()
            }
        }
    }

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                RadioItem(FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                RadioItem(FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                RadioItem(
                    FONT_SIZE_EXTRA_LARGE,
                    getString(org.fossify.commons.R.string.extra_large)
                )
            )

            RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
            }
        }
    }

    private fun setupShowCharacterCounter() = binding.apply {
        settingsShowCharacterCounter.isChecked = config.showCharacterCounter
        settingsShowCharacterCounterHolder.setOnClickListener {
            settingsShowCharacterCounter.toggle()
            config.showCharacterCounter = settingsShowCharacterCounter.isChecked
        }
    }

    private fun setupUseSimpleCharacters() = binding.apply {
        settingsUseSimpleCharacters.isChecked = config.useSimpleCharacters
        settingsUseSimpleCharactersHolder.setOnClickListener {
            settingsUseSimpleCharacters.toggle()
            config.useSimpleCharacters = settingsUseSimpleCharacters.isChecked
        }
    }

    private fun setupSendOnEnter() = binding.apply {
        settingsSendOnEnter.isChecked = config.sendOnEnter
        settingsSendOnEnterHolder.setOnClickListener {
            settingsSendOnEnter.toggle()
            config.sendOnEnter = settingsSendOnEnter.isChecked
        }
    }

    private fun setupEnableDeliveryReports() = binding.apply {
        settingsEnableDeliveryReports.isChecked = config.enableDeliveryReports
        settingsEnableDeliveryReportsHolder.setOnClickListener {
            settingsEnableDeliveryReports.toggle()
            config.enableDeliveryReports = settingsEnableDeliveryReports.isChecked
        }
    }

    private fun setupSendLongMessageAsMMS() = binding.apply {
        settingsSendLongMessageMms.isChecked = config.sendLongMessageMMS
        settingsSendLongMessageMmsHolder.setOnClickListener {
            settingsSendLongMessageMms.toggle()
            config.sendLongMessageMMS = settingsSendLongMessageMms.isChecked
        }
    }

    private fun setupGroupMessageAsMMS() = binding.apply {
        settingsSendGroupMessageMms.isChecked = config.sendGroupMessageMMS
        settingsSendGroupMessageMmsHolder.setOnClickListener {
            settingsSendGroupMessageMms.toggle()
            config.sendGroupMessageMMS = settingsSendGroupMessageMms.isChecked
        }
    }

    private fun setupKeepConversationsArchived() = binding.apply {
        settingsKeepConversationsArchived.isChecked = config.keepConversationsArchived
        settingsKeepConversationsArchivedHolder.setOnClickListener {
            settingsKeepConversationsArchived.toggle()
            config.keepConversationsArchived = settingsKeepConversationsArchived.isChecked
        }
    }

    private fun setupLockScreenVisibility() = binding.apply {
        settingsLockScreenVisibility.text = getLockScreenVisibilityText()
        settingsLockScreenVisibilityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(LOCK_SCREEN_SENDER_MESSAGE, getString(R.string.sender_and_message)),
                RadioItem(LOCK_SCREEN_SENDER, getString(R.string.sender_only)),
                RadioItem(LOCK_SCREEN_NOTHING, getString(org.fossify.commons.R.string.nothing)),
            )

            RadioGroupDialog(this@SettingsActivity, items, config.lockScreenVisibilitySetting) {
                config.lockScreenVisibilitySetting = it as Int
                settingsLockScreenVisibility.text = getLockScreenVisibilityText()
            }
        }
    }

    private fun getLockScreenVisibilityText() = getString(
        when (config.lockScreenVisibilitySetting) {
            LOCK_SCREEN_SENDER_MESSAGE -> R.string.sender_and_message
            LOCK_SCREEN_SENDER -> R.string.sender_only
            else -> org.fossify.commons.R.string.nothing
        }
    )

    private fun setupMMSFileSizeLimit() = binding.apply {
        settingsMmsFileSizeLimit.text = getMMSFileLimitText()
        settingsMmsFileSizeLimitHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(7, getString(R.string.mms_file_size_limit_none), FILE_SIZE_NONE),
                RadioItem(6, getString(R.string.mms_file_size_limit_2mb), FILE_SIZE_2_MB),
                RadioItem(5, getString(R.string.mms_file_size_limit_1mb), FILE_SIZE_1_MB),
                RadioItem(4, getString(R.string.mms_file_size_limit_600kb), FILE_SIZE_600_KB),
                RadioItem(3, getString(R.string.mms_file_size_limit_300kb), FILE_SIZE_300_KB),
                RadioItem(2, getString(R.string.mms_file_size_limit_200kb), FILE_SIZE_200_KB),
                RadioItem(1, getString(R.string.mms_file_size_limit_100kb), FILE_SIZE_100_KB),
            )

            val checkedItemId = items.find { it.value == config.mmsFileSizeLimit }?.id ?: 7
            RadioGroupDialog(this@SettingsActivity, items, checkedItemId) {
                config.mmsFileSizeLimit = it as Long
                settingsMmsFileSizeLimit.text = getMMSFileLimitText()
            }
        }
    }

    private fun setupUseRecycleBin() = binding.apply {
        updateRecycleBinButtons()
        settingsUseRecycleBin.isChecked = config.useRecycleBin
        settingsUseRecycleBin.text = formatWithDeprecatedBadge(
            labelRes = org.fossify.commons.R.string.move_items_into_recycle_bin
        )
        settingsUseRecycleBinHolder.setOnClickListener {
            settingsUseRecycleBin.toggle()
            config.useRecycleBin = settingsUseRecycleBin.isChecked
            updateRecycleBinButtons()
        }
    }

    private fun updateRecycleBinButtons() = binding.apply {
        settingsEmptyRecycleBinHolder.beVisibleIf(config.useRecycleBin)
    }

    private fun setupEmptyRecycleBin() = binding.apply {
        ensureBackgroundThread {
            recycleBinMessages = messagesDB.getArchivedCount()
            runOnUiThread {
                settingsEmptyRecycleBinSize.text =
                    resources.getQuantityString(
                        R.plurals.delete_messages,
                        recycleBinMessages,
                        recycleBinMessages
                    )
            }
        }

        settingsEmptyRecycleBinHolder.setOnClickListener {
            if (recycleBinMessages == 0) {
                toast(org.fossify.commons.R.string.recycle_bin_empty)
            } else {
                ConfirmationDialog(
                    activity = this@SettingsActivity,
                    message = "",
                    messageId = R.string.empty_recycle_bin_messages_confirmation,
                    positive = org.fossify.commons.R.string.yes,
                    negative = org.fossify.commons.R.string.no
                ) {
                    ensureBackgroundThread {
                        emptyMessagesRecycleBin()
                    }
                    recycleBinMessages = 0
                    settingsEmptyRecycleBinSize.text =
                        resources.getQuantityString(
                            R.plurals.delete_messages,
                            recycleBinMessages,
                            recycleBinMessages
                        )
                }
            }
        }
    }

    private fun setupAppPasswordProtection() = binding.apply {
        settingsAppPasswordProtection.isChecked = config.isAppPasswordProtectionOn
        settingsAppPasswordProtectionHolder.setOnClickListener {
            val tabToShow = if (config.isAppPasswordProtectionOn) {
                config.appProtectionType
            } else {
                SHOW_ALL_TABS
            }

            SecurityDialog(
                activity = this@SettingsActivity,
                requiredHash = config.appPasswordHash,
                showTabIndex = tabToShow
            ) { hash, type, success ->
                if (success) {
                    val hasPasswordProtection = config.isAppPasswordProtectionOn
                    settingsAppPasswordProtection.isChecked = !hasPasswordProtection
                    config.isAppPasswordProtectionOn = !hasPasswordProtection
                    config.appPasswordHash = if (hasPasswordProtection) "" else hash
                    config.appProtectionType = type

                    if (config.isAppPasswordProtectionOn) {
                        val confirmationTextId =
                            if (config.appProtectionType == PROTECTION_FINGERPRINT) {
                                org.fossify.commons.R.string.fingerprint_setup_successfully
                            } else {
                                org.fossify.commons.R.string.protection_setup_successfully
                            }

                        ConfirmationDialog(
                            activity = this@SettingsActivity,
                            message = "",
                            messageId = confirmationTextId,
                            positive = org.fossify.commons.R.string.ok,
                            negative = 0
                        ) { }
                    }
                }
            }
        }
    }

    private fun getMMSFileLimitText() = getString(
        when (config.mmsFileSizeLimit) {
            FILE_SIZE_100_KB -> R.string.mms_file_size_limit_100kb
            FILE_SIZE_200_KB -> R.string.mms_file_size_limit_200kb
            FILE_SIZE_300_KB -> R.string.mms_file_size_limit_300kb
            FILE_SIZE_600_KB -> R.string.mms_file_size_limit_600kb
            FILE_SIZE_1_MB -> R.string.mms_file_size_limit_1mb
            FILE_SIZE_2_MB -> R.string.mms_file_size_limit_2mb
            else -> R.string.mms_file_size_limit_none
        }
    )

    private fun setupWebhook() = binding.apply {
        setupWebhookEnabled()
        setupWebhookUrl()
        setupWebhookSim()
        setupWebhookFilter()
        setupWebhookApprovedSenders()
        setupWebhookKeywords()
        setupWebhookForwardIncoming()
        setupWebhookBearerToken()
        setupWebhookTest()
        updateWebhookSettingVisibility()
    }

    private fun updateWebhookSettingVisibility() = binding.apply {
        val filterMode = config.webhookFilterMode
        settingsWebhookTestHolder.beVisibleIf(config.webhookEnabled)
        settingsWebhookApprovedSendersHolder.beVisibleIf(WebhookMessageFilter.usesSenders(filterMode))
        settingsWebhookKeywordsHolder.beVisibleIf(WebhookMessageFilter.usesKeywords(filterMode))
    }

    private fun setupWebhookSim(): ActivitySettingsBinding = binding.apply {
        val activeSims = WebhookSims.active(this@SettingsActivity)
        val state = WebhookSims.state(this@SettingsActivity, activeSims?.map { it.subscriptionId })
        if (state == WebhookSimPolicy.State.MISSING) {
            WebhookSims.requireReselect(this@SettingsActivity)
        }

        val selectedLabel = WebhookSims.selectedLabel(this@SettingsActivity)
        settingsWebhookSim.text = when (state) {
            WebhookSimPolicy.State.READY -> selectedLabel
            WebhookSimPolicy.State.NOT_SELECTED -> getString(R.string.webhook_sim_not_selected)
            else -> "$selectedLabel: ${getString(R.string.webhook_sim_missing)}"
        }

        settingsWebhookSimHolder.setOnClickListener {
            val sims = WebhookSims.active(this@SettingsActivity).orEmpty()
            if (sims.isEmpty()) {
                toast(R.string.webhook_sim_none_active)
                return@setOnClickListener
            }

            // After a pause nothing is preselected, so the user has to choose the SIM again.
            val selectedId = if (state == WebhookSimPolicy.State.READY) {
                WebhookSims.selectedId(this@SettingsActivity)
            } else {
                WebhookSimPolicy.NO_SIM
            }
            val items = ArrayList(
                sims.map { RadioItem(it.subscriptionId, WebhookSims.label(this@SettingsActivity, it)) }
            )
            RadioGroupDialog(this@SettingsActivity, items, selectedId) { chosen ->
                sims.firstOrNull { it.subscriptionId == chosen as Int }?.let {
                    WebhookSims.select(this@SettingsActivity, it)
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
            RadioGroupDialog(this@SettingsActivity, items, config.webhookFilterMode) {
                config.webhookFilterMode = it as Int
                settingsWebhookFilter.text = items.first { item -> item.id == it }.title
                updateWebhookSettingVisibility()
            }
        }
    }

    private fun setupWebhookKeywords() = binding.apply {
        settingsWebhookKeywords.text = getWebhookListText(config.webhookFilterKeywords)
        settingsWebhookKeywordsHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_keywords,
                hintId = R.string.webhook_keywords_hint,
                prefill = getWebhookListText(config.webhookFilterKeywords),
                isUrl = false
            ) { keywords ->
                config.webhookFilterKeywords = keywords
                settingsWebhookKeywords.text = getWebhookListText(keywords)
            }
        }
    }

    private fun setupWebhookEnabled() = binding.apply {
        settingsWebhookEnabled.isChecked = config.webhookEnabled
        settingsWebhookEnabledHolder.setOnClickListener {
            val turningOn = !settingsWebhookEnabled.isChecked
            if (!turningOn) {
                settingsWebhookEnabled.isChecked = false
                config.webhookEnabled = false
                updateWebhookSettingVisibility()
                return@setOnClickListener
            }

            val url = config.webhookUrl.trim()
            if (url.isNotBlank() && Patterns.WEB_URL.matcher(url).matches()) {
                enableWebhook()
            } else {
                askForWebhookUrlAndEnable(url)
            }
        }
    }

    private fun enableWebhook() {
        binding.settingsWebhookEnabled.isChecked = true
        config.webhookEnabled = true
        updateWebhookSettingVisibility()
    }

    private fun askForWebhookUrlAndEnable(prefill: String) {
        WebhookInputDialog(
            activity = this,
            titleId = R.string.webhook_url,
            hintId = R.string.webhook_url,
            prefill = prefill,
            isUrl = true
        ) { newUrl ->
            val trimmed = newUrl.trim()
            config.webhookUrl = trimmed
            binding.settingsWebhookUrl.text = trimmed
            if (trimmed.isNotBlank() && Patterns.WEB_URL.matcher(trimmed).matches()) {
                enableWebhook()
            }
        }
    }

    private fun setupWebhookUrl() = binding.apply {
        settingsWebhookUrl.text = config.webhookUrl.trim()
        settingsWebhookUrlHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_url,
                hintId = R.string.webhook_url,
                prefill = config.webhookUrl.trim(),
                isUrl = true
            ) { newUrl ->
                val trimmed = newUrl.trim()
                config.webhookUrl = trimmed
                settingsWebhookUrl.text = trimmed
                if (config.webhookEnabled && trimmed.isBlank()) {
                    settingsWebhookEnabled.isChecked = false
                    config.webhookEnabled = false
                    updateWebhookSettingVisibility()
                }
            }
        }
    }

    private fun setupWebhookApprovedSenders() = binding.apply {
        settingsWebhookApprovedSenders.text = getWebhookListText(config.webhookApprovedSenders)
        settingsWebhookApprovedSendersHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_approved_senders,
                hintId = R.string.webhook_approved_senders_hint,
                prefill = getWebhookListText(config.webhookApprovedSenders),
                isUrl = false
            ) { senders ->
                config.webhookApprovedSenders = senders
                settingsWebhookApprovedSenders.text = getWebhookListText(senders)
            }
        }
    }

    private fun getWebhookListText(rawList: String): String {
        return rawList
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    private fun setupWebhookForwardIncoming() = binding.apply {
        settingsWebhookForwardIncoming.isChecked = config.webhookForwardIncoming
        settingsWebhookForwardIncomingHolder.setOnClickListener {
            settingsWebhookForwardIncoming.toggle()
            config.webhookForwardIncoming = settingsWebhookForwardIncoming.isChecked
        }
    }

    private fun setupWebhookBearerToken() = binding.apply {
        settingsWebhookBearerToken.text = getWebhookBearerTokenText()
        settingsWebhookBearerTokenHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_bearer_token,
                hintId = R.string.webhook_bearer_token_hint,
                prefill = "",
                isUrl = false
            ) { newToken ->
                config.webhookBearerToken = newToken
                settingsWebhookBearerToken.text = getWebhookBearerTokenText()
            }
        }
    }

    private fun getWebhookBearerTokenText(): String {
        return if (config.webhookBearerToken.isNotEmpty()) {
            "\u2022\u2022\u2022\u2022"
        } else {
            ""
        }
    }

    private fun setupWebhookTest() = binding.apply {
        settingsWebhookTestHolder.setOnClickListener {
            when {
                !config.webhookEnabled -> return@setOnClickListener
                config.webhookUrl.trim().isBlank() -> toast(R.string.webhook_url_required)
                else -> {
                    val preview = WebhookSender.previewTest(this@SettingsActivity)
                    WebhookTestPreviewDialog(
                        activity = this@SettingsActivity,
                        result = preview
                    ) { request ->
                        ensureBackgroundThread {
                            val result = WebhookSender.executeTest(this@SettingsActivity, request)
                            runOnUiThread {
                                WebhookTestResultDialog(this@SettingsActivity, result)
                            }
                        }
                    }
                }
            }
        }
    }
}
