package com.abbeysbite.app

import com.abbeysbite.app.billing.FreeTierLimiter
import com.abbeysbite.app.core.config.AppConfig
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FreeTierLimiterTest {

    private fun limiter(store: MutableMap<String, Int> = mutableMapOf()) =
        FreeTierLimiter(
            readCount = { store[it] ?: 0 },
            writeCount = { k, v -> store[k] = v },
        )

    private val today = LocalDate(2026, 9, 5)
    private val tomorrow = LocalDate(2026, 9, 6)

    @Test
    fun freeUserLimitedToDailyAnalyses() {
        val l = limiter()
        repeat(AppConfig.FreeTier.DAILY_MEAL_ANALYSES) {
            assertTrue(l.canAnalyze(isPremium = false, today))
            l.recordAnalysis(today)
        }
        assertFalse(l.canAnalyze(isPremium = false, today))
        assertEquals(0, l.analysesRemaining(isPremium = false, today))
    }

    @Test
    fun limitResetsNextDay() {
        val l = limiter()
        repeat(AppConfig.FreeTier.DAILY_MEAL_ANALYSES) { l.recordAnalysis(today) }
        assertFalse(l.canAnalyze(isPremium = false, today))
        assertTrue(l.canAnalyze(isPremium = false, tomorrow))
    }

    @Test
    fun premiumBypassesAllLimits() {
        val l = limiter()
        repeat(50) { l.recordAnalysis(today); l.recordChatMessage(today) }
        assertTrue(l.canAnalyze(isPremium = true, today))
        assertTrue(l.canChat(isPremium = true, today))
        assertNull(l.analysesRemaining(isPremium = true, today))
        assertTrue(l.canUseVoice(isPremium = true))
        assertTrue(l.canUsePantryAwareAi(isPremium = true))
    }

    @Test
    fun voiceAndPantryAiArePremiumOnlyByDefault() {
        val l = limiter()
        assertFalse(l.canUseVoice(isPremium = false))
        assertFalse(l.canUsePantryAwareAi(isPremium = false))
    }

    @Test
    fun chatLimitIndependentOfAnalysisLimit() {
        val l = limiter()
        repeat(AppConfig.FreeTier.DAILY_MEAL_ANALYSES) { l.recordAnalysis(today) }
        assertTrue(l.canChat(isPremium = false, today))
    }
}
