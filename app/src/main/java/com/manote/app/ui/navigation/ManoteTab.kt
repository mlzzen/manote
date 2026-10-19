package com.manote.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.manote.app.R

/**
 * 底部导航的四个常驻页签。
 *
 * 中心的「新建」按钮是动作而不是页签，因此不在这里登记，
 * 否则会与「页签必有一个选中项」的语义冲突。
 *
 * **枚举顺序就是横向滑动的页面顺序**，从首页往右依次是场次、牌友、我的。
 * 这也是 `HorizontalPager` 的页码——不另建一张映射表，是为了让
 * 「页码」与「页签」不会因为某次重排而对不上。
 *
 * [key] 只是这一页的稳定标识（给 pager 的 `key` 用），**不是导航路由**：
 * 四个页签共用 [ManoteRoutes.MAIN] 一个目的地。
 */
enum class ManoteTab(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SESSIONS("sessions", R.string.nav_sessions, Icons.AutoMirrored.Filled.List),
    FRIENDS("friends", R.string.nav_friends, Icons.Filled.Person),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
    ;

    /**
     * 在底栏里占第几个等宽槽位。
     *
     * 底栏是「四页 + 中间一个空槽（留给抬起的『记一场』按钮）」，
     * 所以后两页各往右让一格。放在这里而不是画的时候现算，
     * 是因为选中块的位置计算每帧都要用，不该每次再想一遍这个 +1。
     */
    val barSlot: Int get() = if (ordinal < 2) ordinal else ordinal + 1

    companion object {
        /**
         * 连续的页面位置 → 底栏槽位位置（可含小数），[barSlot] 的实数版。
         *
         * 前两页之间只隔一格，后两页之间隔着中间那个空槽（「记一场」按钮），
         * 所以 1→2 这一段要走过两格：`pagePosition - 1` 在 1～2 之间正好由 0 线性升到 1，
         * 到 2 之后不再加，于是 2→3 又回到一比一的步长。
         * 这样拖动时深色块的位置与页面滑动是同一段连续函数，不会在跨过按钮时跳一下。
         */
        fun barSlotAt(pagePosition: Float): Float =
            pagePosition + (pagePosition - 1f).coerceIn(0f, 1f)

        /** 由稳定标识反查页签；不认识就返回 null。 */
        fun fromKey(key: String?): ManoteTab? = entries.firstOrNull { it.key == key }

        /**
         * 由页码取页签。越界一律夹到端点，避免 pager 恢复状态时
         * 拿到一个不存在的页码就把底栏搞崩。
         */
        fun at(page: Int): ManoteTab = entries[page.coerceIn(entries.indices)]
    }
}
