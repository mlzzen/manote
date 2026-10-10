package com.manote.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守护路由登记表的不变量。
 *
 * Navigation Compose 在注册重复路由字符串时会在运行时抛异常，且起始目的地
 * 必须已注册，否则应用启动即崩溃。这两点在 P0 只能靠本测试覆盖。
 */
class ManoteRoutesTest {

    @Test
    fun all_containsEveryDeclaredDestination() {
        val expected = setOf(
            ManoteRoutes.HOME,
            ManoteRoutes.HISTORY,
            ManoteRoutes.FRIENDS,
            ManoteRoutes.SETTINGS,
            ManoteRoutes.SESSION_NEW,
        )

        assertEquals("登记表必须覆盖全部目的地", expected, ManoteRoutes.all.toSet())
        assertEquals("登记表不能包含重复目的地", ManoteRoutes.all.size, ManoteRoutes.all.toSet().size)
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
}
