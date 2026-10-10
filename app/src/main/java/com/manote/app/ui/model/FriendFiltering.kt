package com.manote.app.ui.model

/**
 * 牌友列表的搜索、归档过滤与排序（规格 3.4）。
 *
 * 全部是纯函数，便于单测；排序显式实现而不只依赖 SQL 的 ORDER BY，
 * 这样「updatedAt 倒序、ID 稳定排序」这条不变量可以被测试直接守护。
 */
object FriendFiltering {

    fun apply(
        rows: List<FriendRowUi>,
        query: String,
        includeArchived: Boolean,
    ): List<FriendRowUi> =
        rows.asSequence()
            .filter { includeArchived || !it.archived }
            .filter { matches(it, query) }
            .sortedWith(COMPARATOR)
            .toList()

    /** 搜索匹配昵称或备注，忽略大小写与首尾空白（规格 3.4）。 */
    private fun matches(row: FriendRowUi, query: String): Boolean {
        val keyword = query.trim().lowercase()
        if (keyword.isEmpty()) return true
        return row.nickname.lowercase().contains(keyword) ||
            row.note.lowercase().contains(keyword)
    }

    /**
     * updatedAt 倒序；同一时刻（例如同一批导入的数据）按 ID 升序，
     * 保证顺序稳定，列表不会在重组之间跳来跳去。
     */
    private val COMPARATOR: Comparator<FriendRowUi> =
        compareByDescending<FriendRowUi> { it.updatedAt }.thenBy { it.id }
}
