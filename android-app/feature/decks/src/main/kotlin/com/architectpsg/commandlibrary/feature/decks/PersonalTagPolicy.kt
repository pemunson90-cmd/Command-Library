package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.text.Normalizer
import java.util.Locale

/** Search/uniqueness projection only; stored names and IDs remain exact. */
public object PersonalTagPolicy {
    public fun searchKey(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
}

internal const val PERSONAL_TAG_HEADER_BYTES: Int = 32

/** Complete preflight before IO, normalization, set insertion or payload allocation. */
internal fun validatePersonalTags(tags: SnapshotList<PersonalTag>, cancel: CancelToken) {
    require(tags.size <= PersonalTagLimits.MAX_TAGS)
    var bytes = PERSONAL_TAG_HEADER_BYTES.toLong()
    for (tag in tags) {
        cancel.throwIfCancelled()
        bytes += 9L + personalTagUtf8Length(tag.id.value, DeckPayloadLimits.MAX_ID_BYTES, cancel) +
            personalTagUtf8Length(tag.name, PersonalTagLimits.MAX_NAME_BYTES, cancel)
        require(bytes <= PersonalTagLimits.MAX_PAYLOAD_BYTES)
    }
    val identities = HashSet<EntityId>()
    val activeNames = HashSet<String>()
    for (tag in tags) {
        cancel.throwIfCancelled()
        require(tag.id.value.isNotBlank() && identities.add(tag.id))
        require(tag.name.isNotBlank() && tag.name.none { Character.isISOControl(it) })
        if (!tag.retired) require(activeNames.add(PersonalTagPolicy.searchKey(tag.name)))
    }
}

internal fun personalTagUtf8Length(value: String, cap: Int, cancel: CancelToken): Int {
    require(value.length <= cap)
    var bytes = 0
    var index = 0
    while (index < value.length) {
        if (index % 128 == 0) cancel.throwIfCancelled()
        val ch = value[index++]
        val width = when {
            ch < '\u0080' -> 1
            ch < '\u0800' -> 2
            ch in '\ud800'..'\udbff' -> {
                require(index < value.length && value[index] in '\udc00'..'\udfff')
                index++; 4
            }
            ch in '\udc00'..'\udfff' -> throw IllegalArgumentException()
            else -> 3
        }
        require(width <= cap - bytes)
        bytes += width
    }
    return bytes
}
