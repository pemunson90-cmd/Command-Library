package com.architectpsg.commandlibrary.data.network

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.RequestSpec
import com.architectpsg.commandlibrary.core.model.ResourceUri

/** Bound acquisition factories serialize an authoritative policy check and this enqueue action
 * against policy writers. Invoke enqueue exactly once before returning; never retain it or hold
 * a policy lock while waiting for a response. The URI is the actual redirect/retry destination.
 * Ordinary transport has no intrinsic download-consent authority.
 */
public fun interface WireDispatchGuard {
    public suspend fun dispatch(request: RequestSpec, uri: ResourceUri, cancel: CancelToken, enqueue: () -> Unit): Unit
}
