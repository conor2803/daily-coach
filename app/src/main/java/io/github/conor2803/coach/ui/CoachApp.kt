package io.github.conor2803.coach.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.conor2803.coach.R
import io.github.conor2803.coach.ui.settings.SettingsScreen
import io.github.conor2803.coach.ui.today.TodayScreen
import io.github.conor2803.coach.ui.trends.TrendsScreen

enum class Destination(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_today),
    TRENDS("Trends", R.drawable.ic_trends),
    SETTINGS("Settings", R.drawable.ic_tune),
}

@Composable
fun CoachApp(tick: Int, viewModel: CoachViewModel = viewModel()) {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }

    LaunchedEffect(tick) { viewModel.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = destination == d,
                        onClick = { destination = d },
                        icon = { Icon(painterResource(d.icon), contentDescription = null) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "destination",
            modifier = Modifier.fillMaxSize(),
        ) { shown ->
            when (shown) {
                Destination.TODAY -> TodayScreen(tick, padding, viewModel) { destination = Destination.SETTINGS }
                Destination.TRENDS -> TrendsScreen(padding, viewModel)
                Destination.SETTINGS -> SettingsScreen(tick, padding)
            }
        }
    }
}
