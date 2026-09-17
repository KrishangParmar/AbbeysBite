package com.abbeysbite.app.features.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abbeysbite.app.ai.AiProvider
import com.abbeysbite.app.analytics.Analytics
import com.abbeysbite.app.analytics.AnalyticsEvents
import com.abbeysbite.app.billing.BillingManager
import com.abbeysbite.app.core.util.AppError
import com.abbeysbite.app.data.model.MealEntry
import com.abbeysbite.app.data.model.MealType
import com.abbeysbite.app.data.model.NourishmentSnapshot
import com.abbeysbite.app.data.model.RhythmDay
import com.abbeysbite.app.data.model.SatisfactionLevel
import com.abbeysbite.app.data.model.WeeklyInsight
import com.abbeysbite.app.data.repository.AuthRepository
import com.abbeysbite.app.data.repository.JournalRepository
import com.abbeysbite.app.data.repository.PreferencesRepository
import com.abbeysbite.app.domain.JournalAggregator
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

data class JournalUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val selectedDate: LocalDate? = null,
    val week: List<RhythmDay> = emptyList(),
    val nourishingDays: Int = 0,
    val streak: Int = 0,
    val entriesForSelectedDay: Map<MealType, List<MealEntry>> = emptyMap(),
    val snapshot: NourishmentSnapshot = NourishmentSnapshot(),
    val weeklyInsight: WeeklyInsight? = null,
    val insightLoading: Boolean = false,
)

class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiProvider: AiProvider,
    private val billingManager: BillingManager,
    private val settings: Settings,
    private val analytics: Analytics,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(JournalUiState())
    val state: StateFlow<JournalUiState> = _state.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    private fun today(): LocalDate =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        load()
    }

    fun load() {
        val today = today()
        _state.update { it.copy(loading = true, error = null, selectedDate = it.selectedDate ?: today) }
        viewModelScope.launch {
            // Load 30 days back for snapshot + streak context.
            val from = today.minus(30, DateTimeUnit.DAY)
            journalRepository.load(from, today)
                .onSuccess { entries -> rebuild(entries) }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error) } }
        }
    }

    private fun rebuild(allEntries: List<MealEntry>) {
        val today = today()
        val selected = _state.value.selectedDate ?: today
        val week = JournalAggregator.rhythmWeek(allEntries, today)
        val weekDates = week.map { it.date }.toSet()
        val weekEntries = allEntries.filter {
            runCatching { LocalDate.parse(it.entryDate) }.getOrNull() in weekDates
        }
        val dayEntries = allEntries
            .filter { it.entryDate == selected.toString() }
            .groupBy { it.mealType }
        val allDates = allEntries.mapNotNull { runCatching { LocalDate.parse(it.entryDate) }.getOrNull() }
        _state.update {
            it.copy(
                loading = false,
                selectedDate = selected,
                week = week,
                nourishingDays = JournalAggregator.nourishingDaysCount(week),
                streak = JournalAggregator.loggingStreak(allDates, today),
                entriesForSelectedDay = dayEntries,
                snapshot = JournalAggregator.snapshot(weekEntries),
            )
        }
        maybeLoadWeeklyInsight(weekEntries)
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        viewModelScope.launch {
            val entries = journalRepository.entries.value
                .filter { it.entryDate == date.toString() }
                .groupBy { it.mealType }
            _state.update { it.copy(entriesForSelectedDay = entries) }
        }
    }

    fun updateReflection(entryId: String, note: String?, satisfaction: SatisfactionLevel?) {
        viewModelScope.launch {
            journalRepository.updateNote(entryId, note, satisfaction)
            rebuild(journalRepository.entries.value)
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            journalRepository.delete(entryId)
            rebuild(journalRepository.entries.value)
        }
    }

    /**
     * Weekly insight is generated at most once per week-start and cached.
     * Premium users get it regenerated when new meals arrive.
     */
    private fun maybeLoadWeeklyInsight(weekEntries: List<MealEntry>) {
        val today = today()
        val monday = today.minus(
            (today.dayOfWeek.ordinal) % 7, DateTimeUnit.DAY,
        )
        // Keys are USER-scoped: on a shared device, user B must never read
        // user A's cached AI insight.
        val uid = authRepository.currentUserId ?: "local"
        val cacheKey = "weekly_insight_${uid}_$monday"
        val premium = billingManager.premiumState.value.isPremium
        val entryCountKey = "weekly_insight_count_${uid}_$monday"

        val cached = settings.getStringOrNull(cacheKey)?.let {
            runCatching { json.decodeFromString<WeeklyInsight>(it) }.getOrNull()
        }
        val cachedCount = settings.getInt(entryCountKey, -1)
        val shouldRegenerate = cached == null ||
            (premium && weekEntries.size != cachedCount && weekEntries.isNotEmpty())

        if (!shouldRegenerate) {
            _state.update { it.copy(weeklyInsight = cached) }
            return
        }
        if (weekEntries.isEmpty() && cached == null) {
            _state.update { it.copy(weeklyInsight = null) }
            return
        }
        _state.update { it.copy(insightLoading = true) }
        viewModelScope.launch {
            aiProvider.weeklyInsight(weekEntries, preferencesRepository.preferences.value)
                .onSuccess { insight ->
                    settings.putString(cacheKey, json.encodeToString(WeeklyInsight.serializer(), insight))
                    settings.putInt(entryCountKey, weekEntries.size)
                    analytics.track(AnalyticsEvents.WEEKLY_INSIGHT_VIEWED)
                    _state.update { it.copy(weeklyInsight = insight, insightLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(insightLoading = false, weeklyInsight = cached) }
                }
        }
    }
}
