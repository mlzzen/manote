package com.manote.app.data.repository

import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * JSON 全量备份的文件结构（规格 6.1）。
 *
 * 字段与第 5 节的三张表一一对应，仅用于文件读写，**不是应用的第二份账本**：
 * 导入后统计仍然只从 `session_result` 实时计算。
 *
 * 枚举以字符串表示，可空字段显式输出 `null`（`Json` 的 `explicitNulls` 默认开启），
 * 金额一律为整数分。
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAt: Long,
    val players: List<BackupPlayer>,
    val sessions: List<BackupSession>,
    val sessionResults: List<BackupSessionResult>,
)

@Serializable
data class BackupPlayer(
    val id: String,
    /** "SELF" 或 "FRIEND"；取其它值时按无法识别的取值拒绝。 */
    val kind: String,
    val nickname: String,
    val note: String,
    val archivedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSession(
    val id: String,
    val title: String,
    val playedAt: Long,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

@Serializable
data class BackupSessionResult(
    val sessionId: String,
    val seat: Int,
    val playerId: String,
    val nameSnapshot: String,
    val netMinor: Long,
)

/** 表 ↔ 文件元素的互转。导出时逐字段搬，不做任何业务加工。 */
fun PlayerEntity.toBackupPlayer(): BackupPlayer = BackupPlayer(
    id = id,
    kind = kind.name,
    nickname = nickname,
    note = note,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun SessionEntity.toBackupSession(): BackupSession = BackupSession(
    id = id,
    title = title,
    playedAt = playedAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
)

fun SessionResultEntity.toBackupResult(): BackupSessionResult = BackupSessionResult(
    sessionId = sessionId,
    seat = seat,
    playerId = playerId,
    nameSnapshot = nameSnapshot,
    netMinor = netMinor,
)

fun BackupPlayer.toEntity(kind: PlayerKind): PlayerEntity = PlayerEntity(
    id = id,
    kind = kind,
    nickname = nickname,
    note = note,
    archivedAt = archivedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BackupSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title,
    playedAt = playedAt,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    revision = revision,
)

fun BackupSessionResult.toEntity(): SessionResultEntity = SessionResultEntity(
    sessionId = sessionId,
    seat = seat,
    playerId = playerId,
    nameSnapshot = nameSnapshot,
    netMinor = netMinor,
)

/**
 * JSON 编解码（规格 6.1）。
 *
 * - `ignoreUnknownKeys`：忽略未知附加字段。
 * - 其余保持严格：必要字段缺失、字段类型非法都会直接抛 `SerializationException`，
 *   由调用方按「格式不正确」拒绝，不做任何猜测性修补。
 */
object BackupCodec {

    /** 协议版本，与 Room 版本相互独立（规格 6.1）。 */
    const val PROTOCOL_VERSION = 3

    /** 文件上限 20 MiB，按实际读取字节判断，不信任文件选择器给出的大小。 */
    const val MAX_FILE_BYTES = 20L * 1024L * 1024L

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    private val FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /**
     * 备份文件名用导出时间，不含昵称等用户文本（规格 6.2：文件名不直接用昵称或标题）。
     */
    fun suggestedFileName(exportedAt: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val stamp = FILE_TIME_FORMAT.format(Instant.ofEpochMilli(exportedAt).atZone(zoneId))
        return "manote-backup_$stamp.json"
    }

    /**
     * 解析备份文本。任何解析失败都返回 null，由调用方按「格式不正确」拒绝——
     * 这里不区分失败细节，因为对用户而言处理方式相同（重新导出一份）。
     */
    fun decode(text: String): BackupFile? = runCatching {
        json.decodeFromString(BackupFile.serializer(), text)
    }.getOrNull()
}
