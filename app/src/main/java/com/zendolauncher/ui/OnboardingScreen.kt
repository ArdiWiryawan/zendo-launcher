package com.zendolauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.zendolauncher.ui.theme.LauncherColors
import com.zendolauncher.ui.theme.LauncherSpace
import com.zendolauncher.ui.theme.LauncherType

/**
 * First-run explanation. One scrollable page rather than a wizard: there is nothing to
 * configure here, so a sequence of steps would only delay the first frame of real use.
 * Built from primitives because the shared components are text-first anyway and a
 * bespoke layout keeps the onboarding from reading as a setup flow.
 */
@Composable
fun OnboardingScreen(viewModel: LauncherViewModel, state: UiState) {
    val palette = LauncherColors.current
    val context = LocalContext.current

    BackHandler { viewModel.dismissOnboarding() }

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
                .padding(horizontal = LauncherSpace.gutter),
        ) {
            Spacer(Modifier.height(LauncherSpace.xl + LauncherSpace.md))
            Text(
                text = "Zendo Launcher",
                style = LauncherType.appLabel,
                color = palette.primary,
            )
            Spacer(Modifier.height(LauncherSpace.lg + LauncherSpace.xs))

            Line("It replaces your Home screen with a short text list of the apps you choose, so the phone shows fewer things at once.")
            Spacer(Modifier.height(LauncherSpace.lg - LauncherSpace.xs))
            Line("Open All apps and use the add control on any row to pick your essentials.")
            Spacer(Modifier.height(LauncherSpace.lg - LauncherSpace.xs))
            Line("Android owns the Home app setting, so making Zendo the default happens in your device settings.")
            Spacer(Modifier.height(LauncherSpace.lg - LauncherSpace.xs))
            Line("Choosing another launcher in that same screen restores your previous Home screen.")

            Spacer(Modifier.height(LauncherSpace.xl - LauncherSpace.xs))
            Hairline()
            Spacer(Modifier.height(LauncherSpace.lg - LauncherSpace.xs))
            Line("Zendo is optional: the launcher works fully without it.")
            Spacer(Modifier.height(LauncherSpace.xl))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LauncherSpace.gutter, vertical = LauncherSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isDefaultHome) {
                Text(
                    text = "Zendo is your Home app",
                    style = LauncherType.action,
                    color = palette.secondary,
                    modifier = Modifier.weight(1f),
                )
            } else {
                TextAction(
                    text = "Set as Home app",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            TextAction(text = "Got it", onClick = { viewModel.dismissOnboarding() })
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text = text, style = LauncherType.body, color = LauncherColors.current.secondary)
}

/**
 * A quiet text action with a real 48dp touch target, left-aligned inside the row.
 * [QuietAction] meets the same minimum but centres its label in a Box, which would float
 * "Set as Home app" into the middle of the space its weight gives it; onboarding's bottom
 * row reads as a sentence, so the text stays on its own leading edge.
 */
@Composable
private fun TextAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        style = LauncherType.action,
        color = LauncherColors.current.primary,
        modifier = modifier
            .clickable(onClick = onClick)
            .heightIn(min = LauncherSpace.touchTarget)
            .padding(vertical = LauncherSpace.sm),
    )
}
