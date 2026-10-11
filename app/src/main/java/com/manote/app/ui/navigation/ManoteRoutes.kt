package com.manote.app.ui.navigation

/**
 * 导航目的地登记表。
 *
 * 规格 4：导航只传 Session ID 或 Player ID，不传业务对象。
 */
object ManoteRoutes {

    /** 首页：本人统计与最近场次。 */
    const val HOME = "home"

    /** 场次：按结束时间倒序的场次列表。 */
    const val SESSIONS = "sessions"

    /** 牌友：麻友档案列表与新增。 */
    const val FRIENDS = "friends"

    /** 我的：本人档案、改名与版本信息。 */
    const val SETTINGS = "settings"

    /** 记一场：新建整场表单。 */
    const val SESSION_NEW = "session/new"

    /** 详情路由的参数名。 */
    const val ARG_SESSION_ID = "sessionId"

    /** 四人详情。实际跳转请用 [sessionDetail] 生成完整路由。 */
    const val SESSION_DETAIL = "session/{sessionId}"

    /** 修改整场。实际跳转请用 [sessionEdit] 生成完整路由。 */
    const val SESSION_EDIT = "session/{sessionId}/edit"

    /** 麻友详情路由的参数名。 */
    const val ARG_PLAYER_ID = "playerId"

    /** 麻友详情。实际跳转请用 [friendDetail] 生成完整路由。 */
    const val FRIEND_DETAIL = "friend/{playerId}"

    /** 生成某个场次的详情路由。 */
    fun sessionDetail(sessionId: String): String = "session/$sessionId"

    /** 生成某个场次的修改路由。 */
    fun sessionEdit(sessionId: String): String = "session/$sessionId/edit"

    /** 生成某位麻友的详情路由。 */
    fun friendDetail(playerId: String): String = "friend/$playerId"

    /**
     * 全部已注册目的地。Navigation 会因重复路由在运行时抛异常，
     * 因此该列表是「路由唯一」这一不变量的唯一来源，由单元测试守护。
     */
    val all: List<String> = listOf(
        HOME,
        SESSIONS,
        FRIENDS,
        SETTINGS,
        SESSION_NEW,
        SESSION_DETAIL,
        SESSION_EDIT,
        FRIEND_DETAIL,
    )

    /** 底部导航的四个常驻页签（中心「新建」是动作而非页签）。 */
    val tabRoutes: List<String> = listOf(HOME, SESSIONS, FRIENDS, SETTINGS)

    /** 起始目的地。 */
    const val START: String = HOME
}
