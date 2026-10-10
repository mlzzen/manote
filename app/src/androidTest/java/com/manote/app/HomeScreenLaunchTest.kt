package com.manote.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P0 验收：应用冷启动后进入首页，并可从首页进入「记一场」。
 * 需要设备或模拟器，属 connectedDebugAndroidTest，不能用单元测试替代。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_onHomeScreen() {
        composeRule.onNodeWithText("麻记").assertIsDisplayed()
        composeRule.onNodeWithText("记一场").assertIsDisplayed()
        composeRule.onNodeWithText("我的历史").assertIsDisplayed()
        composeRule.onNodeWithText("麻友").assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }

    @Test
    fun recordEntry_navigatesToSessionScreen() {
        composeRule.onNodeWithText("记一场").performClick()

        // 记一场页顶栏带返回图标按钮（可访问标签「返回」）。
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
    }
}
