package com.manote.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manote.app.ui.friends.FriendDetailScreen
import com.manote.app.ui.friends.FriendsScreen
import com.manote.app.ui.home.HomeScreen
import com.manote.app.ui.session.SessionDetailScreen
import com.manote.app.ui.session.SessionEditMode
import com.manote.app.ui.session.SessionEditScreen
import com.manote.app.ui.sessions.SessionsScreen
import com.manote.app.ui.settings.SettingsScreen

/**
 * 单 Activity 的导航图。页面只接收 ID 与回调，不接收业务对象（规格 4）。
 *
 * 底部导航的四个页签与「记一场」共用同一个返回栈：
 * 切换页签时用 `saveState` / `restoreState` 保留各页滚动位置，且不会把栈堆起来。
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
                onSelectTab = navController::switchTab,
                onRecordSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                onOpenSessionDetail = { navController.navigateTo(ManoteRoutes.sessionDetail(it)) },
            )
        }

        composable(ManoteRoutes.SESSIONS) {
            SessionsScreen(
                onSelectTab = navController::switchTab,
                onRecordSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                onOpenSessionDetail = { navController.navigateTo(ManoteRoutes.sessionDetail(it)) },
            )
        }

        composable(ManoteRoutes.FRIENDS) {
            FriendsScreen(
                onSelectTab = navController::switchTab,
                onRecordSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                onOpenFriend = { navController.navigateTo(ManoteRoutes.friendDetail(it)) },
            )
        }

        composable(ManoteRoutes.SETTINGS) {
            SettingsScreen(
                onSelectTab = navController::switchTab,
                onRecordSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                // 规格 6.1：导入成功后清除旧页面草稿、回到首页，统计从新库刷新
                onImportCompleted = { navController.popBackTo(ManoteRoutes.HOME) },
            )
        }

        composable(ManoteRoutes.SESSION_NEW) {
            SessionEditScreen(
                onNavigateBack = { navController.navigateUp() },
                onSaved = { sessionId ->
                    // 规格 2.3：成功进入详情并清除草稿；表单本身出栈，返回键不会退回空表单。
                    navController.navigate(ManoteRoutes.sessionDetail(sessionId)) {
                        popUpTo(ManoteRoutes.SESSION_NEW) { inclusive = true }
                    }
                },
                // 表单被导入整库作废时直接回首页（规格 2.3、T24）
                onInvalidated = { navController.popBackTo(ManoteRoutes.HOME) },
            )
        }

        composable(
            route = ManoteRoutes.SESSION_DETAIL,
            arguments = listOf(
                navArgument(ManoteRoutes.ARG_SESSION_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            SessionDetailScreen(
                sessionId = entry.arguments?.getString(ManoteRoutes.ARG_SESSION_ID).orEmpty(),
                onNavigateBack = { navController.navigateUp() },
                onEdit = { navController.navigateTo(ManoteRoutes.sessionEdit(it)) },
                // 规格 3.3：删除当前详情后回首页
                onDeleted = { navController.popBackTo(ManoteRoutes.HOME) },
            )
        }

        composable(
            route = ManoteRoutes.SESSION_EDIT,
            arguments = listOf(
                navArgument(ManoteRoutes.ARG_SESSION_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            val sessionId = entry.arguments?.getString(ManoteRoutes.ARG_SESSION_ID).orEmpty()
            SessionEditScreen(
                mode = SessionEditMode.Edit(sessionId),
                onNavigateBack = { navController.navigateUp() },
                onSaved = { navController.navigateUp() },
                // 修改成功后退回详情页；详情页订阅数据库，会自动显示新金额与名次
                onUpdated = { navController.navigateUp() },
                // 表单被导入整库作废时直接回首页（规格 2.3、T24）
                onInvalidated = { navController.popBackTo(ManoteRoutes.HOME) },
            )
        }

        composable(
            route = ManoteRoutes.FRIEND_DETAIL,
            arguments = listOf(
                navArgument(ManoteRoutes.ARG_PLAYER_ID) { type = NavType.StringType },
            ),
        ) { entry ->
            FriendDetailScreen(
                playerId = entry.arguments?.getString(ManoteRoutes.ARG_PLAYER_ID).orEmpty(),
                onNavigateBack = { navController.navigateUp() },
                onOpenSessionDetail = { navController.navigateTo(ManoteRoutes.sessionDetail(it)) },
            )
        }
    }
}

/** 同一目的地连点时不重复入栈，避免返回键要按多次才能退出。 */
private fun NavHostController.navigateTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

/** 切换底部页签：保留各页状态，且不堆积返回栈。 */
private fun NavHostController.switchTab(tab: ManoteTab) {
    if (currentDestination?.route == tab.route) return
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * 回到某个页签并清掉中间栈。
 *
 * 场次被删除后用它回首页（规格 3.3）：详情页留在栈里只会显示「已不存在」，
 * 不如直接把整条链路弹掉，返回键也就不会退回到一个已经不存在的东西上。
 */
private fun NavHostController.popBackTo(route: String) {
    navigate(route) {
        popUpTo(route) { inclusive = false }
        launchSingleTop = true
    }
}
