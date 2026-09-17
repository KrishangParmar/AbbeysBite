package com.abbeysbite.app

import com.abbeysbite.app.billing.RevenueCatKeySelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Release-safety guarantees for RevenueCat key selection: a release build of
 * ANY flavor must never end up on the Test Store key, and flavors can never
 * cross-activate each other's store keys.
 */
class RevenueCatKeySelectionTest {

    private val goog = "goog_real"
    private val galx = "galx_real"
    private val appl = "appl_real"
    private val test = "test_store"

    private fun select(store: String, debug: Boolean, testKey: String = test) =
        RevenueCatKeySelection.selectApiKey(store, debug, goog, galx, appl, testKey)

    @Test
    fun `play release uses goog key, never test store`() {
        assertEquals(goog, select("play", debug = false))
    }

    @Test
    fun `galaxy release uses galx key`() {
        assertEquals(galx, select("galaxy", debug = false))
    }

    @Test
    fun `appstore release uses appl key`() {
        assertEquals(appl, select("appstore", debug = false))
    }

    @Test
    fun `no release variant can ever receive the test store key`() {
        for (store in listOf("play", "galaxy", "appstore", "")) {
            val key = select(store, debug = false)
            assertFalse(key.startsWith("test_"), "release $store got $key")
        }
    }

    @Test
    fun `play debug prefers the test store key`() {
        assertEquals(test, select("play", debug = true))
    }

    @Test
    fun `appstore debug prefers the test store key`() {
        assertEquals(test, select("appstore", debug = true))
    }

    @Test
    fun `galaxy debug keeps the real galaxy key for GalaxyBillingMode TEST`() {
        assertEquals(galx, select("galaxy", debug = true))
    }

    @Test
    fun `play debug without a test key falls back to goog`() {
        assertEquals(goog, select("play", debug = true, testKey = ""))
    }

    @Test
    fun `flavors never cross-activate another store's key`() {
        for (debug in listOf(true, false)) {
            assertFalse(select("play", debug).startsWith("galx_"))
            assertFalse(select("galaxy", debug).let { it.startsWith("goog_") || it.startsWith("test_") })
        }
    }
}
