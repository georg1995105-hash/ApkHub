package com.apkhub.app.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class PackageChangeReceiver(
    private val onPackageChanged: (packageName: String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REPLACED ||
            action == Intent.ACTION_PACKAGE_REMOVED
        ) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            onPackageChanged(pkg)
        }
    }

    companion object {
        fun register(context: Context, onPackageChanged: (String) -> Unit): PackageChangeReceiver {
            val receiver = PackageChangeReceiver(onPackageChanged)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            return receiver
        }
    }
}
