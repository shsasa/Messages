package org.fossify.messages.helpers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Delivers one incoming SMS to the webhook. Only the message ID is stored in the work queue. */
class WebhookWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val messageId = inputData.getLong(KEY_MESSAGE_ID, 0L)
        if (messageId == 0L) {
            return Result.failure()
        }

        return when (WebhookSender.deliver(applicationContext, messageId)) {
            WebhookSender.Delivery.SENT -> Result.success()
            WebhookSender.Delivery.DROPPED -> Result.failure()
            WebhookSender.Delivery.RETRY -> {
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
            }
        }
    }

    companion object {
        const val KEY_MESSAGE_ID = "message_id"
        private const val MAX_ATTEMPTS = 6
    }
}
