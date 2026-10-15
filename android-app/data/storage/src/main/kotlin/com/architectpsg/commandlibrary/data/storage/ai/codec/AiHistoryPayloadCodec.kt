package com.architectpsg.commandlibrary.data.storage.ai.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException

private const val MAGIC = 0x434c4148 // CLAH
private const val FORMAT = 1
private class FutureHistoryFormat : IllegalArgumentException()

/** Bounded format1 history content, independent of SQL-controlled lifecycle metadata. */
public class AiHistoryPayloadCodec : AiHistoryPayloadCodecPort {
    public override fun encodeTurn(value: Turn): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        validate(value)
        val writer = AiWriter()
        writer.int(MAGIC); writer.int(FORMAT)
        writer.v(contentWire, AiHistoryContent(value.id, value.conversationId, value.source, value.prompt, value.provenance,
            value.mode, value.providerProfileId, value.modelId, value.retryOf, value.rawContent, value.usage))
        SnapshotBytes(writer.finish())
    }

    public override fun decodeTurn(payload: SnapshotBytes, metadata: HistoryTurnMetadata): Outcome<Turn> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= AiHistoryLimits.MAX_PAYLOAD_BYTES)
        val reader = AiReader(payload.copyBytes())
        require(reader.int() == MAGIC)
        val version = reader.int()
        if (version > FORMAT) throw FutureHistoryFormat()
        require(version == FORMAT)
        val content = reader.v(contentWire)
        require(reader.remaining == 0)
        require(content.id == metadata.id && content.conversationId == metadata.conversationId && content.source == metadata.source)
        Turn(content.id, content.conversationId, metadata.revision, metadata.ordinal, content.source, content.prompt,
            content.provenance, content.mode, content.providerProfileId, content.modelId, content.retryOf, metadata.ownerEpoch,
            metadata.status, content.rawContent, content.usage, metadata.startedAt, metadata.finishedAt).also(::validate)
    }

    private fun validate(value: Turn) {
        require(value.revision.value > 0 && value.ordinal > 0)
        // Owner is intentionally outside the frame but still obeys strict identifier bounds.
        value.ownerEpoch?.let { AiWriter().string(it.value) }
        require(value.prompt.templateId == value.mode.templateId && value.prompt.templateVersion > 0)
        val unfinished = value.status == TurnStatus.PENDING || value.status == TurnStatus.STREAMING
        if (unfinished) {
            require(value.finishedAt == Knowledge.Unknown(AiHistoryLimits.UNFINISHED_REASON, SnapshotList.empty()))
        } else require(value.finishedAt is Knowledge.Known)
        when (value.source) {
            HistoryTurnSource.OFFLINE_CAPTURE -> {
                require(value.providerProfileId == null && value.modelId == null && value.retryOf == null && value.ownerEpoch == null)
                require(value.status == TurnStatus.COMPLETE && value.usage is Knowledge.Unknown)
            }
            HistoryTurnSource.PROVIDER_GENERATION -> {
                require(value.providerProfileId != null && !value.modelId.isNullOrBlank() && value.ownerEpoch != null)
                if (value.status == TurnStatus.PENDING) {
                    require(value.rawContent.isEmpty() && value.usage is Knowledge.Unknown)
                }
            }
        }
    }

    private inline fun <T> guard(code: FailureCode, action: () -> T): Outcome<T> = try {
        Outcome.Success(action())
    } catch (_: FutureHistoryFormat) {
        Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "History payload requires a newer codec")
    } catch (_: IllegalArgumentException) {
        failure(code)
    } catch (_: IOException) {
        failure(code)
    }
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid history payload", SnapshotList.empty()),
    )
}
