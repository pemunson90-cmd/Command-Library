package com.architectpsg.commandlibrary.feature.shell

import android.app.Activity
import android.content.ContextWrapper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class DraftBackgroundObserverTest {
    @Test fun configurationStopKeepsInputWhileGenuineBackgroundFlushesThroughWrappedActivity() {
        val host=Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity=host.get()
            val owner=object: LifecycleOwner {
                override val lifecycle=LifecycleRegistry(this)
            }
            var flushes=0
            val observer=draftBackgroundObserver(ContextWrapper(activity)) { flushes++ }
            observer.onStateChanged(owner,Lifecycle.Event.ON_PAUSE)
            assertEquals(0,flushes)
            ReflectionHelpers.setField(activity,"mChangingConfigurations",true)
            observer.onStateChanged(owner,Lifecycle.Event.ON_STOP)
            assertEquals(0,flushes)
            ReflectionHelpers.setField(activity,"mChangingConfigurations",false)
            observer.onStateChanged(owner,Lifecycle.Event.ON_STOP)
            assertEquals(1,flushes)
            observer.onStateChanged(owner,Lifecycle.Event.ON_DESTROY)
            assertEquals(1,flushes)
        } finally { host.pause().stop().destroy() }
    }
}
