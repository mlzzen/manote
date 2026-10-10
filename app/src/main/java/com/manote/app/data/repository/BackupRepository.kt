package com.manote.app.data.repository

import androidx.room.withTransaction
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import com.manote.app.domain.BackupRejectReason
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * JSON 全量备份与恢复（规格 6.1、3.5）。
 *
 * 三条不变量：
 * 1. **先全量校验、再动数据库。** 导入前把整份文件在内存里查完，任何一条不合规就整体拒绝，
 *    因此「坏备份不改原库」（T21）不是靠回滚兜底，而是根本不会开始写。
 * 2. **单事务、按外键顺序先删后写。** 中途异常整体回滚，不留半截数据（RESTRICT 外键也不允许）。
 * 3. **写入经 [WriteSerializer] 串行。** 与场次、档案操作共用同一把闸门，导入期间其它写入排队。
 *
 * 导出只是一致性事务里的只读快照，**不会变成应用读取的第二份账本**：
 * 统计永远只从 `session_result` 实时算。
 */
class BackupRepository(
    private val database: ManoteDatabase,
    private val writeSerializer: WriteSerializer,
    private val dataResetSignal: DataResetSignal,
    /** 写进备份的 appVersion；由容器注入，便于测试固定取值。 */
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val playerDao = database.playerDao()
    private val sessionDao = database.sessionDao()
    private val sessionResultDao = database.sessionResultDao()

    /**
     * 导出：在同一个读事务里取三张表的快照，保证三者互相一致
     * （不会出现「场次已改、结果还是旧的」这种半新半旧的备份）。
     */
    suspend fun export(): BackupFile = database.withTransaction {
        BackupFile(
            schemaVersion = BackupCodec.PROTOCOL_VERSION,
            appVersion = appVersion,
            exportedAt = clock(),
            players = playerDao.findAllOrdered().map { it.toBackupPlayer() },
            sessions = sessionDao.findAllOrdered().map { it.toBackupSession() },
            sessionResults = sessionResultDao.findAllOrdered().map { it.toBackupResult() },
        )
    }

    /** 把导出文件编码成写入磁盘的文本。 */
    fun encode(file: BackupFile): String = BackupCodec.encode(file)

    /**
     * 读取到的字节 → 校验结果。顺序为：大小 → UTF-8 → JSON → 内容规则。
     * 任一步不过就整体拒绝，此时没有触碰过数据库。
     */
    fun inspect(bytes: ByteArray): BackupCheck {
        if (bytes.size.toLong() > BackupCodec.MAX_FILE_BYTES) {
            return BackupCheck.Rejected(BackupRejectReason.TOO_LARGE)
        }
        val text = decodeUtf8(bytes) ?: return BackupCheck.Rejected(BackupRejectReason.MALFORMED)
        val file = BackupCodec.decode(text) ?: return BackupCheck.Rejected(BackupRejectReason.MALFORMED)
        return BackupValidator.validate(file)
    }

    /** 本机现有数据量，用于在确认界面上说明「将被整体覆盖」。 */
    suspend fun currentCounts(): DataCounts = DataCounts(
        sessionCount = sessionDao.count(),
        friendCount = playerDao.countByKind(PlayerKind.FRIEND),
    )

    /**
     * 全量覆盖导入（规格 6.1）。仅全量替换，不合并。
     *
     * 写入前**再校验一次**：与 `createSession` 的做法一致，事务内重新验证，
     * 不依赖调用方「外面已经验过了」。校验不过直接返回拒绝，事务根本不开启。
     */
    suspend fun importBackup(file: BackupFile): BackupCheck = writeSerializer.withWriteLock {
        val check = BackupValidator.validate(file)
        if (check is BackupCheck.Rejected) return@withWriteLock check

        val entities = file.toEntities()
            ?: return@withWriteLock BackupCheck.Rejected(BackupRejectReason.MALFORMED)

        database.withTransaction {
            // 顺序由外键决定：结果 → 场次 → 档案；写回时反过来。
            sessionResultDao.deleteAll()
            sessionDao.deleteAll()
            playerDao.deleteAll()

            playerDao.insertAll(entities.players)
            sessionDao.insertAll(entities.sessions)
            sessionResultDao.insertAll(entities.results)
        }

        dataResetSignal.notifyReset()
        check
    }

    /**
     * 严格 UTF-8 解码：非法字节序列直接判为格式不正确，而不是替换成 U+FFFD 后
     * 让 JSON 去猜。开头的 BOM 一并去掉，兼容部分编辑器另存时自动加 BOM 的情况。
     */
    private fun decodeUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix("\uFEFF")
    }.getOrNull()

    /** 待写入的三张表内容。kind 无法识别时返回 null（校验通过后不会发生）。 */
    private class ImportEntities(
        val players: List<PlayerEntity>,
        val sessions: List<SessionEntity>,
        val results: List<SessionResultEntity>,
    )

    private fun BackupFile.toEntities(): ImportEntities? {
        val players = this.players.map { player ->
            val kind = player.playerKindOrNull() ?: return null
            player.toEntity(kind)
        }
        return ImportEntities(
            players = players,
            sessions = sessions.map { it.toEntity() },
            results = sessionResults.map { it.toEntity() },
        )
    }
}
