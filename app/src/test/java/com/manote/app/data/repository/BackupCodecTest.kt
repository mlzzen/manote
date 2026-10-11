package com.manote.app.data.repository

import com.manote.app.domain.BackupRejectReason
import com.manote.app.domain.SessionDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 备份 JSON 的编解码（规格 6.1）。
 *
 * 关注的是「文件格式」这一层：枚举写成字符串、可空字段显式输出 null、
 * 忽略未知附加字段，而必要字段缺失或类型不对一律拒绝。
 */
class BackupCodecTest {

    @Test
    fun encodeThenDecodeKeepsEveryField() {
        val original = sampleFile()

        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun durationIsWrittenToTheFile() {
        val text = BackupCodec.encode(sampleFile())

        assertTrue("时长要随场次一起导出", text.contains("\"durationMinutes\": 270"))
    }

    @Test
    fun oldBackupWithoutDurationFallsBackToTheDefault() {
        // 加时长之前导出的备份（协议版本仍是 3）没有这个字段。
        // 它必须能被导入，且补上默认的 4 小时——这正是「不填按 4 小时算」这条规则本身。
        val text = BackupCodec.encode(sampleFile())
            .replace("\"durationMinutes\": 270,", "")

        val decoded = BackupCodec.decode(text)

        assertNotNull("旧备份必须仍然可读", decoded)
        assertEquals(
            SessionDuration.DEFAULT_MINUTES,
            decoded!!.sessions.single().durationMinutes,
        )
    }

    @Test
    fun encodedTextUsesStringsForEnumsAndExplicitNulls() {
        val text = BackupCodec.encode(sampleFile())

        assertTrue("kind 必须是字符串而不是序号", text.contains("\"kind\": \"SELF\""))
        assertTrue("可空字段必须显式输出 null", text.contains("\"archivedAt\": null"))
        assertTrue("金额必须是整数分而不是小数", text.contains("\"netMinor\": 3000"))
    }

    @Test
    fun unknownExtraFieldsAreIgnored() {
        val text = """
            {
              "schemaVersion": 3,
              "appVersion": "9.9.9",
              "exportedAt": 1,
              "futureField": {"anything": [1, 2, 3]},
              "players": [
                {"id": "$UUID_SELF", "kind": "SELF", "nickname": "我", "note": "",
                 "archivedAt": null, "createdAt": 1, "updatedAt": 1, "brandNew": true}
              ],
              "sessions": [],
              "sessionResults": []
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(text)
        assertNotNull("未知附加字段必须被忽略而不是拒绝整份文件", decoded)
        assertEquals(1, decoded!!.players.size)
    }

    @Test
    fun missingRequiredFieldIsRejected() {
        // 少了 revision
        val text = """
            {
              "schemaVersion": 3,
              "appVersion": "1.0.0",
              "exportedAt": 1,
              "players": [],
              "sessions": [{"id": "$UUID_SELF", "title": "局", "playedAt": 1, "note": "",
                            "createdAt": 1, "updatedAt": 1}],
              "sessionResults": []
            }
        """.trimIndent()

        assertNull(BackupCodec.decode(text))
    }

    @Test
    fun wrongFieldTypeIsRejected() {
        // players 应该是数组，给了字符串就必须拒绝
        val playersAsString = """
            {
              "schemaVersion": 3,
              "appVersion": "1.0.0",
              "exportedAt": 1,
              "players": "不是数组",
              "sessions": [],
              "sessionResults": []
            }
        """.trimIndent()
        assertNull(BackupCodec.decode(playersAsString))

        // exportedAt 应该是数字，给了对象就必须拒绝
        val timeAsObject = """
            {
              "schemaVersion": 3,
              "appVersion": "1.0.0",
              "exportedAt": {"at": 1},
              "players": [],
              "sessions": [],
              "sessionResults": []
            }
        """.trimIndent()
        assertNull(BackupCodec.decode(timeAsObject))
    }

    /**
     * 已知偏差：kotlinx.serialization 允许把数字写成字符串（`"3"` → 3），
     * 因此这里不额外加严。数值语义完全一致，而取值范围仍由校验器把关
     * （`schemaVersion` 必须恰好等于 3）。
     */
    @Test
    fun numericFieldWrittenAsStringIsStillParsed() {
        val text = """
            {
              "schemaVersion": "3",
              "appVersion": "1.0.0",
              "exportedAt": 1,
              "players": [],
              "sessions": [],
              "sessionResults": []
            }
        """.trimIndent()

        assertEquals(3, BackupCodec.decode(text)?.schemaVersion)
    }

    @Test
    fun fractionalMinorAmountIsRejected() {
        val text = """
            {
              "schemaVersion": 3,
              "appVersion": "1.0.0",
              "exportedAt": 1,
              "players": [],
              "sessions": [],
              "sessionResults": [
                {"sessionId": "$UUID_SELF", "seat": 0, "playerId": "$UUID_SELF",
                 "nameSnapshot": "我", "netMinor": 12.5}
              ]
            }
        """.trimIndent()

        assertNull("金额必须是整数分，小数要拒绝", BackupCodec.decode(text))
    }

    @Test
    fun notJsonAtAllIsRejected() {
        assertNull(BackupCodec.decode("这不是 JSON"))
        assertNull(BackupCodec.decode(""))
    }

    @Test
    fun unknownEnumValueDecodesButFailsValidation() {
        val text = BackupCodec.encode(sampleFile())
            .replace("\"kind\": \"FRIEND\"", "\"kind\": \"GUEST\"")

        val decoded = BackupCodec.decode(text)

        assertNotNull("无法识别的枚举取值属于内容问题，由校验器给出可读原因", decoded)
        val check = BackupValidator.validate(decoded!!)
        assertEquals(
            BackupRejectReason.MALFORMED,
            (check as BackupCheck.Rejected).reason,
        )
    }

    @Test
    fun suggestedFileNameUsesExportTimeOnly() {
        val name = BackupCodec.suggestedFileName(
            exportedAt = 1_759_660_200_000L,
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertTrue("文件名必须是 .json", name.endsWith(".json"))
        assertTrue("文件名应包含导出时间且不含用户文本：$name", name.startsWith("manote-backup_"))
    }

    private fun sampleFile(): BackupFile = BackupFile(
        schemaVersion = BackupCodec.PROTOCOL_VERSION,
        appVersion = "1.0.0",
        exportedAt = 1_759_660_200_000L,
        players = listOf(
            BackupPlayer(UUID_SELF, "SELF", "我", "", null, 1L, 2L),
            BackupPlayer(UUID_FRIEND, "FRIEND", "老王", "常来", null, 3L, 4L),
        ),
        sessions = listOf(
            BackupSession(
                id = UUID_SESSION,
                title = "周五晚局",
                playedAt = 10L,
                durationMinutes = 270,
                note = "备注",
                createdAt = 1L,
                updatedAt = 12L,
                revision = 1L,
            ),
        ),
        sessionResults = listOf(
            BackupSessionResult(UUID_SESSION, 0, UUID_SELF, "我", 3000L),
            BackupSessionResult(UUID_SESSION, 1, UUID_FRIEND, "老王", -3000L),
        ),
    )

    private companion object {
        const val UUID_SELF = "00000000-0000-4000-8000-000000000001"
        const val UUID_FRIEND = "00000000-0000-4000-8000-000000000002"
        const val UUID_SESSION = "00000000-0000-4000-8000-000000000009"
    }
}
