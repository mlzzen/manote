package com.manote.app.ui.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.DayNet
import com.manote.app.domain.MonthSummary
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.countPixelsNear
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.renderScreen
import com.manote.app.ui.theme.HeroSurfaceLight
import com.manote.app.ui.theme.WinContainerLight
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 月历页的渲染冒烟测试。
 *
 * 这一页是纯几何：年月两颗切换器、6×7 的格子、英雄卡与当月场次。
 * 没有模拟器可跑，只能靠 Robolectric 真画一遍，确认：
 * - 有记录的那几天真的铺上了赢的颜色（不是一屏空白格子）；
 * - 整页没有因为内容超高而把场次列表挤没（底部仍有内容）。
 *
 * PNG 落在 `build/screenshots/render-calendar.png`，需要肉眼核对时直接打开。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarRenderSmokeTest {

    private val width = 784
    private val height = 2200

    @Test
    fun calendarPage_drawsDayCellsAndSessions() {
        val bitmap = renderScreen(width, height, "render-calendar.png") {
            CalendarContent(
                state = state(),
                onSelectMonth = {},
                onCurrentMonth = {},
                onNavigateBack = {},
                onOpenSessionDetail = {},
            )
        }

        // 赢的格子铺的是 WinContainer（淡砖红）。页面背景是暖白的，
        // 与它差得足够远；数到几百个像素就说明格子里真的铺开了颜色。
        assertTrue(
            "月历格子里没有一块赢的颜色，多半是网格没画出来",
            bitmap.countPixelsNear(WinContainerLight) > 200,
        )
        assertTrue(
            "顶部英雄卡没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 200,
        )
    }

    /** 10 月里刻意留出赢、输、持平三种日子，也留出大片空白日子。 */
    private fun state(): CalendarUiState {
        val month = YearMonth.of(2026, 10)
        val days = mapOf(
            LocalDate.of(2026, 10, 3) to DayNet(LocalDate.of(2026, 10, 3), 4_200L, 2),
            LocalDate.of(2026, 10, 5) to DayNet(LocalDate.of(2026, 10, 5), -3_100L, 1),
            LocalDate.of(2026, 10, 9) to DayNet(LocalDate.of(2026, 10, 9), 1_250L, 1),
            LocalDate.of(2026, 10, 12) to DayNet(LocalDate.of(2026, 10, 12), 0L, 1),
            LocalDate.of(2026, 10, 14) to DayNet(LocalDate.of(2026, 10, 14), -800L, 1),
            LocalDate.of(2026, 10, 21) to DayNet(LocalDate.of(2026, 10, 21), 12_345L, 1),
        )
        val nets = days.values.map { it.netMinor }
        val summary = MonthSummary(
            yearMonth = month,
            days = days.toSortedMap(),
            statistics = PlayerStatistics.of(nets),
            bestDay = days.values.maxByOrNull { it.netMinor },
            worstDay = days.values.minByOrNull { it.netMinor },
        )

        return CalendarUiState(
            loading = false,
            month = month,
            summary = summary,
            sessions = listOf(
                row("s-1", "周五晚局", LocalDate.of(2026, 10, 21), TimeSlot.EVENING, 12_345L),
                row("s-2", "周末下午", LocalDate.of(2026, 10, 14), TimeSlot.AFTERNOON, -800L),
                row("s-3", "工作日早场", LocalDate.of(2026, 10, 9), TimeSlot.MORNING, 1_250L),
            ),
            today = LocalDate.of(2026, 10, 19),
            canGoEarlier = true,
            canGoLater = false,
            isCurrentMonth = true,
        )
    }

    private fun row(
        id: String,
        title: String,
        day: LocalDate,
        slot: TimeSlot,
        netMinor: Long,
    ): SessionRowUi {
        val playedAt = LocalDateTime.of(day, java.time.LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return SessionRowUi(
            id = id,
            title = title,
            note = "",
            playedAt = playedAt,
            timeSlot = slot,
            selfNetMinor = netMinor,
            opponentNames = listOf("老王", "小李", "小陈"),
        )
    }
}
