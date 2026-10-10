package com.manote.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manote.app.ui.friends.FriendsScreen
import com.manote.app.ui.history.HistoryScreen
import com.manote.app.ui.home.HomeScreen
import com.manote.app.ui.session.SessionEditScreen
import com.manote.app.ui.settings.SettingsScreen

/**
 * 单 Activity 的导航图。页面只接收 ID 与回调，不接收业务对象（规格 4）。
 *
 * P0 只包含顶层入口；后续阶段在此追加 `session/{sessionId}`、
 * `friend/{playerId}` 等详情目的地，并保持 [ManoteRoutes.all] 同步。
 */
@Composable
fun ManoteNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = ManoteRoutes.START,
        modifier = modifier,
    ) {
        composable(ManoteRoutes.HOME) {
            HomeScreen(
                onRecordSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                onOpenHistory = { navController.navigateTo(ManoteRoutes.HISTORY) },
                onOpenFriends = { navController.navigateTo(ManoteRoutes.FRIENDS) },
                onOpenSettings = { navController.navigateTo(ManoteRoutes.SETTINGS) },
            )
        }

        composable(ManoteRoutes.SESSION_NEW) {
            SessionEditScreen(onNavigateBack = { navController.navigateUp() })
        }

        composable(ManoteRoutes.HISTORY) {
            HistoryScreen(onNavigateBack = { navController.navigateUp() })
        }

        composable(ManoteRoutes.FRIENDS) {
            FriendsScreen(onNavigateBack = { navController.navigateUp() })
        }

        composable(ManoteRoutes.SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.navigateUp() })
        }
    }
}

/**
 * 同一目的地连点时不重复入栈，避免返回键要按多次才能退出。
 */
private fun NavHostController.navigateTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
