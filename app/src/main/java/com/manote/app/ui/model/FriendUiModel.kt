package com.manote.app.ui.model

import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.SessionRules

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
)

/** 麻友详情：档案 + 跨场统计 + 逐场记录。 */
data class FriendDetailUi(
    val id: String,
    val nickname: String,
    val note: String,
    val archived: Boolean,
    val statistics: PlayerStatistics,
    val sessions: List<FriendSessionRowUi>,
)

/**
 * 档案 + 聚合 → 列表行。
 *
 * 聚合缺失（该麻友还没参与过任何场次）时场数与净输赢都为 0，
 * 而不是把整行丢掉——刚建好的麻友必须能在列表里看到。
 */
fun PlayerEntity.toRowUi(aggregate: PlayerAggregate?): FriendRowUi = FriendRowUi(
    id = id,
    nickname = nickname,
    note = note,
    sessionCount = aggregate?.sessionCount ?: 0,
    netMinor = aggregate?.netMinor ?: 0L,
    archived = archivedAt != null,
    updatedAt = updatedAt,
)

/**
 * 档案 + 统计 + 参与的场次 → 麻友详情。
 *
 * 逐场记录按场次倒序（与数据库查询顺序一致）；某场里找不到这位玩家的结果时跳过该场，
 * 而不是用 0 顶替——那会凭空造出一条他没参加过的记录。
 */
fun PlayerEntity.toDetailUi(
    statistics: PlayerStatistics,
    sessionsWithResults: List<SessionWithResults>,
): FriendDetailUi = FriendDetailUi(
    id = id,
    nickname = nickname,
    note = note,
    archived = archivedAt != null,
    statistics = statistics,
    sessions = sessionsWithResults.mapNotNull { item ->
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
    },
)
