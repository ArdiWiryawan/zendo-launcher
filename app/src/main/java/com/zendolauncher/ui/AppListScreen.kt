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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherType

@Composable
fun AppListScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(28.dp))

        BasicTextField(
            value = state.query,
            onValueChange = { viewModel.setQuery(it) },
            singleLine = true,
            textStyle = LauncherType.appLabel.copy(color = palette.primary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.primary),
            decorationBox = { inner ->
                if (state.query.isEmpty()) {
                    Text("Search", style = LauncherType.appLabel, color = palette.secondary)
                }
                inner()
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(20.dp))

        val list = state.results
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeading(if (state.query.isBlank()) "${list.size} apps" else "Results")
            Spacer(Modifier.weight(1f))
            QuietAction("Home", onClick = { viewModel.goHome() })
        }
        Spacer(Modifier.height(4.dp))

        if (list.isEmpty()) {
            EmptyNote(
                if (state.query.isBlank()) "No launchable apps found." else "Nothing matches \"${state.query}\".",
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(list, key = { it.key }) { target ->
                    AppRow(
                        viewModel = viewModel,
                        target = target,
                        layout = state.settings.layout,
                        isFavourite = target.key in state.settings.favourites,
                        onClick = { viewModel.launch(target) },
                        onToggleFavourite = { viewModel.toggleFavourite(target.key) },
                        onToggleHidden = { viewModel.toggleHidden(target.key) },
                    )
                    Hairline()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            QuietAction("Settings", onClick = { viewModel.openSettings() })
            QuietAction("Refresh", onClick = { viewModel.refreshApps() })
        }
        Spacer(Modifier.height(12.dp))
    }
}
