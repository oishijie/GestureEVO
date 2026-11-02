# 交接说明：GestureEVO 的 Miuix UI 重构

> 写给接手的 AI 助手。先读完本文件再动手。
> 状态时间：2026-11-02，基于 `main` 分支提交 `6ddba9b`。

---

## 一、这个项目在做什么

把 GestureEVO（Android 手势工具，包名 `io.github.lumiagg.gestureevo`）的**全部界面**改造成 KernelSU Manager 的 Miuix（小米 HyperOS）设计语言。

**要求是彻底重构，不是换皮。** 不要满足于改颜色、改圆角，要真正把 View/XML 界面重写成 Miuix Compose。

---

## 二、已完成（15 个页面，编译通过）

| 页面 | 文件 |
|---|---|
| 首页 | `ui/HomeActivity.kt` |
| 外观设置 | `settings/AppearanceSettingsActivity.kt` |
| 高级设置 | `settings/AdvancedSettingsActivity.kt` |
| 后台稳定性 | `settings/BackgroundStabilityActivity.kt` |
| 数据备份 | `settings/DataBackActivity.kt` |
| 数据统计 | `settings/DataStatsActivity.kt` |
| 外部调用记录 | `settings/ExternalActionRecordsActivity.kt` |
| 隐藏搜索结果 | `settings/HiddenSearchResultsActivity.kt` |
| 搜索引擎设置 | `settings/SearchEngineSettingsActivity.kt` |
| 设置搜索 | `settings/SettingsSearchActivity.kt` |
| 冻结触发器 | `settings/FreeTriggerSettingsActivity.kt` |
| 关于 | `settings/ui/AboutActivity.kt` |
| 首次引导 | `onboarding/OnboardingActivity.kt` |
| 功能引导 | `ui/home/FeatureGuideActivity.kt` |
| 崩溃页 | `utils/CrashShowActivity.kt` |

**必须复用的基础设施：**

- `ui/miuix/MiuixScreen.kt` —— 页面骨架，签名：
  ```kotlin
  MiuixScreen(
      title: String,
      onBack: (() -> Unit)? = null,
      actions: @Composable RowScope.() -> Unit = {},
      content: LazyListScope.() -> Unit,
  )
  ```
  内部就是 KernelSU 的结构：`Scaffold` + `TopAppBar` + `LazyColumn(padding horizontal 12dp, spacedBy 12dp)`。

- `MiuixCard { ... }` —— 同文件，`Card` 包裹的 `Column`，用来做分组。

- `ui/compose/EvoTheme.kt` 里的 `MiuixEvoTheme { ... }` —— 把项目现有的「明暗模式 + 动态取色」映射到 Miuix `ColorSchemeMode`，固定 `TonalSpot` + `Spec2025`。**每个 Compose 页面都要用它包起来。**

**标准页面写法（照抄）：**

```kotlin
class XxxActivity : AppCompatActivity() {          // 用 AppCompatActivity，取色对话框要这个类型
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixEvoTheme {
                MiuixScreen(title = stringResource(R.string.xxx_title), onBack = { finish() }) {
                    item { MiuixCard { SwitchPreference(...) } }
                    item { MiuixCard { ArrowPreference(...) } }
                }
            }
        }
    }
}
```

---

## 三、还没做的（这是你的主要工作）

### 3.1 仍用 ViewBinding 的界面（43 个文件）

用 `rg -l 'databinding' app/src/main/java --glob '*.kt'` 可以随时重新列出。分组如下：

**A. 设置类页面（相对简单，优先做）**
- `settings/ActionViewSettingsActivity.kt`
- `settings/FreeFormSettingsActivity.kt` —— 含可拖拽尺寸矩形 + 全屏沉浸 + 横竖屏切换，交互最复杂
- `settings/FreeTriggerConfigActivity.kt`
- `settings/GestureAddActionActivity.kt`（654 行）
- `settings/IconEditorActivity.kt` —— 含轮盘样式自绘预览
- `settings/InspireSettingsActivity.kt`（788 行）
- `settings/LogManagerActivity.kt`（566 行，分页 + 采集 + 导出）
- `settings/SearchEngineAddActivity.kt`

**B. 底部弹层（一次性 10+ 个，模式统一）**
`settings/ui/` 下的 `ActionConfigBottomSheet`、`ActionSelectBottomSheet`、`GestureTimingConfigBottomSheet`、`LinkOpenBottomSheet`、`NotificationDisplayTimeBottomSheet`、`PointerTouchAreaOffsetBottomSheet`，以及 `app_select_bottomsheet.xml` 等布局。

**C. 运行时浮层（风险最高，最后做）**
- `gesture/GestureMainFloating.kt`、`gesture/ui/MainActionView.kt`（手势轮盘）
- `inspire/*`（取词浮层，10+ 文件）
- `pin/PinPoolFloating.kt`（图钉池）
- `pointer/PointerFloating.kt`
- `notification/NotificationFloating.kt`
- `screen_search/ScreenSearchFloating.kt`
- `search/QuickSearchActivity.kt`（快速搜索，含 `QuickSearchMotionController` 等 5 个配套类）
- `quick/QuickStartActivity.kt`

这些浮层与手势运行时强耦合，改之前先读懂它们的生命周期和 WindowManager 用法。

### 3.2 已知功能回退（要做完需要补）

1. **深链定位失效**：原 `SettingsSearchNavigator.focusAnchorIfNeeded(view, intent)` 依赖 View 树 id，Compose 页面没有对应实现。设置搜索跳转后无法定位到具体条目。
2. **搜索引擎列表**：只剩「点击编辑」，丢了长按菜单（设默认 / 隐藏 / 删除）和拖拽排序。
3. **数据统计页**：丢了「刷新」按钮（改为进入自动加载）。
4. **设置搜索页**：丢了共享元素转场和入场动画，以及输入框自动聚焦 + 弹键盘。

---

## 四、环境约束（重要，会踩坑）

### 4.1 构建命令

```bash
cd ~/workspace/GestureEVO
~/tools/gradle-9.3.1/bin/gradle :app:compileDebugKotlin --console=plain --max-workers=1
```

- **不要用 `./gradlew`**（没有执行权限，且 wrapper 指向的 8.13 下载不完整）
- **PATH 里没有 `gradle`**
- 必须 `--max-workers=1`：并发下载会触发 TLS 握手失败
- 单次编译约 3–4 分钟，daemon 复用后更快；`assembleDebug` 约 10 分钟
- **长任务务必放后台并开通知，不要盲等**

### 4.2 工具链版本（已升级，不要降级）

AGP 9.1.0 / Kotlin 2.4.20 / KSP 2.3.13 / Compose BOM 2026.09.00 / Gradle 9.3.1 / compileSdk 37 / **minSdk 33**

`minSdk 33` 是产品决策（模糊效果依赖 `miuix-blur`，它要求 33），不要改回去。

### 4.3 AGP 9 的坑

- **不能**再应用 `org.jetbrains.kotlin.android` 插件（AGP 9 内置 Kotlin），也不要写 `kotlinOptions`
- `sourceSets` 里不能传 Provider，要 `.get().asFile`
- `compileSdk = 37` 必须配 `compileSdkMinor = 0`（平台目录叫 `android-37.0`）
- AGP 9 强制 build-tools ≥36，且会忽略 `buildToolsVersion` 设置

### 4.4 本机 SDK 的特殊情况

容器里的 SDK 是 **arm64 特供版**：
- `build-tools/35.0.0` 是 aarch64
- `build-tools/36.0.0` 原本是 x86-64，跑不了

已把 35.0.0 的二进制整体覆盖进 36.0.0 目录，只保留 36 的 `source.properties` / `package.xml`。**这是当前能编译的前提**，别删别重装（会变回 x86-64）。备份在 `/tmp/bt36.*`。

换到别的机器后，这条不适用，删掉重建干净即可。

### 4.5 native 构建被临时关闭

`app/build.gradle.kts` 里有 **15 处 `TEMP-VERIFY` 注释**，覆盖：
- `ndkVersion = "27.0.12077973"`
- `ndk { abiFilters += listOf("arm64-v8a") }`
- 两处 `externalNativeBuild { cmake { ... } }`

原因：`dl.google.com` 的 SDK 清单拉不下来，NDK 27 装不上。

**后果**：打出的 APK 不含项目自己的 native 库（只有依赖自带的 `libandroidx.graphics.path.so` 等），OCR、部分图像处理会崩溃。

**你在有 NDK 的机器上，恢复这几处注释即可。**

### 4.6 网络

- 华为云 / 腾讯云镜像可用
- `services.gradle.org`、`dl.google.com` 经常超时
- `github.com` 的 git 推送时好时坏（遇到过 TLS handshake failed、连接重置），**失败就重试几次**
- `settings.gradle.kts` 里有 jitpack 排他解析（`com.github.liangjingkanji`、`com.github.thegrizzlylabs`），因为镜像站只有 pom 没有 aar。**不要写成 `com.github.*`**，会误伤 Maven Central 上的 `com.github.ajalt.colormath`

---

## 五、编码约定

1. **字符串只能引用已存在的资源**：改之前先用 `rg 'name="xxx"' app/src/main/res/values/strings.xml` 确认。不要臆造资源名——这是已经犯过的错误。
2. **图标只能引用已存在的 drawable**：`ls app/src/main/res/drawable | grep keyword` 先确认。
3. **保留全部业务逻辑**：偏好键常量、默认值、开关联动、权限申请、跳转 Intent、数据读写，一个都不能丢。重构的是界面，不是行为。
4. **类继承 `AppCompatActivity`**（不是 `ComponentActivity`），因为 `showColorPickerDialog` 等工具函数要求这个类型。
5. **不要自创抽象**：复用 `MiuixScreen` / `MiuixCard`，照着已完成的 15 个页面写。需要新组件时先看 KernelSU 原版怎么做的。
6. **改完集中编译**，不要每改一个文件就编译一次（一轮 3–4 分钟）。

---

## 六、设计语言规范

完整规范在 **`docs/kernelsu-ui-design-language.md`**（589 行），必读。速查：

- 页面边距 **12dp**，卡片间距 **12dp**，卡片内边距 **16dp**
- 字号档位：12（辅助）/ 14 / 15（正文）/ 16（卡片标题）/ 17 / 22 / 35
- 字重只用 400 / 500 / 600
- 语义色：主色 `primary`，次级文字**只用** `onSurfaceVariant`，容器层级 `surfaceContainer → High → Highest`
- 卡片**不描边、不用阴影**，靠容器色分层
- 动效两档：内容 300ms，消失 150ms
- 对话框骨架：`0 水平外边距 + 24dp 内容内边距 + 列表最大 500dp + 底部等宽双按钮 20dp 间隔`

**KernelSU 原版源码参考**（本地已克隆在 `~/workspace/KernelSU/manager/`）：

- 首页：`ui/screen/home/HomeMiuix.kt`
- 设置页：`ui/screen/settings/SettingsMiuix.kt`
- 主题：`ui/theme/MiuixTheme.kt`
- 组件：`ui/component/miuix/`

**注意**：KernelSU 的 `onSurfaceVariant` 用法、卡片规格、对话框间距都是可以直接照抄的；但它的品牌色、Logo、液体玻璃折射实现**不要抄**（见文档第 10 节「禁止复制项」）。

---

## 七、验证要求

当前所有改动**只经过编译验证，没有任何真机运行验证**。

接手后第一件事应该是：**装到设备上跑一遍已完成的 15 个页面**，重点确认：

- Miuix 顶栏折叠、返回按钮
- 对话框样式与间距
- 开关 / 下拉 / 滑杆的交互
- 深色模式与动态取色
- 模糊效果（首页顶栏）

然后再继续迁移剩余页面。**不要在没有运行验证的情况下无限扩充改动面。**

---

## 八、建议的执行顺序

1. 先跑已完成的 15 页，修实际问题
2. 补 3.2 的四项功能回退
3. 迁移 A 组设置类页面（从简单到复杂：`FreeTriggerConfig` → `ActionViewSettings` → `SearchEngineAdd` → `InspireSettings` → `IconEditor` → `LogManager` → `GestureAddAction` → `FreeForm`）
4. 迁移 B 组底部弹层（模式统一，可批量）
5. 最后处理 C 组运行时浮层（每个都要单独设计）

每批改完集中编译一次，并记录改了哪些文件、保留了哪些行为、有无等价性损失。
