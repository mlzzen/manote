package com.manote.app.data.repository

import com.manote.app.domain.BackupRejectReason
import com.manote.app.domain.SessionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 备份内容校验（规格 6.1）。对应验收用例 T21 的各类坏备份。
 *
 * 纯 JVM 测试：校验器不碰数据库、不碰 Android，任何一条不通过都必须给出具体原因。
 */
class BackupValidatorTest {

    // ————————————————————— 通过的基准 —————————————————————

    @Test
    fun validBackupPassesAndSummarizes() {
        val check = BackupValidator.validate(validFile())

        val passed = check as? BackupCheck.Passed
        assertNotNull("合法备份应当通过校验", passed)
        assertEquals(1, passed!!.summary.sessionCount)
        // 归档的麻友也算在牌友数里（规格 5.1：上限含归档）
        assertEquals(3, passed.summary.friendCount)
        assertEquals("我", passed.summary.selfNickname)
    }

    @Test
    fun archivedFriendAndDifferentSnapshotStillPass() {
        // 历史允许引用已归档麻友，快照可以与当前昵称不同（规格 6.1）
        val file = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map { result ->
                    if (result.seat == 3) result.copy(nameSnapshot = "阿强（旧名）") else result
                },
            )
        }

        assertNull(rejectionOf(file))
    }

    @Test
    fun emptyDatabaseBackupPasses() {
        val empty = validFile().copy(sessions = emptyList(), sessionResults = emptyList())

        val passed = BackupValidator.validate(empty) as? BackupCheck.Passed
        assertNotNull("没有场次但有本人档案的备份是合法的", passed)
        assertEquals(0, passed!!.summary.sessionCount)
    }

    // ————————————————————— 版本 —————————————————————

    @Test
    fun olderProtocolVersionIsRejected() {
        assertEquals(
            BackupRejectReason.UNSUPPORTED_VERSION,
            rejectionOf(validFile().copy(schemaVersion = 2)),
        )
        assertEquals(
            BackupRejectReason.UNSUPPORTED_VERSION,
            rejectionOf(validFile().copy(schemaVersion = 4)),
        )
    }

    // ————————————————————— 本人档案 —————————————————————

    @Test
    fun missingSelfIsRejected() {
        val file = validFile().let { base ->
            base.copy(players = base.players.filterNot { it.kind == "SELF" })
        }

        assertEquals(BackupRejectReason.INVALID_SELF, rejectionOf(file))
    }

    @Test
    fun twoSelvesAreRejected() {
        val file = validFile().let { base ->
            base.copy(players = base.players + player(uuid(90), "SELF", "另一个我"))
        }

        assertEquals(BackupRejectReason.INVALID_SELF, rejectionOf(file))
    }

    @Test
    fun archivedSelfIsRejected() {
        val file = validFile().let { base ->
            base.copy(
                players = base.players.map {
                    if (it.kind == "SELF") it.copy(archivedAt = T - 1_000L) else it
                },
            )
        }

        assertEquals(BackupRejectReason.INVALID_SELF, rejectionOf(file))
    }

    // ————————————————————— ID —————————————————————

    @Test
    fun malformedOrDuplicateIdsAreRejected() {
        val malformed = validFile().let { base ->
            base.copy(players = base.players.map { if (it.kind == "FRIEND") it.copy(id = "not-a-uuid") else it })
        }
        assertEquals(BackupRejectReason.INVALID_ID, rejectionOf(malformed))

        // UUID.fromString 对缺组输入过于宽松，必须按规范形式拒绝
        val shortUuid = validFile().let { base ->
            base.copy(players = base.players.map { if (it.kind == "FRIEND") it.copy(id = "1-1-1-1-1") else it })
        }
        assertEquals(BackupRejectReason.INVALID_ID, rejectionOf(shortUuid))

        val duplicated = validFile().let { base ->
            base.copy(sessions = base.sessions + base.sessions.first())
        }
        assertEquals(BackupRejectReason.INVALID_ID, rejectionOf(duplicated))
    }

    // ————————————————————— 外键与座位 —————————————————————

    @Test
    fun orphanReferencesAreRejected() {
        val orphanSession = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 1) it.copy(sessionId = uuid(77)) else it
                },
            )
        }
        assertEquals(BackupRejectReason.ORPHAN_REFERENCE, rejectionOf(orphanSession))

        val orphanPlayer = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 1) it.copy(playerId = uuid(78)) else it
                },
            )
        }
        assertEquals(BackupRejectReason.ORPHAN_REFERENCE, rejectionOf(orphanPlayer))
    }

    @Test
    fun missingSelfSeatOrDuplicateSeatsAreRejected() {
        // 缺少 seat 0：本人必须记录（规格 1.3）
        val missingSelf = validFile().let { base ->
            base.copy(sessionResults = base.sessionResults.filterNot { it.seat == 0 })
        }
        assertEquals(BackupRejectReason.INCOMPLETE_SEATS, rejectionOf(missingSelf))

        // 一条结果都没有的场次
        val noResults = validFile().copy(sessionResults = emptyList())
        assertEquals(BackupRejectReason.INCOMPLETE_SEATS, rejectionOf(noResults))

        val duplicateSeat = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 3) it.copy(seat = 2) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INCOMPLETE_SEATS, rejectionOf(duplicateSeat))

        // 同一场里同一个人占两个位置
        val duplicatePlayer = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 3) it.copy(playerId = base.players[1].id) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INCOMPLETE_SEATS, rejectionOf(duplicatePlayer))

        // 座位越界
        val unknownSeat = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 3) it.copy(seat = 4) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INCOMPLETE_SEATS, rejectionOf(unknownSeat))
    }

    @Test
    fun sessionWithOnlySomeSeatsIsAccepted() {
        // 规格 1.3：只记录了部分位置是合法的，不能因为「不齐」就拒绝自己的备份
        val partial = validFile().let { base ->
            base.copy(sessionResults = base.sessionResults.filter { it.seat <= 1 })
        }

        assertNull(rejectionOf(partial))
    }

    @Test
    fun seatKindMismatchIsRejected() {
        // seat 0 不是 SELF
        val selfInSeatOne = validFile().let { base ->
            val selfId = base.players.first { it.kind == "SELF" }.id
            val friendId = base.players.first { it.kind == "FRIEND" }.id
            base.copy(
                sessionResults = base.sessionResults.map {
                    when (it.seat) {
                        0 -> it.copy(playerId = friendId)
                        1 -> it.copy(playerId = selfId)
                        else -> it
                    }
                },
            )
        }
        assertEquals(BackupRejectReason.KIND_MISMATCH, rejectionOf(selfInSeatOne))
    }

    // ————————————————————— 金额 —————————————————————

    @Test
    fun unbalancedSessionIsAccepted() {
        // 规格 1.3：合计不为 0 的场次是合法的，导入时不再拒绝
        val file = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 0) it.copy(netMinor = 4000L) else it
                },
            )
        }

        assertNull(rejectionOf(file))
    }

    @Test
    fun outOfRangeAmountIsRejected() {
        val file = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    when (it.seat) {
                        0 -> it.copy(netMinor = 2_000_000L)
                        1 -> it.copy(netMinor = -1_000_000L)
                        2 -> it.copy(netMinor = -1_000_000L)
                        else -> it.copy(netMinor = 0L)
                    }
                },
            )
        }

        assertEquals(BackupRejectReason.AMOUNT_OUT_OF_RANGE, rejectionOf(file))
    }

    // ————————————————————— 文本 —————————————————————

    @Test
    fun overlongTextIsRejected() {
        val longNickname = validFile().let { base ->
            base.copy(
                players = base.players.map {
                    if (it.kind == "FRIEND") it.copy(nickname = "名".repeat(13)) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INVALID_TEXT, rejectionOf(longNickname))

        val longTitle = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(title = "题".repeat(31)) })
        }
        assertEquals(BackupRejectReason.INVALID_TEXT, rejectionOf(longTitle))

        val blankTitle = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(title = "") })
        }
        assertEquals(BackupRejectReason.INVALID_TEXT, rejectionOf(blankTitle))

        val untrimmedNote = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(note = " 有空格 ") })
        }
        assertEquals(BackupRejectReason.INVALID_TEXT, rejectionOf(untrimmedNote))

        val overlongSnapshot = validFile().let { base ->
            base.copy(
                sessionResults = base.sessionResults.map {
                    if (it.seat == 1) it.copy(nameSnapshot = "快".repeat(13)) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INVALID_TEXT, rejectionOf(overlongSnapshot))
    }

    @Test
    fun unknownKindIsRejected() {
        val file = validFile().let { base ->
            base.copy(players = base.players.map { if (it.kind == "FRIEND") it.copy(kind = "GUEST") else it })
        }

        assertEquals(BackupRejectReason.MALFORMED, rejectionOf(file))
    }

    // ————————————————————— 时间与 revision —————————————————————

    @Test
    fun invalidTimeRelationshipsAreRejected() {
        val negative = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(playedAt = -1L) })
        }
        assertEquals(BackupRejectReason.INVALID_TIME, rejectionOf(negative))

        val updatedBeforeCreated = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(updatedAt = it.createdAt - 1L) })
        }
        assertEquals(BackupRejectReason.INVALID_TIME, rejectionOf(updatedBeforeCreated))

        val playedAfterUpdated = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(playedAt = it.updatedAt + 1L) })
        }
        assertEquals(BackupRejectReason.INVALID_TIME, rejectionOf(playedAfterUpdated))

        val archivedBeforeCreated = validFile().let { base ->
            base.copy(
                players = base.players.map {
                    if (it.archivedAt != null) it.copy(archivedAt = it.createdAt - 1L) else it
                },
            )
        }
        assertEquals(BackupRejectReason.INVALID_TIME, rejectionOf(archivedBeforeCreated))
    }

    @Test
    fun futureTimesAreAccepted() {
        // 不用当前设备时钟否定导出的历史时间（规格 6.1）：即使时间远超「现在」也照样接受
        val farFuture = validFile().let { base ->
            base.copy(
                players = base.players.map {
                    it.copy(
                        createdAt = FAR_FUTURE - 1_000L,
                        updatedAt = FAR_FUTURE - 500L,
                        archivedAt = if (it.archivedAt != null) FAR_FUTURE - 800L else null,
                    )
                },
                sessions = base.sessions.map {
                    it.copy(
                        createdAt = FAR_FUTURE - 60_000L,
                        playedAt = FAR_FUTURE - 40_000L,
                        updatedAt = FAR_FUTURE - 30_000L,
                    )
                },
            )
        }

        assertNull(rejectionOf(farFuture))
    }

    @Test
    fun nonPositiveRevisionIsRejected() {
        val file = validFile().let { base ->
            base.copy(sessions = base.sessions.map { it.copy(revision = 0L) })
        }

        assertEquals(BackupRejectReason.INVALID_REVISION, rejectionOf(file))
    }

    // ————————————————————— 上限 —————————————————————

    @Test
    fun exceedingCountLimitsIsRejected() {
        val tooManySessions = validFile().let { base ->
            base.copy(
                sessions = (0..SessionRules.MAX_SESSIONS).map { index -> session(uuid(1_000 + index)) },
                sessionResults = emptyList(),
            )
        }
        assertEquals(BackupRejectReason.LIMIT_EXCEEDED, rejectionOf(tooManySessions))

        val tooManyFriends = validFile().let { base ->
            base.copy(
                players = base.players +
                    (0..SessionRules.MAX_FRIENDS).map { index -> player(uuid(2_000_000 + index), "FRIEND", "麻友") },
            )
        }
        assertEquals(BackupRejectReason.LIMIT_EXCEEDED, rejectionOf(tooManyFriends))
    }

    // ————————————————————— 辅助 —————————————————————

    private fun rejectionOf(file: BackupFile): BackupRejectReason? =
        (BackupValidator.validate(file) as? BackupCheck.Rejected)?.reason

    private fun validFile(): BackupFile {
        val self = uuid(1)
        val friend1 = uuid(2)
        val friend2 = uuid(3)
        val friend3 = uuid(4)
        return BackupFile(
            schemaVersion = BackupCodec.PROTOCOL_VERSION,
            appVersion = "1.0.0",
            exportedAt = T,
            players = listOf(
                player(self, "SELF", "我"),
                player(friend1, "FRIEND", "老王"),
                player(friend2, "FRIEND", "小李"),
                // 归档时间不早于 createdAt，历史仍可引用
                player(friend3, "FRIEND", "阿强", archivedAt = T - 1_000L),
            ),
            sessions = listOf(session(uuid(9))),
            sessionResults = listOf(
                result(uuid(9), 0, self, 3000L),
                result(uuid(9), 1, friend1, -1000L),
                result(uuid(9), 2, friend2, -1000L),
                result(uuid(9), 3, friend3, -1000L),
            ),
        )
    }

    private fun player(
        id: String,
        kind: String,
        nickname: String,
        note: String = "",
        archivedAt: Long? = null,
        createdAt: Long = T - 10_000L,
        updatedAt: Long = T - 5_000L,
    ) = BackupPlayer(
        id = id,
        kind = kind,
        nickname = nickname,
        note = note,
        archivedAt = archivedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun session(
        id: String,
        title: String = "周五晚局",
        note: String = "",
        revision: Long = 1L,
        createdAt: Long = T - 60_000L,
        playedAt: Long = T - 40_000L,
        updatedAt: Long = T - 30_000L,
    ) = BackupSession(
        id = id,
        title = title,
        playedAt = playedAt,
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        revision = revision,
    )

    private fun result(
        sessionId: String,
        seat: Int,
        playerId: String,
        netMinor: Long,
        nameSnapshot: String = "快照",
    ) = BackupSessionResult(
        sessionId = sessionId,
        seat = seat,
        playerId = playerId,
        nameSnapshot = nameSnapshot,
        netMinor = netMinor,
    )

    private fun uuid(n: Int): String = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

    private companion object {
        const val T = 1_700_000_000_000L
        const val FAR_FUTURE = 4_000_000_000_000L
    }
}
