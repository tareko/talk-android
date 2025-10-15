/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.services.CallForegroundService

class CallNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_LEAVE_CALL -> {
                val handled = CallActivity.requestLeaveCall()
                if (!handled) {
                    CallForegroundService.stop(context.applicationContext)
                }
            }

            ACTION_SHOW_CALL -> {
                val extras = intent.getBundleExtra(EXTRA_CALL_EXTRAS)
                CallActivity.show(context.applicationContext, extras)
            }
        }
    }

    companion object {
        const val ACTION_LEAVE_CALL = "com.nextcloud.talk.action.LEAVE_CALL"
        const val ACTION_SHOW_CALL = "com.nextcloud.talk.action.SHOW_CALL"
        const val EXTRA_CALL_EXTRAS = "com.nextcloud.talk.extra.CALL_EXTRAS"
    }
}
