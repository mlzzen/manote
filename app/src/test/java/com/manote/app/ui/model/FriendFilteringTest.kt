package com.manote.app.ui.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 牌友列表的搜索、归档过滤与排序（规格 3.4）。
 *
 * 排序在这里显式实现（而不是只靠 SQL 的 ORDER BY），因此可以用纯单测守护
 * 「updatedAt 倒序、同时间按 ID 升序」这条不变量——否则列表会在重组之间跳动。
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
    ) = FriendRowUi(
        id = id,
        nickname = nickname,
        note = note,
        sessionCount = sessionCount,
        netMinor = netMinor,
        archived = archived,
        updatedAt = updatedAt,
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
}
