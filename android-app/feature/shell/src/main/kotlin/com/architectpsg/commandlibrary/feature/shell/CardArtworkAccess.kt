package com.architectpsg.commandlibrary.feature.shell

import android.graphics.Bitmap
import com.architectpsg.commandlibrary.core.model.*

/** Exact source-supplied full-card image. Preparing an offer performs local reads only. */
public data class CardArtworkOffer(
    public val printingId: CardId,
    public val generation: GenerationId,
    public val faceIndex: Int?,
    public val variant: String,
    public val artist: Knowledge<String>,
    public val intent: DownloadIntent,
)

/** Platform pixels decoded from bounded local bytes, never a URL or an image-loader request.
 * Display with Fit and no overlays/filters. The consumer must not recycle a displayed bitmap.
 * No durable image cache or automatic replay is implied by this transient result.
 */
public class CardArtworkImage(public val offer: CardArtworkOffer, public val bitmap: Bitmap)

/** Real application-owned binary-image access, separate from text/catalog acquisition framing. */
public interface CardArtworkAccess {
    public suspend fun prepare(printingId: CardId, generation: GenerationId, cancel: CancelToken): Outcome<SnapshotList<CardArtworkOffer>>
    /** Requires existing explicit approval of the exact offered intent; never approves implicitly. */
    public suspend fun load(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<CardArtworkImage>
    /** Local authoritative consent and generation check; used to revoke visible transient pixels. */
    public suspend fun permitted(offer: CardArtworkOffer, consentId: EntityId, cancel: CancelToken): Outcome<Boolean>

    public companion object {
        public const val MAX_COMPRESSED_BYTES: Long = 3L * 1024 * 1024
        public const val MAX_EDGE: Int = 2048
        public const val MAX_PIXELS: Long = 2_000_000
    }
}
