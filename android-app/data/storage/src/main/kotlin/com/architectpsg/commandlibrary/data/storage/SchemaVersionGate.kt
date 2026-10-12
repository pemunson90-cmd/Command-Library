package com.architectpsg.commandlibrary.data.storage

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import java.io.File

/** What a database file says about its own schema version, read without modifying the file. */
internal sealed interface OnDiskSchema {
    /** No file, or a zero-length file. Room creates the supported schema from scratch. */
    data object Absent : OnDiskSchema

    /** The PRAGMA user_version stored in the file. Room writes its schema version there. */
    data class Versioned(val userVersion: Int) : OnDiskSchema

    /** The file could not be inspected. Do not let a writable Room open touch it. */
    data class Unreadable(val cause: Exception) : OnDiskSchema
}

/**
 * Newer-schema protection. Room3 3.0.3 reports a file whose user_version is newer than the code
 * as `IllegalStateException("A migration from N to M was required but not found ...")` from the
 * first connection (verified on the JVM runtime, which shares this source with Android: the file
 * and its rows are left untouched). The version is therefore read first, read-only, and a newer
 * file becomes a typed, visible and recoverable Outcome before Room touches it. Updating the app
 * recovers; nothing is cleared, and destructive migration is never enabled.
 */
internal object SchemaVersionGate {
    fun inspect(file: File, driver: BundledSQLiteDriver): OnDiskSchema {
        if (!file.exists() || file.length() == 0L) return OnDiskSchema.Absent
        var connection: SQLiteConnection? = null
        return try {
            connection = driver.open(file.absolutePath, SQLITE_OPEN_READONLY)
            val version = connection.prepare("PRAGMA user_version").use { statement ->
                check(statement.step()) { "PRAGMA user_version returned no row" }
                statement.getLong(0)
            }
            if (version < 0 || version > Int.MAX_VALUE) {
                OnDiskSchema.Unreadable(IllegalStateException("user_version $version is outside the supported range"))
            } else {
                OnDiskSchema.Versioned(version.toInt())
            }
        } catch (failure: Exception) {
            OnDiskSchema.Unreadable(failure)
        } finally {
            try {
                connection?.close()
            } catch (ignored: Exception) {
                // The inspection only read; a close failure cannot change the file.
            }
        }
    }

    fun admit(found: OnDiskSchema, supportedVersion: Int): Outcome<Unit> = when (found) {
        is OnDiskSchema.Absent -> Outcome.Success(Unit)
        is OnDiskSchema.Unreadable -> Outcome.Failure(DomainFailure(
            FailureCode.CORRUPT_DATA,
            "The local database could not be read. No data was changed or removed.",
            SnapshotList.empty(),
        ))
        is OnDiskSchema.Versioned ->
            if (found.userVersion <= supportedVersion) {
                Outcome.Success(Unit)
            } else {
                Outcome.Unavailable(UnavailableReason.UNSUPPORTED, newerSchemaDetail(found.userVersion, supportedVersion))
            }
    }

    fun newerSchemaDetail(found: Int, supported: Int): String =
        "The local catalog database uses schema version $found, but this app version supports up to " +
            "version $supported. Update the app to open it. No data was changed or removed."
}
