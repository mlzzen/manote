# 麻记 Manote

一个只做一件事的 Android 记账 App：**记录麻将局打完后的最终净输赢**。

不联网、不申请多余权限、不收集任何数据，所有记录只存在手机本地。

## 它解决什么问题

一场麻将打完，四个人谁赢谁输、各输赢多少，靠脑子记或写在纸上都不方便；
事后想对账、想看看某个牌友最近的手气，更是无从查起。麻记把这件事收敛成一件事：
打开、填数、保存。剩下的历史、统计、结清，都自动算出来。

## 功能

- **记一场**：填入每位牌友的最终净额，或用「赢 / 输」开关；只记录部分位置也可以
- **允许不完整**：不必凑齐四人，合计也不必为 0；不为 0 时只提示、不阻挡保存
- **结清建议**：账能平的时候给出笔数最少的转账方案
- **场次列表与详情**：按自然月分组，支持关键词搜索
- **牌友管理**：档案、搜索、归档、跨场净输赢统计
- **数据管理**：JSON 全量备份导出 / 导入、单场 CSV 导出
- **隐私优先**：不申请网络、通讯录、定位、相机权限；显式关闭系统云备份

## 技术栈

| 项 | 版本 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose（BOM 2024.10.01）+ Material 3 |
| 数据库 | Room 2.6.1（KSP） |
| 导航 | Navigation Compose 2.8.4 |
| 构建 | AGP 8.7.3 / Gradle 8.9 |
| 运行环境 | 最低 Android 8.0（API 26），编译与目标 API 35 |

依赖注入为手工装配（`ManoteContainer`），未引入 Hilt、网络库或通用框架。

## 设计上的几个取舍

- **金额一律用整数「分」存 `Long`**，全程不经过 `Float` / `Double`，避免浮点误差。
- **人员用 UUID 关联，不按昵称**；保存时写入昵称快照，之后改名不影响历史记录。
- **统计实时从记录算出**，不另存任何手工累计的账本，避免两份数据对不上。
- **合计不为 0 只提示、不阻拦**：现实中一局打完常有人没记全，强制平账只会逼用户编数字。
  不平账时不再给结清建议——那个建议会建立在一个错误的总和之上。
- **未记录的位置不写 0**：导出 CSV 时该位置留空，而不是填 `0.00`，
  避免凭空造出一个并不存在的「持平」。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35）。在项目根目录创建 `local.properties`
指向本机 SDK：

```properties
sdk.dir=/path/to/Android/Sdk
```

然后：

```bash
./gradlew assembleDebug        # 构建 debug APK
./gradlew testDebugUnitTest    # 运行单元测试
```

**单元测试不得隐式依赖跑测机器的时区。** 存储一律是 UTC epoch 毫秒，显示与统计按设备时区，
因此测试只有两种写法是安全的：要么「按设备时区构造、按设备时区断言」（两边同源），
要么把 `ZoneId` 显式传进被测函数（`TimeSlot.of`、`SessionCsv.build/fileName`、
`UiText.dateTime`、`toRowUi/toDetailUi` 等都接受可选的 `zoneId`）。
开发机在东八区、CI 的 runner 是 UTC，隐式依赖本机时区的断言会**本地全绿、CI 必挂**。

提交前可以用 init script 换几个时区各跑一遍（不经修改构建文件）：

```bash
cat > /tmp/tz.init.gradle <<'EOF'
allprojects {
    tasks.withType(Test).configureEach {
        systemProperty 'user.timezone', project.findProperty('tz') ?: 'UTC'
        systemProperty 'user.language', 'en'
        systemProperty 'user.country', 'US'
    }
}
EOF
for z in Asia/Shanghai UTC America/New_York Pacific/Kiritimati; do
  ./gradlew testDebugUnitTest -I /tmp/tz.init.gradle -Ptz="$z" --rerun --console=plain
done
```

## 发布

推一个形如 `v1.2.3` 的 tag，就会自动构建签名 APK 并发布 Release：

```bash
git tag v1.2.3
git push origin v1.2.3
```

`.github/workflows/release.yml` 随后会：跑一遍单元测试 → 用 Secrets 里的密钥库打出 release APK →
用 `apksigner` 确认它确实签了名 → 按 tag 推出 `versionName`（`1.2.3`）与 `versionCode`（`10203`）→
把重命名后的 `manote-1.2.3.apk` 附到 Releases 页。更新说明从提交历史自动生成。

也可以到 Actions 页面手动触发：填版本号就发 Release，留空则只构建并上传产物（试跑用）。

### 一次性配置

签名材料不进版本库，需要在仓库的 **Settings → Secrets and variables → Actions** 里配四个 Secret：

| Secret | 内容 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | 密钥库文件的 base64，单行不换行 |
| `SIGNING_STORE_PASSWORD` | 密钥库口令 |
| `SIGNING_KEY_ALIAS` | 密钥别名，当前为 `manote` |
| `SIGNING_KEY_PASSWORD` | 密钥口令 |

密钥库在本机是 `keystore/manote-release.jks`（与 `keystore.properties` 一起被 `.gitignore` 排除）。
需要把它转成单行 base64 填进 `SIGNING_KEYSTORE_BASE64`：

```bash
# Git Bash / Linux
base64 -w0 keystore/manote-release.jks > keystore/manote-release.jks.base64.txt
clip < keystore/manote-release.jks.base64.txt
echo "已复制 $(wc -c < keystore/manote-release.jks.base64.txt) 个字符"
```

```powershell
# PowerShell
Get-Content keystore/manote-release.jks.base64.txt -Raw | Set-Clipboard
"已复制 $((Get-Content keystore/manote-release.jks.base64.txt -Raw).Length) 个字符"
```

macOS 用 `base64 -i keystore/manote-release.jks | tr -d '\n' | pbcopy`。

> **这两个命令成功时不输出任何东西**——静默就是成功，`clip` 的退出码为 0。想看退出码可以 `echo $?`。
>
> 但「没输出」也意味着看不出到底复制进去的是什么，所以粘贴到 GitHub 之前确认一次。
> 当前这把密钥库复制正确时应当是：**3468 个字符**、以 `MIIKJAIBAzCCCc4G` 开头、以 `ICJxA=` 结尾。
>
> ```powershell
> (Get-Clipboard -Raw).Length      # 应为 3468
> ```
>
> 很容易踩的坑：如果长度只有 70 多、内容是 `Get-Content ...` 开头，那是**把命令本身复制进了剪贴板**，
> 而不是命令的执行结果——粘进 Secret 会得到一个坏掉的密钥库。

可选：在 **Variables** 里把 `SIGNING_CERT_SHA256` 设为下面的证书指纹，
发版时会比对，防止 Secrets 里配成了另一把钥匙：

```
9D:CA:3A:36:43:9E:D5:D3:62:19:C1:C1:FF:E0:2A:0B:B9:D2:90:7A:73:08:C1:01:89:89:12:27:8F:56:92:F6
```

### 本地打签名包

```bash
cp keystore.properties.example keystore.properties   # 填入口令
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没配签名时产物叫 `app-release-unsigned.apk` —— 文件名本身就在说明这个包没签名，
而不是悄悄用 debug 签名打一个「看起来像正式包」的东西。CI 里则更进一步：
发布流程会带上 `MANOTE_REQUIRE_SIGNING=1`，拿不到密钥库直接构建失败。

> **务必备份密钥库与口令。** 两者一旦丢失，就无法再发布任何能覆盖安装的新版本——
> Android 只允许同一签名的包互相升级，届时只能让所有人卸载重装。建议把
> `keystore/manote-release.jks` 和口令离线存一份（密码管理器 / 加密 U 盘），
> 并注意它一旦泄露，任何人都能冒充本项目发布可覆盖安装的「新版」。

## 当前状态

核心功能已完成。单元测试覆盖金额解析与校验、数据层事务与并发、备份编解码与校验、
CSV 转义、UI 纯逻辑等。

**尚未在真机或模拟器上运行过**：开发环境缺少硬件加速，无法启动模拟器，
因此 `connectedDebugAndroidTest` 从未执行。界面只经过编译与逻辑测试验证，
未做过设备上的实际交互与视觉确认。如果你在真机上发现问题，欢迎提 Issue。

## 许可证

[MIT](LICENSE)
