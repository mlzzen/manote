package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 牌友列表的搜索、归档过滤与排序（规格 3.4）。
 *
 * 排序在这里显式实现（而不是只靠 SQL 的 ORDER BY），因此可以用纯单测守护：
 * - 默认是「updatedAt 倒序、同时间按 ID 升序」这条既有不变量；
 * - 三个可排序指标（他的场均、我的场均、他累计的净输赢）各按各的数，
 *   并列按 ID 升序、**没有可谈数字的始终垫底**。
 * 没有这些约束，列表会在重组之间跳动，名次也说不清依据。
 */
class FriendFilteringTest {

    private fun row(
        id: String,
        nickname: String,
        note: String = "",
        sessionCount: Int = 0,
        netMinor: Long = 0L,
        archived: Boolean = false,
        updatedAt: Long = 0L,
        myAverageTogetherMinor: Long? = null,
    ) = FriendRowUi(
        id = id,
        nickname = nickname,
        note = note,
        sessionCount = sessionCount,
        netMinor = netMinor,
        archived = archived,
        updatedAt = updatedAt,
        myAverageTogetherMinor = myAverageTogetherMinor,
    )

    private val aMing = row("f1", "阿明", "戴眼镜", sessionCount = 3, netMinor = 4500L, updatedAt = 300L)
    private val aHua = row("f2", "阿华", "", sessionCount = 1, netMinor = -1200L, updatedAt = 200L)
    private val retired = row("f3", "老陈", "不常来", archived = true, updatedAt = 100L)
    private val all = listOf(aMing, aHua, retired)

    @Test
    fun archivedAreHiddenByDefault() {
        val result = FriendFiltering.apply(all, query = "", includeArchived = false)
        assertEquals(listOf("f1", "f2"), result.map { it.id })
    }

    @Test
    fun includeArchivedShowsThemInPlace() {
        val result = FriendFiltering.apply(all, query = "", includeArchived = true)
        assertEquals(listOf("f1", "f2", "f3"), result.map { it.id })
        assertEquals(true, result.last().archived)
    }

    @Test
    fun searchMatchesNickname() {
        val result = FriendFiltering.apply(all, query = "阿", includeArchived = true)
        assertEquals(listOf("f1", "f2"), result.map { it.id })
    }

    @Test
    fun searchMatchesNote() {
        val result = FriendFiltering.apply(all, query = "眼镜", includeArchived = false)
        assertEquals(listOf("f1"), result.map { it.id })
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimsQuery() {
        val alice = row("x", "Alice", "常来")
        assertEquals(listOf("x"), FriendFiltering.apply(listOf(alice), "  ALI  ", false).map { it.id })
        assertEquals(listOf("x"), FriendFiltering.apply(listOf(alice), "alice", false).map { it.id })
    }

    @Test
    fun blankQueryKeepsEverything() {
        assertEquals(listOf("f1", "f2"), FriendFiltering.apply(all, "", false).map { it.id })
        assertEquals(listOf("f1", "f2"), FriendFiltering.apply(all, "   ", false).map { it.id })
    }

    @Test
    fun searchDoesNotResurrectArchivedWhenSwitchIsOff() {
        // 归档开关优先于搜索：即使关键词命中，已归档且未开启开关的麻友也不出现
        assertEquals(emptyList<String>(), FriendFiltering.apply(all, "老陈", false).map { it.id })
        assertEquals(listOf("f3"), FriendFiltering.apply(all, "老陈", true).map { it.id })
    }

    @Test
    fun sortsByUpdatedAtDescending() {
        val older = row("old", "早", updatedAt = 100L)
        val newer = row("new", "晚", updatedAt = 900L)

        val result = FriendFiltering.apply(listOf(older, newer), "", false)

        assertEquals(listOf("new", "old"), result.map { it.id })
    }

    @Test
    fun tiesOnUpdatedAtAreBrokenByIdSoOrderIsStable() {
        // 同一批导入的数据 updatedAt 相同，必须按 ID 升序给出确定顺序
        val sameTime = listOf(
            row("b", "乙", updatedAt = 500L),
            row("a", "甲", updatedAt = 500L),
            row("c", "丙", updatedAt = 500L),
        )

        val result = FriendFiltering.apply(sameTime, "", false)

        assertEquals(listOf("a", "b", "c"), result.map { it.id })
    }

    @Test
    fun emptyInputProducesEmptyOutput() {
        assertEquals(emptyList<FriendRowUi>(), FriendFiltering.apply(emptyList(), "", false))
        assertEquals(emptyList<FriendRowUi>(), FriendFiltering.apply(emptyList(), "阿", true))
    }

    // ————————————————————— 排序 —————————————————————

    // 刻意让三个指标给出**三种不同的名次**，否则测不出它们真的各按各的算：
    //   净额： steady +20_000 > rich +10_000 > loser -8_000
    //   场均： rich +5_000 > steady +2_000 > loser -2_000
    //   我的场均： loser +1_500 > steady -500 > rich -3_000
    //     （他在桌上我反而打得好的，正是那位「常输」）
    //   blank 最近编辑过，但一场都没打过，三个榜都垫底。
    private val rich = row(
        id = "rich",
        nickname = "大赢家",
        sessionCount = 2,
        netMinor = 10_000L,
        updatedAt = 100L,
        myAverageTogetherMinor = -3_000L,
    )
    private val steady = row(
        id = "steady",
        nickname = "小赢",
        sessionCount = 10,
        netMinor = 20_000L,
        updatedAt = 200L,
        myAverageTogetherMinor = -500L,
    )
    private val loser = row(
        id = "loser",
        nickname = "常输",
        sessionCount = 4,
        netMinor = -8_000L,
        updatedAt = 300L,
        myAverageTogetherMinor = 1_500L,
    )
    private val blank = row("blank", "还没打过", sessionCount = 0, netMinor = 0L, updatedAt = 999L)
    private val mixed = listOf(rich, steady, loser, blank)

    @Test
    fun sortsByAverageDescending() {
        // 场均：rich +5000、steady +2000、loser -2000，没打过的垫底
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.AVERAGE_DESC)

        assertEquals(listOf("rich", "steady", "loser", "blank"), result.map { it.id })
    }

    @Test
    fun sortsByAverageAscending() {
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.AVERAGE_ASC)

        assertEquals(listOf("loser", "steady", "rich", "blank"), result.map { it.id })
    }

    @Test
    fun sortsByNetDescending() {
        // 净额：steady 最高，rich 次之——与场均的顺序不同，说明两者各按各的算
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.NET_DESC)

        assertEquals(listOf("steady", "rich", "loser", "blank"), result.map { it.id })
    }

    @Test
    fun sortsByNetAscending() {
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.NET_ASC)

        assertEquals(listOf("loser", "rich", "steady", "blank"), result.map { it.id })
    }

    @Test
    fun sortsByMyAverageDescending() {
        // 我的场均：loser +1500、steady -500、rich -3000，没打过的垫底。
        // 这个顺序与上面「他的场均」「净额」都不同，三者确实是三个数。
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.MY_AVERAGE_DESC)

        assertEquals(listOf("loser", "steady", "rich", "blank"), result.map { it.id })
    }

    @Test
    fun sortsByMyAverageAscending() {
        val result = FriendFiltering.apply(mixed, "", false, FriendSort.MY_AVERAGE_ASC)

        assertEquals(listOf("rich", "steady", "loser", "blank"), result.map { it.id })
    }

    @Test
    fun myAverageSortLooksAtMyNumbersNotHis() {
        // 常赢的那位自己赚得最多，但我在他面前是输的；常输的那位自己亏最多，
        // 我在他面前反而是赢的。「我的场均」必须把后者排在前面——
        // 若把两侧的净额写反，这条断言会立刻红。
        val shark = row(
            id = "shark",
            nickname = "常赢",
            sessionCount = 5,
            netMinor = 25_000L,
            myAverageTogetherMinor = 100L,
        )
        val fish = row(
            id = "fish",
            nickname = "常输",
            sessionCount = 5,
            netMinor = -20_000L,
            myAverageTogetherMinor = 2_000L,
        )

        assertEquals(
            listOf("fish", "shark"),
            FriendFiltering.apply(listOf(shark, fish), "", false, FriendSort.MY_AVERAGE_DESC)
                .map { it.id },
        )
        // 换成「他的场均」，名次正好反过来——两个排序读的是两边的数
        assertEquals(
            listOf("shark", "fish"),
            FriendFiltering.apply(listOf(shark, fish), "", false, FriendSort.AVERAGE_DESC)
                .map { it.id },
        )
    }

    @Test
    fun friendsIHaveNeverPlayedWithGoLastInMyAverageSort() {
        // 他打过牌，只是没和我同过场：这一格是 null，同样垫底，
        // 而不是被当成「我的场均 0 元」挤进榜单中段
        val stranger = row(
            id = "stranger",
            nickname = "没同场过",
            sessionCount = 6,
            netMinor = 9_000L,
            updatedAt = 999L,
        )
        val withMe = row(
            id = "withMe",
            nickname = "同场过",
            sessionCount = 6,
            netMinor = -9_000L,
            myAverageTogetherMinor = -5_000L,
        )

        val result = FriendFiltering.apply(listOf(stranger, withMe), "", false, FriendSort.MY_AVERAGE_DESC)

        assertEquals(listOf("withMe", "stranger"), result.map { it.id })
    }

    @Test
    fun unrankedFriendsGoLastWhateverTheDirection() {
        // 还没有可谈数字的牌友不该挤进榜单中段——他不是「场均 0 元」，而是还没有这个数。
        // 他更新最新（updatedAt = 999），依然排到最后。
        FriendSort.entries.filter { it != FriendSort.RECENT }.forEach { sort ->
            assertEquals(
                "$sort 下没有数字的都该垫底",
                "blank",
                FriendFiltering.apply(mixed, "", false, sort).last().id,
            )
        }
    }

    @Test
    fun severalUnrankedFriendsAreOrderedById() {
        val alsoBlank = row("aaa", "也没打过", sessionCount = 0, updatedAt = 5L)

        val result = FriendFiltering.apply(listOf(blank, alsoBlank), "", false, FriendSort.AVERAGE_DESC)

        assertEquals(listOf("aaa", "blank"), result.map { it.id })
    }

    @Test
    fun tiesOnTheChosenMetricAreBrokenByIdSoOrderIsStable() {
        val sameNumbers = listOf(
            row("b", "乙", sessionCount = 3, netMinor = 900L),
            row("a", "甲", sessionCount = 3, netMinor = 900L),
            row("c", "丙", sessionCount = 3, netMinor = 900L),
        )

        assertEquals(
            listOf("a", "b", "c"),
            FriendFiltering.apply(sameNumbers, "", false, FriendSort.AVERAGE_DESC).map { it.id },
        )
        assertEquals(
            listOf("a", "b", "c"),
            FriendFiltering.apply(sameNumbers, "", false, FriendSort.NET_ASC).map { it.id },
        )
    }

    @Test
    fun sortingIsAppliedAfterTheArchivedAndSearchFilters() {
        val archived = row("gone", "老陈", archived = true, sessionCount = 5, netMinor = 9_000L)
        val rows = mixed + archived

        // 已归档默认不出现，哪怕它的净额排得上号
        assertEquals(
            listOf("steady", "rich", "loser", "blank"),
            FriendFiltering.apply(rows, "", false, FriendSort.NET_DESC).map { it.id },
        )

        // 打开开关后它按净额插进该在的位置
        assertEquals(
            listOf("steady", "rich", "gone", "loser", "blank"),
            FriendFiltering.apply(rows, "", true, FriendSort.NET_DESC).map { it.id },
        )
    }

    @Test
    fun defaultSortIsStillMostRecentlyEdited() {
        // 没有显式指定排序时，仍是列表一直以来的顺序，不因为新增排序而改掉默认行为
        val result = FriendFiltering.apply(mixed, "", false)

        assertEquals(listOf("blank", "loser", "steady", "rich"), result.map { it.id })
    }
}
