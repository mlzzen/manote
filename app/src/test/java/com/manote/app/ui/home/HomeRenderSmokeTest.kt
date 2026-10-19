package com.manote.app.ui.home

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.DatedNet
import com.manote.app.domain.DayNet
import com.manote.app.domain.MonthSummary
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.countPixelsNear
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.renderScreen
import com.manote.app.ui.theme.WinContainerLight
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.YearMonth

/**
 * 首页新增两段的渲染冒烟测试：近 10 场柱状图与本月的月历。
 *
 * 这两段都是「一堆小方块」，纯靠尺寸算出来的，最怕的是格子挤在一起、
 * 或者高到把月历挤出首屏。这里真画一遍，确认：
 * - 月历格子里铺开了赢的颜色（说明那一天有记录、且颜色是照着输赢给的）；
 * - 页面在 2600px 的高度内仍然画得出这两段（若被挤出组合范围，这里就是白的）。
 *
 * PNG 落在 `build/screenshots/render-home-charts.png`。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeRenderSmokeTest {

    private val width = 784
    private val height = 2600

    @Test
    fun home_drawsBarChartAndMonthCalendar() {
        val bitmap = renderScreen(width, height, "render-home-charts.png") {
            HomeContent(
                state = state(),
                onOpenSessions = {},
                onRecordSession = {},
                onOpenSessionDetail = {},
                onOpenCalendar = {},
                today = LocalDate.of(2026, 10, 19),
            )
        }

        assertTrue(
            "首页里没有一块赢的颜色：柱状图或月历多半没画出来",
            bitmap.countPixelsNear(WinContainerLight) > 200,
        )
    }

    /** 10 月的月历与最近 8 场，数值与预览同一套，便于两处对照。 */
    private fun state(): HomeUiState {
        val days = mapOf(
            LocalDate.of(2026, 10, 3) to DayNet(LocalDate.of(2026, 10, 3), 4_200L, 1),
            LocalDate.of(2026, 10, 5) to DayNet(LocalDate.of(2026, 10, 5), -3_100L, 1),
            LocalDate.of(2026, 10, 9) to DayNet(LocalDate.of(2026, 10, 9), 1_250L, 1),
            LocalDate.of(2026, 10, 12) to DayNet(LocalDate.of(2026, 10, 12), -800L, 1),
        )
        return HomeUiState(
            loading = false,
            statistics = PlayerStatistics(
                sessionCount = 4,
                winCount = 2,
                lossCount = 2,
                drawCount = 0,
                winMinor = 5_450L,
                lossMinor = 3_900L,
            ),
            month = MonthSummary(
                yearMonth = YearMonth.of(2026, 10),
                days = days.toSortedMap(),
                statistics = PlayerStatistics.of(days.values.map { it.netMinor }),
                bestDay = days.values.maxByOrNull { it.netMinor },
                worstDay = days.values.minByOrNull { it.netMinor },
            ),
            recentBars = listOf(
                DatedNet(1_786_000_000_000L, -2_600L),
                DatedNet(1_786_400_000_000L, 900L),
                DatedNet(1_786_800_000_000L, 2_600L),
                DatedNet(1_787_200_000_000L, -200L),
                DatedNet(1_787_600_000_000L, -4_100L),
                DatedNet(1_788_000_000_000L, 0L),
                DatedNet(1_788_400_000_000L, 1_500L),
                DatedNet(1_788_800_000_000L, -3_400L),
            ),
            recentSessions = listOf(
                SessionRowUi(
                    id = "1",
                    title = "周五晚局",
                    note = "",
                    playedAt = 1_788_800_000_000L,
                    timeSlot = TimeSlot.EVENING,
                    selfNetMinor = -3_400L,
                    opponentNames = listOf("老王", "小李", "小陈"),
                ),
            ),
        )
    }
}
