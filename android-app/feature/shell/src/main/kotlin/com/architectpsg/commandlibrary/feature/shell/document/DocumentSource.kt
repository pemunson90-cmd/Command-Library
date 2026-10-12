package com.architectpsg.commandlibrary.feature.shell.document

import android.content.ContentResolver
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.io.InputStream

/** A user-selected document, identified by the content URI the system picker returned. */
@JvmInline
internal value class DocumentRef(val uri: String) {
    init {
        require(uri.isNotBlank())
    }
}

/** Provider-reported metadata. Neither value is verified; the size is used only for display and an early limit check. */
internal data class DocumentDescription(val displayName: String?, val reportedBytes: Long?)

/** Blocking document access. Callers invoke both functions on an IO dispatcher and close the stream. */
internal interface DocumentSource {
    fun describe(ref: DocumentRef): DocumentDescription
    fun open(ref: DocumentRef): InputStream
}

/** Storage Access Framework documents read through the application's ContentResolver. */
internal class ContentResolverDocumentSource(private val resolver: ContentResolver) : DocumentSource {
    override fun describe(ref: DocumentRef): DocumentDescription {
        val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val cursor = resolver.query(ref.uri.toUri(), columns, null, null, null)
            ?: return DocumentDescription(displayName = null, reportedBytes = null)
        cursor.use {
            if (!it.moveToFirst()) return DocumentDescription(displayName = null, reportedBytes = null)
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0 && !it.isNull(nameIndex)) it.getString(nameIndex)?.takeIf(String::isNotBlank) else null
            val size = if (sizeIndex >= 0 && !it.isNull(sizeIndex)) it.getLong(sizeIndex).takeIf { bytes -> bytes >= 0 } else null
            return DocumentDescription(displayName = name, reportedBytes = size)
        }
    }

    override fun open(ref: DocumentRef): InputStream =
        resolver.openInputStream(ref.uri.toUri()) ?: throw FileNotFoundException("The document provider returned no stream")
}
