package com.manote.app.domain

/**
 * 单个座位在一场中的净结果，是排名与结清计算的唯一输入。
 * 只带座位号与金额，不引用 Player，因此 domain 不依赖数据库。
 */
data class SeatNet(val seat: Int, val netMinor: Long)
