package de.xitee.torchbrightness.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.xitee.torchbrightness.App

/** Starts the service after boot and after an app update, if enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if ((context.applicationContext as App).prefs.settings.value.enabled) TorchBrightnessService.start(context)
    }
}
