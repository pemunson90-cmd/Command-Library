package com.architectpsg.commandlibrary.data.vault

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.SecretRef
import com.architectpsg.commandlibrary.core.model.SecretScope
import com.architectpsg.commandlibrary.core.model.SecretVault
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.UnavailableReason
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.NoSuchPaddingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single-process vault. All instances share one mutex, including separate injected test instances.
 * Construction is lazy: no key/provider access or filesystem operation occurs here.
 * Strings in the public contract cannot be erased; temporary plaintext byte arrays are wiped.
 */
public class AndroidSecretVault internal constructor(
    private val keys: VaultKeyBackend,
    private val records: VaultRecordStore,
) : SecretVault {
    public constructor(context: Context) : this(
        AndroidVaultKeyStore(),
        VaultRecordStore({ File(context.applicationContext.noBackupFilesDir, "command-library-vault-v1") }),
    )

    public override fun reference(scope: SecretScope): Outcome<SecretRef> = try {
        Outcome.Success(VaultEnvelope.ref(scope))
    } catch (problem: Exception) {
        classify(problem)
    }

    public override suspend fun put(scope: SecretScope, value: String, cancel: CancelToken): Outcome<SecretRef> =
        operation(cancel) { checkpoint ->
            val ref = VaultEnvelope.ref(scope)
            val plaintext = VaultEnvelope.valueBytes(value)
            try {
                val alias = VaultEnvelope.alias(ref)
                val exists = records.exists(ref.id.value)
                var key = keys.find(alias)
                if (key == null) {
                    // Never replace a lost key under ciphertext or an existing invalidated alias.
                    if (exists || keys.contains(alias)) fault(VaultIssue.KEY_MISSING)
                    checkpoint()
                    key = keys.create(alias)
                }
                val record = VaultEnvelope.encrypt(ref, key, plaintext)
                records.write(ref.id.value, record, checkpoint)
                ref
            } finally {
                plaintext.fill(0)
            }
        }

    public override suspend fun read(ref: SecretRef, cancel: CancelToken): Outcome<String> = operation(cancel) { checkpoint ->
        VaultEnvelope.checkRef(ref)
        val record = records.read(ref.id.value) ?: fault(VaultIssue.NOT_FOUND)
        val key = keys.find(VaultEnvelope.alias(ref)) ?: fault(VaultIssue.KEY_MISSING)
        val value = VaultEnvelope.decrypt(ref, key, record)
        // Reads have no committed mutation: observe cancellation before publishing plaintext.
        // The envelope has already wiped its temporary plaintext byte array in finally.
        checkpoint()
        value
    }

    public override suspend fun remove(ref: SecretRef, cancel: CancelToken): Outcome<Unit> = operation(cancel) { checkpoint ->
        VaultEnvelope.checkRef(ref)
        val alias = VaultEnvelope.alias(ref)
        checkpoint()
        // Both deletions are attempted, even if the first fails. Partial deletion is never success.
        var problem = false
        var denial = false
        try { records.remove(ref.id.value) } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { problem = true; denial = error is SecurityException }
        try {
            keys.delete(alias)
            if (keys.contains(alias)) problem = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) { problem = true; denial = denial || error is SecurityException }
        if (denial) fault(VaultIssue.PERMISSION)
        if (problem || records.exists(ref.id.value)) fault(VaultIssue.PERSISTENCE)
        Unit
    }

    private suspend fun <T> operation(cancel: CancelToken, action: (() -> Unit) -> T): Outcome<T> = try {
        withContext(Dispatchers.IO) {
            processMutex.withLock {
                val context = currentCoroutineContext()
                val checkpoint = { context.ensureActive(); cancel.throwIfCancelled() }
                checkpoint()
                Outcome.Success(action(checkpoint))
                // No postcommit token check promises rollback. Structured cancellation may still
                // escape on dispatcher return after a mutation; stable refs permit explicit retry.
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (problem: Exception) {
        classify(problem)
    }

    private companion object { val processMutex = Mutex() }
}

internal enum class VaultIssue { INVALID, MISMATCH, NOT_FOUND, KEY_MISSING, CORRUPT, UNAVAILABLE, UNSUPPORTED, PERSISTENCE, PERMISSION }
internal class VaultFault(val issue: VaultIssue) : RuntimeException("Vault operation failed")
internal fun fault(issue: VaultIssue): Nothing = throw VaultFault(issue)

/** Static messages only; never retain provider exception text, raw scopes, aliases or values. */
private fun classify(problem: Exception): Outcome<Nothing> {
    val causes = generateSequence<Throwable>(problem) { it.cause }.take(12).toList()
    val issue = when {
        problem is VaultFault -> problem.issue
        causes.any { it is KeyPermanentlyInvalidatedException || it is UnrecoverableKeyException } -> VaultIssue.KEY_MISSING
        causes.any { it is SecurityException } -> VaultIssue.PERMISSION
        causes.any { it is AEADBadTagException || it is BadPaddingException } -> VaultIssue.CORRUPT
        causes.any { it is NoSuchAlgorithmException || it is NoSuchProviderException || it is NoSuchPaddingException } -> VaultIssue.UNSUPPORTED
        causes.any { it is IOException } -> VaultIssue.PERSISTENCE
        problem is GeneralSecurityException || problem is ProviderException -> VaultIssue.UNAVAILABLE
        else -> VaultIssue.UNAVAILABLE
    }
    return when (issue) {
        VaultIssue.NOT_FOUND -> Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Credential record is absent")
        VaultIssue.KEY_MISSING -> Outcome.Unavailable(UnavailableReason.KEY_MISSING, "Credential key is unavailable; explicit removal is required")
        VaultIssue.UNAVAILABLE -> Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Native credential service is unavailable")
        VaultIssue.UNSUPPORTED -> Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Native credential encryption is unsupported")
        else -> {
            val code = when (issue) {
                VaultIssue.INVALID -> FailureCode.INVALID_INPUT
                VaultIssue.MISMATCH -> FailureCode.SOURCE_MISMATCH
                VaultIssue.CORRUPT -> FailureCode.CORRUPT_DATA
                VaultIssue.PERMISSION -> FailureCode.PERMISSION
                else -> FailureCode.PERSISTENCE
            }
            Outcome.Failure(DomainFailure(code, "Credential operation failed: ${code.name}", SnapshotList.empty()))
        }
    }
}
