package com.manote.app

import android.content.Context
import androidx.room.Room
import com.manote.app.data.local.ManoteDatabase
import com.manote.app.data.local.ManoteMigrations
import com.manote.app.data.repository.AndroidBackupFileStore
import com.manote.app.data.repository.BackupFileStore
import com.manote.app.data.repository.BackupRepository
import com.manote.app.data.repository.DataResetSignal
import com.manote.app.data.repository.PlayerRepository
import com.manote.app.data.repository.SessionRepository
import com.manote.app.data.repository.WriteSerializer

/**
 * 手动构造依赖（规格 4：单 Activity、手动构造依赖，不引入 Hilt 或通用框架）。
 *
 * 数据库与写入闸门在应用内各只有一份；三个 Repository 共享同一个 [WriteSerializer]，
 * 因此跨仓库的写操作同样串行，备份恢复与场次编辑不会相互穿插。
 */
class ManoteContainer(context: Context) {

    private val database: ManoteDatabase = Room
        .databaseBuilder(
            context.applicationContext,
            ManoteDatabase::class.java,
            ManoteDatabase.DATABASE_NAME,
        )
        // 显式 migration（规格 5.1）：升级路径写死在代码里，不依赖任何破坏性回退
        .addMigrations(*ManoteMigrations.ALL)
        .build()

    private val writeSerializer = WriteSerializer()

    private val dataResetSignal = DataResetSignal()

    /** 备份恢复成功后用于通知已打开的编辑表单作废（规格 2.3、T24）。 */
    val dataReset: DataResetSignal get() = dataResetSignal

    val playerRepository: PlayerRepository = PlayerRepository(database, writeSerializer)

    val sessionRepository: SessionRepository = SessionRepository(database, writeSerializer)

    val backupRepository: BackupRepository = BackupRepository(
        database = database,
        writeSerializer = writeSerializer,
        dataResetSignal = dataResetSignal,
        appVersion = BuildConfig.VERSION_NAME,
    )

    /** 导出/导入的文件读写走系统文件选择器，不需要存储权限。 */
    val backupFileStore: BackupFileStore =
        AndroidBackupFileStore(context.applicationContext.contentResolver)
}

