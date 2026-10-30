package com.architectpsg.commandlibrary.di

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.CardArtworkOffer
import java.net.URI
import java.security.MessageDigest

/** Reviewed full-card source profile only. Never constructs a source URL or uses legacy imageUri. */
internal object OrdinaryArtworkProfile {
    private val source = ProviderId("scryfall")

    fun offers(record: PrintingRecord, id: CardId, generation: GenerationId, cancel: CancelToken): SnapshotList<CardArtworkOffer> {
        cancel.throwIfCancelled()
        require(id.source == source && id.raw.length <= 1024 && generation.value.length <= 1024)
        require(record.id == id && record.language == LanguageTag.ENGLISH)
        require(record.provenance.provider == source && record.provenance.generation == generation)
        bounded(record.provenance.version)
        require(record.faceAssets.size <= 16 && record.images.size <= 32)
        require(record.faceAssets.map { it.identity.index }.distinct().size == record.faceAssets.size)
        val result = ArrayList<CardArtworkOffer>(17)
        location(record.images, id, generation, null, Knowledge.Unknown("Parent image artist is not supplied", SnapshotList.empty()), cancel)?.let(result::add)
        for (face in record.faceAssets) {
            cancel.throwIfCancelled()
            require(face.identity.printingId == id && face.language == LanguageTag.ENGLISH)
            bounded(face.identity.providerFaceId)
            bounded(face.artist)
            location(face.images, id, generation, face.identity.index, face.artist, cancel)?.let(result::add)
        }
        return SnapshotList.copyOf(result)
    }

    private fun bounded(value: Knowledge<String>) {
        when (value) {
            is Knowledge.Known -> require(value.value.length <= 1024)
            is Knowledge.Absent -> require(value.reason.length <= 1024)
            is Knowledge.Unknown -> {
                require(value.reason.length <= 1024 && value.evidence.size <= 16)
                value.evidence.forEach { require(it.description.length <= 1024 && (it.source?.value?.length ?: 0) <= 4096) }
            }
        }
    }

    private fun location(images: SnapshotList<PrintingImageAsset>, id: CardId, generation: GenerationId,
        face: Int?, artist: Knowledge<String>, cancel: CancelToken): CardArtworkOffer? {
        require(images.size <= 32)
        val seen = HashSet<String>()
        for (image in images) {
            cancel.throwIfCancelled()
            require(image.variant.length <= 1024 && image.uri.value.length <= 4096 && seen.add(image.variant))
        }
        // A present malformed preferred link is a source rejection, never a reason to use grid.
        val image = images.singleOrNull { it.variant == "normal" } ?: images.singleOrNull { it.variant == "grid" } ?: return null
        require(trusted(image.uri))
        val fields = listOf("ordinary-artwork-v1", id.source.value, id.raw, generation.value, face?.toString() ?: "parent", image.variant, image.uri.value)
        // Length framing prevents delimiter collisions; generation binds source version changes.
        val encoded = fields.joinToString("") { "${it.length}:$it" }.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it.toInt() and 255) }
        val intent = DownloadIntent(EntityId("artwork-$digest"), DownloadKind.IMAGES, source, image.uri,
            "ordinary-artwork-v1:$digest", LanguageTag.ENGLISH, null, null,
            Knowledge.Unknown("Source image size is not supplied", SnapshotList.empty()))
        return CardArtworkOffer(id, generation, face, image.variant, artist, intent)
    }

    fun trusted(uri: ResourceUri): Boolean {
        val text = uri.value
        if (text.length > 4096 || text.any { it <= ' ' || it == '\\' || it == '\u007f' }) return false
        return try {
            val parsed = URI(text)
            val path = parsed.rawPath ?: return false
            val lower = text.lowercase(java.util.Locale.ROOT)
            parsed.scheme == "https" && parsed.host == "cards.scryfall.io" &&
                parsed.rawAuthority in listOf("cards.scryfall.io", "cards.scryfall.io:443") &&
                parsed.rawUserInfo == null && parsed.rawFragment == null && !parsed.isOpaque &&
                path.startsWith("/") && path.length > 1 && !path.contains("//") &&
                path.split('/').none { it == "." || it == ".." } &&
                listOf("%2e", "%2f", "%5c", "%25", "%00", "%0a", "%0d", "%7f").none(lower::contains) &&
                Regex("%[01][0-9a-f]").find(lower) == null
        } catch (_: Exception) { false }
    }
}
