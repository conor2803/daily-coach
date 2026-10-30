package io.github.conor2803.coach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.conor2803.coach.coach.Readiness
import io.github.conor2803.coach.coach.ReadinessCalculator
import io.github.conor2803.coach.health.HealthHistory
import io.github.conor2803.coach.health.HealthHistoryReader
import io.github.conor2803.coach.health.HealthReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HealthUiState(
    val loading: Boolean = true,
    val available: Boolean = true,
    val history: HealthHistory = HealthHistory.EMPTY,
    val readiness: Readiness? = null,
)

/** Loads the last 30 days from Health Connect once per screen visit; Today and Trends share it. */
class CoachViewModel(app: Application) : AndroidViewModel(app) {
    private val _health = MutableStateFlow(HealthUiState())
    val health: StateFlow<HealthUiState> = _health.asStateFlow()

    fun refresh() {
        val context = getApplication<Application>()
        if (!HealthReader.isAvailable(context)) {
            _health.value = HealthUiState(loading = false, available = false)
            return
        }
        _health.update { it.copy(loading = true, available = true) }
        viewModelScope.launch {
            val history = HealthHistoryReader(context).read()
            _health.value = HealthUiState(
                loading = false,
                history = history,
                readiness = ReadinessCalculator.from(history, LocalDate.now()),
            )
        }
    }
}
