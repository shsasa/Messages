package org.fossify.messages.helpers

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import org.fossify.messages.R
import org.fossify.messages.activities.WebhookSettingsActivity
import org.fossify.messages.extensions.subscriptionManagerCompat

/**
 * Stores which SIM the webhook forwards from and reads the active SIMs.
 *
 * The selection lives in the preferences file that is excluded from backups, because a
 * subscription ID restored onto another phone could point at a different SIM.
 */
object WebhookSims {
    private const val KEY_SUBSCRIPTION_ID = "sim_subscription_id"
    private const val KEY_LABEL = "sim_label"
    private const val KEY_RESELECT_REQUIRED = "sim_reselect_required"
    private const val CHANNEL_ID = "webhook_status"
    private const val NOTIFICATION_ID = 0x5EB0

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(WebhookTokenStore.PREFS_NAME, Context.MODE_PRIVATE)

    fun selectedId(context: Context) = prefs(context).getInt(KEY_SUBSCRIPTION_ID, WebhookSimPolicy.NO_SIM)

    /** The label of the selected SIM as it was when it was selected. */
    fun selectedLabel(context: Context) = prefs(context).getString(KEY_LABEL, "").orEmpty()

    /** The active SIMs, or null if they cannot be read (for example without the phone permission). */
    @SuppressLint("MissingPermission")
    fun active(context: Context): List<WebhookSim>? {
        return try {
            context.subscriptionManagerCompat().activeSubscriptionInfoList.orEmpty().map {
                val carrier = it.displayName?.toString().orEmpty().ifBlank { it.carrierName?.toString().orEmpty() }
                WebhookSim(it.subscriptionId, it.simSlotIndex, carrier.trim())
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * False while a SIM slot is still starting up, for example right after a reboot. A missing
     * SIM only counts as removed once the slots have settled.
     */
    fun isSettled(context: Context): Boolean {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val slots = context.subscriptionManagerCompat().activeSubscriptionInfoCountMax
        val starting = setOf(TelephonyManager.SIM_STATE_UNKNOWN, TelephonyManager.SIM_STATE_NOT_READY)
        return (0 until slots).none { telephony.getSimState(it) in starting }
    }

    /** For example "SIM 1 · Carrier", or just "SIM 1" when the carrier is unknown. */
    fun label(context: Context, sim: WebhookSim): String {
        val slot = context.getString(R.string.webhook_sim_slot, sim.slotIndex + 1)
        return if (sim.carrier.isEmpty()) slot else "$slot · ${sim.carrier}"
    }

    fun state(context: Context, activeIds: Collection<Int>?): WebhookSimPolicy.State {
        val prefs = prefs(context)
        return WebhookSimPolicy.state(
            selectedId = selectedId(context),
            reselectRequired = prefs.getBoolean(KEY_RESELECT_REQUIRED, false),
            activeIds = activeIds
        )
    }

    fun select(context: Context, sim: WebhookSim) {
        prefs(context).edit()
            .putInt(KEY_SUBSCRIPTION_ID, sim.subscriptionId)
            .putString(KEY_LABEL, label(context, sim))
            .putBoolean(KEY_RESELECT_REQUIRED, false)
            .apply()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /**
     * Pauses forwarding until the user selects a SIM again, and tells them once. Called when
     * the selected SIM is found to be missing, so a SIM that is removed or replaced can never
     * resume forwarding on its own.
     */
    fun requireReselect(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(KEY_RESELECT_REQUIRED, false)) {
            return
        }

        prefs.edit().putBoolean(KEY_RESELECT_REQUIRED, true).apply()
        showPausedNotification(context)
    }

    private fun showPausedNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = context.getString(R.string.webhook_paused)
        val channelName = context.getString(R.string.webhook_forwarding)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH)
        )

        val intent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, WebhookSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = context.getString(R.string.webhook_sim_missing)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
