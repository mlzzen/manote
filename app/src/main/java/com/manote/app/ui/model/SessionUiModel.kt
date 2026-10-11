package com.manote.app.ui.model

import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.Ranking
import com.manote.app.domain.SeatNet
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
     * 打完时间所属时段（早场 / 下午场 / 晚场）。
     *
     * 与界面显示时间用同一个时区判定，列表里「21:30」旁边不会出现「早场」。
     */
    val timeSlot: TimeSlot,
    /** 本人（seat 0）本场净额。 */
    val selfNetMinor: Long,
    /** 其余各位的昵称快照，按 seat 升序；本场只记录了部分位置时会少于三个（规格 1.3）。 */
    val opponentNames: List<String>,
    /**
     * 关键词搜索用的比对串：标题 + 四人当时的昵称快照，已统一转小写。
     * 预先算好，搜索时只做一次 `contains`，不必在每次按键时重新拼字符串。
     */
    val searchHaystack: String,
) {
    /**
     * 用户是否真的起过标题。
     *
     * 为 false 时列表不显示标题行——[SessionRules.DEFAULT_TITLE] 是占位名，
     * 每行都重复一句「麻将对局」只是噪音。
     * 搜索串里仍然保留它，用「麻将」依旧搜得到这些场次。
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
    val playedAt: Long,
    /** 打完时间所属时段（早场 / 下午场 / 晚场），与列表行同源同口径。 */
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
 *
 * 搜索串按规格 3.3 只含「标题 + 历史昵称（nameSnapshot）」，
 * 不把备注纳入，否则「搜索标题」会被备注里的词意外命中。
 */
fun SessionWithResults.toRowUi(zoneId: ZoneId = ZoneId.systemDefault()): SessionRowUi {
    val ordered = results.sortedBy { it.seat }
    val self = ordered.firstOrNull { it.seat == SessionRules.SEAT_SELF }
    return SessionRowUi(
        id = session.id,
        title = session.title,
        note = session.note,
        playedAt = session.playedAt,
        timeSlot = TimeSlot.of(session.playedAt, zoneId),
        selfNetMinor = self?.netMinor ?: 0L,
        opponentNames = ordered.filter { it.seat != SessionRules.SEAT_SELF }.map { it.nameSnapshot },
        searchHaystack = (listOf(session.title) + ordered.map { it.nameSnapshot })
            .joinToString(SEARCH_HAYSTACK_SEPARATOR)
            .lowercase(),
    )
}

/** 用换行分隔，避免「标题结尾 + 昵称开头」拼出一个并不存在的词而被误命中。 */
private const val SEARCH_HAYSTACK_SEPARATOR = "\n"

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
        timeSlot = TimeSlot.of(session.playedAt, zoneId),
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
