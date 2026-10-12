package com.architectpsg.commandlibrary.data.storage

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SchemaVersionGateTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun missingFileIsAbsentAndAdmitted() {
        val found = SchemaVersionGate.inspect(File(folder.root, "none.db"), BundledSQLiteDriver())
        assertEquals(OnDiskSchema.Absent, found)
        assertEquals(Outcome.Success(Unit), SchemaVersionGate.admit(found, 1))
    }

    @Test
    fun zeroLengthFileIsAbsentAndAdmitted() {
        val empty = folder.newFile("empty.db")
        val found = SchemaVersionGate.inspect(empty, BundledSQLiteDriver())
        assertEquals(OnDiskSchema.Absent, found)
        assertEquals(Outcome.Success(Unit), SchemaVersionGate.admit(found, 1))
    }

    @Test
    fun sameOrOlderVersionIsAdmitted() {
        assertEquals(Outcome.Success(Unit), SchemaVersionGate.admit(OnDiskSchema.Versioned(0), 1))
        assertEquals(Outcome.Success(Unit), SchemaVersionGate.admit(OnDiskSchema.Versioned(1), 1))
        assertEquals(Outcome.Success(Unit), SchemaVersionGate.admit(OnDiskSchema.Versioned(3), 3))
    }

    @Test
    fun newerVersionIsUnsupportedAndExplainsNoDataWasChanged() {
        val outcome = SchemaVersionGate.admit(OnDiskSchema.Versioned(2), 1)
        assertTrue(outcome is Outcome.Unavailable)
        outcome as Outcome.Unavailable
        assertEquals(UnavailableReason.UNSUPPORTED, outcome.reason)
        assertTrue(outcome.detail.contains("version 2"))
        assertTrue(outcome.detail.contains("version 1"))
        assertTrue(outcome.detail.contains("No data was changed"))
    }

    @Test
    fun unreadableFileIsRejectedBeforeWritableOpen() {
        val unreadable = OnDiskSchema.Unreadable(IllegalStateException("not a database"))
        assertTrue(SchemaVersionGate.admit(unreadable, 1) is Outcome.Failure)
    }
}
