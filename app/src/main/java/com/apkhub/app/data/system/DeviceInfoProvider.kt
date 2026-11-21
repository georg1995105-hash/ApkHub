package com.apkhub.app.data.system

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.apkhub.app.domain.model.DeviceProfile

class DeviceInfoProvider(private val context: Context) {

    fun getDeviceProfile(): DeviceProfile {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        return DeviceProfile(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
            isAndroidTv = isTv
        )
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
