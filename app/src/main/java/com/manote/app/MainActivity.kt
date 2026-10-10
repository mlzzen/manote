package com.manote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manote.app.ui.navigation.ManoteNavHost
import com.manote.app.ui.theme.ManoteTheme

/**
 * 单 Activity 宿主（规格 4）。依赖采用手动构造，不引入 DI 框架。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ManoteTheme {
                ManoteNavHost()
            }
        }
    }
}
