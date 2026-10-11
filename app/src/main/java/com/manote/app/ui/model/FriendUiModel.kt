package com.manote.app.ui.model

import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.DatedNet
import com.manote.app.domain.OpponentMatch
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.SessionRules
import com.manote.app.domain.Trend
import com.manote.app.domain.TrendPoint

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
    /** 是否是同桌净输最多的那位（「最苦主」）；由 [com.manote.app.domain.Rivalry] 判定，不是本行自己算的。 */
    val isWorstRival: Boolean = false,
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
    val sessions: List<FriendSessionRowUi>,
)

/**
 * 档案 + 聚合 → 列表行。
 *
 * 聚合缺失（该麻友还没参与过任何场次）时场数与净输赢都为 0，
 * 而不是把整行丢掉——刚建好的麻友必须能在列表里看到。
 *
 * [isWorstRival] 由调用方用 [com.manote.app.domain.Rivalry.worstRival] 算好后传入：
 * 「最苦主」要横比所有牌友，单看一行是判不出来的。
 */
fun PlayerEntity.toRowUi(
    aggregate: PlayerAggregate?,
    isWorstRival: Boolean = false,
): FriendRowUi = FriendRowUi(
    id = id,
    nickname = nickname,
    note = note,
    sessionCount = aggregate?.sessionCount ?: 0,
    netMinor = aggregate?.netMinor ?: 0L,
    archived = archivedAt != null,
    updatedAt = updatedAt,
    isWorstRival = isWorstRival,
)

/**
 * 一场 → 与各位同桌牌友的净额对照（「最苦主」的输入）。
 *
 * 本场的 seat 0 恒为本人；缺 seat 0 说明库已损坏，这时返回空列表，
 * 宁可这一场不参与评选，也不拿一个错误的基准去和人比。
 */
fun SessionWithResults.opponentMatches(): List<OpponentMatch> {
    val selfNet = results.firstOrNull { it.seat == SessionRules.SEAT_SELF }?.netMinor
        ?: return emptyList()
    return results
        .filter { it.seat != SessionRules.SEAT_SELF }
        .map { OpponentMatch(playerId = it.playerId, myNetMinor = selfNet, theirNetMinor = it.netMinor) }
}

/**
 * 档案 + 统计 + 参与的场次 → 麻友详情。
 *
 * 逐场记录按场次倒序（与数据库查询顺序一致）；某场里找不到这位玩家的结果时跳过该场，
 * 而不是用 0 顶替——那会凭空造出一条他没参加过的记录。
 *
 * 净额走势按时间**升序**累加（图从左到右即时间先后），因此把逐场记录反过来再交给 [Trend]。
 * 只有一场时也照常给出一个点，由界面决定画不画得出来。
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
    return FriendDetailUi(
        id = id,
        nickname = nickname,
        note = note,
        archived = archivedAt != null,
        statistics = statistics,
        trend = Trend.cumulative(
            rows.asReversed().map { DatedNet(playedAt = it.playedAt, netMinor = it.netMinor) },
        ),
        sessions = rows,
    )
}
