package com.architectpsg.commandlibrary.data.vault

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.AtomicFile
import com.architectpsg.commandlibrary.core.model.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Genuine JCE + real task-local files, deliberately not evidence of AndroidKeyStore behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidSecretVaultTest {
    @get:Rule val temporary = TemporaryFolder(File(System.getProperty("java.io.tmpdir"), "cl-android-vault-worker-01").apply { mkdirs() })
    private val scope = SecretScope(EntityId("synthetic-vault-host"), Origin("https", "example.test", 443), "key")
    private val cancel = Token()
    private class Token : CancelToken {
        override var isCancelled = false
        override fun throwIfCancelled() { if (isCancelled) throw CancellationException("Synthetic cancellation") }
    }
    private class Keys : VaultKeyBackend {
        val entries = mutableMapOf<String, SecretKey>()
        var creates = 0
        var finds = 0
        var error: Exception? = null
        var silentDelete = false
        var afterFind: (() -> Unit)? = null
        override fun find(alias: String): SecretKey? {
            finds++
            error?.let { throw it }
            val key = entries[alias]
            afterFind?.invoke()
            return key
        }
        override fun create(alias: String): SecretKey {
            creates++
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { entries[alias] = it }
        }
        override fun contains(alias: String) = entries.containsKey(alias)
        override fun delete(alias: String) { if (!silentDelete) entries.remove(alias) }
    }
    private fun vault(keys: Keys, directory: File, ops: VaultFileOps = VaultFileOps()) =
        AndroidSecretVault(keys, VaultRecordStore({ directory }, ops))
    private fun <T> success(outcome: Outcome<T>): T { assertTrue(outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun failed(outcome: Outcome<*>, code: FailureCode) { assertEquals(code, (outcome as Outcome.Failure).error.code) }
    private fun unavailable(outcome: Outcome<*>, reason: UnavailableReason) { assertEquals(reason, (outcome as Outcome.Unavailable).reason) }
    private fun file(directory: File, ref: SecretRef) = File(directory, ref.id.value + ".bin")

    @Test fun lazyConstructionAbsentReadAndScopeMismatchTouchNoKeyOrFiles() = runBlocking {
        val directory = File(temporary.root, "not-created")
        val keys = Keys()
        val vault = vault(keys, directory)
        val context: Context = RuntimeEnvironment.getApplication()
        AndroidSecretVault(context) // Public production constructor must remain lazy on a host.
        assertFalse(directory.exists())
        unavailable(vault.read(VaultEnvelope.ref(scope), cancel), UnavailableReason.NOT_FOUND)
        failed(vault.read(VaultEnvelope.ref(scope).copy(scope = scope.copy(purpose = "other")), cancel), FailureCode.SOURCE_MISMATCH)
        assertEquals(0, keys.finds)
        assertEquals(0, keys.creates)
        assertFalse(directory.exists())
    }

    @Test fun upsertReopenRemoveAndUnrelatedScopeConservation() = runBlocking {
        val directory = temporary.newFolder()
        val keys = Keys()
        val vault = vault(keys, directory)
        val ref = success(vault.put(scope, " synthetic-😀 ", cancel))
        val others = listOf(scope.copy(providerProfileId = EntityId("other")), scope.copy(purpose = "other"),
            scope.copy(origin = Origin("https", "other.test", 443)), scope.copy(origin = Origin("http", "example.test", 443)),
            scope.copy(origin = Origin("https", "example.test", 444))).map { success(vault.put(it, "unrelated", cancel)) }
        val conserved = others.associateWith { file(directory, it).readBytes() }
        assertEquals(" synthetic-😀 ", success(vault(keys, directory).read(ref, cancel)))
        val first = file(directory, ref).readBytes()
        assertFalse(String(first, Charsets.ISO_8859_1).contains("synthetic"))
        assertEquals(ref, success(vault.put(scope, "replacement", cancel)))
        assertFalse(first.contentEquals(file(directory, ref).readBytes()))
        assertEquals(6, keys.creates)
        assertEquals("replacement", success(vault.read(ref, cancel)))
        success(vault.remove(ref, cancel)); success(vault.remove(ref, cancel))
        unavailable(vault.read(ref, cancel), UnavailableReason.NOT_FOUND)
        for (other in others) {
            assertArrayEquals(conserved.getValue(other), file(directory, other).readBytes())
            assertEquals("unrelated", success(vault.read(other, cancel)))
        }
    }

    @Test fun explicitCancellationDuringKeyLookupPreventsReadSuccessAndConservesData() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "synthetic-midread", cancel))
        val unrelated = success(vault.put(scope.copy(purpose = "unrelated"), "retained", cancel))
        val before = listOf(ref, unrelated).associateWith { file(directory, it).readBytes() }
        val aliases = keys.entries.toMap()
        val token = Token()
        var lookupTriggered = false
        keys.afterFind = { lookupTriggered = true; token.isCancelled = true }
        try {
            vault.read(ref, token)
            fail("Mid-read cancellation must escape before a successful plaintext result")
        } catch (_: CancellationException) { /* Expected, despite the successful key lookup. */ }
        assertTrue(lookupTriggered)
        assertTrue(token.isCancelled)
        for ((fixture, bytes) in before) assertArrayEquals(bytes, file(directory, fixture).readBytes())
        assertEquals(aliases, keys.entries)
        assertEquals(2, keys.creates)
        keys.afterFind = null
        assertEquals("synthetic-midread", success(vault.read(ref, cancel)))
        assertEquals("retained", success(vault.read(unrelated, cancel)))
    }

    @Test fun missingOrInvalidatedKeyRequiresExplicitRemovalAndNeverRegenerates() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "synthetic", cancel))
        val original = file(directory, ref).readBytes()
        keys.entries.clear()
        unavailable(vault.read(ref, cancel), UnavailableReason.KEY_MISSING)
        unavailable(vault.put(scope, "replacement", cancel), UnavailableReason.KEY_MISSING)
        assertArrayEquals(original, file(directory, ref).readBytes()); assertEquals(1, keys.creates)
        success(vault.remove(ref, cancel)); success(vault.put(scope, "reentered", cancel))
        keys.error = KeyPermanentlyInvalidatedException()
        unavailable(vault.put(scope, "blocked", cancel), UnavailableReason.KEY_MISSING)
        unavailable(vault.read(ref, cancel), UnavailableReason.KEY_MISSING)
        assertEquals(2, keys.creates)
        keys.error = null
        success(vault.remove(ref, cancel)); success(vault.put(scope, "explicit-reentry", cancel))
        assertEquals(3, keys.creates)
    }

    @Test fun corruptionNativeTransientPermissionAndMalformedInputAreStaticTyped() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "synthetic", cancel))
        val original = file(directory, ref).readBytes()
        file(directory, ref).writeBytes(original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        failed(vault.read(ref, cancel), FailureCode.CORRUPT_DATA)
        file(directory, ref).writeBytes(ByteArray(VaultEnvelope.MAX_RECORD_BYTES + 1))
        failed(vault.read(ref, cancel), FailureCode.CORRUPT_DATA)
        file(directory, ref).writeBytes(original)
        keys.error = ProviderException("sensitive synthetic detail")
        val result = vault.read(ref, cancel)
        unavailable(result, UnavailableReason.SOURCE_UNAVAILABLE)
        assertFalse(result.toString().contains("sensitive"))
        keys.error = java.security.NoSuchProviderException("sensitive synthetic detail")
        unavailable(vault.read(ref, cancel), UnavailableReason.UNSUPPORTED)
        keys.error = SecurityException("sensitive synthetic detail")
        failed(vault.read(ref, cancel), FailureCode.PERMISSION)
        keys.error = null
        for (value in listOf("", "\uD800", "x".repeat(16385))) failed(vault.put(scope, value, cancel), FailureCode.INVALID_INPUT)
        assertArrayEquals(original, file(directory, ref).readBytes())
    }

    @Test fun checkedSyncAndPrecommitCancellationPreserveCommittedRecord() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "before", cancel)); val prior = file(directory, ref).readBytes()
        val failing = object : VaultFileOps() { override fun sync(stream: FileOutputStream) { throw IOException("synthetic") } }
        failed(vault(keys, directory, failing).put(scope, "after", cancel), FailureCode.PERSISTENCE)
        assertArrayEquals(prior, file(directory, ref).readBytes())
        val silentRollback = object : VaultFileOps() {
            override fun sync(stream: FileOutputStream) { throw IOException("synthetic") }
            override fun rollback(file: AtomicFile, stream: FileOutputStream?) { stream?.close() }
        }
        failed(vault(keys, directory, silentRollback).put(scope, "after", cancel), FailureCode.PERSISTENCE)
        assertArrayEquals(prior, file(directory, ref).readBytes())
        val denied = object : VaultFileOps() {
            override fun exists(file: File): Boolean { throw SecurityException("synthetic denial") }
        }
        failed(vault(keys, directory, denied).read(ref, cancel), FailureCode.PERMISSION)
        val token = Token()
        val cancelling = object : VaultFileOps() {
            override fun sync(stream: FileOutputStream) { super.sync(stream); token.isCancelled = true }
        }
        try { vault(keys, directory, cancelling).put(scope, "after", token); fail("Cancellation must escape") }
        catch (_: CancellationException) { /* Expected. */ }
        assertArrayEquals(prior, file(directory, ref).readBytes())
        token.isCancelled = true
        try { vault.remove(ref, token); fail("Cancellation must escape") } catch (_: CancellationException) { }
        assertEquals("before", success(vault.read(ref, cancel)))
    }

    @Test fun silentFinishRenameRollbackAndDeleteFailuresCannotReportSuccess() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "before", cancel))
        val unrelated = success(vault.put(scope.copy(purpose = "unrelated"), "retained", cancel))
        val conserved = file(directory, unrelated).readBytes()
        // Simulate a void finish whose rename never happens (.new remains on modern AtomicFile).
        val noFinish = object : VaultFileOps() { override fun finish(file: AtomicFile, stream: FileOutputStream) { stream.close() } }
        failed(vault(keys, directory, noFinish).put(scope, "after", cancel), FailureCode.PERSISTENCE)
        // Finish may have committed before reporting failure: return uncertainty, retain readable commit.
        val afterCommit = object : VaultFileOps() {
            override fun finish(file: AtomicFile, stream: FileOutputStream) { super.finish(file, stream); throw IOException("synthetic") }
        }
        failed(vault(keys, directory, afterCommit).put(scope, "committed", cancel), FailureCode.PERSISTENCE)
        assertEquals("committed", success(vault.read(ref, cancel)))
        val badRollback = object : VaultFileOps() {
            override fun sync(stream: FileOutputStream) { throw IOException("synthetic") }
            override fun rollback(file: AtomicFile, stream: FileOutputStream?) {
                stream?.close()
                file.baseFile.writeBytes(byteArrayOf(1))
                File(file.baseFile.path + ".new").delete()
                File(file.baseFile.path + ".bak").delete()
            }
        }
        failed(vault(keys, directory, badRollback).put(scope, "failed", cancel), FailureCode.PERSISTENCE)
        val noDelete = object : VaultFileOps() { override fun delete(file: AtomicFile) { } }
        failed(vault(keys, directory, noDelete).remove(ref, cancel), FailureCode.PERSISTENCE)
        assertTrue(file(directory, ref).exists())
        // Explicit cleanup handles recovery files and can reenter after uncertain failures.
        success(vault.remove(ref, cancel)); success(vault.put(scope, "reentered", cancel))
        keys.silentDelete = true
        failed(vault.remove(ref, cancel), FailureCode.PERSISTENCE)
        keys.silentDelete = false; success(vault.remove(ref, cancel))
        assertArrayEquals(conserved, file(directory, unrelated).readBytes())
        assertEquals("retained", success(vault.read(unrelated, cancel)))
    }

    @Test fun backupRecoveryAndExplicitRemovalIncludeAllRecoveryNames() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys(); val vault = vault(keys, directory)
        val ref = success(vault.put(scope, "before", cancel)); val base = file(directory, ref)
        val prior = base.readBytes()
        File(base.path + ".bak").writeBytes(prior)
        base.writeBytes(byteArrayOf(1))
        assertEquals("before", success(vault.read(ref, cancel)))
        assertFalse(File(base.path + ".bak").exists())
        File(base.path + ".new").writeBytes(byteArrayOf(1))
        success(vault.remove(ref, cancel))
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(base.path + suffix).exists())
        assertFalse(keys.contains(VaultEnvelope.alias(ref)))
    }

    @Test fun processMutexSerializesSeparateInstancesAndConcurrentReplacement() = runBlocking {
        val directory = temporary.newFolder(); val keys = Keys()
        val refs = (0 until 16).map { index -> async {
            success(vault(keys, directory).put(scope, "synthetic-$index", cancel))
        } }.awaitAll()
        assertEquals(1, refs.toSet().size); assertEquals(1, keys.creates)
        val value = success(vault(keys, directory).read(refs.first(), cancel))
        assertTrue(value in (0 until 16).map { "synthetic-$it" })
        assertEquals(1, directory.listFiles()!!.size)
    }
}
