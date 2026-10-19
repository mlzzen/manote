package com.manote.app.ui.model

import com.manote.app.data.local.PlayerEntity
import com.manote.app.domain.TimeSlot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 本人这一场的结果分类，供「输赢」筛选使用。
 *
 * 与列表行上的结果标签同一口径：正数赢、负数输、0 持平（规格 2.1）。
 */
enum class SessionOutcome {
    WIN,
    LOSS,
    DRAW,
    ;

    companion object {
        fun of(netMinor: Long): SessionOutcome = when {
            netMinor > 0L -> WIN
            netMinor < 0L -> LOSS
            else -> DRAW
        }
    }
}

/**
 * 场次列表的筛选条件。
 *
 * **每一组留空即「不限」**，没有「全部」这种特殊态——空集合与 null 就是全部，
 * 少一个枚举值，也就少一处「全部」与「未选」不一致的可能。
 *
 * 组与组之间是「且」，组内多选是「或」：选了老王和小李、又选了晚场，
 * 得到的是「和他们中的任一位打过的晚场」。
 *
 * 时间有三个刻度：年、月、日，层层收窄。选某一天时会一并落到年与月上
 * （见 [withDate]），因此界面上三个刻度显示的是同一件事的三个粒度，
 * 而不是三种互相打架的条件。年与月可以单独成立——「所有年份的 10 月」也是有用的筛选。
 */
data class SessionFilters(
    val timeSlots: Set<TimeSlot> = emptySet(),
    val year: Int? = null,
    val month: Int? = null,
    val date: LocalDate? = null,
    /** 参与过这一场的牌友（按 playerId，昵称改了也仍然筛得到）。 */
    val friendIds: Set<String> = emptySet(),
    val outcomes: Set<SessionOutcome> = emptySet(),
) {
    val isActive: Boolean get() = activeGroupCount > 0

    /**
     * 生效的组数，而不是生效的选项数。
     *
     * 顶栏按钮上的小圆点、弹层里的「重置」都只关心「有没有在筛」，
     * 选了三个时段仍然只是一组条件，用选项数会让圆点看起来比实际更严重。
     */
    val activeGroupCount: Int
        get() = listOf(
            timeSlots.isNotEmpty(),
            year != null || month != null || date != null,
            friendIds.isNotEmpty(),
            outcomes.isNotEmpty(),
        ).count { it }

    fun toggleTimeSlot(value: TimeSlot): SessionFilters =
        copy(timeSlots = timeSlots.toggled(value))

    fun toggleFriend(playerId: String): SessionFilters =
        copy(friendIds = friendIds.toggled(playerId))

    fun toggleOutcome(value: SessionOutcome): SessionFilters =
        copy(outcomes = outcomes.toggled(value))

    /**
     * 选年 / 月会把「某一天」清掉：年份换了，原来那天自然不再成立；
     * 而清掉某一天时保留年与月，于是「10 月里具体哪天」可以退回到「整个 10 月」。
     */
    fun withYear(value: Int?): SessionFilters = copy(year = value, date = null)

    fun withMonth(value: Int?): SessionFilters = copy(month = value, date = null)

    /** 选某一天：年、月一并落到这一天所在的年月上，三个刻度不会互相矛盾。 */
    fun withDate(value: LocalDate?): SessionFilters =
        if (value == null) {
            copy(date = null)
        } else {
            copy(year = value.year, month = value.monthValue, date = value)
        }
}

/** 筛选弹层里的一位牌友选项。 */
data class SessionFriendOption(
    val id: String,
    val nickname: String,
    /**
     * 是否需要补一个 ID 短串。
     *
     * 昵称允许重名（规格 2.2），两个「老王」并排时只能靠短串分辨——
     * 与牌友选择器里的处理一致（规格 3.4）。
     */
    val showId: Boolean = false,
)

/**
 * 场次筛选。
 *
 * 纯函数，便于单元测试。时间一律按设备时区判断，与列表显示同一套时区，
 * 不会出现「列表显示 10 月 5 日、却被 10 月 4 日的筛选漏掉」。
 *
 * 全部在内存里完成：上限 1000 场，一次全量遍历的开销远小于一次额外查询，
 * 也省掉了「筛选条件怎么翻译成 SQL」这层容易出错的转换。
 */
object SessionFiltering {

    fun apply(
        rows: List<SessionRowUi>,
        filters: SessionFilters,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<SessionRowUi> {
        if (!filters.isActive) return rows
        return rows.filter { matches(it, filters, zoneId) }
    }

    /** 单行是否命中全部条件。 */
    fun matches(
        row: SessionRowUi,
        filters: SessionFilters,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (filters.timeSlots.isNotEmpty() && row.timeSlot !in filters.timeSlots) return false
        if (filters.friendIds.isNotEmpty() && row.opponentPlayerIds.none { it in filters.friendIds }) {
            return false
        }
        if (filters.outcomes.isNotEmpty() && SessionOutcome.of(row.selfNetMinor) !in filters.outcomes) {
            return false
        }
        if (filters.year == null && filters.month == null && filters.date == null) return true

        // 只有真的用到年月日时才解析时间：只筛时段或牌友时，这一行可以完全不碰日历
        val playedOn = dateOf(row.playedAt, zoneId)
        if (filters.year != null && playedOn.year != filters.year) return false
        if (filters.month != null && playedOn.monthValue != filters.month) return false
        if (filters.date != null && playedOn != filters.date) return false
        return true
    }

    /**
     * 年份选项：库里出现过的年份，由近到远。
     *
     * 只列出现过的年份，是因为「筛出一个必然为空的年份」对用户没有意义，
     * 却会让一排胶囊越铺越长。
     */
    fun yearOptions(
        rows: List<SessionRowUi>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Int> = rows
        .map { dateOf(it.playedAt, zoneId).year }
        .distinct()
        .sortedDescending()

    /**
     * 牌友选项：曾经同桌过的牌友，同桌次数多的排在前。
     *
     * 昵称优先取档案里的当前昵称（改名后筛选项跟着改），档案缺失时回落到
     * 最近一场保存的昵称快照——显示的仍是这一桌上当时那个名字。
     *
     * 一次没同桌过的牌友不出现在这里：选了他也不可能有结果。
     */
    fun friendOptions(
        rows: List<SessionRowUi>,
        friends: List<PlayerEntity>,
    ): List<SessionFriendOption> {
        val snapshotById = LinkedHashMap<String, String>()
        val countById = HashMap<String, Int>()
        // rows 已按 playedAt 倒序，因此 putIfAbsent 留下的是最近一场的昵称
        for (row in rows) {
            for ((index, playerId) in row.opponentPlayerIds.withIndex()) {
                snapshotById.putIfAbsent(playerId, row.opponentNames.getOrElse(index) { "" })
                countById[playerId] = (countById[playerId] ?: 0) + 1
            }
        }
        if (snapshotById.isEmpty()) return emptyList()

        val nicknameById = friends.associate { it.id to it.nickname }
        val options = snapshotById.map { (playerId, snapshot) ->
            SessionFriendOption(
                id = playerId,
                nickname = nicknameById[playerId]?.takeIf { it.isNotBlank() } ?: snapshot,
            )
        }
        val duplicatedNames = options
            .groupingBy { it.nickname }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        return options
            .map { it.copy(showId = it.nickname in duplicatedNames) }
            .sortedWith(
                compareByDescending<SessionFriendOption> { countById[it.id] ?: 0 }
                    .thenBy { it.nickname },
            )
    }
}

/** 多选胶囊的点击语义：已在集合里就取消，不在就加上。 */
private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value

/**
 * 时间戳 → 设备时区下的日期。
 *
 * 与 [UiText.toLocalDate] 是同一套换算，这里单写一份是为了不让筛选逻辑
 * 依赖界面文案那一层；「按哪个时区算日期」在这个文件里因此只有一个答案。
 */
private fun dateOf(epochMillis: Long, zoneId: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
