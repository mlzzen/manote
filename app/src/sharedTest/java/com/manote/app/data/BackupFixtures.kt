package com.manote.app.data

import com.manote.app.data.repository.BackupFile

/**
 * 备份测试用的公共工具。
 *
 * 放在 sharedTest 里，两个源集共用：本机由 Robolectric 跑，连上设备后同一份代码
 * 直接作为 Room 仪器测试执行。
 */

/**
 * 把整份备份的 ID 换成另一套，模拟「从另一台设备导入」。
 *
 * 只改 Player 的昵称（快照保持原样，规格允许快照与当前昵称不同），
 * 因此改动后的备份依然必须通过校验。
 */
internal fun BackupFile.withFreshIds(): BackupFile {
    val playerIds = players.mapIndexed { index, player -> player.id to uuidFor(900 + index) }.toMap()
    val sessionIds = sessions.mapIndexed { index, session -> session.id to uuidFor(950 + index) }.toMap()
    return copy(
        players = players.map { player ->
            player.copy(id = playerIds.getValue(player.id), nickname = player.nickname + ARRIVED_SUFFIX)
        },
        sessions = sessions.map { it.copy(id = sessionIds.getValue(it.id)) },
        sessionResults = sessionResults.map { result ->
            result.copy(
                sessionId = sessionIds.getValue(result.sessionId),
                playerId = playerIds.getValue(result.playerId),
            )
        },
    )
}

/** 换一套 ID 之后，昵称统一加这个后缀，用来断言「导入后看到的是文件里的昵称」。 */
internal const val ARRIVED_SUFFIX = "(新)"
