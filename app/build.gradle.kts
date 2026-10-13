import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // JSON 备份导出/导入（规格 4 指定的序列化方案）。
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// 发布签名与版本号：一律从外部注入，源码里不出现任何密钥。
//
//   优先级：环境变量（CI 用 GitHub Secrets 注入） > keystore.properties（本地）
//   keystore.properties 与 keystore/ 都已在 .gitignore 中排除。
//
// 若两者都没有，release 产出的是「未签名」APK，而不是静默改用 debug 签名：
// debug 签名换台机器就变，用它发出去的包既不能覆盖升级，也看不出哪里不对。
// 未签名是一个一眼可见的失败，更容易被发现。
// ---------------------------------------------------------------------------
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun externalValue(envName: String, propertyName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val signingStorePath = externalValue("SIGNING_STORE_FILE", "storeFile")
val releaseStoreFile = signingStorePath?.let { rootProject.file(it) }?.takeIf { it.exists() }

val releaseStorePassword = externalValue("SIGNING_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = externalValue("SIGNING_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = externalValue("SIGNING_KEY_PASSWORD", "keyPassword")

// ---------------------------------------------------------------------------
// 调试签名：四台开发机各自生成一把 debug 密钥，换台电脑签名就不一致——
// 别人的 debug 包装不上你的手机（签名冲突），装过正式包的手机更是直接被挡住。
//
// 这里统一用仓库里那把公开的调试密钥（口令就是众所周知的 android），
// 让任何一台机器、任何一个 CI 打出来的 debug 包都互认。
// 它只用于本地调试，与发布密钥完全无关；发布密钥仍然只走 Secrets / keystore.properties。
//
// 同时 debug 变体带上 .debug 后缀的 applicationId，于是
//   com.manote.app        ← 正式包（用户装的那个）
//   com.manote.app.debug  ← 开发包
// 是两个独立应用，可以同时安装在同一台手机上，谁也覆盖不掉谁。
// ---------------------------------------------------------------------------
val debugStoreFile = rootProject.file("keystore/manote-debug.jks").takeIf { it.exists() }
val debugKeystorePassword = "android"
val debugKeyAlias = "androiddebugkey"

// 密钥库在、口令不全时立刻报错，而不是等到签名阶段抛一句难懂的异常。
val missingSigningFields = buildList {
    if (releaseStoreFile != null) {
        if (releaseStorePassword == null) add("storePassword / SIGNING_STORE_PASSWORD")
        if (releaseKeyAlias == null) add("keyAlias / SIGNING_KEY_ALIAS")
        if (releaseKeyPassword == null) add("keyPassword / SIGNING_KEY_PASSWORD")
    }
}
require(missingSigningFields.isEmpty()) {
    "已找到签名密钥库（$signingStorePath），但缺少：${missingSigningFields.joinToString()}。" +
        "请补全 keystore.properties 或对应的环境变量。"
}

// 发版流程显式要求「必须签名」。有了这道闸，Release 里不会出现未签名的包。
val signingRequired = System.getenv("MANOTE_REQUIRE_SIGNING") == "1"
require(!(signingRequired && releaseStoreFile == null)) {
    "MANOTE_REQUIRE_SIGNING=1，但没找到签名密钥库（SIGNING_STORE_FILE=$signingStorePath）。" +
        "发布流程不允许打未签名的包。"
}

// 版本号由 tag 推出后在 CI 里注入（v1.2.3 → 1.2.3 / 10203）；
// 本地不设这两个环境变量，走下面 defaultConfig 里的手写值。
val injectedVersionName = System.getenv("MANOTE_VERSION_NAME")?.takeIf { it.isNotBlank() }
val injectedVersionCode = System.getenv("MANOTE_VERSION_CODE")?.toIntOrNull()

android {
    namespace = "com.manote.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manote.app"
        minSdk = 26
        targetSdk = 35
        // 本地默认值；发版时由 tag 注入覆盖，不必手改这个文件。
        versionCode = injectedVersionCode ?: 1
        versionName = injectedVersionName ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // 调试签名：用仓库里那把公用的调试密钥，换台电脑也能互相覆盖安装。
        // 文件不在（例如有人只拷了源码）时保持 AGP 默认行为，不做任何替换。
        if (debugStoreFile != null) {
            maybeCreate("debug").apply {
                storeFile = debugStoreFile
                storePassword = debugKeystorePassword
                keyAlias = debugKeyAlias
                keyPassword = debugKeystorePassword
            }
        }

        // 只在真的拿到密钥库时才声明 release 配置，避免凭空造出一个空签名的 config。
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 开发包与正式包用不同的 applicationId，两者可以同时装在一台手机上：
            // 调试时不必先卸掉用户装着的正式包，也不会把正式包覆盖成调试版。
            applicationIdSuffix = ".debug"
            // 手机上两个图标长得一样，靠版本号后缀区分哪个是开发包。
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没配签名时保持 null：产出 app-release-unsigned.apk，
            // 文件名本身就说明「这个包没签名」，不会与正式包混淆。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // 「我的」页需要展示版本号，直接读生成的 BuildConfig。
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric 需要访问打包后的资源与清单。
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // 事务测试与设备无关，写入 sharedTest 后同时参与两个源集：
        //   - src/test      → JVM（Robolectric）执行，本机无设备时唯一可运行的途径
        //   - src/androidTest → 真机/模拟器上的 Room 仪器测试（规格 8 要求）
        // 两个源集共用同一份测试代码，避免同一断言写两遍而产生分歧。
        getByName("test").java.srcDir("src/sharedTest/java")
        getByName("androidTest").java.srcDir("src/sharedTest/java")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room schema 从版本 1 起导出并随源码提交（规格 5.2）。
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.runner)
    // Compose 组件与页面可以在 JVM 上通过 Robolectric 渲染并断言（规格 8 的补充）：
    // 真机仪器测试在开发环境跑不起来，UI 层至少要有能在本机执行的回归网。
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
