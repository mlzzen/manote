package com.manote.app.ui.model

/**
 * 牌友列表的排序方式。
 *
 * 「场均」与「净额」都从 [FriendRowUi] 现算，不额外存字段；
 * 两个方向各自是一个枚举值，界面因此不必再维护一个「升 / 降序」开关，
 * 也就不会出现「选了场均、方向还停在上一项」这种半新半旧的状态。
 */
enum class FriendSort {
    /** 默认：最近编辑在前，与列表一直以来的顺序一致。 */
    RECENT,

    /** 场均从高到低。 */
    AVERAGE_DESC,

    /** 场均从低到高。 */
    AVERAGE_ASC,

    /** 累计净输赢从高到低。 */
    NET_DESC,

    /** 累计净输赢从低到高。 */
    NET_ASC,
}

/**
 * 牌友列表的搜索、归档过滤与排序（规格 3.4）。
 *
 * 全部是纯函数，便于单测；排序显式实现而不只依赖 SQL 的 ORDER BY，
 * 这样「顺序稳定」这条不变量可以被测试直接守护。
 */
object FriendFiltering {

    fun apply(
        rows: List<FriendRowUi>,
        query: String,
        includeArchived: Boolean,
        sort: FriendSort = FriendSort.RECENT,
    ): List<FriendRowUi> =
        rows.asSequence()
            .filter { includeArchived || !it.archived }
            .filter { matches(it, query) }
            .sortedWith(comparatorFor(sort))
            .toList()

    /** 搜索匹配昵称或备注，忽略大小写与首尾空白（规格 3.4）。 */
    private fun matches(row: FriendRowUi, query: String): Boolean {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return true
        return row.nickname.lowercase().contains(keyword) ||
            row.note.lowercase().contains(keyword)
    }

    private fun comparatorFor(sort: FriendSort): Comparator<FriendRowUi> = when (sort) {
        FriendSort.RECENT -> RECENT_FIRST
        FriendSort.AVERAGE_DESC -> ranking(descending = true) { it.averageMinor }
        FriendSort.AVERAGE_ASC -> ranking(descending = false) { it.averageMinor }
        FriendSort.NET_DESC -> ranking(descending = true) { it.netMinor }
        FriendSort.NET_ASC -> ranking(descending = false) { it.netMinor }
    }

    /**
     * updatedAt 倒序；同一时刻（例如同一批导入的数据）按 ID 升序，
     * 保证顺序稳定，列表不会在重组之间跳来跳去。
     */
    private val RECENT_FIRST: Comparator<FriendRowUi> =
        compareByDescending<FriendRowUi> { it.updatedAt }.thenBy { it.id }

    /**
     * 按数值排名的比较器（场均或净额，方向由 [descending] 决定）。
     *
     * 两条规则：
     * 1. **一场都没打过的排在最末**，两个方向都是。他不是「场均 0 元」，
     *    而是还没有场均可谈；把他放到榜首会让人误以为那是一种成绩。
     * 2. 数值并列时按 ID 升序，顺序在两次刷新之间保持稳定。
     */
    private fun ranking(
        descending: Boolean,
        select: (FriendRowUi) -> Long,
    ): Comparator<FriendRowUi> = Comparator { a, b ->
        val aUnranked = a.sessionCount == 0
        val bUnranked = b.sessionCount == 0
        when {
            aUnranked != bUnranked -> if (aUnranked) 1 else -1
            aUnranked -> a.id.compareTo(b.id)
            else -> {
                val compared = select(a).compareTo(select(b))
                if (compared != 0) {
                    if (descending) -compared else compared
                } else {
                    a.id.compareTo(b.id)
                }
            }
        }
    }
}
