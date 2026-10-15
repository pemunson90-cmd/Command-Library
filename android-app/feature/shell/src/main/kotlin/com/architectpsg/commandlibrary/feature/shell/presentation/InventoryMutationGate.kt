package com.architectpsg.commandlibrary.feature.shell.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retained in shell composition; ownership coordinates writers, never authorizes persistence. */
internal class InventoryMutationGate {
    private val monitor = Any()
    private var current: Grant? = null
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    /** Acquire before any initial-confirmation suspension; retries retain this exact grant. */
    fun tryAcquire(): Lease? = synchronized(monitor) {
        if (current != null) null else Grant().also {
            current = it
            mutableBusy.value = true
        }
    }

    internal interface Lease {
        val owns: Boolean
        fun release()
    }

    private inner class Grant : Lease {
        override val owns: Boolean get() = synchronized(monitor) { current === this }

        /** A late callback holding an old grant cannot release a newer operation's ownership. */
        override fun release() = synchronized(monitor) {
            if (current === this) {
                current = null
                mutableBusy.value = false
            }
        }
    }
}
