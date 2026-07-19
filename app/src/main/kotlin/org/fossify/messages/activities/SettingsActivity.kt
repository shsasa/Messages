package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.activities.ManageBlockedNumbersActivity
import org.fossify.commons.dialogs.ChangeDateTimeFormatDialog
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.FeatureLockedDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.dialogs.SecurityDialog
import org.fossify.commons.extensions.addLockedLabelIfNeeded
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatWithDeprecatedBadge
import org.fossify.commons.extensions.getBlockedNumbers
import org.fossify.commons.extensions.getFontSizeText
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.isOrWasThankYouInstalled
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
import org.fossify.messages.helpers.WEBHOOK_METHOD_GET
import org.fossify.messages.helpers.WEBHOOK_METHOD_POST
import org.fossify.messages.helpers.WEBHOOK_METHOD_PUT
import org.fossify.messages.helpers.WebhookSender
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
            addLockedLabelIfNeeded(org.fossify.commons.R.string.manage_blocked_numbers)
        settingsManageBlockedNumbersHolder.beVisible()
        settingsManageBlockedNumbersHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this@SettingsActivity, ManageBlockedNumbersActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this@SettingsActivity) { }
            }
        }
    }

    private fun setupManageBlockedKeywords() = binding.apply {
        settingsManageBlockedKeywords.text =
            addLockedLabelIfNeeded(R.string.manage_blocked_keywords)

        settingsManageBlockedKeywordsHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                Intent(this@SettingsActivity, ManageBlockedKeywordsActivity::class.java).apply {
                    startActivity(this)
                }
            } else {
                FeatureLockedDialog(this@SettingsActivity) { }
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
        setupWebhookMethod()
        setupWebhookForwardIncoming()
        setupWebhookForwardOutgoing()
        setupWebhookBearerToken()
        setupWebhookTest()
        updateWebhookSettingVisibility()
    }

    private fun updateWebhookSettingVisibility() = binding.apply {
        val enabled = config.webhookEnabled
        settingsWebhookUrlHolder.beVisibleIf(enabled)
        settingsWebhookMethodHolder.beVisibleIf(enabled)
        settingsWebhookForwardIncomingHolder.beVisibleIf(enabled)
        settingsWebhookForwardOutgoingHolder.beVisibleIf(enabled)
        settingsWebhookBearerTokenHolder.beVisibleIf(enabled)
        settingsWebhookTestHolder.beVisibleIf(enabled)
    }

    private fun setupWebhookEnabled() = binding.apply {
        settingsWebhookEnabled.isChecked = config.webhookEnabled
        settingsWebhookEnabledHolder.setOnClickListener {
            val turningOn = !settingsWebhookEnabled.isChecked
            if (turningOn) {
                when {
                    config.webhookUrl.isBlank() -> {
                        toast(R.string.webhook_url_required)
                        return@setOnClickListener
                    }

                    !Patterns.WEB_URL.matcher(config.webhookUrl).matches() -> {
                        toast(R.string.webhook_url_invalid)
                        return@setOnClickListener
                    }
                }
            }

            settingsWebhookEnabled.toggle()
            config.webhookEnabled = settingsWebhookEnabled.isChecked
            updateWebhookSettingVisibility()
        }
    }

    private fun setupWebhookUrl() = binding.apply {
        settingsWebhookUrl.text = config.webhookUrl
        settingsWebhookUrlHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_url,
                hintId = R.string.webhook_url,
                prefill = config.webhookUrl,
                isUrl = true
            ) { newUrl ->
                config.webhookUrl = newUrl
                settingsWebhookUrl.text = newUrl
                if (config.webhookEnabled && newUrl.isBlank()) {
                    settingsWebhookEnabled.isChecked = false
                    config.webhookEnabled = false
                    updateWebhookSettingVisibility()
                }
            }
        }
    }

    private fun setupWebhookMethod() = binding.apply {
        settingsWebhookMethod.text = getWebhookMethodText(config.webhookHttpMethod)
        settingsWebhookMethodHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(WEBHOOK_METHOD_POST, getString(R.string.webhook_method_post)),
                RadioItem(WEBHOOK_METHOD_GET, getString(R.string.webhook_method_get)),
                RadioItem(WEBHOOK_METHOD_PUT, getString(R.string.webhook_method_put))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.webhookHttpMethod) {
                config.webhookHttpMethod = it as Int
                settingsWebhookMethod.text = getWebhookMethodText(config.webhookHttpMethod)
            }
        }
    }

    private fun getWebhookMethodText(method: Int) = getString(
        when (method) {
            WEBHOOK_METHOD_GET -> R.string.webhook_method_get
            WEBHOOK_METHOD_PUT -> R.string.webhook_method_put
            else -> R.string.webhook_method_post
        }
    )

    private fun setupWebhookForwardIncoming() = binding.apply {
        settingsWebhookForwardIncoming.isChecked = config.webhookForwardIncoming
        settingsWebhookForwardIncomingHolder.setOnClickListener {
            settingsWebhookForwardIncoming.toggle()
            config.webhookForwardIncoming = settingsWebhookForwardIncoming.isChecked
        }
    }

    private fun setupWebhookForwardOutgoing() = binding.apply {
        settingsWebhookForwardOutgoing.isChecked = config.webhookForwardOutgoing
        settingsWebhookForwardOutgoingHolder.setOnClickListener {
            settingsWebhookForwardOutgoing.toggle()
            config.webhookForwardOutgoing = settingsWebhookForwardOutgoing.isChecked
        }
    }

    private fun setupWebhookBearerToken() = binding.apply {
        settingsWebhookBearerToken.text = getWebhookBearerTokenText()
        settingsWebhookBearerTokenHolder.setOnClickListener {
            WebhookInputDialog(
                activity = this@SettingsActivity,
                titleId = R.string.webhook_bearer_token,
                hintId = R.string.webhook_bearer_token,
                prefill = config.webhookBearerToken,
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
                config.webhookUrl.isBlank() -> toast(R.string.webhook_url_required)
                else -> {
                    ensureBackgroundThread {
                        val success = WebhookSender.sendTest(this@SettingsActivity)
                        runOnUiThread {
                            toast(if (success) R.string.webhook_test_sent else R.string.webhook_test_failed)
                        }
                    }
                }
            }
        }
    }
}
