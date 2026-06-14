package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `test financial tracker screen`() {
    composeTestRule.waitForIdle()
    println(composeTestRule.onRoot().printToString())

    // Click tabs to see if it crashes
    composeTestRule.onNodeWithTag("nav_transaksi_baru").performClick()
    composeTestRule.waitForIdle()

    // Fill in the form
    composeTestRule.onNodeWithTag("form_amount_input").performTextInput("150000")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("form_description_input").performTextInput("Makan Malam Istimewa")
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("form_notes_input").performTextInput("Makan bareng keluarga besar")
    composeTestRule.waitForIdle()

    // Save
    composeTestRule.onNodeWithTag("form_submit_button").performClick()
    composeTestRule.waitForIdle()

    // Confirm we are back to Beranda / it doesn't crash on saving
    composeTestRule.onNodeWithTag("nav_beranda").performClick()
    composeTestRule.waitForIdle()
  }
}
