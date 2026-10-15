package com.architectpsg.commandlibrary.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Must execute on real Android runtime with AndroidKeyStore; host/JCE execution is not this gate. */
@RunWith(AndroidJUnit4::class)
class AndroidSecretVaultInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val vault = AndroidSecretVault(context)
    private val fixtureId = "synthetic-native-vault-" + UUID.randomUUID().toString()
    private val refs = mutableSetOf<SecretRef>()
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun scope(purpose: String = "key") = SecretScope(EntityId(fixtureId), Origin("https", "example.test", 443), purpose)
    private fun ref(scope: SecretScope): SecretRef = VaultEnvelope.ref(scope).also { refs += it }
    private fun <T> success(outcome: Outcome<T>): T { assertTrue(outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    private fun nativeStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun record(ref: SecretRef) = File(File(context.noBackupFilesDir, "command-library-vault-v1"), ref.id.value + ".bin")

    @After fun removeOnlySyntheticFixtures() = runBlocking {
        for (ref in refs) success(vault.remove(ref, cancel))
    }

    @Test fun nativeNonexportableKeyRoundTripReopenReplaceAndNoBackupPlacement() = runBlocking {
        val scope = scope(); val ref = ref(scope)
        assertEquals(ref, success(vault.put(scope, " synthetic-native-😀 ", cancel)))
        val file = record(ref)
        assertTrue(file.isFile)
        assertEquals(context.noBackupFilesDir.canonicalFile, file.parentFile!!.parentFile!!.canonicalFile)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("synthetic-native"))
        val alias = VaultEnvelope.alias(ref)
        val key = nativeStore().getKey(alias, null)
        assertNotNull(key); assertNull(key.encoded)
        assertEquals(" synthetic-native-😀 ", success(AndroidSecretVault(context).read(ref, cancel)))
        val before = file.readBytes()
        assertEquals(ref, success(vault.put(scope, "synthetic-native-updated", cancel)))
        assertFalse(before.contentEquals(file.readBytes()))
        assertEquals("synthetic-native-updated", success(vault.read(ref, cancel)))
        success(vault.remove(ref, cancel)); success(vault.remove(ref, cancel))
        assertFalse(nativeStore().containsAlias(alias))
        for (suffix in listOf("", ".bak", ".new")) assertFalse(File(file.path + suffix).exists())
        assertEquals(UnavailableReason.NOT_FOUND, (vault.read(ref, cancel) as Outcome.Unavailable).reason)
    }

    @Test fun exactScopeMismatchAndIndependentScopesConserveUnrelatedNativeRecords() = runBlocking {
        val scopes = listOf(scope(), scope("other"), scope().copy(providerProfileId = EntityId(fixtureId + "-other")),
            scope().copy(origin = Origin("http", "example.test", 443)),
            scope().copy(origin = Origin("https", "EXAMPLE.test", 443)),
            scope().copy(origin = Origin("https", "example.test", 444)))
        val fixtureRefs = scopes.mapIndexed { index, scope ->
            val expected = ref(scope)
            assertEquals(expected, success(vault.put(scope, "synthetic-$index", cancel)))
            expected
        }
        val before = fixtureRefs.drop(1).associateWith { record(it).readBytes() }
        val mismatch = fixtureRefs.first().copy(scope = scope("different"))
        assertEquals(FailureCode.SOURCE_MISMATCH, (vault.read(mismatch, cancel) as Outcome.Failure).error.code)
        assertEquals(FailureCode.SOURCE_MISMATCH, (vault.remove(mismatch, cancel) as Outcome.Failure).error.code)
        assertEquals(FailureCode.SOURCE_MISMATCH,
            (vault.read(fixtureRefs.first().copy(id = EntityId("../../not-a-path")), cancel) as Outcome.Failure).error.code)
        success(vault.remove(fixtureRefs.first(), cancel))
        for ((index, other) in fixtureRefs.drop(1).withIndex()) {
            assertArrayEquals(before.getValue(other), record(other).readBytes())
            assertTrue(nativeStore().containsAlias(VaultEnvelope.alias(other)))
            assertEquals("synthetic-${index + 1}", success(vault.read(other, cancel)))
        }
    }

    @Test fun nativeKeyLossNeverRegeneratesUntilExplicitRemoveThenPut() = runBlocking {
        val scope = scope(); val ref = ref(scope)
        success(vault.put(scope, "synthetic-before-loss", cancel))
        val ciphertext = record(ref).readBytes(); val alias = VaultEnvelope.alias(ref)
        nativeStore().deleteEntry(alias)
        assertFalse(nativeStore().containsAlias(alias))
        assertEquals(UnavailableReason.KEY_MISSING, (vault.read(ref, cancel) as Outcome.Unavailable).reason)
        assertEquals(UnavailableReason.KEY_MISSING, (vault.put(scope, "synthetic-blocked", cancel) as Outcome.Unavailable).reason)
        assertArrayEquals(ciphertext, record(ref).readBytes())
        assertFalse(nativeStore().containsAlias(alias))
        success(vault.remove(ref, cancel))
        success(vault.put(scope, "synthetic-reentered", cancel))
        assertTrue(nativeStore().containsAlias(alias))
        assertEquals("synthetic-reentered", success(vault.read(ref, cancel)))
    }

    @Test fun nativeGcmRejectsModifiedCiphertextAndMalformedHeaderWithoutErasure() = runBlocking {
        val scope = scope(); val ref = ref(scope)
        success(vault.put(scope, "synthetic-corruption-fixture", cancel))
        val file = record(ref); val original = file.readBytes()
        val tampered = original.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(tampered)
        assertEquals(FailureCode.CORRUPT_DATA, (vault.read(ref, cancel) as Outcome.Failure).error.code)
        assertArrayEquals(tampered, file.readBytes())
        file.writeBytes(original.copyOf().apply { this[4] = 127 })
        assertEquals(FailureCode.CORRUPT_DATA, (vault.read(ref, cancel) as Outcome.Failure).error.code)
        assertTrue(nativeStore().containsAlias(VaultEnvelope.alias(ref)))
        file.writeBytes(original)
        assertEquals("synthetic-corruption-fixture", success(vault.read(ref, cancel)))
    }
}
