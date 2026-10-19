package com.manote.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.manote.app.ui.calendar.CalendarScreen
import com.manote.app.ui.components.ManoteBottomBar
import com.manote.app.ui.components.ManotePageBackground
import com.manote.app.ui.friends.FriendDetailScreen
import com.manote.app.ui.friends.FriendsScreen
import com.manote.app.ui.home.HomeScreen
import com.manote.app.ui.recyclebin.RecycleBinScreen
import com.manote.app.ui.session.SessionDetailScreen
import com.manote.app.ui.session.SessionEditMode
import com.manote.app.ui.session.SessionEditScreen
import com.manote.app.ui.sessions.SessionsScreen
import com.manote.app.ui.settings.SettingsScreen
import com.manote.app.ui.theme.ManoteMotion
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * 单 Activity 的导航图。页面只接收 ID 与回调，不接收业务对象（规格 4）。
 *
 * 返回栈上只有两层：根容器 [ManoteRoutes.MAIN] 与压在其上的子页面。
 * 四个页签**不是**四个目的地，而是根容器里的一台 [HorizontalPager]——
 * 左右滑动是页签之间唯一的切换方式，点底栏只是让那台 pager 滚过去。
 *
 * 为什么页签不进返回栈：若一页一个目的地，每滑一次都是一次导航，
 * 返回键就要一页一页往回倒；而「左右滑」在用户心里是平级浏览，
 * 不是一层层进来。合并成一个容器之后，从任何一页按返回都直接退出应用，
 * 这也正是平级页签应有的行为。
 *
 * **底栏与整页背景不在页面里，而在这里。** 这是为了让它们在切页时保持同一个实例：
 * - 底栏若由每个根页面各画一份，切页时它就是一整块被销毁再重建的新控件，
 *   选中态的颜色动画从初始值重跑、深色块会「闪一下再长出来」，看起来像掉帧；
 * - 背景若由每页各画一份，转场时两份半透明渐变叠在一起，整屏亮度先暗后亮。
 * 提到宿主层之后，切页只是内容层在换，底栏的选中块照旧跟手滑过去。
 *
 * 转场分两种语感：
 * - 页签之间：**没有转场**，滑动本身就是转场，见 [MainTabs]；
 * - 子页面（详情、编辑、记一场）：从右侧滑入，返回时滑回右侧 ——
 *   与系统返回手势同一个方向，「这是一层子页面」的进深由方向给出。
 */
@Composable
fun ManoteNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    /**
     * 由桌面快捷方式唤醒时为 true：起始页落在「记一场」而不是首页。
     *
     * 只在首次组合时消费一次（下面那个 [rememberSaveable] 的闸门），
     * 否则转屏重建会再入栈一层表单。
     */
    startWithRecordSession: Boolean = false,
) {
    // 快捷方式只消费一次：转屏、进程重建都靠这个已保存的布尔值挡住重复入栈。
    var shortcutConsumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startWithRecordSession) {
        if (startWithRecordSession && !shortcutConsumed) {
            shortcutConsumed = true
            navController.navigateTo(ManoteRoutes.SESSION_NEW)
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    // 停在根容器上才显示底栏；子页面（详情、编辑、回收站）没有底栏。
    val onMain = backStackEntry?.destination?.route == ManoteRoutes.MAIN

    /**
     * 页码放在宿主而不是 [MainTabs] 里。
     *
     * 进子页面时根容器整棵子树会被移出组合，pager 也随之销毁；
     * 页码若跟着 pager 走，从详情返回就会退回首页——用户明明是从「牌友」点进去的。
     * 放在宿主的 `remember` 里，它就不参与那一次销毁。
     */
    val pagerState = rememberPagerState(pageCount = { ManoteTab.entries.size })
    val scope = rememberCoroutineScope()

    // 点底栏：让 pager 用同一段动画滑过去，而不是直接跳页。
    // 用 remember 包住，避免每次重组都造一个新 lambda 让底栏跟着重组。
    val selectTab: (ManoteTab) -> Unit = remember(pagerState, scope) {
        { tab -> scope.launch { pagerState.animateScrollToPage(tab.ordinal) } }
    }

    /**
     * 回到某个页签，并清掉中间栈（删除场次、导入整库之后用）。
     *
     * 页码先摆好再弹栈：弹栈会让根容器的子树重新组合，
     * 那时读到的已经是目标页，不会先闪一帧旧页面。
     */
    val returnToRoot: (ManoteTab) -> Unit = remember(pagerState, navController) {
        { tab ->
            pagerState.requestScrollToPage(tab.ordinal)
            navController.popBackToRoot()
        }
    }

    ManotePageBackground(modifier = modifier) {
        Scaffold(
            // 背景在外层，Scaffold 只负责排版，不能自带底色
            containerColor = Color.Transparent,
            // 不让 Scaffold 自己扣系统栏内边距：顶栏的 statusBarsPadding、底栏的
            // navigationBarsPadding 各自已经处理过，这里再扣一次会把内容顶下去一截。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (onMain) {
                    // 读页码这一步关在这个小组件里：越过 50% 时它才会变，
                    // 若在宿主里直接读，一次滑动就会把整个 NavHost 重组一遍。
                    ManoteTabsBar(
                        pagerState = pagerState,
                        onSelect = selectTab,
                        onNewSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                    )
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = ManoteRoutes.START,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { enterFor(targetState) },
                exitTransition = { exitFor(targetState) },
                popEnterTransition = { enterFor(targetState) },
                popExitTransition = { popExitFor(initialState) },
            ) {
                composable(ManoteRoutes.MAIN) {
                    MainTabs(
                        pagerState = pagerState,
                        onSelectTab = selectTab,
                        onOpenSessionDetail = {
                            navController.navigateTo(ManoteRoutes.sessionDetail(it))
                        },
                        onNewSession = { navController.navigateTo(ManoteRoutes.SESSION_NEW) },
                        onOpenFriend = { navController.navigateTo(ManoteRoutes.friendDetail(it)) },
                        onOpenRecycleBin = { navController.navigateTo(ManoteRoutes.BIN) },
                        onOpenCalendar = { navController.navigateTo(ManoteRoutes.calendar(it)) },
                        // 规格 6.1：导入成功后清除旧页面草稿、回到首页，统计从新库刷新
                        onImportCompleted = { returnToRoot(ManoteTab.HOME) },
                    )
                }

                composable(ManoteRoutes.BIN) {
                    RecycleBinScreen(onNavigateBack = { navController.navigateUp() })
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
                        onInvalidated = { returnToRoot(ManoteTab.HOME) },
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
                        onDeleted = { returnToRoot(ManoteTab.HOME) },
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
                        onInvalidated = { returnToRoot(ManoteTab.HOME) },
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
                        onOpenSessionDetail = {
                            navController.navigateTo(ManoteRoutes.sessionDetail(it))
                        },
                    )
                }

                composable(
                    route = ManoteRoutes.CALENDAR,
                    arguments = listOf(
                        navArgument(ManoteRoutes.ARG_YEAR_MONTH) { type = NavType.StringType },
                    ),
                ) { entry ->
                    CalendarScreen(
                        // 路由里是 `2026-10`。参数缺失或被手改坏时退回本月，
                        // 让页面照常打开——这里没有值得为它崩一次的分支。
                        initialMonth = parseYearMonth(
                            entry.arguments?.getString(ManoteRoutes.ARG_YEAR_MONTH),
                        ),
                        onNavigateBack = { navController.navigateUp() },
                        onOpenSessionDetail = {
                            navController.navigateTo(ManoteRoutes.sessionDetail(it))
                        },
                    )
                }
            }
        }
    }
}

/**
 * 四个页签的横向分页容器。
 *
 * **每页自带一份整页背景。** 滑动时两页并排出现在屏幕上，只要有一页是透明的，
 * 就会透出邻页的卡片——两个页面的内容叠在一起，既看不清也像花屏。
 * 因此这里不用外层那层常驻背景，而是让每页自己铺满一道晨光；
 * 两页各自带着自己的背景走，滑动时看到的是两张不透明的「纸」在换位。
 *
 * `beyondViewportPageCount = 1` 让左右相邻页保持组合：
 * 若不预组合，滑到一半才首次组合，那一帧要做完整的一轮测量与数据首帧，
 * 正好落在手指最用力的时候——用户看到的还是「卡一下」。
 * 代价是启动时三页都会组合，但本地库查询很快，换来的是全程不掉帧。
 */
@Composable
private fun MainTabs(
    pagerState: PagerState,
    onSelectTab: (ManoteTab) -> Unit,
    onOpenSessionDetail: (String) -> Unit,
    onNewSession: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenCalendar: (YearMonth) -> Unit,
    onImportCompleted: () -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
        // 用页签自己的稳定标识做 key：页码若因以后重排而变，页面状态也不会串到邻页
        key = { ManoteTab.entries[it].key },
    ) { page ->
        ManotePageBackground {
            when (ManoteTab.entries[page]) {
                ManoteTab.HOME -> HomeScreen(
                    onOpenSessions = { onSelectTab(ManoteTab.SESSIONS) },
                    onRecordSession = onNewSession,
                    onOpenSessionDetail = onOpenSessionDetail,
                    onOpenCalendar = onOpenCalendar,
                )

                ManoteTab.SESSIONS -> SessionsScreen(
                    onOpenSessionDetail = onOpenSessionDetail,
                )

                ManoteTab.FRIENDS -> FriendsScreen(
                    onOpenFriend = onOpenFriend,
                )

                ManoteTab.SETTINGS -> SettingsScreen(
                    onOpenRecycleBin = onOpenRecycleBin,
                    onImportCompleted = onImportCompleted,
                )
            }
        }
    }
}

/**
 * 底栏的薄壳：只负责把页码翻成当前页签。
 *
 * 单独拆出来是为了把「读 `currentPage`」这件事关在最小的重组范围里——
 * 页码越过 50% 时它会变，那一下只该重组这一条底栏，而不是整个导航宿主。
 */
@Composable
private fun ManoteTabsBar(
    pagerState: PagerState,
    onSelect: (ManoteTab) -> Unit,
    onNewSession: () -> Unit,
) {
    ManoteBottomBar(
        current = ManoteTab.at(pagerState.currentPage),
        onSelect = onSelect,
        onNewSession = onNewSession,
        // 传 lambda 不传值：拖动时它每帧都在变，值一传进来底栏就得每帧重组
        pageOffset = { pagerState.currentPageOffsetFraction },
    )
}

/** 同一目的地连点时不重复入栈，避免返回键要按多次才能退出。 */
private fun NavHostController.navigateTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}

/**
 * 解析月历路由里的年月。
 *
 * 只接受 ISO 写法（`2026-10`）；拿不到或不合法时退回本月——
 * 这条路由只会由 [ManoteRoutes.calendar] 生成，解析失败意味着参数被外部改坏了，
 * 那时给用户一个可用的月份，比让月历页在 `YearMonth.parse` 上崩掉更合理。
 */
private fun parseYearMonth(raw: String?): YearMonth = try {
    if (raw.isNullOrBlank()) YearMonth.now() else YearMonth.parse(raw)
} catch (_: DateTimeParseException) {
    YearMonth.now()
}

/**
 * 进场：子页面从右侧滑入，根容器**直接出现**。
 *
 * 根容器不做任何转场：从详情返回时，底下的页面早就在了（只是被盖住），
 * 再让它淡入一次，看起来就像整屏闪了一下。页签之间则由滑动本身承担过渡。
 */
private fun enterFor(targetState: NavBackStackEntry): EnterTransition {
    return if (targetState.destination.route in ManoteRoutes.subRoutes) {
        slideInHorizontally(
            tween(ManoteMotion.DURATION_SLOW, easing = ManoteMotion.EASING_STANDARD),
        ) { it / 4 } + fadeIn(
            tween(ManoteMotion.DURATION_SLOW, easing = ManoteMotion.EASING_STANDARD),
        )
    } else {
        EnterTransition.None
    }
}

/**
 * 退场：只有「根容器 → 子页面」需要淡出。
 *
 * 子页面从右侧滑上来时，它左边还留着一条旧页面；让旧页面在这期间淡掉，
 * 滑到一半时边缘才不会是生硬的一道切边。
 */
private fun exitFor(targetState: NavBackStackEntry): ExitTransition {
    return if (targetState.destination.route in ManoteRoutes.subRoutes) {
        fadeOut(tween(ManoteMotion.DURATION_FAST, easing = ManoteMotion.EASING_EXIT))
    } else {
        ExitTransition.None
    }
}

/**
 * 子页面出栈：往右滑回去，与系统返回手势同一个方向。
 *
 * 判断依据是**要离开的那一页**（[initialState]）而不是目标页：
 * 详情 → 首页要走滑动，编辑 → 详情也要走滑动，两者「离开的都是子页面」。
 */
private fun popExitFor(initialState: NavBackStackEntry): ExitTransition {
    return if (initialState.destination.route in ManoteRoutes.subRoutes) {
        slideOutHorizontally(
            tween(ManoteMotion.DURATION_NORMAL, easing = ManoteMotion.EASING_EXIT),
        ) { it / 4 } + fadeOut(
            tween(ManoteMotion.DURATION_NORMAL, easing = ManoteMotion.EASING_EXIT),
        )
    } else {
        ExitTransition.None
    }
}

/**
 * 回到根容器并清掉中间栈。
 *
 * 场次被删除后用它回首页（规格 3.3）：详情页留在栈里只会显示「已不存在」，
 * 不如直接把整条链路弹掉，返回键也就不会退回到一个已经不存在的东西上。
 */
private fun NavHostController.popBackToRoot() {
    navigate(ManoteRoutes.MAIN) {
        popUpTo(ManoteRoutes.MAIN) { inclusive = false }
        launchSingleTop = true
    }
}
