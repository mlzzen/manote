package com.manote.app.data.repository

import androidx.room.withTransaction
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.local.PlayerAggregate
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.domain.ManoteError
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.PlayerStatistics
import com.manote.app.domain.SessionRules
import com.manote.app.domain.SessionValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 档案事务（规格 2.2、2.3）。
 *
 * 本人与麻友共用一张表；所有写入都经过 [WriteSerializer] 串行化并在事务中重新校验，
 * UI 不能直接调用 DAO。
 */
class PlayerRepository(
    private val database: ManoteDatabase,
    private val writeSerializer: WriteSerializer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    /**
     * 新建本人档案时用的默认昵称。
     *
     * 由容器注入当前语言的文案——英文设备上新建的本人档案叫 "Me" 而不是「我」。
     * 默认值仍是 [SessionRules.DEFAULT_SELF_NICKNAME]，因此不关心语言的调用方
     * （测试、纯 JVM 场景）拿到的还是同一个确定值。
     */
    private val defaultSelfNickname: String = SessionRules.DEFAULT_SELF_NICKNAME,
) {

    private val playerDao = database.playerDao()
    private val sessionResultDao = database.sessionResultDao()

    /**
     * 幂等创建唯一的 SELF（规格 2.2：首次启动事务创建，重启不重复创建）。
     *
     * 库中已有 SELF 时直接返回既有档案，ID 不变；多于一个说明库已损坏，明确报错而不是随便挑一个。
     */
    suspend fun ensureSelfPlayer(): ManoteResult<PlayerEntity> = writeSerializer.withWriteLock {
        database.withTransaction {
            when (playerDao.countByKind(PlayerKind.SELF)) {
                0 -> {
                    val now = clock()
                    val self = PlayerEntity(
                        id = idFactory(),
                        kind = PlayerKind.SELF,
                        nickname = defaultSelfNickname,
                        note = "",
                        archivedAt = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                    playerDao.insert(self)
                    ManoteResult.Success(self)
                }

                1 -> {
                    val existing = playerDao.findFirstByKind(PlayerKind.SELF)
                    if (existing == null) {
                        ManoteResult.Failure(ManoteError.INTERNAL_STATE_CORRUPTED)
                    } else {
                        ManoteResult.Success(existing)
                    }
                }

                else -> ManoteResult.Failure(ManoteError.INTERNAL_STATE_CORRUPTED)
            }
        }
    }

    /** 读取本人档案。 */
    suspend fun self(): PlayerEntity? = playerDao.findFirstByKind(PlayerKind.SELF)

    suspend fun findById(id: String): PlayerEntity? = playerDao.findById(id)

    /** 新建麻友。昵称允许重名，重名提示由 UI 负责（规格 2.2）。 */
    suspend fun createFriend(rawNickname: String, rawNote: String): ManoteResult<String> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                val nickname = when (val result = SessionValidator.normalizeNickname(rawNickname)) {
                    is ManoteResult.Success -> result.value
                    is ManoteResult.Failure -> return@withTransaction result
                }
                val note = when (val result = SessionValidator.normalizeNote(rawNote)) {
                    is ManoteResult.Success -> result.value
                    is ManoteResult.Failure -> return@withTransaction result
                }
                if (playerDao.countByKind(PlayerKind.FRIEND) >= SessionRules.MAX_FRIENDS) {
                    return@withTransaction ManoteResult.Failure(ManoteError.FRIEND_LIMIT_REACHED)
                }

                val now = clock()
                val friend = PlayerEntity(
                    id = idFactory(),
                    kind = PlayerKind.FRIEND,
                    nickname = nickname,
                    note = note,
                    archivedAt = null,
                    createdAt = now,
                    updatedAt = now,
                )
                playerDao.insert(friend)
                ManoteResult.Success(friend.id)
            }
        }

    /** 编辑麻友的昵称与备注。改名不影响历史场次中已保存的快照。 */
    suspend fun updateFriend(id: String, rawNickname: String, rawNote: String): ManoteResult<Unit> =
        writeSerializer.withWriteLock {
            database.withTransaction {
                val existing = playerDao.findById(id)
                when {
                    existing == null -> return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_NOT_FOUND)
                    existing.kind == PlayerKind.SELF ->
                        return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_IS_SELF)
                }

                val nickname = when (val result = SessionValidator.normalizeNickname(rawNickname)) {
                    is ManoteResult.Success -> result.value
                    is ManoteResult.Failure -> return@withTransaction result
                }
                val note = when (val result = SessionValidator.normalizeNote(rawNote)) {
                    is ManoteResult.Success -> result.value
                    is ManoteResult.Failure -> return@withTransaction result
                }

                playerDao.updateFriendProfile(
                    id = id,
                    nickname = nickname,
                    note = note,
                    updatedAt = clock(),
                )
                ManoteResult.Success(Unit)
            }
        }

    /** 本人只能改昵称，不能归档、删除或切换身份（规格 2.2）。 */
    suspend fun renameSelf(rawNickname: String): ManoteResult<Unit> = writeSerializer.withWriteLock {
        database.withTransaction {
            val self = playerDao.findFirstByKind(PlayerKind.SELF)
                ?: return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_NOT_FOUND)
            val nickname = when (val result = SessionValidator.normalizeNickname(rawNickname)) {
                is ManoteResult.Success -> result.value
                is ManoteResult.Failure -> return@withTransaction result
            }
            playerDao.updateSelfNickname(id = self.id, nickname = nickname, updatedAt = clock())
            ManoteResult.Success(Unit)
        }
    }

    /**
     * 归档麻友：保留历史且可取消归档。重复归档不改动原归档时间。
     */
    suspend fun archiveFriend(id: String): ManoteResult<Unit> = writeSerializer.withWriteLock {
        database.withTransaction {
            val existing = playerDao.findById(id)
            when {
                existing == null -> return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_NOT_FOUND)
                existing.kind == PlayerKind.SELF ->
                    return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_IS_SELF)
                existing.archivedAt != null -> return@withTransaction ManoteResult.Success(Unit)
            }
            val now = clock()
            playerDao.setFriendArchivedAt(id = id, archivedAt = now, updatedAt = now)
            ManoteResult.Success(Unit)
        }
    }

    /** 取消归档，使该麻友重新可用于新建场次。 */
    suspend fun unarchiveFriend(id: String): ManoteResult<Unit> = writeSerializer.withWriteLock {
        database.withTransaction {
            val existing = playerDao.findById(id)
            when {
                existing == null -> return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_NOT_FOUND)
                existing.kind == PlayerKind.SELF ->
                    return@withTransaction ManoteResult.Failure(ManoteError.PLAYER_IS_SELF)
                existing.archivedAt == null -> return@withTransaction ManoteResult.Success(Unit)
            }
            playerDao.setFriendArchivedAt(id = id, archivedAt = null, updatedAt = clock())
            ManoteResult.Success(Unit)
        }
    }

    /**
     * 某位玩家的跨场统计（规格 2.4）。
     * 直接由 session_result 的净金额实时计算，不读取任何手工累计。
     */
    suspend fun statistics(playerId: String): PlayerStatistics =
        PlayerStatistics.of(sessionResultDao.findNetMinorsByPlayer(playerId))

    /** 订阅唯一 SELF，供首页与「我的」显示本人昵称；重启或改昵称后自动刷新。 */
    fun observeSelf(): Flow<PlayerEntity?> = playerDao.observeSelf()

    /** 订阅未归档麻友，供记一场的麻友选择器使用。 */
    fun observeActiveFriends(): Flow<List<PlayerEntity>> = playerDao.observeActiveFriends()

    /** 订阅全部麻友（含已归档），供牌友页的「包含已归档」开关使用（规格 3.4）。 */
    fun observeAllFriends(): Flow<List<PlayerEntity>> = playerDao.observeAllFriends()

    /** 订阅单个档案，供麻友详情实时刷新；档案不存在时发出 null。 */
    fun observeById(playerId: String): Flow<PlayerEntity?> = playerDao.observeById(playerId)

    /**
     * 订阅全部玩家在 `session_result` 上的聚合（场数、净输赢），供牌友列表一次取齐。
     * 派生自同一份结果数据，与 [observeStatistics] 口径一致。
     */
    fun observeAggregates(): Flow<List<PlayerAggregate>> = sessionResultDao.observeAggregates()

    /**
     * 同名麻友查询（规格 2.2）。返回空列表表示没有同名档案。
     * 仅用于提示，是否继续创建由用户决定。
     */
    suspend fun findFriendsByNickname(nickname: String): List<PlayerEntity> =
        playerDao.findFriendsByNickname(nickname)

    /**
     * 订阅某位玩家的跨场统计。
     * 与 [statistics] 使用同一份 `session_result` 数据，只是改为订阅式以便界面实时刷新。
     */
    fun observeStatistics(playerId: String): Flow<PlayerStatistics> =
        sessionResultDao.observeNetMinorsByPlayer(playerId).map(PlayerStatistics::of)
}
