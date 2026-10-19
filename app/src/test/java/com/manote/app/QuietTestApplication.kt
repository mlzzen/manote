package com.manote.app

import android.app.Application

/**
 * JVM 单元测试专用的空壳 Application（由 `src/test/resources/robolectric.properties` 指定）。
 *
 * 真实的 [ManoteApplication.onCreate] 会启动两条后台协程：补建本人档案、清理过期回收站。
 * 它们最终按 Room 的默认执行器打开真实数据库，测试结束时这条连接被关掉，
 * 异常落在 `ArchTaskExecutor` 的 `arch_disk_io` 线程上没人接收。
 * 普通 JUnit 测试只是把它丢掉，但 Compose 测试（`createComposeRule`）内部用
 * kotlinx-coroutines 的 TestScope 启动，`TestScope.enter()` 会把「测试开始前
 * 已经存在的未捕获异常」直接判为失败——表现为**随机某个 UI 测试挂掉**，
 * 且每次挂的还不是同一条。
 *
 * 单元测试各自建自己的内存库，不需要应用的启动副作用，因此统一换成这个空壳。
 * 真机/模拟器上的仪器测试不读 robolectric.properties，仍走真实 Application。
 */
class QuietTestApplication : Application()
