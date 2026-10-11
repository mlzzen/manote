package com.manote.app.ui.model

import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.DatedNet
import com.manote.app.domain.OpponentMatch
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.Rivalry
import com.manote.app.domain.SessionRules
import com.manote.app.domain.Trend
import com.manote.app.domain.TrendPoint

/**
 * 牌友列表上的标签。
 *
 * 「谁该挂哪个标签」在 [Rivalry] 里判定——那要横比所有牌友，单看一行是判不出来的；
 * 这个枚举只承载判定结果，界面据此查文案与配色。
 */
enum class FriendBadge {
    /** 最苦主：我在他面前的场均最低。 */
    WORST_RIVAL,

    /** 福星：我在他面前的场均最高。 */
    LUCKY_STAR,

    /** 牌王：他自己的场均最高。 */
    TABLE_KING,
}

/**
 * 牌友列表的一行（规格 3.4）。
 *
 * 场数与净输赢来自 `session_result` 的聚合，不是另存的累计值。
 */
data class FriendRowUi(
    val id: String,
    val nickname: String,
    val note: String,
    /** 参与过的场数。 */
    val sessionCount: Int,
    /** 跨场净输赢，分。 */
    val netMinor: Long,
    val archived: Boolean,
    /** 编辑时间，用于「按 updatedAt 倒序」这一排序不变量的显式实现与测试。 */
    val updatedAt: Long,
    /**
     * 挂在这一行上的标签。可能同时满足多个，也可能一个都没有——
     * 场数都不够门槛时就是不挂，界面不必为此特判。
     */
    val badges: Set<FriendBadge> = emptySet(),
)

/** 麻友详情里的「逐场」一行：这一场他在哪个位置、他和本人各是什么结果。 */
data class FriendSessionRowUi(
    val sessionId: String,
    val title: String,
    val playedAt: Long,
    /** 该麻友在本场的位置（1～3）。 */
    val seat: Int,
    /** 该麻友本场净额，分。 */
    val netMinor: Long,
    /** 本人（seat 0）本场净额，分。 */
    val selfNetMinor: Long,
) {
    /** 用户是否真的起过标题；为 false 时这一行不显示标题（见 `SessionRowUi.hasCustomTitle`）。 */
    val hasCustomTitle: Boolean get() = !SessionRules.isPlaceholderTitle(title)
}

/** 麻友详情：档案 + 跨场统计 + 净额走势 + 逐场记录。 */
data class FriendDetailUi(
    val id: String,
    val nickname: String,
    val note: String,
    val archived: Boolean,
    val statistics: PlayerStatistics,
    /** 这位麻友的累计净额走势，按结束时间升序；场次少于 2 场时为空。 */
    val trend: List<TrendPoint> = emptyList(),
    /**
     * 我与这位麻友同场时，我的场均（分）。从没同场过时为 null。
     *
     * 与牌友列表判定「最苦主」同源：只要他在桌上，我平均打成什么样。
     */
    val myAverageTogetherMinor: Long? = null,
    /** 我与这位麻友同场的场数；从没同场过时为 0。 */
    val commonSessionCount: Int = 0,
    val sessions: List<FriendSessionRowUi>,
)

/**
 * 档案 + 聚合 → 列表行。
 *
 * 聚合缺失（该麻友还没参与过任何场次）时场数与净输赢都为 0，
 * 而不是把整行丢掉——刚建好的麻友必须能在列表里看到。
 *
 * [badges] 由调用方用 [Rivalry] 算好后传入：三个标签都要横比所有牌友，
 * 孤零零一行给不出结论。
 */
fun PlayerEntity.toRowUi(
    aggregate: PlayerAggregate?,
    badges: Set<FriendBadge> = emptySet(),
): FriendRowUi = FriendRowUi(
    id = id,
    nickname = nickname,
    note = note,
    sessionCount = aggregate?.sessionCount ?: 0,
    netMinor = aggregate?.netMinor ?: 0L,
    archived = archivedAt != null,
    updatedAt = updatedAt,
    badges = badges,
)

/**
 * 一场 → 与各位同场牌友的对照（三个标签的输入）。
 *
 * 本场的 seat 0 恒为本人；缺 seat 0 说明库已损坏，这时返回空列表，
 * 宁可这一场不参与评选，也不拿一个错误的基准去和人比。
 */
fun SessionWithResults.opponentMatches(): List<OpponentMatch> {
    val selfNet = results.firstOrNull { it.seat == SessionRules.SEAT_SELF }?.netMinor
        ?: return emptyList()
    return results
        .filter { it.seat != SessionRules.SEAT_SELF }
        .map {
            OpponentMatch(
                playerId = it.playerId,
                myNetMinor = selfNet,
                theirNetMinor = it.netMinor,
            )
        }
}

/**
 * 档案 + 统计 + 参与的场次 → 麻友详情。
 *
 * 逐场记录按场次倒序（与数据库查询顺序一致）；某场里找不到这位玩家的结果时跳过该场，
 * 而不是用 0 顶替——那会凭空造出一条他没参加过的记录。
 *
 * 净额走势按时间**升序**累加（图从左到右即时间先后），因此把逐场记录反过来再交给 [Trend]。
 * 只有一场时也照常给出一个点，由界面决定画不画得出来。
 *
 * 「同场时我的场均」也在这里算，且与牌友列表判定「最苦主」用的是同一个函数：
 * 两处若各算各的，迟早会出现「列表标了他，详情页的数字却对不上」。
 */
fun PlayerEntity.toDetailUi(
    statistics: PlayerStatistics,
    sessionsWithResults: List<SessionWithResults>,
): FriendDetailUi {
    val rows = sessionsWithResults.mapNotNull { item ->
        val mine = item.results.firstOrNull { it.playerId == id } ?: return@mapNotNull null
        val self = item.results.firstOrNull { it.seat == SessionRules.SEAT_SELF }
        FriendSessionRowUi(
            sessionId = item.session.id,
            title = item.session.title,
            playedAt = item.session.playedAt,
            seat = mine.seat,
            netMinor = mine.netMinor,
            selfNetMinor = self?.netMinor ?: 0L,
        )
    }
    val record = Rivalry.recordFor(sessionsWithResults.flatMap { it.opponentMatches() }, id)
    return FriendDetailUi(
        id = id,
        nickname = nickname,
        note = note,
        archived = archivedAt != null,
        statistics = statistics,
        trend = Trend.cumulative(
            rows.asReversed().map { DatedNet(playedAt = it.playedAt, netMinor = it.netMinor) },
        ),
        myAverageTogetherMinor = record?.myAverageMinor,
        commonSessionCount = record?.commonSessionCount ?: 0,
        sessions = rows,
    )
}
