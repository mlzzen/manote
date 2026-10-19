package com.manote.app.ui.model

import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.domain.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 场次筛选（时段 / 年 / 月 / 日 / 牌友 / 输赢）。
 *
 * 时区是这里最容易出错的地方：列表显示用的是设备时区，
 * 因此年月日必须按同一时区判断，否则月末月初会出现「显示 10 月却被筛掉」。
 */
class SessionFilteringTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun row(
        id: String,
        playedAt: Long,
        title: String = "麻将对局",
        names: List<String> = emptyList(),
        ids: List<String> = emptyList(),
        netMinor: Long = 0L,
        slot: TimeSlot = TimeSlot.EVENING,
    ) = SessionRowUi(
        id = id,
        title = title,
        note = "",
        playedAt = playedAt,
        timeSlot = slot,
        selfNetMinor = netMinor,
        opponentNames = names,
        opponentPlayerIds = ids,
        // 与 SessionWithResults.toRowUi() 的拼法保持一致
    )

    private val october = row("oct", at(2026, 10, 5), netMinor = 2_000L)
    private val september = row("sep", at(2026, 9, 28), netMinor = -2_000L)
    private val august = row("aug", at(2025, 8, 15), netMinor = 0L)
    private val all = listOf(october, september, august)

    @Test
    fun emptyFilters_keepEverythingAndOrder() {
        assertEquals(all, SessionFiltering.apply(all, SessionFilters(), zone))
        assertFalse(SessionFilters().isActive)
    }

    // ————————————————————— 时段 —————————————————————

    @Test
    fun timeSlots_matchAnySelectedSlot() {
        val morning = row("m", at(2026, 10, 5), slot = TimeSlot.MORNING)
        val evening = row("e", at(2026, 10, 5), slot = TimeSlot.EVENING)
        val afternoon = row("a", at(2026, 10, 5), slot = TimeSlot.AFTERNOON)

        val result = SessionFiltering.apply(
            listOf(morning, evening, afternoon),
            SessionFilters(timeSlots = setOf(TimeSlot.MORNING, TimeSlot.EVENING)),
            zone,
        )

        assertEquals("组内多选是「或」", listOf("m", "e"), result.map { it.id })
    }

    // ————————————————————— 年 / 月 / 日 —————————————————————

    @Test
    fun year_keepsOnlyThatYear() {
        val result = SessionFiltering.apply(all, SessionFilters(year = 2026), zone)
        assertEquals(listOf("oct", "sep"), result.map { it.id })
    }

    @Test
    fun month_alone_spansEveryYear() {
        val result = SessionFiltering.apply(all, SessionFilters(month = 8), zone)
        assertEquals(listOf("aug"), result.map { it.id })
    }

    @Test
    fun yearAndMonth_narrowTogether() {
        val result = SessionFiltering.apply(all, SessionFilters(year = 2025, month = 8), zone)
        assertEquals(listOf("aug"), result.map { it.id })
    }

    @Test
    fun date_keepsOnlyThatDay() {
        val sameDayLate = row("late", at(2026, 10, 5, hour = 23, minute = 30))
        val nextDay = row("next", at(2026, 10, 6, hour = 0, minute = 10))

        val result = SessionFiltering.apply(
            listOf(october, sameDayLate, nextDay),
            SessionFilters(date = LocalDate.of(2026, 10, 5)),
            zone,
        )

        assertEquals(listOf("oct", "late"), result.map { it.id })
    }

    @Test
    fun withDate_fillsYearAndMonthSoChipsAgree() {
        val filters = SessionFilters().withDate(LocalDate.of(2026, 10, 5))

        assertEquals(2026, filters.year)
        assertEquals(10, filters.month)
        assertEquals(LocalDate.of(2026, 10, 5), filters.date)
    }

    @Test
    fun withYear_orMonth_clearsTheChosenDay() {
        // 年份换了，原来那天自然不再成立；但清掉「某一天」时年、月要留着，
        // 于是「10 月 5 日」可以退回到「整个 10 月」。
        val picked = SessionFilters().withDate(LocalDate.of(2026, 10, 5))

        assertEquals(null, picked.withYear(2025).date)
        assertEquals(null, picked.withMonth(9).date)
        assertEquals(10, picked.withDate(null).month)
        assertEquals(2026, picked.withDate(null).year)
    }

    @Test
    fun monthBoundary_usesDeviceTimeZoneNotUtc() {
        // 2026-09-30 23:00（东八区）按 UTC 仍是 9 月 30 日 15:00；
        // 2026-10-01 00:30（东八区）按 UTC 才是 9 月 30 日，若按 UTC 判月会被误判成 9 月。
        val lateSeptember = row("late-sep", at(2026, 9, 30, hour = 23, minute = 0))
        val earlyOctober = row("early-oct", at(2026, 10, 1, hour = 0, minute = 30))
        val rows = listOf(earlyOctober, lateSeptember)

        val result = SessionFiltering.apply(rows, SessionFilters(year = 2026, month = 10), zone)

        assertEquals("东八区 10-01 00:30 属于 10 月", listOf("early-oct"), result.map { it.id })
    }

    // ————————————————————— 牌友 —————————————————————

    @Test
    fun friends_matchWhenAnySelectedPlayerWasSeated() {
        val withWang = row("wang", at(2026, 10, 5), names = listOf("老王", "小李"), ids = listOf("p-wang", "p-li"))
        val withChen = row("chen", at(2026, 10, 4), names = listOf("小陈"), ids = listOf("p-chen"))
        val rows = listOf(withWang, withChen)

        assertEquals(
            listOf("wang", "chen"),
            SessionFiltering.apply(rows, SessionFilters(friendIds = setOf("p-wang", "p-chen")), zone)
                .map { it.id },
        )
        assertEquals(
            listOf("chen"),
            SessionFiltering.apply(rows, SessionFilters(friendIds = setOf("p-chen")), zone).map { it.id },
        )
    }

    @Test
    fun friends_matchById_notByNickname() {
        // 麻友改过名：场次里存的是旧昵称「王哥」，档案现在叫「老王」。
        // 按 ID 筛仍然命中——按名字筛会漏掉他自己那些旧场次。
        val renamed = row("old", at(2026, 10, 5), names = listOf("王哥"), ids = listOf("p-wang"))
        val result = SessionFiltering.apply(listOf(renamed), SessionFilters(friendIds = setOf("p-wang")), zone)
        assertEquals(listOf("old"), result.map { it.id })
    }

    // ————————————————————— 输赢 —————————————————————

    @Test
    fun outcomes_splitByNetSign() {
        val rows = listOf(
            row("win", at(2026, 10, 5), netMinor = 100L),
            row("loss", at(2026, 10, 4), netMinor = -100L),
            row("draw", at(2026, 10, 3), netMinor = 0L),
        )

        assertEquals(
            listOf("win", "draw"),
            SessionFiltering.apply(rows, SessionFilters(outcomes = setOf(SessionOutcome.WIN, SessionOutcome.DRAW)), zone)
                .map { it.id },
        )
        assertEquals(
            listOf("loss"),
            SessionFiltering.apply(rows, SessionFilters(outcomes = setOf(SessionOutcome.LOSS)), zone).map { it.id },
        )
    }

    // ————————————————————— 组合 —————————————————————

    @Test
    fun groupsCombineWithAnd() {
        val target = row(
            "target",
            at(2026, 10, 5),
            ids = listOf("p-wang"),
            netMinor = 500L,
            slot = TimeSlot.EVENING,
        )
        val wrongSlot = target.copy(id = "slot", timeSlot = TimeSlot.MORNING)
        val wrongFriend = target.copy(id = "friend", opponentPlayerIds = listOf("p-li"))
        val wrongOutcome = target.copy(id = "lost", selfNetMinor = -500L)
        val wrongMonth = target.copy(id = "month", playedAt = at(2026, 9, 5))
        val rows = listOf(target, wrongSlot, wrongFriend, wrongOutcome, wrongMonth)

        val result = SessionFiltering.apply(
            rows,
            SessionFilters(
                timeSlots = setOf(TimeSlot.EVENING),
                year = 2026,
                month = 10,
                friendIds = setOf("p-wang"),
                outcomes = setOf(SessionOutcome.WIN),
            ),
            zone,
        )

        assertEquals(listOf("target"), result.map { it.id })
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertEquals(emptyList<SessionRowUi>(), SessionFiltering.apply(emptyList(), SessionFilters(), zone))
        assertEquals(
            emptyList<SessionRowUi>(),
            SessionFiltering.apply(emptyList(), SessionFilters(year = 2026), zone),
        )
    }

    // ————————————————————— 弹层选项 —————————————————————

    @Test
    fun yearOptions_areDistinctAndNewestFirst() {
        val rows = all + row("older", at(2024, 3, 1)) + row("same-year", at(2025, 1, 1))
        assertEquals(listOf(2026, 2025, 2024), SessionFiltering.yearOptions(rows, zone))
    }

    @Test
    fun friendOptions_preferCurrentNicknameAndCountUsage() {
        val rows = listOf(
            row("a", at(2026, 10, 5), names = listOf("王哥"), ids = listOf("p-wang")),
            row("b", at(2026, 10, 4), names = listOf("老王", "小李"), ids = listOf("p-wang", "p-li")),
        )
        val friends = listOf(friend(id = "p-wang", nickname = "老王"), friend(id = "p-li", nickname = "小李"))

        val options = SessionFiltering.friendOptions(rows, friends)

        assertEquals("同桌次数多的在前", listOf("p-wang", "p-li"), options.map { it.id })
        assertEquals("用档案里的当前昵称", "老王", options.first().nickname)
        assertFalse("不同名时不补 ID", options.first().showId)
    }

    @Test
    fun friendOptions_fallBackToSnapshotWhenProfileMissing() {
        val rows = listOf(row("a", at(2026, 10, 5), names = listOf("阿强"), ids = listOf("p-gone")))
        val options = SessionFiltering.friendOptions(rows, friends = emptyList())
        assertEquals("阿强", options.single().nickname)
    }

    @Test
    fun friendOptions_flagDuplicateNicknames() {
        val rows = listOf(
            row("a", at(2026, 10, 5), names = listOf("老王"), ids = listOf("p-1")),
            row("b", at(2026, 10, 4), names = listOf("老王"), ids = listOf("p-2")),
        )
        val friends = listOf(friend(id = "p-1", nickname = "老王"), friend(id = "p-2", nickname = "老王"))

        val options = SessionFiltering.friendOptions(rows, friends)

        assertTrue("同名牌友必须补 ID 短串", options.all { it.showId })
    }

    private fun friend(id: String, nickname: String) = PlayerEntity(
        id = id,
        kind = PlayerKind.FRIEND,
        nickname = nickname,
        note = "",
        archivedAt = null,
        createdAt = 0L,
        updatedAt = 0L,
    )
}
