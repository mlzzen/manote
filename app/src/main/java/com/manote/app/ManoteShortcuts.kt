package com.manote.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * 桌面快捷方式：长按应用图标即可直接跳到「记一场」。
 *
 * 记录一场是这个应用最高频的一次动作，但正常路径要先开 App 再点底栏中间那颗按钮；
 * 快捷方式把这段路压成一跳。
 *
 * 用**动态快捷方式**而不是 `res/xml/shortcuts.xml` 的静态快捷方式：静态清单里的
 * `targetPackage` 必须写死，而本项目的 debug 变体带 `.debug` 后缀
 * （见 build.gradle.kts），写死的包名在 debug 包上会解析到另一个应用、
 * 甚至被系统直接丢地不显示。动态注册用 [Intent] 显式指向本应用的
 * [MainActivity]，实际包名由系统按变体填写，debug 与 release 都能正确落地。
 */
object ManoteShortcuts {

    /** 快捷方式携带的动作；由 [MainActivity] 读出后决定起始页面。 */
    const val ACTION_RECORD_SESSION = "com.manote.app.action.RECORD_SESSION"

    /** 快捷方式的稳定 ID，重复注册时覆盖同一条而不是新增一条。 */
    private const val SHORTCUT_ID = "record_session"

    /**
     * 注册（或刷新）「记一场」快捷方式。幂等，可在每次启动时调用；
     * 系统不支持或调用失败时静默跳过——快捷方式本就是一个便利入口，不该影响启动。
     */
    fun installRecordSessionShortcut(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.shortcut_record_short))
            .setLongLabel(context.getString(R.string.shortcut_record_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_record))
            .setIntent(
                Intent(context, MainActivity::class.java)
                    .setAction(ACTION_RECORD_SESSION),
            )
            .build()

        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, listOf(shortcut)) }
    }
}
