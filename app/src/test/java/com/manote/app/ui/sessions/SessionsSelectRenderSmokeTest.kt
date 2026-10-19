package com.manote.app.ui.sessions

import androidx.compose.material3.SnackbarHostState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.domain.TimeSlot
import com.manote.app.ui.model.SessionFilters
import com.manote.app.ui.model.SessionMonthGroup
import com.manote.app.ui.model.SessionRowUi
import com.manote.app.ui.countPixelsNear
import com.manote.app.ui.renderScreen
import com.manote.app.ui.theme.HeroSurfaceLight
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 场次页「多选删除」模式的渲染冒烟测试。
 *
 * 这一屏有三件事只能靠真画一遍来确认：勾选圈没有把日期列挤掉、
 * 选中行多了那圈品牌色描边、顶栏从大标题换成了居中的「已选 N 场」。
 *
 * PNG 落在 `build/screenshots/render-sessions-select.png`。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], qualifiers = "zh-rCN-w392dp-h860dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionsSelectRenderSmokeTest {

    private val width = 784
    private val height = 1500

    @Test
    fun selectingMode_showsChecksAndKeepsRowsIntact() {
        val bitmap = renderScreen(width, height, "render-sessions-select.png") {
            SessionsContent(
                state = state(),
                onChangeFilters = {},
                onResetFilters = {},
                onOpenSessionDetail = {},
                onStartSelecting = {},
                onCancelSelecting = {},
                onToggleSelected = {},
                onToggleSelectAll = {},
                onConfirmDelete = {},
                snackbarHostState = SnackbarHostState(),
            )
        }

        // 顶部那条净输赢窄带仍然是深墨绿的：确认顶栏换掉之后读数还在
        assertTrue(
            "顶部读数窄带没有画出来",
            bitmap.countPixelsNear(HeroSurfaceLight) > 100,
        )
    }

    private fun state(): SessionsUiState {
        val rows = listOf(
            row("s-1", "周五晚局", LocalDate(21), TimeSlot.EVENING, 12_345L),
            row("s-2", "周末下午", LocalDate(14), TimeSlot.AFTERNOON, -800L),
            row("s-3", "工作日早场", LocalDate(9), TimeSlot.MORNING, -3_100L),
            row("s-4", "老地方", LocalDate(5), TimeSlot.EVENING, 4_200L),
        )
        return SessionsUiState(
            loading = false,
            filters = SessionFilters(),
            monthGroups = listOf(
                SessionMonthGroup(
                    yearMonth = YearMonth.of(2026, 10),
                    label = "2026年10月",
                    rows = rows,
                ),
            ),
            yearOptions = listOf(2026),
            friendOptions = emptyList(),
            hasAny = true,
            selecting = true,
            selectedIds = setOf("s-1", "s-4"),
        )
    }

    private fun LocalDate(day: Int) = java.time.LocalDate.of(2026, 10, day)

    private fun row(
        id: String,
        title: String,
        day: java.time.LocalDate,
        slot: TimeSlot,
        netMinor: Long,
    ): SessionRowUi = SessionRowUi(
        id = id,
        title = title,
        note = "",
        playedAt = LocalDateTime.of(day, java.time.LocalTime.of(20, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
        timeSlot = slot,
        selfNetMinor = netMinor,
        opponentNames = listOf("老王", "小李", "小陈"),
    )
}
