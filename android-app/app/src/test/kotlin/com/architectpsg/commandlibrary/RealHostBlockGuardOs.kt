package com.architectpsg.commandlibrary

import java.io.FileDescriptor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBlockGuardOs
import org.robolectric.util.ReflectionHelpers

/** Robolectric's default BlockGuardOs.close is a no-op; forward to the scoped real host close. */
@Implements(className = "libcore.io.BlockGuardOs", isInAndroidSdk = false)
class RealHostBlockGuardOs : ShadowBlockGuardOs() {
    @RealObject private lateinit var real: Any

    @Implementation
    public override fun close(descriptor: FileDescriptor) {
        val delegate = ReflectionHelpers.getField<Any>(real, "os")
        Shadow.extract<RealHostDirectoryLinux>(delegate).close(descriptor)
    }
}
