package com.manote.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.data.RoomTestCase
import com.manote.app.data.uuidFor
import com.manote.app.data.value
import com.manote.app.domain.SessionDuration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 「加时长之前导出的备份」还能不能导入（规格 6.1）。
 *
 * 加一个字段最容易踩的坑是顺手把 `PROTOCOL_VERSION` 也加一：那样用户手里所有旧备份
 * 都会被自己的校验挡在门外，而且提示只有一句「版本不受支持」。
 * 这里不靠推理，而是**真的造一份旧格式的文件**跑完整链路：inspect → importBackup → 查库。
 *
 * 「旧格式」的造法是把新导出里的 `durationMinutes` 整行删掉——旧版本写出的文件
 * 与新文件的唯一差别就是这个字段，其余逐字节相同。
 */
@RunWith(AndroidJUnit4::class)
class LegacyBackupImportTest : RoomTestCase() {

    @Test
    fun legacyFileWithoutDurationPassesValidationAndKeepsEverythingElse() = runBlocking {
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(
                ids,
                listOf(3000L, -1000L, -1000L, -1000L),
                id = uuidFor(101),
                // 故意填一个非默认的时长，确保这个字段真的出现在文件里、又真的被删掉
                durationMinutes = 270,
            ),
        ).value()

        val legacyJson = asLegacyFile(backupRepository.encode(backupRepository.export()))
        assertFalse(
            "这份文件里不该还有 durationMinutes，否则测的就不是旧格式",
            legacyJson.contains("durationMinutes"),
        )

        val check = backupRepository.inspect(legacyJson.toByteArray(Charsets.UTF_8))
        assertTrue("加时长之前的备份必须仍然能通过校验，实际是：$check", check is BackupCheck.Passed)

        assertTrue(
            "旧备份必须能真的导入",
            backupRepository.importBackup((check as BackupCheck.Passed).file) is BackupCheck.Passed,
        )

        val detail = sessionRepository.getSessionDetail(uuidFor(101)).value()
        assertEquals(
            "旧文件没写时长，导入后按默认的 4 小时算",
            SessionDuration.DEFAULT_MINUTES,
            detail.session.durationMinutes,
        )
        assertEquals(
            "金额逐笔原样恢复",
            listOf(3000L, -1000L, -1000L, -1000L),
            detail.results.map { it.netMinor },
        )
        assertEquals("昵称快照原样恢复", "上家", detail.results.first { it.seat == 1 }.nameSnapshot)
        assertEquals("统计照常从结果实时算出", 3000L, playerRepository.statistics(ids[0]).netMinor)
        assertEquals(-1000L, playerRepository.statistics(ids[1]).netMinor)
    }

    @Test
    fun reExportingAnImportedLegacyFileReproducesItByteForByte() = runBlocking {
        // 旧备份导入之后如果被「顺手改写」——比如多出一个字段——那就等于悄悄改了用户的数据。
        // 这条断言把「原样进、原样出」钉死：默认时长不会被写进文件，因此旧文件能原样再吐出来。
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(ids, listOf(3000L, -1000L, -1000L, -1000L), id = uuidFor(101)),
        ).value()

        val legacyJson = asLegacyFile(backupRepository.encode(backupRepository.export()))
        val passed = backupRepository.inspect(legacyJson.toByteArray(Charsets.UTF_8)) as BackupCheck.Passed
        backupRepository.importBackup(passed.file)

        assertEquals(
            "导入旧备份后再导出，应当与原件逐字节一致",
            legacyJson,
            backupRepository.encode(backupRepository.export()),
        )
    }

    @Test
    fun nonDefaultDurationSurvivesTheRoundTrip() = runBlocking {
        // 反向的一半：用户真填了别的时长，就必须原样写进文件、原样读回来
        val ids = givenSelfAndThreeFriends()
        sessionRepository.createSession(
            draftOf(
                ids,
                listOf(3000L, -1000L, -1000L, -1000L),
                id = uuidFor(101),
                durationMinutes = 270,
            ),
        ).value()

        val json = backupRepository.encode(backupRepository.export())
        assertTrue("非默认时长要写进文件", json.contains("\"durationMinutes\": 270"))

        val passed = backupRepository.inspect(json.toByteArray(Charsets.UTF_8)) as BackupCheck.Passed
        backupRepository.importBackup(passed.file)

        assertEquals(
            270,
            sessionRepository.getSessionDetail(uuidFor(101)).value().session.durationMinutes,
        )
    }

    /**
     * 删掉 `durationMinutes` 那一行，得到旧版本会写出的文本。
     *
     * 用 `split` 而不是 `lineSequence`：前者保留末尾那个空串，
     * 拼回去的文件与原件只差被删的那一行，可以拿来做逐字节比对。
     */
    private fun asLegacyFile(json: String): String =
        json.split("\n")
            .filterNot { it.trimStart().startsWith("\"durationMinutes\":") }
            .joinToString("\n")
}
