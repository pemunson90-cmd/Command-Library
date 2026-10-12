package com.architectpsg.commandlibrary.feature.shell.document

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.feature.shell.syntheticBytes
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class ContentResolverDocumentSourceTest {
    /** Test-only provider serving one synthetic document plus a document without metadata. */
    class SyntheticDocumentsProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
            val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            when (uri.lastPathSegment) {
                "named" -> cursor.addRow(arrayOf<Any?>("synthetic catalog.json", BYTES.size.toLong()))
                "unnamed" -> cursor.addRow(arrayOf<Any?>(null, null))
            }
            return cursor
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            if (uri.lastPathSegment == "missing") throw FileNotFoundException("synthetic missing document")
            val file = File(context!!.cacheDir, "synthetic-document.json")
            file.writeBytes(BYTES)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String = "application/json"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
        override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
        override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    }

    private companion object {
        const val AUTHORITY = "synthetic.documents"
        val BYTES: ByteArray = syntheticBytes(70_000)
    }

    private fun source(): ContentResolverDocumentSource {
        Robolectric.setupContentProvider(SyntheticDocumentsProvider::class.java, AUTHORITY)
        return ContentResolverDocumentSource(ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver)
    }

    @Test
    fun describesProviderMetadata() {
        val source = source()
        assertEquals(DocumentDescription("synthetic catalog.json", BYTES.size.toLong()), source.describe(DocumentRef("content://$AUTHORITY/document/named")))
        assertEquals(DocumentDescription(null, null), source.describe(DocumentRef("content://$AUTHORITY/document/unnamed")))
        assertEquals(DocumentDescription(null, null), source.describe(DocumentRef("content://$AUTHORITY/document/norow")))
    }

    @Test
    fun opensTheDocumentStream() {
        val bytes = source().open(DocumentRef("content://$AUTHORITY/document/named")).use { it.readBytes() }
        assertArrayEquals(BYTES, bytes)
    }

    @Test(expected = FileNotFoundException::class)
    fun missingDocumentFailsToOpen() {
        source().open(DocumentRef("content://$AUTHORITY/document/missing")).close()
    }

    @Test
    fun unknownAuthorityHasNoMetadata() {
        val description = source().describe(DocumentRef("content://synthetic.unregistered/document/x"))
        assertNull(description.displayName)
        assertNull(description.reportedBytes)
    }
}
