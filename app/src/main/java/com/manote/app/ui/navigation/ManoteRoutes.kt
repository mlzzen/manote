package com.manote.app.ui.navigation

import java.time.YearMonth

/**
 * 导航目的地登记表。
 *
 * 规格 4：导航只传 Session ID 或 Player ID，不传业务对象。
 *
 * **四个页签不在这里。** 它们没有各自的目的地，而是共用 [MAIN] 这一个——
 * 页签之间靠 `HorizontalPager` 横向滑动切换，滑动位置是这个容器的内部状态，
 * 不该进返回栈（否则左右滑几下，返回键要按好几次）。
 * 页签自身的标识见 [ManoteTab.key]。
 */
object ManoteRoutes {

    /**
     * 根容器：首页 / 场次 / 牌友 / 我的 四页共用。
     *
     * 返回栈上永远只有它一个「根」，子页面从它之上压入、再弹出，
     * 因此 `popUpTo(MAIN)` 就是「回到根」。
     */
    const val MAIN = "main"

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

    /** 回收站：删除的场次暂存在这里，可还原或彻底删除。 */
    const val BIN = "bin"

    /** 月历路由的参数名，形如 `2026-10`。 */
    const val ARG_YEAR_MONTH = "yearMonth"

    /** 月历：按年月翻看逐日输赢。实际跳转请用 [calendar] 生成完整路由。 */
    const val CALENDAR = "calendar/{yearMonth}"

    /** 生成某个场次的详情路由。 */
    fun sessionDetail(sessionId: String): String = "session/$sessionId"

    /** 生成某个场次的修改路由。 */
    fun sessionEdit(sessionId: String): String = "session/$sessionId/edit"

    /** 生成某位麻友的详情路由。 */
    fun friendDetail(playerId: String): String = "friend/$playerId"

    /**
     * 生成某个月的月历路由。
     *
     * 用 [YearMonth.toString] 的 ISO 写法（`2026-10`）：带前导零、跨语言都不变，
     * 解析回来只需 `YearMonth.parse`，不必自己拼「年 / 月」两个字。
     */
    fun calendar(yearMonth: YearMonth): String = "calendar/$yearMonth"

    /**
     * 全部已注册目的地。Navigation 会因重复路由在运行时抛异常，
     * 因此该列表是「路由唯一」这一不变量的唯一来源，由单元测试守护。
     *
     * 只有七个：一个根容器 + 六个子页面。页签不占名额。
     */
    val all: List<String> = listOf(
        MAIN,
        SESSION_NEW,
        SESSION_DETAIL,
        SESSION_EDIT,
        FRIEND_DETAIL,
        BIN,
        CALENDAR,
    )

    /**
     * 子页面路由（详情、编辑、记一场、回收站、月历）：转场从右侧滑入滑出，
     * 与根容器区分开。转场判断用的就是这一份名单——[MAIN] 刻意不在其中，
     * 它回到前台时应该是「直接就位」，不能再滑一次。
     */
    val subRoutes: Set<String> =
        setOf(SESSION_NEW, SESSION_DETAIL, SESSION_EDIT, FRIEND_DETAIL, BIN, CALENDAR)

    /** 起始目的地。 */
    const val START: String = MAIN
}
