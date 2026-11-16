package org.fossify.messages.helpers

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the webhook Bearer token encrypted with an AES-GCM key held in the Android Keystore.
 *
 * The ciphertext lives in its own preferences file, which is excluded from backups and device
 * transfers (see backup_rules.xml and data_extraction_rules.xml). The key never leaves the
 * Keystore, so a copy of the file is useless on another device. If the key is lost, the token
 * reads as empty and has to be entered again.
 */
object WebhookTokenStore {
    const val PREFS_NAME = "webhook_secure"

    private const val KEY_TOKEN = "bearer_token"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "webhook_bearer_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val TAG_SIZE_BITS = 128
    private const val IV_SIZE_BYTES = 12

    @Synchronized
    fun get(context: Context, legacyPrefs: SharedPreferences): String {
        migrateLegacyToken(context, legacyPrefs)
        val stored = securePrefs(context).getString(KEY_TOKEN, null) ?: return ""
        return try {
            decrypt(stored)
        } catch (_: GeneralSecurityException) {
            ""
        } catch (_: IllegalArgumentException) {
            ""
        }
    }

    @Synchronized
    fun set(context: Context, legacyPrefs: SharedPreferences, token: String): Boolean {
        legacyPrefs.edit().remove(WEBHOOK_BEARER_TOKEN).commit()
        val editor = securePrefs(context).edit()
        if (token.isBlank()) {
            return editor.remove(KEY_TOKEN).commit()
        }

        return try {
            editor.putString(KEY_TOKEN, encrypt(token)).commit()
        } catch (_: GeneralSecurityException) {
            false
        }
    }

    /**
     * Moves a token saved by older versions in the plain app preferences into encrypted storage.
     * The plain copy is removed only after the encrypted copy has been written.
     */
    @Synchronized
    fun migrateLegacyToken(context: Context, legacyPrefs: SharedPreferences) {
        if (!legacyPrefs.contains(WEBHOOK_BEARER_TOKEN)) {
            return
        }

        val legacyToken = legacyPrefs.getString(WEBHOOK_BEARER_TOKEN, "").orEmpty()
        val migrated = legacyToken.isBlank() || try {
            securePrefs(context).edit().putString(KEY_TOKEN, encrypt(legacyToken)).commit()
        } catch (_: GeneralSecurityException) {
            false
        }
        if (migrated) {
            legacyPrefs.edit().remove(WEBHOOK_BEARER_TOKEN).commit()
        }
    }

    private fun securePrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        require(bytes.size > IV_SIZE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE_BITS, bytes, 0, IV_SIZE_BYTES))
        return String(cipher.doFinal(bytes, IV_SIZE_BYTES, bytes.size - IV_SIZE_BYTES), Charsets.UTF_8)
    }
}
