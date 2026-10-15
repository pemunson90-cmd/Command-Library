package com.architectpsg.commandlibrary.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Injection is internal and test-only; the public constructor always uses AndroidKeyStore. */
internal interface VaultKeyBackend {
    fun find(alias: String): SecretKey?
    fun create(alias: String): SecretKey
    fun delete(alias: String)
    fun contains(alias: String): Boolean
}

internal class AndroidVaultKeyStore : VaultKeyBackend {
    // Deliberately no provider access, initialization or key generation in the constructor.
    private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun find(alias: String): SecretKey? {
        val key = store().getKey(alias, null) ?: return null
        return key as? SecretKey ?: fault(VaultIssue.KEY_MISSING)
    }

    override fun create(alias: String): SecretKey {
        // Called under the process mutex only after both alias and record are absent.
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun delete(alias: String) { store().deleteEntry(alias) }
    override fun contains(alias: String): Boolean = store().containsAlias(alias)

    private companion object { const val PROVIDER = "AndroidKeyStore" }
}
