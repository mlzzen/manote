package com.manote.app.ui.navigation

/**
 * 导航目的地登记表。
 *
 * 规格 4：导航只传 Session ID 或 Player ID，不传业务对象。
 * P0 只注册顶层入口；P2 起在同一 [ManoteNavHost] 中补充
 * `session/{sessionId}`、`friend/{playerId}` 等携带 ID 的详情路由。
 */
object ManoteRoutes {

    /** 首页：本人统计与最近场次。 */
    const val HOME = "home"

    /** 我的历史：场次列表与搜索。 */
    const val HISTORY = "history"

    /** 麻友管理。 */
    const val FRIENDS = "friends"

    /** 设置与数据管理。 */
    const val SETTINGS = "settings"

    /** 记一场：新建整场表单。 */
    const val SESSION_NEW = "session/new"

    /**
     * 全部已注册目的地。Navigation 会因重复路由在运行时抛异常，
     * 因此该列表是「路由唯一」这一不变量的唯一来源，由单元测试守护。
     */
    val all: List<String> = listOf(HOME, HISTORY, FRIENDS, SETTINGS, SESSION_NEW)

    /** 起始目的地。 */
    const val START: String = HOME
}
