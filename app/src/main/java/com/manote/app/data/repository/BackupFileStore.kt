package com.manote.app.data.repository

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 备份导出/导入的文件读写。
 *
 * 单独抽出这一层有两个原因：
 * 1. 让 ViewModel 不直接依赖 `ContentResolver`，界面逻辑可以在 JVM 上测；
 * 2. 把「读多少字节」这类与规格 6.1 上限相关的细节集中在一处。
 *
 * 使用系统文件选择器返回的 URI，因此不需要任何存储权限。
 */
interface BackupFileStore {

    /**
     * 读取用户选定文件的字节。
     * 超过备份上限时**不继续读完**，只多读一点即可判定超限，避免大文件把内存撑爆。
     */
    suspend fun readBytes(uri: String): ByteArray

    /** 把文本写到用户选定的位置。返回即表示流已写完并关闭。 */
    suspend fun writeText(uri: String, text: String)
}

class AndroidBackupFileStore(
    private val resolver: ContentResolver,
) : BackupFileStore {

    override suspend fun readBytes(uri: String): ByteArray = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        resolver.openInputStream(parsed)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(READ_BUFFER_BYTES)
            var total = 0L
            while (total <= BackupCodec.MAX_FILE_BYTES) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
            out.toByteArray()
        } ?: throw IOException("无法打开所选文件：$uri")
    }

    override suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        // "wt"：覆盖写入并截断，避免目标位置原有内容比新文件长时留下残尾。
        resolver.openOutputStream(parsed, "wt")?.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
        } ?: throw IOException("无法写入所选位置：$uri")
    }

    private companion object {
        const val READ_BUFFER_BYTES = 64 * 1024
    }
}
