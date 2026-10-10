package com.manote.app.data.repository

import com.manote.app.data.local.PlayerKind
import com.manote.app.domain.BackupRejectReason
import com.manote.app.domain.Money
import com.manote.app.domain.SessionRules
import com.manote.app.domain.SessionValidator
import com.manote.app.domain.valueOrNull
import java.util.UUID

/** 备份文件的校验结果。 */
sealed interface BackupCheck {

    /** 通过：给出确认界面要展示的摘要，并带上已解析的文件供导入使用。 */
    data class Passed(val summary: BackupSummary, val file: BackupFile) : BackupCheck

    /** 拒绝：给出明确原因；此时数据库一定没有被改动（T21）。 */
    data class Rejected(val reason: BackupRejectReason) : BackupCheck
}

/**
 * 导入前展示的信息（规格 3.5）：将覆盖的场数、牌友数，以及导入后的本人昵称。
 * 只含数量与昵称，不含金额明细。
 */
data class BackupSummary(
    val sessionCount: Int,
    val friendCount: Int,
    val selfNickname: String,
)

/** 本机现有数据量，用于和备份内容对照，说明「将被整体覆盖」。 */
data class DataCounts(
    val sessionCount: Int,
    val friendCount: Int,
)

/** 文件里的 kind 字符串 → 枚举；取其它值时返回 null，由校验按「无法识别」拒绝。 */
fun BackupPlayer.playerKindOrNull(): PlayerKind? = when (kind) {
    PlayerKind.SELF.name -> PlayerKind.SELF
    PlayerKind.FRIEND.name -> PlayerKind.FRIEND
    else -> null
}

/**
 * 备份内容的纯校验（规格 6.1「确认前校验」）。
 *
 * 全部在内存里完成，任何一条不通过就整体拒绝，**不做修补、不做部分导入**——
 * 这是「坏备份不改原库」的前提（T21）。
 *
 * 校验的是「数据是否自洽」，而不是「是否满足新建时的输入限制」：
 * 因此只记录部分座位、合计不为 0 的场次都算合法（规格 1.3），
 * 否则用户自己导出的备份会被自己的校验挡在门外。
 *
 * 时间只校验彼此的先后关系，**不用当前设备时钟去否定导出的历史时间**，
 * 以兼容设备时钟有差异的情况。
 */
object BackupValidator {

    fun validate(file: BackupFile): BackupCheck {
        if (file.schemaVersion != BackupCodec.PROTOCOL_VERSION) {
            return BackupCheck.Rejected(BackupRejectReason.UNSUPPORTED_VERSION)
        }

        // 无法识别的 kind 先拒掉，后面所有按 kind 的判断才不会有空档。
        if (file.players.any { it.playerKindOrNull() == null }) {
            return BackupCheck.Rejected(BackupRejectReason.MALFORMED)
        }
        val friends = file.players.filter { it.playerKindOrNull() == PlayerKind.FRIEND }
        if (file.sessions.size > SessionRules.MAX_SESSIONS || friends.size > SessionRules.MAX_FRIENDS) {
            return BackupCheck.Rejected(BackupRejectReason.LIMIT_EXCEEDED)
        }

        // —— 档案表 ——
        val playerIds = mutableSetOf<String>()
        for (player in file.players) {
            if (!isCanonicalUuid(player.id) || !playerIds.add(player.id)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_ID)
            }
        }
        for (player in file.players) {
            if (!isNormalizedNickname(player.nickname) || !isNormalizedNote(player.note)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_TEXT)
            }
            if (!isValidPlayerTimes(player)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_TIME)
            }
            // 本人不能归档（规格 2.2）。
            if (player.playerKindOrNull() == PlayerKind.SELF && player.archivedAt != null) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_SELF)
            }
        }
        val selves = file.players.filter { it.playerKindOrNull() == PlayerKind.SELF }
        if (selves.size != 1) return BackupCheck.Rejected(BackupRejectReason.INVALID_SELF)

        // —— 场次表 ——
        val sessionIds = mutableSetOf<String>()
        for (session in file.sessions) {
            if (!isCanonicalUuid(session.id) || !sessionIds.add(session.id)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_ID)
            }
        }
        for (session in file.sessions) {
            if (!isNormalizedTitle(session.title) || !isNormalizedNote(session.note)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_TEXT)
            }
            if (session.revision <= 0L) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_REVISION)
            }
            if (!isValidSessionTimes(session)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_TIME)
            }
        }

        // —— 结果表：外键、座位、文本、金额 ——
        val kindById = file.players.associate { it.id to it.playerKindOrNull() }
        val seatKeys = mutableSetOf<Pair<String, Int>>()
        val playerKeys = mutableSetOf<Pair<String, String>>()
        val resultsBySession = mutableMapOf<String, MutableList<BackupSessionResult>>()

        for (result in file.sessionResults) {
            if (result.sessionId !in sessionIds || result.playerId !in playerIds) {
                return BackupCheck.Rejected(BackupRejectReason.ORPHAN_REFERENCE)
            }
            if (result.seat < 0 || result.seat >= SessionRules.SEAT_COUNT) {
                return BackupCheck.Rejected(BackupRejectReason.INCOMPLETE_SEATS)
            }
            // 联合主键 (sessionId, seat) 与唯一 (sessionId, playerId)，两者都必须成立。
            if (!seatKeys.add(result.sessionId to result.seat)) {
                return BackupCheck.Rejected(BackupRejectReason.INCOMPLETE_SEATS)
            }
            if (!playerKeys.add(result.sessionId to result.playerId)) {
                return BackupCheck.Rejected(BackupRejectReason.INCOMPLETE_SEATS)
            }
            if (!isNormalizedNickname(result.nameSnapshot)) {
                return BackupCheck.Rejected(BackupRejectReason.INVALID_TEXT)
            }
            if (!Money.isWithinRange(result.netMinor)) {
                return BackupCheck.Rejected(BackupRejectReason.AMOUNT_OUT_OF_RANGE)
            }
            resultsBySession.getOrPut(result.sessionId) { mutableListOf() }.add(result)
        }

        // —— 逐场检查：至少一条结果、必含 seat 0、类型匹配 ——
        //
        // 只记录了部分座位是合法的（规格 1.3），所以这里不再要求「四位置齐全」；
        // 合计不为 0 同样合法，不在此处拒绝，否则用户自己的备份就恢复不回来。
        for (session in file.sessions) {
            val rows = resultsBySession[session.id].orEmpty()
            if (rows.isEmpty() || rows.none { it.seat == SessionRules.SEAT_SELF }) {
                return BackupCheck.Rejected(BackupRejectReason.INCOMPLETE_SEATS)
            }
            for (row in rows) {
                val expected = if (row.seat == SessionRules.SEAT_SELF) {
                    PlayerKind.SELF
                } else {
                    PlayerKind.FRIEND
                }
                if (kindById[row.playerId] != expected) {
                    return BackupCheck.Rejected(BackupRejectReason.KIND_MISMATCH)
                }
            }
        }

        return BackupCheck.Passed(
            summary = BackupSummary(
                sessionCount = file.sessions.size,
                friendCount = friends.size,
                selfNickname = selves.single().nickname,
            ),
            file = file,
        )
    }

    /**
     * 合法的 ID 是规范形式的 UUID（8-4-4-4-12）。
     * `UUID.fromString` 对某些缺组的输入过于宽松，因此再要求往返一致。
     */
    private fun isCanonicalUuid(id: String): Boolean {
        val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: return false
        return uuid.toString().equals(id, ignoreCase = true)
    }

    /** 文本必须是规范化后的存储形态：无首尾空白，且通过长度校验。 */
    private fun isNormalizedNickname(raw: String): Boolean =
        raw == raw.trim() && SessionValidator.normalizeNickname(raw).valueOrNull() != null

    private fun isNormalizedNote(raw: String): Boolean =
        raw == raw.trim() && SessionValidator.normalizeNote(raw).valueOrNull() != null

    /** 标题额外要求非空：空白标题在库中会被规范化成默认标题，不应以空串存回。 */
    private fun isNormalizedTitle(raw: String): Boolean =
        raw.isNotBlank() && raw == raw.trim() &&
            SessionValidator.normalizeTitle(raw).valueOrNull() != null

    private fun isValidPlayerTimes(player: BackupPlayer): Boolean {
        if (player.createdAt < 0L || player.updatedAt < 0L) return false
        if (player.updatedAt < player.createdAt) return false
        val archivedAt = player.archivedAt
        if (archivedAt != null && (archivedAt < 0L || archivedAt < player.createdAt)) return false
        return true
    }

    private fun isValidSessionTimes(session: BackupSession): Boolean {
        if (session.createdAt < 0L || session.updatedAt < 0L || session.playedAt < 0L) return false
        if (session.updatedAt < session.createdAt) return false
        // 打完时间不可能晚于这条记录最后一次被修改的时间。
        if (session.playedAt > session.updatedAt) return false
        return true
    }
}
