package com.kuunyi.scanner.ui.screens

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit4.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kuunyi.scanner.BuildConfig
import com.kuunyi.scanner.viewmodel.ScannerViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenPinTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var vm: ScannerViewModel

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = ctx.getSharedPreferences("test_scanner_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        vm = ScannerViewModel(prefs = prefs)
        rule.setContent { SettingsScreen(vm) }
    }

    @Test
    fun tappingHostFieldWhenLockedShowsPinDialog() {
        rule.onNodeWithContentDescription("Edit API host").performClick()
        rule.onNodeWithText("Enter PIN").assertIsDisplayed()
    }

    @Test
    fun tappingPortFieldWhenLockedShowsPinDialog() {
        rule.onNodeWithContentDescription("Edit port").performClick()
        rule.onNodeWithText("Enter PIN").assertIsDisplayed()
    }

    @Test
    fun tappingResetWhenLockedShowsPinDialog() {
        rule.onNodeWithText("Reset").performClick()
        rule.onNodeWithText("Enter PIN").assertIsDisplayed()
    }

    @Test
    fun wrongPinShowsErrorAndKeepsDialogOpen() {
        rule.onNodeWithContentDescription("Edit API host").performClick()
        rule.onNodeWithContentDescription("PIN input").performTextInput("000000")
        rule.onNodeWithText("Confirm").performClick()
        rule.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        rule.onNodeWithText("Enter PIN").assertIsDisplayed()
    }

    @Test
    fun correctPinDismissesDialog() {
        rule.onNodeWithContentDescription("Edit API host").performClick()
        rule.onNodeWithContentDescription("PIN input").performTextInput(BuildConfig.SETTINGS_PIN)
        rule.onNodeWithText("Confirm").performClick()
        rule.onNodeWithText("Enter PIN").assertDoesNotExist()
    }

    @Test
    fun cancelDismissesDialog() {
        rule.onNodeWithContentDescription("Edit API host").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Enter PIN").assertDoesNotExist()
    }

    @Test
    fun resetAfterUnlockRestoresDefaults() {
        // Unlock with correct PIN
        rule.onNodeWithContentDescription("Edit API host").performClick()
        rule.onNodeWithContentDescription("PIN input").performTextInput(BuildConfig.SETTINGS_PIN)
        rule.onNodeWithText("Confirm").performClick()
        // Change host and port, then reset
        vm.setApiHost("https://changed.example.com")
        vm.setApiPort("9999")
        rule.onNodeWithText("Reset").performClick()
        // Verify defaults are restored in the UI
        rule.onNodeWithText(BuildConfig.SCAN_API_HOST).assertIsDisplayed()
    }
}
