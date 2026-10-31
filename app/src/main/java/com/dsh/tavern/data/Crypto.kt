package com.dsh.tavern.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API Key 本地加密：优先使用 Android Keystore 的 AES/GCM 密钥；失败时退化为 Base64（仅防肩窥，不防取证）。
 */
object Crypto {
    private const val ALIAS = "tavern_api_key_v1"
    private const val PREFIX = "enc:"
    private const val B64_PREFIX = "b64:"

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            B64_PREFIX + Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    fun decrypt(data: String): String {
        if (data.isEmpty()) return ""
        return try {
            when {
                data.startsWith(PREFIX) -> {
                    val all = Base64.decode(data.removePrefix(PREFIX), Base64.NO_WRAP)
                    val iv = all.copyOfRange(0, 12)
                    val ct = all.copyOfRange(12, all.size)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                    cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
                    String(cipher.doFinal(ct), Charsets.UTF_8)
                }
                data.startsWith(B64_PREFIX) ->
                    String(Base64.decode(data.removePrefix(B64_PREFIX), Base64.NO_WRAP), Charsets.UTF_8)
                else -> data
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** E4：PIN 的 SHA-256（轻量应用锁，只防随手翻看，不防取证）。 */
    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val entry = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (entry != null) return entry.secretKey

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }
}
