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
)

/** 修改一整场：只允许改标题、打完时间、备注与四人金额，不支持换人员与位置。 */
data class SessionEdit(
    val id: String,
    val expectedRevision: Long,
    val playedAt: Long,
    val title: String,
    val note: String,
    val netMinorBySeat: Map<Int, Long>,
)

/** 场次详情：场次本身加四个座位结果。 */
data class SessionDetail(
    val session: SessionEntity,
    val results: List<SessionResultEntity>,
)

/**
 * 场次事务（规格 2.3、5.1）。
 *
 * 新建、修改、删除都在单个 Room 事务内完成，并在事务内重新校验身份、重复、金额与时间，
 * 不信任 UI 的旧状态。全部写入经 [WriteSerializer] 串行化。
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
     * 新建一整场：一次事务写入 Session 与四条 SessionResult，全部成功才返回成功。
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
     * 人员、座位与昵称快照保持不变。
     */
    suspend fun updateSession(edit: SessionEdit): ManoteResult<Long> =
        writeSerializer.withWriteLock {
            database.withTransaction { updateInTransaction(edit) }
        }

    private suspend fun updateInTransaction(edit: SessionEdit): ManoteResult<Long> {
        val now = clock()

        val session = sessionDao.findById(edit.id)
            ?: return ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)

        SessionValidator.validatePlayedAt(edit.playedAt, now)?.let {
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
        val expectedSeats = (0 until SessionRules.SEAT_COUNT).toSet()
        if (existingResults.size != SessionRules.SEAT_COUNT ||
            existingResults.map { it.seat }.toSet() != expectedSeats
        ) {
            // 已保存的场次必然有四条结果；不满足说明库已损坏，明确报错而不是修补。
            return ManoteResult.Failure(ManoteError.INTERNAL_STATE_CORRUPTED)
        }

        if (edit.netMinorBySeat.keys != expectedSeats) {
            return ManoteResult.Failure(ManoteError.INVALID_SEAT_SET)
        }
        SessionValidator.validateSeatNets(edit.netMinorBySeat)?.let { return ManoteResult.Failure(it) }

        // revision 一致才提交并 +1；已被删除或版本已变时拒绝，保留草稿由 UI 负责（规格 2.3）。
        val updatedRows = sessionDao.updateIfRevisionMatches(
            id = session.id,
            expectedRevision = edit.expectedRevision,
            title = title,
            playedAt = edit.playedAt,
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

    /** 删除整场：清掉四条结果，保留 Player 档案，应用内不提供恢复。 */
    suspend fun deleteSession(sessionId: String): ManoteResult<Unit> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                sessionDao.findById(sessionId)
                    ?: return@withTransaction ManoteResult.Failure(ManoteError.SESSION_NOT_FOUND)

                sessionResultDao.deleteBySession(sessionId)
                sessionDao.deleteById(sessionId)
                ManoteResult.Success(Unit)
            }
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

    /** 座位必须是 0、1、2、3 各一次，且四个 playerId 互不相同。 */
    private fun validateSeatInputs(seats: List<SeatResultInput>): ManoteError? {
        val expectedSeats = (0 until SessionRules.SEAT_COUNT).toSet()
        if (seats.size != SessionRules.SEAT_COUNT) return ManoteError.INVALID_SEAT_SET
        if (seats.map { it.seat }.toSet() != expectedSeats) return ManoteError.INVALID_SEAT_SET
        if (seats.map { it.playerId }.toSet().size != SessionRules.SEAT_COUNT) {
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
}
