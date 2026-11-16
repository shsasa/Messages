package org.fossify.messages.helpers

/**
 * Decides whether an SMS sender is on the explicit list of approved senders.
 * An empty list approves nobody, so nothing is forwarded until senders are added.
 */
object WebhookSenderFilter {
    private const val PHONE_PUNCTUATION = "+-(). "

    /** Splits a user-entered list (commas, semicolons or new lines) into normalized senders. */
    fun parse(rawList: String): Set<String> {
        return rawList.split(',', ';', '\n')
            .map(::normalize)
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Phone numbers are reduced to their digits, so "+973 1234-5678" equals "97312345678".
     * Alphanumeric sender IDs such as "GitHub" are compared case-insensitively.
     */
    fun normalize(sender: String): String {
        val trimmed = sender.trim()
        val isPhoneNumber = trimmed.any(Char::isDigit) &&
            trimmed.all { it.isDigit() || it in PHONE_PUNCTUATION }
        return if (isPhoneNumber) trimmed.filter(Char::isDigit) else trimmed.lowercase()
    }

    fun isApproved(address: String, rawList: String): Boolean {
        val sender = normalize(address)
        return sender.isNotEmpty() && sender in parse(rawList)
    }
}
