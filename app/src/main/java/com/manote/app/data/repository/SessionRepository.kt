package com.manote.app.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.data.local.SessionWithResults
import com.manote.app.domain.ManoteError
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.SessionDuration
import com.manote.app.domain.SessionRules
import com.manote.app.domain.SessionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 新建场次时对某个座位的输入。
 * 只带 playerId 与金额；昵称快照在事务内从 Player 表读取，不使用 UI 传值。
 */
data class SeatResultInput(
    val seat: Int,
    val playerId: String,
    val netMinor: Long,
)

/** 新建一整场的草稿。id 由调用方生成，同次提交重试复用，避免连点重复记场。 */
data class SessionDraft(
    val id: String,
    val playedAt: Long,
    val title: String,
    val note: String,
    val seats: List<SeatResultInput>,
    /**
     * 本场时长（分钟）；界面留空时由调用方填入 [SessionDuration.DEFAULT_MINUTES]。
     * 这里再给一次默认值，是为了让「没填就是 4 小时」这条规则在漏传时也成立。
     */
    val durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
)

/** 修改一整场：只允许改标题、结束时间、时长、备注与四人金额，不支持换人员与位置。 */
data class SessionEdit(
    val id: String,
    val expectedRevision: Long,
    val playedAt: Long,
    val title: String,
    val note: String,
    val netMinorBySeat: Map<Int, Long>,
    /** 本场时长（分钟）。与金额一样，属于「可以改错、可以改回」的内容。 */
    val durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
)

/** 场次详情：场次本身加四个座位结果。 */
data class SessionDetail(
    val session: SessionEntity,
    val results: List<SessionResultEntity>,
)

/**
 * 场次事务（规格 1.3、2.3、5.1）。
 *
 * 新建、修改、删除都在单个 Room 事务内完成，并在事务内重新校验身份、重复、金额与时间，
 * 不信任 UI 的旧状态。全部写入经 [WriteSerializer] 串行化。
 *
 * 一场记录 1～4 个座位：只记录已选牌友的位置，seat 0 必为本人；
 * 合计不为 0 属于正常情况，事务不再因此拒绝（规格 1.3）。
 */
class SessionRepository(
    private val database: ManoteDatabase,
    private val writeSerializer: WriteSerializer,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val sessionDao = database.sessionDao()
    private val sessionResultDao = database.sessionResultDao()
    private val playerDao = database.playerDao()

    /**
     * 新建一整场：一次事务写入 Session 与**每个已记录座位**的一条 SessionResult，全部成功才返回成功。
     * 未提交的草稿不入库。
     */
    suspend fun createSession(draft: SessionDraft): ManoteResult<String> =
        writeSerializer.withWriteLock {
            database.withTransaction { createInTransaction(draft) }
        }

    private suspend fun createInTransaction(draft: SessionDraft): ManoteResult<String> {
        val now = clock()

        SessionValidator.validatePlayedAt(draft.playedAt, now)?.let {
            return ManoteResult.Failure(it)
        }
        SessionValidator.validateDuration(draft.durationMinutes)?.let {
            return ManoteResult.Failure(it)
        }
        val title = when (val result = SessionValidator.normalizeTitle(draft.title)) {
            is ManoteResult.Success -> result.value
            is ManoteResult.Failure -> return result
        }
        val note = when (val result = SessionValidator.normalizeNote(draft.note)) {
            is ManoteResult.Success -> result.value
            is ManoteResult.Failure -> return result
        }

        validateSeatInputs(draft.seats)?.let { return ManoteResult.Failure(it) }

        val netsBySeat = draft.seats.associate { it.seat to it.netMinor }
        SessionValidator.validateSeatNets(netsBySeat)?.let { return ManoteResult.Failure(it) }

        // 同 ID 重试不能插入第二场。
        if (sessionDao.findById(draft.id) != null) {
            return ManoteResult.Failure(ManoteError.SESSION_ID_ALREADY_EXISTS)
        }
        if (sessionDao.count() >= SessionRules.MAX_SESSIONS) {
            return ManoteResult.Failure(ManoteError.SESSION_LIMIT_REACHED)
        }

        val playersById = loadPlayersOrNull(draft.seats.map { it.playerId })
            ?: return ManoteResult.Failure(ManoteError.PLAYER_NOT_FOUND)

        // 身份与归档状态在事务内重新判定（规格 2.2、5.1）。
        for (input in draft.seats) {
            val player = playersById.getValue(input.playerId)
            if (input.seat == SessionRules.SEAT_SELF) {
                if (player.kind != PlayerKind.SELF) {
                    return ManoteResult.Failure(ManoteError.SEAT_ZERO_NOT_SELF)
                }
            } else {
                if (player.kind != PlayerKind.FRIEND) {
                    return ManoteResult.Failure(ManoteError.NON_SELF_SEAT_NOT_FRIEND)
                }
                if (player.archivedAt != null) {
                    return ManoteResult.Failure(ManoteError.FRIEND_ARCHIVED)
                }
            }
        }

        val session = SessionEntity(
            id = draft.id,
            title = title,
            playedAt = draft.playedAt,
            durationMinutes = draft.durationMinutes,
            note = note,
            createdAt = now,
            updatedAt = now,
            revision = 1L,
        )
        try {
            sessionDao.insert(session)
        } catch (_: SQLiteConstraintException) {
            // 前置已校验，此处只可能是主键冲突（同 ID 重试或并发连点）。
            return ManoteResult.Failure(ManoteError.SESSION_ID_ALREADY_EXISTS)
        }

        sessionResultDao.insertAll(
            draft.seats.map { input ->
                SessionResultEntity(
                    sessionId = draft.id,
                    seat = input.seat,
                    playerId = input.playerId,
                    nameSnapshot = playersById.getValue(input.playerId).nickname,
                    netMinor = input.netMinor,
                )
            },
        )

        return ManoteResult.Success(draft.id)
    }

    /**
     * 修改整场：使用原 Session ID 原子替换结果，不新增场次、不叠加金额。
     * 人员、座位与昵称快照保持不变（座位集合必须与保存时完全一致）。
     */
    suspend fun updateSession(edit: SessionEdit): ManoteResult<Long> =
        writeSerializer.withWriteLock {
            database.withTransaction { updateInTransaction(edit) }
        }

    private suspend fun updateInTransaction(edit: SessionEdit): ManoteResult<Long> {
        val now = clock()

        val session = sessionDao.findById(edit.id)
            ?: return ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
        // 已在回收站里的场次视同已删除：不允许再编辑一份不在列表里的记录。
        // 单独判一次而不是只靠 update 语句的 `deletedAt IS NULL`，是为了给出
        // 「已删除」而不是「版本冲突」——后者会让用户以为重新加载就能救回来。
        if (session.deletedAt != null) {
            return ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
        }

        SessionValidator.validatePlayedAt(edit.playedAt, now)?.let {
            return ManoteResult.Failure(it)
        }
        SessionValidator.validateDuration(edit.durationMinutes)?.let {
            return ManoteResult.Failure(it)
        }
        val title = when (val result = SessionValidator.normalizeTitle(edit.title)) {
            is ManoteResult.Success -> result.value
            is ManoteResult.Failure -> return result
        }
        val note = when (val result = SessionValidator.normalizeNote(edit.note)) {
            is ManoteResult.Success -> result.value
            is ManoteResult.Failure -> return result
        }

        val existingResults = sessionResultDao.findBySession(edit.id)
        val existingSeats = existingResults.map { it.seat }.toSet()
        // 已保存的场次至少有一条本人结果；不满足说明库已损坏，明确报错而不是修补。
        if (existingResults.isEmpty() ||
            existingSeats.size != existingResults.size ||
            SessionValidator.validateSeatSet(existingSeats) != null
        ) {
            return ManoteResult.Failure(ManoteError.INTERNAL_STATE_CORRUPTED)
        }

        // 人员与位置只读：提交的座位集合必须与保存时逐一对应（规格 2.3）。
        if (edit.netMinorBySeat.keys != existingSeats) {
            return ManoteResult.Failure(ManoteError.INVALID_SEAT_SET)
        }
        SessionValidator.validateSeatNets(edit.netMinorBySeat)?.let { return ManoteResult.Failure(it) }

        // revision 一致才提交并 +1；已被删除或版本已变时拒绝，保留草稿由 UI 负责（规格 2.3）。
        val updatedRows = sessionDao.updateIfRevisionMatches(
            id = session.id,
            expectedRevision = edit.expectedRevision,
            title = title,
            playedAt = edit.playedAt,
            durationMinutes = edit.durationMinutes,
            note = note,
            updatedAt = now,
        )
        if (updatedRows == 0) {
            return ManoteResult.Failure(ManoteError.SESSION_REVISION_CONFLICT)
        }

        sessionResultDao.deleteBySession(session.id)
        sessionResultDao.insertAll(
            existingResults.map { existing ->
                SessionResultEntity(
                    sessionId = session.id,
                    seat = existing.seat,
                    playerId = existing.playerId,
                    nameSnapshot = existing.nameSnapshot,
                    netMinor = edit.netMinorBySeat.getValue(existing.seat),
                )
            },
        )

        return ManoteResult.Success(edit.expectedRevision + 1L)
    }

    /**
     * 删除整场 → **移入回收站**（软删除，规格 3.3）。
     *
     * 只打上删除时间，结果行原样保留：这一场立刻从列表、统计与牌友战绩里消失，
     * 但在回收站里还能还原。Player 档案不受影响。真正不可逆的清除只有
     * [purgeSession] 与 [emptyTrash] 两条路。
     */
    suspend fun moveSessionToTrash(sessionId: String): ManoteResult<Unit> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                val now = clock()
                if (sessionDao.moveToTrash(sessionId, deletedAt = now, updatedAt = now) == 0) {
                    return@withTransaction ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
                }
                ManoteResult.Success(Unit)
            }
        }

    /**
     * 从回收站还原整场。
     *
     * 还原会重新占用一个在场名额，因此这里复查 1,000 场上限：满额时明确失败，
     * 而不是先把它放回来再让下一次记一场撞上限。
     */
    suspend fun restoreSession(sessionId: String): ManoteResult<Unit> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                if (sessionDao.count() >= SessionRules.MAX_SESSIONS) {
                    return@withTransaction ManoteResult.Failure(ManoteError.SESSION_LIMIT_REACHED)
                }
                if (sessionDao.restoreFromTrash(sessionId, updatedAt = clock()) == 0) {
                    return@withTransaction ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
                }
                ManoteResult.Success(Unit)
            }
        }

    /** 从回收站彻底删除一场：清掉结果行再删场次，之后无法再还原。 */
    suspend fun purgeSession(sessionId: String): ManoteResult<Unit> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                if (sessionDao.deleteFromTrashById(sessionId) == 0) {
                    return@withTransaction ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
                }
                sessionResultDao.deleteBySession(sessionId)
                ManoteResult.Success(Unit)
            }
        }

    /** 清空回收站，返回被彻底删除的场次数。 */
    suspend fun emptyTrash(): ManoteResult<Int> =
        writeSerializer.withWriteLock {
            database.withTransaction { ManoteResult.Success(purgeTrashWhere(sessionDao.findTrashedIds())) }
        }

    /**
     * 清理移入回收站超过 [retentionMillis] 的场次，返回清掉的场次数。
     * 由应用启动时调用一次，保证回收站不会无限增长。
     */
    suspend fun purgeExpiredTrash(retentionMillis: Long = TRASH_RETENTION_MILLIS): Int =
        writeSerializer.withWriteLock {
            database.withTransaction {
                purgeTrashWhere(sessionDao.findTrashedIdsBefore(clock() - retentionMillis))
            }
        }

    /** 结果行先删、场次后删：顺序由外键决定，且必须在同一个事务里完成。 */
    private suspend fun purgeTrashWhere(ids: List<String>): Int {
        ids.forEach { sessionResultDao.deleteBySession(it) }
        ids.forEach { sessionDao.deleteById(it) }
        return ids.size
    }

    /** 读取场次详情；只读，不参与写串行。 */
    suspend fun getSessionDetail(sessionId: String): ManoteResult<SessionDetail> {
        val session = sessionDao.findById(sessionId)
            ?: return ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)
        return ManoteResult.Success(
            SessionDetail(session = session, results = sessionResultDao.findBySession(sessionId)),
        )
    }

    /**
     * 订阅全部场次及其结果，按 playedAt 倒序、同时间按 ID 稳定排序（规格 2.4）。
     * 首页统计、最近场次与场次列表共用这一条查询。
     */
    fun observeSessions(): Flow<List<SessionWithResults>> = sessionDao.observeAllWithResults()

    /**
     * 订阅单个场次及其结果；场次不存在（已被删除）时发出 null，
     * 供四人详情页显示「该场次已不存在」而不是崩溃。
     */
    fun observeSessionDetail(sessionId: String): Flow<SessionWithResults?> =
        sessionDao.observeByIdWithResults(sessionId).map { it.firstOrNull() }

    /**
     * 订阅某位玩家参与过的全部场次（规格 3.4 逐场统计）。
     * 麻友详情页据此同时显示麻友与本人各自的本场结果。
     */
    fun observeSessionsByPlayer(playerId: String): Flow<List<SessionWithResults>> =
        sessionDao.observeByPlayerWithResults(playerId)

    /**
     * 订阅回收站里的场次及其结果，按移入时间倒序（规格 3.3）。
     * 与 [observeSessions] 互补：两者互不重叠，加起来才是库里的全部行。
     */
    fun observeTrash(): Flow<List<SessionWithResults>> = sessionDao.observeTrashWithResults()

    /** 回收站里的场次数，用于「我的」页上的入口摘要。 */
    suspend fun trashCount(): Int = sessionDao.trashCount()

    /** 订阅回收站场次数，供「我的」页上的入口摘要实时刷新。 */
    fun observeTrashCount(): Flow<Int> = sessionDao.observeTrashCount()

    /**
     * 座位必须是 0～3 中互不相同的一个集合（1～4 个、必含 seat 0），
     * 且同一场里四个 playerId 互不相同。
     */
    private fun validateSeatInputs(seats: List<SeatResultInput>): ManoteError? {
        val seatNumbers = seats.map { it.seat }
        if (seatNumbers.toSet().size != seatNumbers.size) return ManoteError.INVALID_SEAT_SET
        SessionValidator.validateSeatSet(seatNumbers.toSet())?.let { return it }
        if (seats.map { it.playerId }.toSet().size != seats.size) {
            return ManoteError.DUPLICATE_PLAYER
        }
        return null
    }

    private suspend fun loadPlayersOrNull(ids: List<String>) =
        if (ids.isEmpty()) {
            null
        } else {
            playerDao.findByIds(ids)
                .takeIf { it.size == ids.toSet().size }
                ?.associateBy { it.id }
        }

    companion object {
        /**
         * 回收站保留时长：30 天。
         *
         * 回收站是一个「刚删错、还来得及」的缓冲，不是一个归档区；
         * 超过这个时间的记录在下次启动时被清掉，免得它无限增长。
         */
        const val TRASH_RETENTION_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L
    }
}
