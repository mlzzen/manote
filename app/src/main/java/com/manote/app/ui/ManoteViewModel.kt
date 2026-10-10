package com.manote.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.manote.app.ManoteApplication
import com.manote.app.ManoteContainer

/**
 * 取当前应用的依赖容器。
 *
 * 规格 4 要求手动构造依赖、不引入 Hilt 或通用框架，
 * 因此这里直接从 [ManoteApplication] 上取唯一的容器实例。
 */
@Composable
fun rememberManoteContainer(): ManoteContainer {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as ManoteApplication).container
    }
}

/**
 * 构造带依赖的 ViewModel。
 *
 * [create] 额外拿到 [CreationExtras]，需要 `SavedStateHandle` 的表单可以用
 * `extras.createSavedStateHandle()` 拿到与导航条目绑定的句柄。
 *
 * 工厂按容器实例缓存，重组不会反复创建；ViewModel 自身按 [key] 复用。
 */
@Composable
inline fun <reified VM : ViewModel> manoteViewModel(
    key: String? = null,
    crossinline create: (ManoteContainer, CreationExtras) -> VM,
): VM {
    val container = rememberManoteContainer()
    val factory: ViewModelProvider.Factory = remember(container) {
        viewModelFactory {
            initializer { create(container, this) }
        }
    }
    return viewModel(key = key, factory = factory)
}
