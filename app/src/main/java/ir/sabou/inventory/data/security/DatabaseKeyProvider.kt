package ir.sabou.inventory.data.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DatabaseKeyUnavailableException(cause: Throwable) :
    IllegalStateException("کلید پایگاه داده در دسترس نیست؛ بازیابی امن لازم است.", cause)

class DatabaseKeyProvider(private val context: Context) {
    @SuppressLint("ApplySharedPref", "UseKtx")
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrapped = preferences.getString(WRAPPED_KEY, null)
        return try {
            if (wrapped == null) {
                val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
                // The encrypted key must be durably stored before it is returned to the caller.
                preferences.edit()
                    .putString(WRAPPED_KEY, wrap(passphrase))
                    .commit()
                    .also { require(it) { "ذخیره کلید پایگاه داده انجام نشد." } }
                passphrase
            } else {
                unwrap(wrapped)
            }
        } catch (error: DatabaseKeyUnavailableException) {
            throw error
        } catch (error: Throwable) {
            throw DatabaseKeyUnavailableException(error)
        }
    }

    private fun wrap(value: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(value)
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun unwrap(value: String): ByteArray {
        val packed = Base64.decode(value, Base64.NO_WRAP)
        require(packed.size > IV_BYTES) { "ساختار کلید رمزگذاری‌شده معتبر نیست." }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateMasterKey(),
            GCMParameterSpec(128, packed.copyOfRange(0, IV_BYTES)),
        )
        return cipher.doFinal(packed.copyOfRange(IV_BYTES, packed.size)).also {
            require(it.size == PASSPHRASE_BYTES) { "طول کلید پایگاه داده معتبر نیست." }
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFS_NAME = "sabou_v3_secure"
        const val WRAPPED_KEY = "wrapped_database_key"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sabou_v3_database_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PASSPHRASE_BYTES = 32
        const val IV_BYTES = 12
    }
}
