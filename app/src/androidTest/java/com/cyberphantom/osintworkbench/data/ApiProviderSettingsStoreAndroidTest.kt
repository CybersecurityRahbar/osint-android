package com.cyberphantom.osintworkbench.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiProviderSettingsStoreAndroidTest {
    @Test
    fun apiKeysAreEncryptedAndPreferredSlotControlsFallbackOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ApiProviderSettingsStore(context)
        val provider = ApiProviderId.TAVILY
        val preferences = context.getSharedPreferences("osint_external_api_settings_v1", 0)

        try {
            (1..ApiProviderSettingsStore.KEY_SLOTS).forEach { store.clearKey(provider, it) }
            store.setEnabled(provider, false)

            store.saveKey(provider, 1, "unit-test-key-alpha")
            store.saveKey(provider, 2, "unit-test-key-bravo")
            store.setEnabled(provider, true)

            assertTrue(store.isEnabled(provider))
            assertTrue(store.hasEncryptedKey(provider, 1))
            assertTrue(store.hasEncryptedKey(provider, 2))

            val storedCiphertext = preferences.getString("secret_TAVILY_1", "").orEmpty()
            assertTrue(storedCiphertext.isNotBlank())
            assertFalse(storedCiphertext.contains("unit-test-key-alpha"))

            assertEquals(
                listOf(1, 2),
                store.availableKeys(provider).map { it.slot }
            )
            assertEquals(
                listOf("unit-test-key-alpha", "unit-test-key-bravo"),
                store.availableKeys(provider).map { it.value }
            )

            store.recordSuccessfulKey(provider, 2)
            assertEquals(
                listOf(2, 1),
                store.availableKeys(provider).map { it.slot }
            )

            store.rotatePreferredKey(provider)
            assertEquals(
                listOf(1, 2),
                store.availableKeys(provider).map { it.slot }
            )

            store.setExternalProviderLimit(5)
            assertEquals(5, store.externalProviderLimit())
            store.setExternalPageLimit(4)
            assertEquals(4, store.externalPageLimit())
            store.setSearchResultLimit(50)
            assertEquals(50, store.searchResultLimit())
            store.setSearchResultLimit(500)
            assertEquals(500, store.searchResultLimit())
        } finally {
            (1..ApiProviderSettingsStore.KEY_SLOTS).forEach { store.clearKey(provider, it) }
            store.setEnabled(provider, false)
            store.recordSuccessfulKey(provider, 1)
            store.setExternalProviderLimit(ApiProviderSettingsStore.DEFAULT_SEARCH_REQUEST_LIMIT)
            store.setExternalPageLimit(2)
            store.setSearchResultLimit(ApiProviderSettingsStore.DEFAULT_SEARCH_RESULT_LIMIT)
        }
    }
}
