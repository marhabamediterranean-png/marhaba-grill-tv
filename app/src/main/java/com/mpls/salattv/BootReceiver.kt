package com.mpls.salattv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Relaunches the app automatically after the device powers on, so the wall
 * display comes back up on its own after a power cut or reboot — no remote needed.
 *
 * Works on most Android TV / Fire TV / signage boxes. A few locked-down OEM
 * builds restrict background activity starts; on those, also enable the device's
 * own "auto-start"/"open on boot" setting for this app if available.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val launch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(launch)
            } catch (_: Exception) {
            }
        }
    }
}
