package com.manote.app.ui.model

import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.Ranking
import com.manote.app.domain.SeatNet
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.SessionRules
import com.manote.app.domain.Settlement
import com.manote.app.domain.TimeSlot
import java.time.ZoneId

/** 某个座位在界面上的展示数据。 */
data class SeatUi(
    /** 0=自己、1=上家、2=对家、3=下家。 */
    val seat: Int,
    /** 保存时的昵称快照。 */
    val nickname: String,
    /** 本场净额，分。 */
    val netMinor: Long,
    /** 本场名次（竞赛排名）。 */
    val rank: Int,
)

/** 首页「最近场次」与场次列表共用的一行数据。 */
data class SessionRowUi(
    val id: String,
    val title: String,
    val note: String,
    val playedAt: Long,
    /**
     * 本场时段（早场 / 下午场 / 晚场）。
     *
     * 按「结束时间 − 时长 ÷ 2」算出的**中点**判定，而不是结束时间或开始时间：
     * 14:00 打到 18:30 是下午场，17:30 打到 21:30 是晚场，通宵到早上 6:00 也仍是晚场。
     * 与界面显示用同一个时区，不会出现「显示 21:30、却算成下午场」。
     */
    val timeSlot: TimeSlot,
    /** 本人（seat 0）本场净额。 */
    val selfNetMinor: Long,
    /** 其余各位的昵称快照，按 seat 升序；本场只记录了部分位置时会少于三个（规格 1.3）。 */
    val opponentNames: List<String>,
    /**
     * 其余各位的档案 ID，顺序与 [opponentNames] 严格一一对应。
     *
     * 「按牌友筛选」认 ID 不认昵称：麻友改名后，旧场次的昵称快照仍是旧名，
     * 按名字筛会漏掉他自己那些场次；两个同名牌友也会被并成一个人。
     * 默认空列表只服务于测试样张，真实数据一律由 [toRowUi] 填满。
     */
    val opponentPlayerIds: List<String> = emptyList(),
) {
    /**
     * 用户是否真的起过标题。
     *
     * 为 false 时列表不显示标题行——[SessionRules.DEFAULT_TITLE] 是占位名，
     * 每行都重复一句「麻将对局」只是噪音。
     */
    val hasCustomTitle: Boolean get() = !SessionRules.isPlaceholderTitle(title)
}

/** 一笔结清建议的展示数据。 */
data class TransferUi(val fromSeat: Int, val toSeat: Int, val amountMinor: Long)

/** 四人详情页的展示数据。 */
data class SessionDetailUi(
    val id: String,
    val title: String,
    val note: String,
    /** 结束时间。 */
    val playedAt: Long,
    /** 由结束时间与时长倒推出的开始时间；展示时间区间用。 */
    val startedAt: Long,
    /** 本场时长（分钟）。 */
    val durationMinutes: Int,
    /** 本场中点所属时段（早场 / 下午场 / 晚场），与列表行同源同口径。 */
    val timeSlot: TimeSlot,
    /** 已记录的座位，按 seat 升序。可能少于四个（规格 1.3）。 */
    val seats: List<SeatUi>,
    /** 已记录座位的净额合计，分。 */
    val totalMinor: Long,
    /**
     * 已记录座位的合计是否为 0。
     *
     * 为 false 时 [transfers] 恒为空——结清算法假定合计为零才能撮合完，
     * 否则会输出一笔算错的余额（规格 1.3）。
     */
    val balanced: Boolean,
    /** 结清建议，最多三笔；空表示无需结清或本场不平账。 */
    val transfers: List<TransferUi>,
)

/**
 * 场次 → 列表行。
 *
 * 本人结果取 seat 0（规格 2.2：每场的 seat 0 必为 SELF）。
 * 若库处于异常状态导致缺少 seat 0，净额按 0 展示而不是崩溃。
 */
fun SessionWithResults.toRowUi(zoneId: ZoneId = ZoneId.systemDefault()): SessionRowUi {
    val ordered = results.sortedBy { it.seat }
    val self = ordered.firstOrNull { it.seat == SessionRules.SEAT_SELF }
    // 昵称与 ID 必须来自同一个列表：筛选按 ID、显示按昵称，两者错位就会张冠李戴
    val opponents = ordered.filter { it.seat != SessionRules.SEAT_SELF }
    return SessionRowUi(
        id = session.id,
        title = session.title,
        note = session.note,
        playedAt = session.playedAt,
        timeSlot = timeSlotOf(session.playedAt, session.durationMinutes, zoneId),
        selfNetMinor = self?.netMinor ?: 0L,
        opponentNames = opponents.map { it.nameSnapshot },
        opponentPlayerIds = opponents.map { it.playerId },
    )
}

/**
 * 时段判定的唯一入口：先由结束时间与时长算出这一场的中点，再看中点落在哪个时段。
 *
 * 抽成一处是为了让列表、详情、统计三处永远给同一场同一个答案——
 * 各自写一遍 `TimeSlot.of(...)`，改口径时必然漏掉一处。
 */
private fun timeSlotOf(playedAt: Long, durationMinutes: Int, zoneId: ZoneId): TimeSlot =
    TimeSlot.of(SessionDuration.midpointAt(playedAt, durationMinutes), zoneId)

/** 场次 → 四人详情。排名与结清都在这里一次算好，Composable 只负责渲染。 */
fun SessionWithResults.toDetailUi(zoneId: ZoneId = ZoneId.systemDefault()): SessionDetailUi {
    val ordered = results.sortedBy { it.seat }
    val nets = ordered.map { SeatNet(seat = it.seat, netMinor = it.netMinor) }
    val ranksBySeat = Ranking.rank(nets).associate { it.seat to it.rank }
    val total = nets.sumOf { it.netMinor }
    val balanced = total == 0L
    return SessionDetailUi(
        id = session.id,
        title = session.title,
        note = session.note,
        playedAt = session.playedAt,
        startedAt = SessionDuration.startAt(session.playedAt, session.durationMinutes),
        durationMinutes = session.durationMinutes,
        timeSlot = timeSlotOf(session.playedAt, session.durationMinutes, zoneId),
        seats = ordered.map { result ->
            SeatUi(
                seat = result.seat,
                nickname = result.nameSnapshot,
                netMinor = result.netMinor,
                rank = ranksBySeat[result.seat] ?: 0,
            )
        },
        totalMinor = total,
        balanced = balanced,
        // 不平账时不给建议：撮合到最后一个赢家会剩下算不清的余额，宁可明确说「给不了」。
        transfers = if (balanced) {
            Settlement.suggest(nets).map {
                TransferUi(fromSeat = it.fromSeat, toSeat = it.toSeat, amountMinor = it.amountMinor)
            }
        } else {
            emptyList()
        },
    )
}
