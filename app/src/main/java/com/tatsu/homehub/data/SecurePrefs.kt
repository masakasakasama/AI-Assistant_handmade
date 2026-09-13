package com.tatsu.homehub.data

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
    private val alias = "tatsu_home_master_key"

    fun put(name: String, value: String): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)

        return prefs.edit()
            .putString(name, Base64.encodeToString(payload, Base64.NO_WRAP))
            .commit()
    }

    fun get(name: String): String? {
        val encoded = prefs.getString(name, null) ?: return null
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)

            // v0.1.1+ stores IV length in byte 0.
            // v0.1.0 stored a raw 12-byte IV followed by ciphertext.
            val (iv, encrypted) = if (
                payload.isNotEmpty() &&
                payload[0].toInt() in 12..16 &&
                payload.size > 1 + payload[0].toInt()
            ) {
                val ivLength = payload[0].toInt()
                payload.copyOfRange(1, 1 + ivLength) to
                    payload.copyOfRange(1 + ivLength, payload.size)
            } else {
                payload.copyOfRange(0, 12) to payload.copyOfRange(12, payload.size)
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasSwitchBotCredentials(): Boolean =
        !get(KEY_SWITCHBOT_TOKEN).isNullOrBlank() &&
            !get(KEY_SWITCHBOT_SECRET).isNullOrBlank()

    fun switchBotTokenSuffix(): String? =
        get(KEY_SWITCHBOT_TOKEN)?.takeIf { it.isNotBlank() }?.takeLast(4)

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(
            alias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        const val KEY_SWITCHBOT_TOKEN = "switchbot_token"
        const val KEY_SWITCHBOT_SECRET = "switchbot_secret"
    }
}
