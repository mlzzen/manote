package com.manote.app.domain

/**
 * 规格 2.1、2.2、5.1 中的数值与文本约束。集中在此，避免散落在 UI 与 Repository。
 */
object SessionRules {

    /** 一条记录固定四个座位：0=自己、1=上家、2=对家、3=下家。 */
    const val SEAT_COUNT = 4
    const val SEAT_SELF = 0

    /** 本人默认昵称（规格 2.2）。 */
    const val DEFAULT_SELF_NICKNAME = "我"

    /** 标题去除首尾空白后最多 30 字；空白时使用该默认标题。 */
    const val DEFAULT_TITLE = "麻将对局"
    const val MAX_TITLE_LENGTH = 30

    /** 备注可选，最多 100 字。 */
    const val MAX_NOTE_LENGTH = 100

    /** 麻友昵称去除首尾空白后 1～12 字。 */
    const val MIN_NICKNAME_LENGTH = 1
    const val MAX_NICKNAME_LENGTH = 12

    /** 全库上限：1,000 场、5,000 个 FRIEND（含归档）。 */
    const val MAX_SESSIONS = 1_000
    const val MAX_FRIENDS = 5_000

    /**
     * 按 Unicode 码点计数，避免把 emoji 等代理对算成两个字，
     * 使「最多 N 字」对所有输入都成立。
     */
    fun textLength(text: String): Int = text.codePointCount(0, text.length)
}
