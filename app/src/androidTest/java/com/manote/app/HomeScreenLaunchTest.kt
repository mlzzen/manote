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
 * 冷启动验收：应用进入首页，并可从首页进入「记一场」。
 * 需要设备或模拟器，属 connectedDebugAndroidTest，不能用单元测试替代。
 *
 * 注意：首页顶栏标题「首页」与底部导航标签「首页」文本相同，
 * 因此这里只断言互不重复的文案，避免多节点匹配导致的歧义失败。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_onHomeScreen() {
        composeRule.onNodeWithText("战绩概览").assertIsDisplayed()
        composeRule.onNodeWithText("最近场次").assertIsDisplayed()
        composeRule.onNodeWithText("记一场").assertIsDisplayed()

        // 底部导航的四个页签都在
        composeRule.onNodeWithText("场次").assertIsDisplayed()
        composeRule.onNodeWithText("牌友").assertIsDisplayed()
        composeRule.onNodeWithText("我的").assertIsDisplayed()
    }

    @Test
    fun recordEntry_navigatesToSessionScreen() {
        composeRule.onNodeWithText("记一场").performClick()

        // 记一场页顶栏带返回图标按钮（可访问标签「返回」）。
        composeRule.onNodeWithContentDescription("返回").assertIsDisplayed()
    }
}
