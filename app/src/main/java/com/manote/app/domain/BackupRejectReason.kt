package com.manote.app.domain

/**
 * 拒绝导入备份的原因（规格 6.1「确认前校验」）。
 *
 * 每个取值都对应一条能说清楚问题的提示，而不是统一说一句「文件有问题」：
 * 用户拿到 20 MiB 的文件时，需要知道是版本不对、格式坏了，还是里面某一类数据不合规。
 *
 * 这些原因只描述数据规则，不携带昵称或金额，因此可以安全地记入日志（规格 5.2）。
 */
enum class BackupRejectReason {
    /** 不是 schemaVersion = 3（含旧规格 v1/v2 与未知版本）。 */
    UNSUPPORTED_VERSION,

    /** 文件超过 20 MiB（按实际读取字节判断）。 */
    TOO_LARGE,

    /** JSON 解析失败、必要字段缺失、字段类型非法或枚举取值无法识别。 */
    MALFORMED,

    /** 不是「恰好一个未归档的本人档案」，或本人档案被标成了已归档。 */
    INVALID_SELF,

    /** ID 不是合法 UUID，或在同一张表里重复。 */
    INVALID_ID,

    /** 结果行引用了不存在的场次或档案（外键不完整）。 */
    ORPHAN_REFERENCE,

    /**
     * 某一场没有任何结果行、缺少 seat 0，座位越界或重复，或同一场重复出现同一个 playerId。
     *
     * 注意「只记录了部分座位」不算不完整（规格 1.3）：一场记录 1～4 个位置都合法。
     */
    INCOMPLETE_SEATS,

    /** 座位与人员类型不匹配：seat 0 必须是 SELF，其余必须是 FRIEND。 */
    KIND_MISMATCH,

    /** 单个净金额超出 ±1,000,000 分。 */
    AMOUNT_OUT_OF_RANGE,

    /** 标题、备注或昵称不符合长度与规范化要求。 */
    INVALID_TEXT,

    /** 时间戳为负，或 createdAt/updatedAt/playedAt/archivedAt 的先后关系不成立。 */
    INVALID_TIME,

    /**
     * 场次时长不在 0.5～24 小时之间，或不是 0.1 小时的整数倍。
     *
     * 旧版本导出的备份没有这个字段，解析时会补上默认的 4 小时，
     * 因此这条只会拒绝**明确写了**一个非法时长（例如 0 或 99 小时）的文件。
     */
    INVALID_DURATION,

    /** revision 不是正整数。 */
    INVALID_REVISION,

    /** 超过 1,000 场或 5,000 位牌友的上限。 */
    LIMIT_EXCEEDED,
}
