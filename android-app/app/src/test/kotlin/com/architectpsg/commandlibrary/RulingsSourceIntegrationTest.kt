package com.architectpsg.commandlibrary

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowProcess
import java.nio.file.Files

/** Only host directory OS/connection setup is bridged; all data and pins execute production code. */
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class RulingsSourceIntegrationTest : RulingsSourceIntegrationCases() {
    override fun prepareEnvironment(context: Context) {
        val uid = (Files.getAttribute(context.cacheDir.toPath(), "unix:uid") as Number).toInt()
        ShadowProcess.setUid(uid)
        val info = shadowOf(context.packageManager).getInternalMutablePackageInfo(context.packageName)
        requireNotNull(info.applicationInfo).uid = uid
        info.providers?.forEach { it.applicationInfo.uid = uid }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = NetworkCapabilities()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(capabilities).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
    }
}
