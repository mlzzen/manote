package com.manote.app.data.local

import androidx.room.TypeConverter

/**
 * Player 类型（规格 2.2、5.1）。
 * 持久化为字符串，与备份 JSON 中的枚举写法保持一致。
 */
enum class PlayerKind {
    /** 本人档案，全库恰有一个，不可删除、归档或切换身份。 */
    SELF,

    /** 麻友档案。 */
    FRIEND,
}

/** Room 类型转换器。 */
class ManoteConverters {

    @TypeConverter
    fun fromPlayerKind(kind: PlayerKind): String = kind.name

    @TypeConverter
    fun toPlayerKind(raw: String): PlayerKind =
        PlayerKind.entries.firstOrNull { it.name == raw }
            ?: throw IllegalArgumentException("未知的 PlayerKind: $raw")
}
