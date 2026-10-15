package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

/** Complete HTTPS/manual-model text capability; no implied model discovery/tools/provider eligibility. */
public interface ProviderTextClient {
    public fun stream(profile: ProviderProfile, request: GenerationRequest, cancel: CancelToken): Flow<Outcome<TokenChunk>>
}
public object ProviderTextLimits {
    public const val MAX_REQUEST_BYTES: Int = 8 * 1024 * 1024
    public const val MAX_DECIMAL_PRECISION: Int = 32
    public const val MAX_ABSOLUTE_DECIMAL_SCALE: Int = 32
    public const val MAX_MESSAGES: Int = 64
    public const val MAX_MESSAGE_BYTES: Int = 4 * 1024 * 1024
    public const val MAX_WIRE_RESPONSE_BYTES: Long = 8L * 1024 * 1024
    public const val MAX_EVENT_BYTES: Int = 2 * 1024 * 1024
    public const val MAX_REPLY_BYTES: Int = AiHistoryLimits.MAX_REPLY_BYTES
    public const val MAX_JSON_DEPTH: Int = 32
    public const val MAX_JSON_NODES: Int = 10_000
    public const val MAX_ID_BYTES: Int = 4096
    public const val MAX_STOP_SEQUENCES: Int = 32
}
