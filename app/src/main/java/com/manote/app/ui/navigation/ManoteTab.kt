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
 */
enum class ManoteTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(ManoteRoutes.HOME, R.string.nav_home, Icons.Filled.Home),
    SESSIONS(ManoteRoutes.SESSIONS, R.string.nav_sessions, Icons.AutoMirrored.Filled.List),
    FRIENDS(ManoteRoutes.FRIENDS, R.string.nav_friends, Icons.Filled.Person),
    SETTINGS(ManoteRoutes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
    ;

    companion object {
        /** 由当前路由反查选中的页签；不在页签内（如详情页）返回 null。 */
        fun fromRoute(route: String?): ManoteTab? = entries.firstOrNull { it.route == route }
    }
}
