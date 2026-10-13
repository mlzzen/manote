package com.manote.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.repository.BackupRepository
import com.manote.app.data.repository.DataResetSignal
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SeatResultInput
import com.manote.app.data.repository.SessionDraft
import com.manote.app.data.repository.SessionRepository
import com.manote.app.data.repository.WriteSerializer
import com.manote.app.domain.ManoteError
import com.manote.app.domain.ManoteResult
import com.manote.app.domain.SessionDuration
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import java.util.concurrent.Executors

/**
 * Room 事务测试基类。
 *
 * 该源集（src/sharedTest）同时参与 testDebugUnitTest（本机以 Robolectric 在 JVM 上执行）
 * 与 connectedDebugAndroidTest（真机/模拟器上的 Room 仪器测试），
 * 因此同一份断言不会在两个环境里出现分歧。
 */
@RunWith(AndroidJUnit4::class)
abstract class RoomTestCase {

    protected lateinit var database: ManoteDatabase
    protected lateinit var playerRepository: PlayerRepository
    protected lateinit var sessionRepository: SessionRepository
    protected lateinit var backupRepository: BackupRepository
    protected lateinit var dataResetSignal: DataResetSignal

    /** 可控时钟：测试通过修改它来构造「过去」「未来」时间。 */
    protected var nowMillis: Long = BASE_NOW

    private var idCounter = 0

    /**
     * 自带的查询/事务线程池。
     *
     * 不用 Room 默认的 `ArchTaskExecutor` 那条全局 IO 线程：它在 `database.close()`
     * 之后仍可能在后台跑一次失效通知，撞上已经关闭的连接就抛
     * `IllegalStateException: Illegal connection pointer`。这条异常在普通 JUnit 测试里
     * 只是被丢掉，但在同 JVM 里后跑的 Compose 测试中会被 kotlinx-coroutines 的
     * TestScope 捕获成 `UncaughtExceptionsBeforeTest` —— 表现为**随机某个 UI 测试挂掉**。
     * 自带线程池并在 `@After` 里关掉它，泄漏从根上就没有了。
     */
    private val queryExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-test-db-query") }
    private val transactionExecutor = Executors.newSingleThreadExecutor { Thread(it, "manote-test-db-tx") }

    @Before
    fun createInMemoryDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ManoteDatabase::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(queryExecutor)
            .setTransactionExecutor(transactionExecutor)
            .build()

        // 三个仓库共享同一个写入闸门，与生产环境的装配方式一致。
        val writeSerializer = WriteSerializer()

        playerRepository = PlayerRepository(
            database = database,
            writeSerializer = writeSerializer,
            clock = { nowMillis },
            idFactory = { uuidFor(++idCounter) },
        )
        sessionRepository = SessionRepository(
            database = database,
            writeSerializer = writeSerializer,
            clock = { nowMillis },
        )
        dataResetSignal = DataResetSignal()
        backupRepository = BackupRepository(
            database = database,
            writeSerializer = writeSerializer,
            dataResetSignal = dataResetSignal,
            appVersion = TEST_APP_VERSION,
            clock = { nowMillis },
        )
    }

    @After
    fun closeInMemoryDatabase() {
        database.close()
        queryExecutor.shutdownNow()
        transactionExecutor.shutdownNow()
    }

    /** 建好本人与三位麻友，返回 [自己, 上家, 对家, 下家] 的 ID。 */
    protected suspend fun givenSelfAndThreeFriends(): List<String> {
        val self = playerRepository.ensureSelfPlayer().value()
        val friends = listOf("上家", "对家", "下家").map { nickname ->
            playerRepository.createFriend(nickname, "").value()
        }
        return listOf(self.id) + friends
    }

    protected fun draftOf(
        players: List<String>,
        nets: List<Long>,
        id: String = "session-1",
        playedAt: Long = BASE_NOW - 60_000L,
        title: String = "周五晚局",
        note: String = "",
        durationMinutes: Int = SessionDuration.DEFAULT_MINUTES,
    ): SessionDraft = SessionDraft(
        id = id,
        playedAt = playedAt,
        title = title,
        note = note,
        seats = players.indices.map { seat ->
            SeatResultInput(seat = seat, playerId = players[seat], netMinor = nets[seat])
        },
        durationMinutes = durationMinutes,
    )

    /** 每个座位都必须是 0、1、2、3 各一次。 */
    protected suspend fun resultsOf(sessionId: String) =
        database.sessionResultDao().findBySession(sessionId)

    /** 在场场次数（不含回收站）。 */
    protected suspend fun sessionCount(): Int = database.sessionDao().count()

    /** 回收站里的场次数。 */
    protected suspend fun trashCount(): Int = database.sessionDao().trashCount()
}

/**
 * 所有测试共用的固定「当前时间」，让时间断言可复现。
 */
internal const val BASE_NOW = 1_700_000_000_000L

/** 备份文件里写入的 appVersion；测试固定取值，避免断言依赖真实构建版本。 */
internal const val TEST_APP_VERSION = "0.0.0-test"

/**
 * 确定性 UUID：既满足备份校验要求的规范形式（8-4-4-4-12），又让断言可复现。
 * 生产环境的 idFactory 用的是随机 UUID。
 */
internal fun uuidFor(n: Int): String = "00000000-0000-4000-8000-" + n.toString().padStart(12, '0')

/** 测试辅助：成功时取值，失败时抛出带错误码的断言错误。 */
internal fun <T> ManoteResult<T>.value(): T = when (this) {
    is ManoteResult.Success -> value
    is ManoteResult.Failure -> throw AssertionError("预期成功，实际失败：$error")
}

/** 测试辅助：失败时取错误码，成功时抛出断言错误。 */
internal fun <T> ManoteResult<T>.error(): ManoteError = when (this) {
    is ManoteResult.Failure -> error
    is ManoteResult.Success -> throw AssertionError("预期失败，实际成功：$value")
}
