package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testChatNavigationAndInput() {
        composeTestRule.waitForIdle()

        // 1. Click bottom navigation tab "Chat"
        composeTestRule.onNodeWithTag("nav_chat").performClick()
        composeTestRule.waitForIdle()

        // 2. Verify chat list and input field exist
        composeTestRule.onNodeWithTag("chat_messages_list").assertExists()
        composeTestRule.onNodeWithTag("chat_input_field").assertExists()

        // 3. Enter a standard transaction query
        composeTestRule.onNodeWithTag("chat_input_field").performTextInput("makan nasi goreng 25000")
        composeTestRule.waitForIdle()

        // 4. Verify send button is present and click it
        composeTestRule.onNodeWithTag("chat_send_button").assertExists()
        composeTestRule.onNodeWithTag("chat_send_button").performClick()
        composeTestRule.waitForIdle()
    }
}
