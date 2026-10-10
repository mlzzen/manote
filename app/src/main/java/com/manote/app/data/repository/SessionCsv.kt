package com.manote.app.data.repository

import com.manote.app.domain.Money
import com.manote.app.domain.SessionRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 单场 CSV 导出（规格 6.2）。仅查看，不支持导入。
 *
 * 格式固定：UTF-8 BOM + CRLF，一行表头加一行整场结果，16 列。
 * 一场只记录部分位置时（规格 1.3），未记录位置的三列为空。
 * 金额从「分」精确转成两位小数的元后**按数值原样输出**；
 * 其余文本列做两层处理：
 * 1. 转义逗号、双引号与换行（按 RFC 4180 用双引号包裹并把内部引号翻倍）；
 * 2. 以 `=`、`+`、`-`、`@` 或控制字符开头时加一个单引号前缀，
 *    避免被表格软件当成公式执行（T22）。
 */
object SessionCsv {

    /** UTF-8 BOM：让 Excel 之类按 UTF-8 打开，而不是本地代码页。 */
    const val BOM: String = "\uFEFF"

    /** 行分隔一律用 CRLF。 */
    const val LINE_SEPARATOR: String = "\r\n"

    /** 未记录的位置：三列都写成空单元格，而不是填 0.00。 */
    private const val EMPTY_CELL = ""

    /** 固定表头，顺序即列顺序（规格 6.2）。 */
    val HEADERS: List<String> = listOf(
        "对局ID", "标题", "打完时间",
        "自己ID", "自己昵称", "自己净金额(元)",
        "上家ID", "上家昵称", "上家净金额(元)",
        "对家ID", "对家昵称", "对家净金额(元)",
        "下家ID", "下家昵称", "下家净金额(元)",
        "备注",
    )

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    /**
     * 生成整份 CSV 文本。
     *
     * 一场可能只记录了 1～4 个位置（规格 1.3）：未记录的位置三列全部留空，
     * 表头与列数仍然固定为 16 列。**不填 0.00**——凭空写 0 会造出一个并不存在的持平结果。
     *
     * 连 seat 0 都没有说明库已损坏，这时返回 null，由调用方给出失败提示，
     * 而不是导出一份连本人都没有的文件。
     */
    fun build(detail: SessionDetail, zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val bySeat = detail.results.associateBy { it.seat }
        if (bySeat[SessionRules.SEAT_SELF] == null) return null
        val session = detail.session

        val row = buildList {
            add(textCell(session.id))
            add(textCell(session.title))
            add(textCell(formatTime(session.playedAt, zoneId)))
            (0 until SessionRules.SEAT_COUNT).forEach { seat ->
                val result = bySeat[seat]
                if (result == null) {
                    add(EMPTY_CELL)
                    add(EMPTY_CELL)
                    add(EMPTY_CELL)
                } else {
                    add(textCell(result.playerId))
                    add(textCell(result.nameSnapshot))
                    add(Money.formatMinor(result.netMinor))
                }
            }
            add(textCell(session.note))
        }

        return buildString {
            append(BOM)
            append(HEADERS.joinToString(",") { textCell(it) })
            append(LINE_SEPARATOR)
            append(row.joinToString(","))
            append(LINE_SEPARATOR)
        }
    }

    /**
     * 文件名用打完时间与 Session ID 短串，**不含昵称与标题**
     * （避免把用户文本写进文件名，也避免不同场次重名）。
     */
    fun fileName(detail: SessionDetail, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val stamp = FILE_TIME_FORMAT.format(
            Instant.ofEpochMilli(detail.session.playedAt).atZone(zoneId),
        )
        return "manote_${stamp}_${detail.session.id.take(8)}.csv"
    }

    private fun formatTime(epochMillis: Long, zoneId: ZoneId): String =
        TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(zoneId))

    /**
     * 文本单元格：先防公式，再按 CSV 规则转义。
     * 顺序不能颠倒——前缀必须加在原始文本前面，包在引号里也仍然生效。
     */
    private fun textCell(raw: String): String {
        val guarded = if (needsFormulaGuard(raw)) "'$raw" else raw
        val needsQuoting = guarded.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuoting) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else {
            guarded
        }
    }

    /**
     * 会被表格软件当公式或格式触发的开头字符。
     * 「控制空白」按 ISO 控制字符实现（制表符、回车、换行等），普通前导空格不在此列——
     * 它不会被当作公式，加上前缀反而会改动用户原文。
     */
    private fun needsFormulaGuard(raw: String): Boolean {
        val first = raw.firstOrNull() ?: return false
        return first == '=' || first == '+' || first == '-' || first == '@' || first.isISOControl()
    }
}
