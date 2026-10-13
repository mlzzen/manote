package com.manote.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.theme.ManoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI 层（`ui/components`）的回归测试。
 *
 * 这一层原先一条测试都没有：所有单测都落在 domain 与 ui-model 的纯函数上，
 * 而「颜色之外还要有文字」「禁用态不能点」这类约束恰恰只在组件里成立。
 * 真机仪器测试在本项目的开发环境跑不起来（见 README「当前状态」），
 * 因此这里用 Robolectric 在 JVM 上把组件真的渲染出来再断言。
 *
 * 只断言「用户能读到什么」，不比对像素：颜色是辅助线索，规格 3.6 要求
 * 输赢必须有文字表达，所以输赢一律断文字。
 */
@RunWith(AndroidJUnit4::class)
// 语言必须钉死：应用有两套文案（values / values-en），Robolectric 默认按 en-US 取资源，
// 断言中文而机器上是英文就会全挂——这与「单元测试不得依赖跑测机器的时区」是同一条纪律。
@Config(sdk = [33], qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ManoteComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun resultBadge_showsTextAlongsideColor() {
        // 规格 2.1、3.6：颜色只是辅助，文字必须在场
        composeRule.setContent {
            ManoteTheme {
                Column {
                    ResultBadge(netMinor = 1_230L)
                    ResultBadge(netMinor = -500L)
                    ResultBadge(netMinor = 0L)
                }
            }
        }

        composeRule.onNodeWithText("赢").assertIsDisplayed()
        composeRule.onNodeWithText("输").assertIsDisplayed()
        composeRule.onNodeWithText("持平").assertIsDisplayed()
    }

    @Test
    fun signedAmount_printsSignSoColorIsNotTheOnlyCue() {
        composeRule.setContent {
            ManoteTheme {
                Column {
                    SignedAmount(netMinor = 1_230L)
                    SignedAmount(netMinor = -500L)
                    SignedAmount(netMinor = 0L)
                }
            }
        }

        composeRule.onNodeWithText("+¥12.30").assertIsDisplayed()
        composeRule.onNodeWithText("-¥5.00").assertIsDisplayed()
        composeRule.onNodeWithText("¥0.00").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en")
    fun timeSlotTag_usesTheEnglishCopyWhenTheDeviceIsEnglish() {
        // 英文界面不是「留空回落到中文」：资源真的被换掉了，这里顺带守住 values-en 的存在
        composeRule.setContent {
            ManoteTheme {
                TimeSlotTag(slot = TimeSlot.MORNING)
            }
        }

        composeRule.onNodeWithText("Morning").assertIsDisplayed()
    }

    @Test
    fun timeSlotTag_showsTheSlotName() {
        composeRule.setContent {
            ManoteTheme {
                Column {
                    TimeSlotTag(slot = TimeSlot.MORNING)
                    TimeSlotTag(slot = TimeSlot.AFTERNOON)
                    TimeSlotTag(slot = TimeSlot.EVENING)
                }
            }
        }

        composeRule.onNodeWithText("早场").assertIsDisplayed()
        composeRule.onNodeWithText("下午场").assertIsDisplayed()
        composeRule.onNodeWithText("晚场").assertIsDisplayed()
    }

    @Test
    fun playerAvatar_neverLeavesTheTileBlank() {
        composeRule.setContent {
            ManoteTheme {
                Column {
                    PlayerAvatar(name = "老王")
                    PlayerAvatar(name = "")
                }
            }
        }

        composeRule.onNodeWithText("老").assertIsDisplayed()
        // 空昵称回落成「?」，而不是留一个空方块
        composeRule.onNodeWithText("?").assertIsDisplayed()
    }

    @Test
    fun emptyState_showsTitleAndDescription() {
        composeRule.setContent {
            ManoteTheme {
                ManoteEmptyState(
                    icon = Icons.Filled.Delete,
                    title = "这里还没有场次",
                    description = "换个筛选条件看看",
                )
            }
        }

        composeRule.onNodeWithText("这里还没有场次").assertIsDisplayed()
        composeRule.onNodeWithText("换个筛选条件看看").assertIsDisplayed()
    }

    @Test
    fun filterChip_reportsClicks() {
        var clicks = 0
        composeRule.setContent {
            ManoteTheme {
                ManoteFilterChip(
                    label = "本月",
                    selected = false,
                    onClick = { clicks += 1 },
                )
            }
        }

        composeRule.onNodeWithText("本月").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun primaryButton_disabledIsNotClickable() {
        var clicks = 0
        composeRule.setContent {
            ManoteTheme {
                ManotePrimaryButton(
                    text = "保存整场结果",
                    onClick = { clicks += 1 },
                    enabled = false,
                )
            }
        }

        composeRule.onNodeWithText("保存整场结果").assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun topBar_withBackButton_showsTitleAndSendsBack() {
        var backs = 0
        composeRule.setContent {
            ManoteTheme {
                ManoteTopBar(
                    title = "回收站",
                    onNavigateBack = { backs += 1 },
                )
            }
        }

        composeRule.onNodeWithText("回收站").assertIsDisplayed()
        // 返回按钮必须带可访问标签，读屏用户才知道它是干什么的（规格 3.6）
        composeRule.onNodeWithContentDescription("返回").performClick()

        assertEquals(1, backs)
    }

    @Test
    fun readout_showsLabelValueAndCaption() {
        composeRule.setContent {
            ManoteTheme {
                ManoteReadout(
                    label = "场均",
                    value = "+¥12.30",
                    valueColor = Color.Unspecified,
                    caption = "10.05 · 2 场",
                )
            }
        }

        composeRule.onNodeWithText("场均").assertIsDisplayed()
        composeRule.onNodeWithText("+¥12.30").assertIsDisplayed()
        composeRule.onNodeWithText("10.05 · 2 场").assertIsDisplayed()
    }

    @Test
    fun animatedSignedYuan_settlesOnTheTargetValue() {
        // 动画的结束帧必须精确落在目标值上，否则英雄卡上的数字会一直差一点
        composeRule.setContent {
            ManoteTheme {
                AnimatedSignedYuan(netMinor = 1_230L)
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("+¥12.30").assertIsDisplayed()
    }

    @Test
    fun pill_rendersItsText() {
        composeRule.setContent {
            ManoteTheme {
                ManotePill(text = "已记 5 场")
            }
        }

        composeRule.onNodeWithText("已记 5 场").assertIsDisplayed()
    }
}
