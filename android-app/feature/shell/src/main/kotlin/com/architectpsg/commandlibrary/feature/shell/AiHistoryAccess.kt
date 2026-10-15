package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.LocalAiHistory
import com.architectpsg.commandlibrary.core.model.Outcome

/** Real root initialization/recovery; a failed open is retryable without blocking unrelated screens. */
public fun interface AiHistoryAccess {
    public suspend fun open(cancel: CancelToken): Outcome<LocalAiHistory>
}
