package org.fossify.messages.helpers

/**
 * The optional content filter applied after the SIM check.
 *
 * With [MODE_NONE] every incoming SMS on the selected SIM is forwarded. With both filters on,
 * a message must match the sender list and contain a keyword. An active filter with an empty
 * list matches nothing.
 */
object WebhookMessageFilter {
    const val MODE_NONE = 0
    const val MODE_SENDERS = 1
    const val MODE_KEYWORDS = 2
    const val MODE_SENDERS_AND_KEYWORDS = 3

    /**
     * New configurations start unfiltered. A configuration made by an older version keeps
     * its approved-sender rules, so an upgrade never widens what is forwarded.
     */
    fun defaultMode(hasExistingConfiguration: Boolean): Int {
        return if (hasExistingConfiguration) MODE_SENDERS else MODE_NONE
    }

    fun usesSenders(mode: Int) = mode == MODE_SENDERS || mode == MODE_SENDERS_AND_KEYWORDS

    fun usesKeywords(mode: Int) = mode == MODE_KEYWORDS || mode == MODE_SENDERS_AND_KEYWORDS

    /** False when the mode needs a list that is still empty, so nothing could ever match. */
    fun isUsable(mode: Int, senders: String, keywords: String): Boolean {
        val sendersReady = !usesSenders(mode) || WebhookSenderFilter.parse(senders).isNotEmpty()
        val keywordsReady = !usesKeywords(mode) || parseKeywords(keywords).isNotEmpty()
        return sendersReady && keywordsReady
    }

    /** Splits a user-entered list (commas, semicolons or new lines) into lowercase keywords. */
    fun parseKeywords(rawList: String): Set<String> {
        return rawList.split(',', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** A user-entered list as shown in settings: trimmed entries separated by ", ". */
    fun displayList(rawList: String): String {
        return rawList.split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
    }

    /** True if the message text contains at least one keyword, ignoring case. */
    fun containsKeyword(body: String, rawList: String): Boolean {
        val text = body.lowercase()
        return parseKeywords(rawList).any { it in text }
    }

    fun matches(mode: Int, address: String, body: String, senders: String, keywords: String): Boolean {
        return when (mode) {
            MODE_NONE -> true
            MODE_SENDERS -> WebhookSenderFilter.isApproved(address, senders)
            MODE_KEYWORDS -> containsKeyword(body, keywords)
            MODE_SENDERS_AND_KEYWORDS ->
                WebhookSenderFilter.isApproved(address, senders) && containsKeyword(body, keywords)

            else -> false
        }
    }
}
