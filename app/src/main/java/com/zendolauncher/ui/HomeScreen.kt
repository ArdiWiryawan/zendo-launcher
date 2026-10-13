package com.zendolauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.zendolauncher.data.LayoutMode
import com.zendolauncher.model.AgendaItem
import com.zendolauncher.model.ZendoToday
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherType
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            Clock(state.settings.clock24h)
            Spacer(Modifier.height(6.dp))
            Text(
                text = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", currentLocale())),
                style = LauncherType.date,
                color = palette.secondary,
            )

            if (state.settings.todayEnabled) {
                state.zendo?.let { today ->
                    Spacer(Modifier.height(44.dp))
                    TodayBlock(today)
                }
            }

            Spacer(Modifier.height(44.dp))
            SectionHeading("Essentials")
            Spacer(Modifier.height(8.dp))

            if (state.favourites.isEmpty()) {
                EmptyNote("Nothing pinned yet. Open All apps and long-press an app to add it here.")
            } else {
                state.favourites.forEach { target ->
                    AppRow(
                        viewModel = viewModel,
                        target = target,
                        layout = state.settings.layout,
                        isFavourite = true,
                        onClick = { viewModel.launch(target) },
                        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                        onToggleHidden = { viewModel.toggleHidden(target.key) },
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietAction("All apps", onClick = { viewModel.openApps() })
            QuietAction("Settings", onClick = { viewModel.openSettings() })
            Spacer(Modifier.weight(1f))
            if (state.settings.layout == LayoutMode.ICON_TEXT) {
                QuietAction("Refresh", onClick = { viewModel.refreshApps() })
            }
        }
    }
}

/**
 * Ticks on the minute boundary rather than every second: the display only ever shows
 * minutes, and a home screen that wakes the CPU 86,400 times a day is not calm.
 */
@Composable
private fun Clock(use24Hour: Boolean) {
    val now by produceState(initialValue = LocalTime.now()) {
        while (true) {
            val current = LocalTime.now()
            value = current
            delay(1_000L * (60 - current.second))
        }
    }
    val locale = currentLocale()
    val pattern = if (use24Hour) "HH:mm" else "h:mm"
    val text = now.format(DateTimeFormatter.ofPattern(pattern, locale))
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = text, style = LauncherType.clock, color = LauncherColors.current.primary)
        if (!use24Hour) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern("a", locale)),
                style = LauncherType.secondary,
                color = LauncherColors.current.secondary,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * The current locale as observable state. Reading Locale.getDefault() inside a
 * composable would not re-run when the user changes locale, leaving a stale date
 * format on screen until the next process start.
 */
@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun TodayBlock(today: ZendoToday) {
    val palette = LauncherColors.current
    SectionHeading("Today")

    today.highlight?.let {
        Spacer(Modifier.height(12.dp))
        Text(text = it, style = LauncherType.body, color = palette.primary)
    }

    today.mainAction?.let {
        Spacer(Modifier.height(14.dp))
        SecondaryText("Main action")
        Spacer(Modifier.height(2.dp))
        Text(text = it, style = LauncherType.body, color = palette.primary)
    }

    today.nextAgenda?.let { agenda ->
        Spacer(Modifier.height(14.dp))
        SecondaryText("Next")
        Spacer(Modifier.height(2.dp))
        Text(text = agenda.display(), style = LauncherType.body, color = palette.primary)
    }

    if (today.practices.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SecondaryText("Practices")
        Spacer(Modifier.height(2.dp))
        today.practices.forEach {
            Text(text = it, style = LauncherType.body, color = palette.primary)
        }
    }
}

private fun AgendaItem.display(): String = if (time.isNullOrBlank()) title else "$title · $time"
