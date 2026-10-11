package com.manote.app.ui.model

/**
 * 牌友列表的排序方式。
 *
 * 三个可排序指标（他那边的场均、我与他的同场场均、他累计的净输赢）都从 [FriendRowUi] 现算，
 * 不额外存字段；两个方向各自是一个枚举值，界面因此不必再维护一个「升 / 降序」开关，
 * 也就不会出现「选了场均、方向还停在上一项」这种半新半旧的状态。
 */
enum class FriendSort {
    /** 默认：最近编辑在前，与列表一直以来的顺序一致。 */
    RECENT,

    /** 这位牌友自己的场均，从高到低。 */
    AVERAGE_DESC,

    /** 这位牌友自己的场均，从低到高。 */
    AVERAGE_ASC,

    /**
     * 我与这位牌友同场时、我的场均，从高到低。
     *
     * 与 [AVERAGE_DESC] 是两个不同的数：那个看「他打成什么样」，
     * 这个看「他在桌上我打成什么样」，也就是「最苦主 / 福星」用的那个量。
     */
    MY_AVERAGE_DESC,

    /** 我与这位牌友同场时、我的场均，从低到高。 */
    MY_AVERAGE_ASC,

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
        FriendSort.AVERAGE_DESC -> ranking(descending = true) { rankable(it, it.averageMinor) }
        FriendSort.AVERAGE_ASC -> ranking(descending = false) { rankable(it, it.averageMinor) }
        // 没同场过时这一格本来就是 null，正好落进「没有可谈的数」那条规则
        FriendSort.MY_AVERAGE_DESC -> ranking(descending = true) { it.myAverageTogetherMinor }
        FriendSort.MY_AVERAGE_ASC -> ranking(descending = false) { it.myAverageTogetherMinor }
        FriendSort.NET_DESC -> ranking(descending = true) { rankable(it, it.netMinor) }
        FriendSort.NET_ASC -> ranking(descending = false) { rankable(it, it.netMinor) }
    }

    /**
     * 打过至少一场，场均与净额才有可谈的数；否则返回 null。
     *
     * 不能用 0 顶替：0 是一个真实的成绩（上过桌、一分不输不赢），
     * 拿它冒充「还没打过」会让人插到榜单中间去。
     */
    private fun rankable(row: FriendRowUi, value: Long): Long? =
        value.takeIf { row.sessionCount > 0 }

    /**
     * updatedAt 倒序；同一时刻（例如同一批导入的数据）按 ID 升序，
     * 保证顺序稳定，列表不会在重组之间跳来跳去。
     */
    private val RECENT_FIRST: Comparator<FriendRowUi> =
        compareByDescending<FriendRowUi> { it.updatedAt }.thenBy { it.id }

    /**
     * 按数值排名的比较器（方向由 [descending] 决定）。
     *
     * [select] 返回 null 表示**这一行在这个指标上没有可谈的数**，一律排在最末，
     * 两个方向都是。三种指标「没有数」各有各的条件：场均与净额要求打过至少一场，
     * 我的场均要求我和他至少同场过一次——统一成 null，排序规则就只剩一套。
     *
     * 数值并列时按 ID 升序，顺序在两次刷新之间保持稳定。
     */
    private fun ranking(
        descending: Boolean,
        select: (FriendRowUi) -> Long?,
    ): Comparator<FriendRowUi> = Comparator { a, b ->
        val left = select(a)
        val right = select(b)
        when {
            left == null && right == null -> a.id.compareTo(b.id)
            left == null -> 1
            right == null -> -1
            else -> {
                val compared = left.compareTo(right)
                if (compared != 0) {
                    if (descending) -compared else compared
                } else {
                    a.id.compareTo(b.id)
                }
            }
        }
    }
}
