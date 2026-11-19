package com.zendolauncher.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zendolauncher.LauncherActivity
import com.zendolauncher.core.MindfulGate
import com.zendolauncher.data.LabelCase
import com.zendolauncher.model.LaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MindfulGateTest {

    @get:Rule
    val rule = createAndroidComposeRule<LauncherActivity>()

    private val vm: LauncherViewModel
        get() = ViewModelProvider(rule.activity)[LauncherViewModel::class.java]

    private fun instagram() = LaunchTarget("com.instagram.android", ".Main", "Instagram")

    @Before
    fun setUp() {
        vm.setPauseEnabled(true)
        vm.setPauseDelaySeconds(0)
        vm.setGateTimeboxEnabled(true)
        vm.setLabelCase(LabelCase.LOWERCASE)
        vm.state.value.settings.distract.toList().forEach { vm.toggleDistract(it) }
        vm.cancelPause()
        vm.goHome()
        rule.waitForIdle()
    }

    @Test
    fun zeroDelay_showsIntentionPromptAndTimeboxes() {
        val target = instagram()
        vm.toggleDistract(target.key)
        vm.launch(target)
        rule.waitForIdle()

        rule.onNodeWithText("Mindful pause").assertIsDisplayed()
        rule.onNodeWithText("instagram").assertIsDisplayed()
        rule.onNodeWithText("What brings you here?").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Remind me after").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("10m").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun choosingBoredIntention_offersPutItDownPivot_andCancelsCleanly() {
        val target = instagram()
        vm.toggleDistract(target.key)
        vm.launch(target)
        rule.waitForIdle()

        rule.onNodeWithText(MindfulGate.BORED_INTENTION).performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithText("No pressure to open this right now.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Put it down").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Check calendar").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open anyway (5m check-in)").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Continue").assertCountEquals(0)

        rule.onNodeWithText("Put it down").performScrollTo().performClick()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun breathDelay_holdsContinueUntilTimerFinishes() {
        val target = instagram()
        vm.setPauseDelaySeconds(3)
        vm.toggleDistract(target.key)
        vm.launch(target)
        rule.waitForIdle()

        rule.onNodeWithText("Notice the urge before opening.").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Continue").assertCountEquals(0)
        rule.onNodeWithText("Take a break").performClick()
        rule.waitForIdle()
        assertNull(vm.state.value.pendingPause)
    }

    @Test
    fun continueWithPurpose_launchesAndClearsPending() {
        val target = instagram()
        vm.toggleDistract(target.key)
        vm.launch(target)
        rule.waitForIdle()

        rule.onNodeWithText("Reply / messages").performScrollTo().performClick()
        rule.onNodeWithText("No timer").performScrollTo().performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.waitForIdle()

        assertNull(vm.state.value.pendingPause)
        assertEquals(Screen.HOME, vm.state.value.screen)
    }
}
