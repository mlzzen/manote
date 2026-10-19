package com.manote.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护路由登记表与页签映射的不变量。
 *
 * Navigation Compose 在注册重复路由字符串时会在运行时抛异常，且起始目的地
 * 必须已注册，否则应用启动即崩溃。这两点只能靠本测试覆盖。
 *
 * 页签那部分（顺序、槽位、越界）覆盖的是横向滑动的几何：
 * 页码与页签一旦错位，底栏的选中块会停在一个「没有内容」的格子上，
 * 而这种错位在界面上看起来只是「选中态有点歪」，很难一眼看出是映射错了。
 */
class ManoteRoutesTest {

    @Test
    fun all_containsEveryDeclaredDestination() {
        val expected = setOf(
            ManoteRoutes.MAIN,
            ManoteRoutes.SESSION_NEW,
            ManoteRoutes.SESSION_DETAIL,
            ManoteRoutes.SESSION_EDIT,
            ManoteRoutes.FRIEND_DETAIL,
            ManoteRoutes.BIN,
        )

        assertEquals("登记表必须覆盖全部目的地", expected, ManoteRoutes.all.toSet())
        assertEquals("登记表不能包含重复目的地", ManoteRoutes.all.size, ManoteRoutes.all.toSet().size)
        // 转场名单引用的是同一份路由，漏登记会让某个子页面拿不到「从右侧滑入」
        assertTrue(
            "子页面路由必须都已登记：${ManoteRoutes.subRoutes - ManoteRoutes.all.toSet()}",
            ManoteRoutes.all.toSet().containsAll(ManoteRoutes.subRoutes),
        )
        // 回收站是子页面（居中标题 + 返回键）
        assertTrue(ManoteRoutes.BIN in ManoteRoutes.subRoutes)
    }

    @Test
    fun all_routesAreWellFormed() {
        assertTrue("至少需要一个目的地", ManoteRoutes.all.isNotEmpty())

        ManoteRoutes.all.forEach { route ->
            assertTrue("路由不能为空白：'$route'", route.isNotBlank())
            assertEquals("路由不应有首尾空白：'$route'", route, route.trim())
            assertTrue("路由不应以斜杠开头或结尾：'$route'", !route.startsWith("/") && !route.endsWith("/"))
        }
    }

    @Test
    fun startDestination_isTheTabContainer() {
        assertTrue("起始目的地必须已登记", ManoteRoutes.START in ManoteRoutes.all)
        assertEquals("起始目的地应为页签容器", ManoteRoutes.MAIN, ManoteRoutes.START)
        // 根容器自己带转场的话，从详情返回时会整屏再淡入一次
        assertFalse("根容器不能出现在子页面名单里", ManoteRoutes.MAIN in ManoteRoutes.subRoutes)
    }

    @Test
    fun sessionDetail_buildsConcreteRouteMatchingTemplate() {
        val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val built = ManoteRoutes.sessionDetail(id)

        assertEquals("session/$id", built)
        // 具体路由必须能匹配登记模板，否则点击列表会跳转失败
        val prefix = ManoteRoutes.SESSION_DETAIL.substringBefore("{")
        val suffix = ManoteRoutes.SESSION_DETAIL.substringAfter("}")
        assertTrue("具体路由应以模板前缀开头", built.startsWith(prefix))
        assertTrue("具体路由应以模板后缀结尾", built.endsWith(suffix))
    }

    @Test
    fun sessionEdit_buildsConcreteRouteMatchingTemplate() {
        val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val built = ManoteRoutes.sessionEdit(id)

        assertEquals("session/$id/edit", built)
        val prefix = ManoteRoutes.SESSION_EDIT.substringBefore("{")
        val suffix = ManoteRoutes.SESSION_EDIT.substringAfter("}")
        assertTrue("具体路由应以模板前缀开头", built.startsWith(prefix))
        assertTrue("具体路由应以模板后缀结尾", built.endsWith(suffix))

        // 修改路由不能被详情模板吃掉：详情模板只有一段，而修改路由在它之后还有 /edit
        assertTrue(
            "修改路由与详情路由不相等",
            ManoteRoutes.sessionEdit(id) != ManoteRoutes.sessionDetail(id),
        )
    }

    @Test
    fun friendDetail_buildsConcreteRouteMatchingTemplate() {
        val id = "0f8fad5b-d9cb-469f-a165-70867728950e"
        val built = ManoteRoutes.friendDetail(id)

        assertEquals("friend/$id", built)
        val prefix = ManoteRoutes.FRIEND_DETAIL.substringBefore("{")
        val suffix = ManoteRoutes.FRIEND_DETAIL.substringAfter("}")
        assertTrue("具体路由应以模板前缀开头", built.startsWith(prefix))
        assertTrue("具体路由应以模板后缀结尾", built.endsWith(suffix))
    }

    @Test
    fun placeholderNamesAreUniquePerRoute() {
        // 两条路由用了不同的参数名，避免 Argument 复用时互相覆盖
        assertEquals("sessionId", ManoteRoutes.ARG_SESSION_ID)
        assertEquals("playerId", ManoteRoutes.ARG_PLAYER_ID)
        assertTrue("参数名不能相同", ManoteRoutes.ARG_SESSION_ID != ManoteRoutes.ARG_PLAYER_ID)
    }

    @Test
    fun tabs_areExactlyTheFourSwipePages() {
        assertEquals(
            "页签的枚举顺序就是左右滑动的页面顺序",
            listOf("home", "sessions", "friends", "settings"),
            ManoteTab.entries.map { it.key },
        )
        assertEquals("页签标识不能重复", 4, ManoteTab.entries.map { it.key }.toSet().size)
    }

    @Test
    fun tab_lookupByKeyAndPage() {
        ManoteTab.entries.forEach { tab ->
            assertEquals(tab, ManoteTab.fromKey(tab.key))
            assertEquals("页码必须能翻回同一个页签", tab, ManoteTab.at(tab.ordinal))
        }
        // 页签标识不是路由，拿一个路由去查必须查不到
        assertEquals(null, ManoteTab.fromKey(ManoteRoutes.SESSION_NEW))
        assertEquals(null, ManoteTab.fromKey(null))

        // pager 恢复状态时可能短暂给出越界页码，夹取而不是抛异常
        assertEquals(ManoteTab.HOME, ManoteTab.at(-1))
        assertEquals(ManoteTab.SETTINGS, ManoteTab.at(ManoteTab.entries.size))
    }

    @Test
    fun tab_barSlotSkipsTheCentreGap() {
        // 底栏是「四页 + 中间一格留给『记一场』」，后两页各往右让一格
        assertEquals(0, ManoteTab.HOME.barSlot)
        assertEquals(1, ManoteTab.SESSIONS.barSlot)
        assertEquals(3, ManoteTab.FRIENDS.barSlot)
        assertEquals(4, ManoteTab.SETTINGS.barSlot)

        // 停靠时槽位必须与 barSlot 一致，否则松手后块会停在错位
        ManoteTab.entries.forEach { tab ->
            assertEquals(
                "停靠时槽位应当等于 barSlot：${tab.key}",
                tab.barSlot.toFloat(),
                ManoteTab.barSlotAt(tab.ordinal.toFloat()),
                0.0001f,
            )
        }
    }

    @Test
    fun tab_barSlotAt_isContinuousAndMonotonic() {
        // 拖动时每帧都取这个值，跳一下就表现为选中块「闪一下」
        var previous = ManoteTab.barSlotAt(0f)
        var page = 0f
        while (page <= 3f) {
            val slot = ManoteTab.barSlotAt(page)
            assertTrue("槽位不应回退：page=$page", slot >= previous)
            assertTrue("相邻取样之间不应跳变：page=$page", slot - previous < 0.05f)
            previous = slot
            page += 0.01f
        }
        assertEquals(4f, ManoteTab.barSlotAt(3f), 0.0001f)
    }
}
