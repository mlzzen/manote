package com.manote.app.data.repository

import com.manote.app.data.BASE_NOW
import com.manote.app.data.RoomTestCase
import com.manote.app.data.error
import com.manote.app.data.local.PlayerEntity
import com.manote.app.data.local.PlayerKind
import com.manote.app.data.value
import com.manote.app.domain.ManoteError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 档案事务（规格 2.2、验收用例 T06、T09、T12、T13）。 */
class PlayerRepositoryTest : RoomTestCase() {

    @Test
    fun selfPlayerIsCreatedOnceAndKeepsItsIdentity() = runBlocking {
        // T06：首次启动创建唯一 SELF，昵称默认「我」
        val first = playerRepository.ensureSelfPlayer().value()
        assertEquals(PlayerKind.SELF, first.kind)
        assertEquals("我", first.nickname)
        assertNull(first.archivedAt)

        // T06：多次重启不重复创建，ID 不变
        repeat(3) {
            assertEquals(first.id, playerRepository.ensureSelfPlayer().value().id)
        }
        assertEquals(1, database.playerDao().countByKind(PlayerKind.SELF))
    }

    @Test
    fun renamingSelfKeepsTheSameId() = runBlocking {
        val self = playerRepository.ensureSelfPlayer().value()

        playerRepository.renameSelf("  老王  ").value()

        val reloaded = playerRepository.self()
        assertNotNull(reloaded)
        assertEquals(self.id, reloaded!!.id)
        assertEquals("老王", reloaded.nickname)
        assertEquals(1, database.playerDao().countByKind(PlayerKind.SELF))
    }

    @Test
    fun selfCannotBeArchivedNorEditedAsFriend() = runBlocking {
        val self = playerRepository.ensureSelfPlayer().value()

        assertEquals(ManoteError.PLAYER_IS_SELF, playerRepository.archiveFriend(self.id).error())
        assertEquals(ManoteError.PLAYER_IS_SELF, playerRepository.unarchiveFriend(self.id).error())
        assertEquals(ManoteError.PLAYER_IS_SELF, playerRepository.updateFriend(self.id, "我", "").error())

        assertNull(playerRepository.self()!!.archivedAt)
        assertEquals("我", playerRepository.self()!!.nickname)
    }

    @Test
    fun createFriendValidatesNicknameAndNote() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        assertEquals(ManoteError.INVALID_NICKNAME, playerRepository.createFriend("", "").error())
        assertEquals(ManoteError.INVALID_NICKNAME, playerRepository.createFriend("   ", "").error())
        assertEquals(
            ManoteError.INVALID_NICKNAME,
            playerRepository.createFriend("这个名字实在是太长了超了十二个字", "").error(),
        )
        assertEquals(ManoteError.INVALID_NOTE, playerRepository.createFriend("阿明", "备".repeat(101)).error())

        // 校验失败不应留下任何档案
        assertEquals(0, database.playerDao().countByKind(PlayerKind.FRIEND))
    }

    @Test
    fun sameNicknameProducesDistinctProfiles() = runBlocking {
        // T07：昵称允许重名，统计按 ID 分开
        playerRepository.ensureSelfPlayer().value()

        val first = playerRepository.createFriend("阿明", "戴眼镜").value()
        val second = playerRepository.createFriend("阿明", "高个子").value()

        assertEquals(false, first == second)
        assertEquals(2, database.playerDao().countByKind(PlayerKind.FRIEND))
        assertEquals("戴眼镜", playerRepository.findById(first)!!.note)
        assertEquals("高个子", playerRepository.findById(second)!!.note)
    }

    @Test
    fun friendNicknameIsTrimmedOnSave() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        val id = playerRepository.createFriend("  阿明  ", "  常来  ").value()

        assertEquals("阿明", playerRepository.findById(id)!!.nickname)
        assertEquals("常来", playerRepository.findById(id)!!.note)
    }

    @Test
    fun updateFriendRejectsUnknownProfile() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        assertEquals(ManoteError.PLAYER_NOT_FOUND, playerRepository.updateFriend("no-such-id", "阿明", "").error())
    }

    @Test
    fun archivingIsIdempotentAndReversible() = runBlocking {
        // T13：归档保留历史，可取消归档
        playerRepository.ensureSelfPlayer().value()
        val id = playerRepository.createFriend("阿明", "").value()

        playerRepository.archiveFriend(id).value()
        val archivedAt = playerRepository.findById(id)!!.archivedAt
        assertNotNull(archivedAt)

        nowMillis += 60_000L
        playerRepository.archiveFriend(id).value()
        assertEquals("重复归档不应改动原归档时间", archivedAt, playerRepository.findById(id)!!.archivedAt)

        playerRepository.unarchiveFriend(id).value()
        assertNull(playerRepository.findById(id)!!.archivedAt)

        // 已取消归档后再取消应是无副作用的空操作
        nowMillis += 60_000L
        playerRepository.unarchiveFriend(id).value()
        assertNull(playerRepository.findById(id)!!.archivedAt)
    }

    @Test
    fun duplicateSelfIsReportedAsCorruptedState() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        // 人为制造第二个 SELF，验证不会随便挑一个继续用，而是明确报错
        database.playerDao().insert(
            PlayerEntity(
                id = "second-self",
                kind = PlayerKind.SELF,
                nickname = "另一个我",
                note = "",
                archivedAt = null,
                createdAt = BASE_NOW,
                updatedAt = BASE_NOW,
            ),
        )

        assertEquals(ManoteError.INTERNAL_STATE_CORRUPTED, playerRepository.ensureSelfPlayer().error())
        // 明确报错而不是悄悄删掉一个或随便挑一个继续用
        assertEquals(2, database.playerDao().countByKind(PlayerKind.SELF))
    }

    @Test
    fun archivingUnknownProfileFails() = runBlocking {
        playerRepository.ensureSelfPlayer().value()

        assertEquals(ManoteError.PLAYER_NOT_FOUND, playerRepository.archiveFriend("no-such-id").error())
        assertEquals(ManoteError.PLAYER_NOT_FOUND, playerRepository.unarchiveFriend("no-such-id").error())
    }

    @Test
    fun statisticsAggregateAcrossSessionsAndPositions() = runBlocking {
        // T09：某麻友两场分别在上家赢 30 元、在对家输 10 元，跨场换位置仍归集到同一个 ID
        val players = givenSelfAndThreeFriends()
        val self = players[0]
        val friend = players[1]

        // 第一场：自己在 seat0，friend 在上家（seat1）赢 30 元
        sessionRepository.createSession(
            draftOf(players, listOf(-3000L, 3000L, 0L, 0L), id = "s-1"),
        ).value()
        // 第二场：friend 换到对家（seat2）输 10 元
        val secondSeating = listOf(players[0], players[2], friend, players[3])
        sessionRepository.createSession(
            draftOf(secondSeating, listOf(1000L, 0L, -1000L, 0L), id = "s-2"),
        ).value()

        val statistics = playerRepository.statistics(friend)

        assertEquals(2, statistics.sessionCount)
        assertEquals(1, statistics.winCount)
        assertEquals(1, statistics.lossCount)
        assertEquals(3000L, statistics.winMinor)
        assertEquals(1000L, statistics.lossMinor)
        assertEquals(2000L, statistics.netMinor)

        // 本人账目同样由 session_result 实时汇总
        val selfStatistics = playerRepository.statistics(self)
        assertEquals(-2000L, selfStatistics.netMinor)
    }

    @Test
    fun statisticsIgnoreUncommittedDrafts() = runBlocking {
        // 未提交的草稿不入库、不参与统计（规格 2.3）
        val players = givenSelfAndThreeFriends()

        val statistics = playerRepository.statistics(players[1])

        assertEquals(0, statistics.sessionCount)
        assertEquals(0L, statistics.netMinor)
    }
}
