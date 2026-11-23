package com.cyberphantom.osintworkbench.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ApiProviderGroup { SEARCH, NEWS }

enum class ApiProviderId(val group: ApiProviderGroup) {
    SERP_API(ApiProviderGroup.SEARCH),
    TAVILY(ApiProviderGroup.SEARCH),
    TINYFISH(ApiProviderGroup.SEARCH),
    FIRECRAWL(ApiProviderGroup.SEARCH),
    EXA(ApiProviderGroup.SEARCH),
    CURRENTS(ApiProviderGroup.NEWS),
    THE_NEWS_API(ApiProviderGroup.NEWS),
    NEWSDATA(ApiProviderGroup.NEWS),
    GNEWS(ApiProviderGroup.NEWS)
}

data class ApiKeyCandidate(val slot: Int, val value: String)

/**
 * Stores provider switches and encrypted user-supplied API keys.
 *
 * Each secret is encrypted with an AES-256 key kept by Android Keystore. Only the
 * authenticated ciphertext and its random GCM IV are written to SharedPreferences.
 */
class ApiProviderSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "osint_external_api_settings_v1",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun isEnabled(provider: ApiProviderId): Boolean =
        preferences.getBoolean(enabledKey(provider), false)

    @Synchronized
    fun setEnabled(provider: ApiProviderId, enabled: Boolean) {
        preferences.edit().putBoolean(enabledKey(provider), enabled).apply()
    }

    @Synchronized
    fun hasEncryptedKey(provider: ApiProviderId, slot: Int): Boolean {
        require(slot in 1..KEY_SLOTS)
        return !preferences.getString(secretKey(provider, slot), null).isNullOrBlank()
    }

    @Synchronized
    fun saveKey(provider: ApiProviderId, slot: Int, value: String) {
        require(slot in 1..KEY_SLOTS)
        val clean = value.trim()
        if (clean.isEmpty()) {
            clearKey(provider, slot)
            return
        }
        val encrypted = encrypt(clean)
        preferences.edit().putString(secretKey(provider, slot), encrypted).apply()
    }

    @Synchronized
    fun clearKey(provider: ApiProviderId, slot: Int) {
        require(slot in 1..KEY_SLOTS)
        preferences.edit().remove(secretKey(provider, slot)).apply()
    }

    @Synchronized
    fun availableKeys(provider: ApiProviderId): List<ApiKeyCandidate> {
        val start = preferences.getInt(preferredSlotKey(provider), 1).coerceIn(1, KEY_SLOTS)
        return (0 until KEY_SLOTS).mapNotNull { offset ->
            val slot = ((start - 1 + offset) % KEY_SLOTS) + 1
            val encrypted = preferences.getString(secretKey(provider, slot), null) ?: return@mapNotNull null
            val decrypted = runCatching { decrypt(encrypted) }.getOrNull()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ApiKeyCandidate(slot, decrypted)
        }
    }

    @Synchronized
    fun recordSuccessfulKey(provider: ApiProviderId, slot: Int) {
        require(slot in 1..KEY_SLOTS)
        preferences.edit().putInt(preferredSlotKey(provider), slot).apply()
    }

    @Synchronized
    fun rotatePreferredKey(provider: ApiProviderId) {
        val current = preferences.getInt(preferredSlotKey(provider), 1).coerceIn(1, KEY_SLOTS)
        val next = (current % KEY_SLOTS) + 1
        preferences.edit().putInt(preferredSlotKey(provider), next).apply()
    }

    @Synchronized
    fun externalProviderLimit(): Int =
        preferences.getInt(KEY_EXTERNAL_PROVIDER_LIMIT, DEFAULT_SEARCH_REQUEST_LIMIT).coerceIn(1, 5)

    @Synchronized
    fun setExternalProviderLimit(value: Int) {
        preferences.edit()
            .putInt(KEY_EXTERNAL_PROVIDER_LIMIT, value.coerceIn(1, 5))
            .apply()
    }

    @Synchronized
    fun externalPageLimit(): Int =
        preferences.getInt(KEY_EXTERNAL_PAGE_LIMIT, DEFAULT_EXTERNAL_PAGE_LIMIT).coerceIn(1, 5)

    @Synchronized
    fun setExternalPageLimit(value: Int) {
        preferences.edit().putInt(KEY_EXTERNAL_PAGE_LIMIT, value.coerceIn(1, 5)).apply()
    }

    /**
     * Target maximum number of merged records shown and saved for one identifier/news search.
     * Individual providers may return fewer records because of quotas, paging, or indexing coverage.
     */
    @Synchronized
    fun searchResultLimit(): Int = normalizeSearchResultLimit(
        preferences.getInt(KEY_SEARCH_RESULT_LIMIT, DEFAULT_SEARCH_RESULT_LIMIT)
    )

    @Synchronized
    fun setSearchResultLimit(value: Int) {
        preferences.edit()
            .putInt(KEY_SEARCH_RESULT_LIMIT, normalizeSearchResultLimit(value))
            .apply()
    }

    private fun normalizeSearchResultLimit(value: Int): Int =
        value.coerceIn(MIN_SEARCH_RESULT_LIMIT, MAX_SEARCH_RESULT_LIMIT)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = iv.size.toByte()
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.isNotEmpty()) { "Invalid encrypted key record." }
        val ivLength = payload[0].toInt() and 0xff
        require(ivLength in 12..16 && payload.size > 1 + ivLength) { "Invalid encrypted key record." }
        val iv = payload.copyOfRange(1, 1 + ivLength)
        val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun enabledKey(provider: ApiProviderId) = "enabled_" + provider.name
    private fun secretKey(provider: ApiProviderId, slot: Int) = "secret_" + provider.name + "_" + slot
    private fun preferredSlotKey(provider: ApiProviderId) = "preferred_slot_" + provider.name

    companion object {
        const val KEY_SLOTS = 3
        const val DEFAULT_SEARCH_REQUEST_LIMIT = 5
        const val DEFAULT_SEARCH_RESULT_LIMIT = 100
        const val MIN_SEARCH_RESULT_LIMIT = 10
        const val MAX_SEARCH_RESULT_LIMIT = 500
        private const val DEFAULT_EXTERNAL_PAGE_LIMIT = 2
        private const val KEY_EXTERNAL_PROVIDER_LIMIT = "external_provider_limit"
        private const val KEY_EXTERNAL_PAGE_LIMIT = "external_page_limit"
        private const val KEY_SEARCH_RESULT_LIMIT = "search_result_limit"
        private const val KEY_ALIAS = "osint_external_api_aes_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
