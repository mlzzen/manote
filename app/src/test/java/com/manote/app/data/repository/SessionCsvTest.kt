package com.manote.app.data.repository

import com.manote.app.data.local.SessionEntity
import com.manote.app.data.local.SessionResultEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 单场 CSV 导出（规格 6.2）。对应验收用例 T22。
 *
 * 纯 JVM 测试：格式、转义与公式注入防护都是纯字符串规则，必须逐条钉死。
 */
class SessionCsvTest {

    @Test
    fun headerHasFixedColumnsAndFileUsesCrlfWithBom() {
        val text = build()

        assertTrue("UTF-8 BOM 必须在最前", text.startsWith(SessionCsv.BOM))
        assertTrue("行分隔必须是 CRLF", text.contains(SessionCsv.LINE_SEPARATOR))

        val lines = text.removePrefix(SessionCsv.BOM).split(SessionCsv.LINE_SEPARATOR)
        assertEquals("表头 + 一行整场结果 + 末尾换行", 3, lines.size)
        assertEquals("", lines[2])

        assertEquals(
            "对局ID,标题,打完时间," +
                "自己ID,自己昵称,自己净金额(元)," +
                "上家ID,上家昵称,上家净金额(元)," +
                "对家ID,对家昵称,对家净金额(元)," +
                "下家ID,下家昵称,下家净金额(元)," +
                "备注",
            lines[0],
        )
        assertEquals(16, lines[1].split(",").size)
    }

    @Test
    fun rowCarriesIdsSnapshotsAndTwoDecimalAmounts() {
        val cells = build().removePrefix(SessionCsv.BOM)
            .split(SessionCsv.LINE_SEPARATOR)[1]
            .split(",")

        assertEquals(SESSION_ID, cells[0])
        assertEquals("周五晚局", cells[1])
        assertEquals("2026-10-05 21:30", cells[2])
        assertEquals(SELF_ID, cells[3])
        assertEquals("我", cells[4])
        assertEquals("30.00", cells[5])
        assertEquals("老王", cells[7])
        assertEquals("-10.00", cells[8])
        assertEquals("阿强", cells[13])
        assertEquals("-10.00", cells[14])
        assertEquals("", cells[15])
    }

    @Test
    fun amountsAreNumbersNotText() {
        val line = dataLine()

        // 负数金额不能被加上防公式前缀，否则表格软件会把它当成文本
        assertTrue("金额列必须原样输出数值：$line", line.contains(",-10.00,"))
        assertFalse(line.contains("'-10.00"))
    }

    @Test
    fun commasQuotesAndNewlinesAreEscaped() {
        assertTrue(dataLine(title = "周五,晚局").contains("\"周五,晚局\""))
        assertTrue(dataLine(title = "周\"五").contains("\"周\"\"五\""))

        val newline = dataLine(note = "第一行\n第二行")
        assertTrue("含换行的单元格必须用双引号包裹", newline.contains("\"第一行\n第二行\""))
    }

    @Test
    fun formulaPrefixesAreNeutralised() {
        // 用户文本以 = + - @ 或控制字符开头时加单引号前缀
        assertTrue(dataLine(title = "=1+1").contains("'=1+1"))
        assertTrue(dataLine(title = "+1").contains("'+1"))
        assertTrue(dataLine(title = "-1").contains("'-1"))
        assertTrue(dataLine(note = "@SUM(A1)").contains("'@SUM(A1)"))
        assertTrue(dataLine(note = "\tabc").contains("'\tabc"))
    }

    @Test
    fun ordinaryLeadingSpaceIsNotModified() {
        // 前导空格不会触发公式，加上前缀反而改动了用户原文
        assertTrue(dataLine(note = " abc").contains(", abc"))
        assertFalse(dataLine(note = " abc").contains("' abc"))
    }

    @Test
    fun formulaLikeSnapshotIsAlsoGuarded() {
        // 昵称是快照文本，同样可能以敏感字符开头
        assertTrue(dataLine(friendName = "=老王").contains("'=老王"))
    }

    @Test
    fun fileNameUsesTimeAndShortIdOnly() {
        val name = SessionCsv.fileName(detail())

        assertEquals("manote_20261005-2130_00000000.csv", name)
        assertFalse("文件名不能带标题", name.contains("周五晚局"))
        assertFalse("文件名不能带昵称", name.contains("老王"))
    }

    @Test
    fun seatsWithoutRecordKeepTheirColumnsButStayEmpty() {
        // 规格 1.3：只记录了 0/1 两个位置，未记录的位置三列留空，列数仍然是 16
        val partial = detail().let { it.copy(results = it.results.filter { r -> r.seat <= 1 }) }

        val line = SessionCsv.build(partial, ZONE)!!.removePrefix(SessionCsv.BOM)
            .split(SessionCsv.LINE_SEPARATOR)[1]
        val cells = line.split(",")

        assertEquals(16, cells.size)
        assertEquals("老王", cells[7])
        // 对家、下家三列（9/10/11、12/13/14）全部为空，而不是填 0.00
        assertEquals(listOf("", "", "", "", "", ""), cells.subList(9, 15))
    }

    @Test
    fun missingSelfSeatProducesNoFile() {
        val broken = detail().let { it.copy(results = it.results.filterNot { r -> r.seat == 0 }) }

        assertNull("连本人都没有说明库已损坏，宁可失败", SessionCsv.build(broken, ZONE))
    }

    // ————————————————————— 辅助 —————————————————————

    private fun build(
        title: String = "周五晚局",
        note: String = "",
        friendName: String = "老王",
    ): String = SessionCsv.build(detail(title, note, friendName), ZONE)!!

    private fun dataLine(
        title: String = "周五晚局",
        note: String = "",
        friendName: String = "老王",
    ): String = build(title, note, friendName)
        .removePrefix(SessionCsv.BOM)
        .split(SessionCsv.LINE_SEPARATOR)[1]

    private fun detail(
        title: String = "周五晚局",
        note: String = "",
        friendName: String = "老王",
    ): SessionDetail {
        val playedAt = PLAYED_AT
        return SessionDetail(
            session = SessionEntity(
                id = SESSION_ID,
                title = title,
                playedAt = playedAt,
                note = note,
                createdAt = playedAt,
                updatedAt = playedAt,
                revision = 1L,
            ),
            results = listOf(
                SessionResultEntity(SESSION_ID, 0, SELF_ID, "我", 3000L),
                SessionResultEntity(SESSION_ID, 1, FRIEND1_ID, friendName, -1000L),
                SessionResultEntity(SESSION_ID, 2, FRIEND2_ID, "小李", -1000L),
                SessionResultEntity(SESSION_ID, 3, FRIEND3_ID, "阿强", -1000L),
            ),
        )
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

        /** 2026-10-05 21:30（上海），固定取值让时间格式断言可复现。 */
        val PLAYED_AT: Long = LocalDateTime.of(2026, 10, 5, 21, 30)
            .atZone(ZONE)
            .toInstant()
            .toEpochMilli()

        const val SESSION_ID = "00000000-0000-4000-8000-000000000009"
        const val SELF_ID = "00000000-0000-4000-8000-000000000001"
        const val FRIEND1_ID = "00000000-0000-4000-8000-000000000002"
        const val FRIEND2_ID = "00000000-0000-4000-8000-000000000003"
        const val FRIEND3_ID = "00000000-0000-4000-8000-000000000004"
    }
}
