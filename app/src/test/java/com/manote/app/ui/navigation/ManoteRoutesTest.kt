package com.manote.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护路由登记表的不变量。
 *
 * Navigation Compose 在注册重复路由字符串时会在运行时抛异常，且起始目的地
 * 必须已注册，否则应用启动即崩溃。这两点只能靠本测试覆盖。
 */
class ManoteRoutesTest {

    @Test
    fun all_containsEveryDeclaredDestination() {
        val expected = setOf(
            ManoteRoutes.HOME,
            ManoteRoutes.SESSIONS,
            ManoteRoutes.FRIENDS,
            ManoteRoutes.SETTINGS,
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
        // 回收站是子页面（居中标题 + 返回键），而不是页签
        assertTrue(ManoteRoutes.BIN in ManoteRoutes.subRoutes)
        assertTrue(ManoteRoutes.BIN !in ManoteRoutes.tabRoutes)
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
    fun startDestination_isRegistered() {
        assertTrue("起始目的地必须已登记", ManoteRoutes.START in ManoteRoutes.all)
        assertEquals("起始目的地应为首页", ManoteRoutes.HOME, ManoteRoutes.START)
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
    fun tabs_areExactlyTheFourPersistentDestinations() {
        assertEquals(
            listOf(
                ManoteRoutes.HOME,
                ManoteRoutes.SESSIONS,
                ManoteRoutes.FRIENDS,
                ManoteRoutes.SETTINGS,
            ),
            ManoteRoutes.tabRoutes,
        )
        // 每一条页签路由都必须已登记
        ManoteRoutes.tabRoutes.forEach { route ->
            assertTrue("页签路由必须已登记：$route", route in ManoteRoutes.all)
        }
    }

    @Test
    fun tab_lookupByRoute() {
        assertEquals(ManoteTab.HOME, ManoteTab.fromRoute(ManoteRoutes.HOME))
        assertEquals(ManoteTab.SESSIONS, ManoteTab.fromRoute(ManoteRoutes.SESSIONS))
        assertEquals(ManoteTab.FRIENDS, ManoteTab.fromRoute(ManoteRoutes.FRIENDS))
        assertEquals(ManoteTab.SETTINGS, ManoteTab.fromRoute(ManoteRoutes.SETTINGS))
        // 非页签目的地（记一场 / 详情）不选中任何页签
        assertEquals(null, ManoteTab.fromRoute(ManoteRoutes.SESSION_NEW))
        assertEquals(null, ManoteTab.fromRoute(null))
    }
}
