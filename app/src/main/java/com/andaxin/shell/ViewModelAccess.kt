package com.andaxin.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.andaxin.app.AppGraph
import com.andaxin.app.LocalAppGraph

/**
 * 拿 ViewModel 的唯一入口。
 *
 * 依赖全部从 AppGraph 传进来 —— 这就是不用 Hilt 之后省掉的那一大套注解处理器，
 * 代价是这个文件必须存在。加一个 ViewModel 就多一行调用，不用改模块、不用清缓存。
 */
@Composable
inline fun <reified VM : ViewModel> adxViewModel(
    crossinline make: (AppGraph) -> VM,
): VM {
    val graph = LocalAppGraph.current
    return viewModel(
        factory = viewModelFactory { initializer { make(graph) } }
    )
}
