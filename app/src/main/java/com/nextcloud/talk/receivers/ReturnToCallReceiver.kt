/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nextcloud.talk.activities.CallActivity

class ReturnToCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RETURN_TO_CALL) {
            return
        }
        val extras = intent.getBundleExtra(EXTRA_EXTRAS)
        CallActivity.show(context, extras)
    }

    companion object {
        private const val ACTION_RETURN_TO_CALL = "com.nextcloud.talk.action.RETURN_TO_CALL"
        private const val EXTRA_EXTRAS = "extra_call_extras"

        fun createPendingIntent(context: Context, extras: Bundle?): PendingIntent {
            val broadcastIntent = Intent(context, ReturnToCallReceiver::class.java).apply {
                action = ACTION_RETURN_TO_CALL
                extras?.let { putExtra(EXTRA_EXTRAS, Bundle(it)) }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(context, 0, broadcastIntent, flags)
        }
    }
}
