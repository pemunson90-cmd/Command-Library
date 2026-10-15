package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Explicitly approved attempt only. Opening screens or history never invokes this capability. */
public fun interface ProviderGenerationAccess {
    public suspend fun run(profile: StoredProviderProfile, conversation: NewConversation, input: NewHistoryTurn,
        cancel: CancelToken, onProgress: suspend (Turn) -> Unit): Outcome<Turn>
}
