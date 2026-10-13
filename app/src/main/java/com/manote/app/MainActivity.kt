package com.manote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manote.app.ui.navigation.ManoteNavHost
import com.manote.app.ui.theme.ManoteTheme

/**
 * 单 Activity 宿主（规格 4）。依赖采用手动构造，不引入 DI 框架。
 *
 * 除了作为唯一的页面容器，这里还负责识别桌面快捷方式带来的 [ManoteShortcuts.ACTION_RECORD_SESSION]
 * 动作——带上它启动时直接落到「记一场」表单上，而不是先停在首页再让用户点一次。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val startWithRecordSession = intent?.action == ManoteShortcuts.ACTION_RECORD_SESSION

        setContent {
            ManoteTheme {
                ManoteNavHost(startWithRecordSession = startWithRecordSession)
            }
        }
    }
}
