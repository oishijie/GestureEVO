# KernelSU 管理器 UI 设计语言逆向分析

> 分析对象：KernelSU Manager（`tiann/KernelSU`，`manager/` 模块）
> 分析方法：静态源码读取 + 全量 grep 量化
> 样本规模：210 个 Compose 文件、946 个 dp 字面量、74 个 sp 字面量、214 处动画调用
> 结论性质：全部数值来自源码字面量，未编译、未上机渲染

---

## 0. 前提：这是一个「双轨」设计系统

KernelSU 管理器内部并存两套完整实现，切换入口在设置页：

| | Miuix 模式（默认） | Material 模式 |
|---|---|---|
| 来源 | `top.yukonga.miuix.kmp` 0.9.4（小米 HyperOS 风格） | Material3 Expressive |
| 入口 | `ui/theme/MiuixTheme.kt` | `ui/theme/MaterialTheme.kt` |
| 默认值 | `UiMode.DEFAULT_VALUE = Miuix`（`ui/UiMode.kt:15`） | 需用户手动切换 |
| 同名屏幕 | `HomeMiuix.kt`（709 行） | `HomeMaterial.kt`（609 行） |

两套共用业务层，仅界面不同。**下面的规范凡有分歧处都双列标注。**

---

## 1. 整体气质

| 关键词 | 依据 |
|---|---|
| **双轨并存** | 两套完整 UI 实现，同名屏幕各写一份 |
| **系统同源** | 默认 `key_color=0` 从壁纸取色，App 本身没有品牌主色 |
| **克制** | `component/` 目录零显式描边（`BorderStroke` 0 命中）；唯一自定义阴影在浮动底栏 |
| **卡片化** | 首页、设置、模块页全部卡片堆叠；Miuix `Card` 124 次、`BasicComponent` 35 次 |
| **高信息密度** | 页面边距 12–16dp，辅助文字降到 12sp |
| **触感化** | 卡片按压用 `PressFeedbackType.Tilt`/`Sink`，非 ripple |
| **语义分层严格** | 次级文字 40 次全部用 `onSurfaceVariant`，不靠透明度 |
| **工具理性** | 版本号、内核号用等宽字体，无装饰插画 |

---

## 2. 配色系统

### 2.1 架构

```
种子色 (seed)  →  materialkolor 生成  →  40+ 语义色 (ColorScheme)
     ↑                                         ↓
壁纸取色 / 15 个预设                    界面只引用语义名，不写 hex
```

**核心规则：UI 代码几乎不出现颜色值，只出现语义名。** Kotlin 侧 `MaterialTheme.colorScheme.*` 引用 139 次、`MiuixTheme.colorScheme.*` 22 次。

默认配置（`data/repository/SettingsRepositoryImpl.kt`）：

| 键 | 默认值 | 含义 |
|---|---|---|
| `color_mode` | 0 | 跟随系统 |
| `key_color` | 0 | 取系统壁纸色 |
| `color_style` | `TonalSpot` | 调色风格 |
| `color_spec` | `SPEC_2025` | Material 色彩规范版本 |
| `miuix_monet` | false | Miuix 是否跟随壁纸 |

### 2.2 语义色角色表

| 角色 | Token | 用途 | 引用次数 |
|---|---|---|---|
| 主色 | `primary` / `onPrimary` | 主按钮底、链接、FAB、选中态 | 12 + 10 |
| 辅助色 | `secondary` / `secondaryContainer` | 「已安装」类次级标签、图标占位底 | 5 |
| 强调辅助 | `tertiary` / `tertiaryContainer` | **专用于「有更新」提示** | 6 |
| 背景 | `background` / `surface` | 页面底、WebView 底 | 4 |
| 容器层级 | `surfaceContainer` → `High` → `Highest` → `Bright` | 页面底(10) → 代码块/卡片(5) → 输入框/小控件(7) → TonalCard(2) | — |
| 文字主 | `onSurface` / `onBackground` | 正文、主内容 | 8 + 3 |
| 文字次 | `onSurfaceVariant` | 副标题、说明、图标 tint | **40**（第二名的 3.3 倍） |
| 文字三 | `onSurfaceVariantSummary`（Miuix） | 卡片内副文案 | — |
| 边框 | `outline.copy(alpha=0.5f)` | 唯一边框写法 | — |
| 错误 | `error` → `errorContainer` → `onErrorContainer` | 文字 → 块底 → 块内文字 | 6 + 5 + 3 |

### 2.3 兜底色板（非动态取色时硬编码）

| 用途 | 浅色 | 深色 |
|---|---|---|
| 首页成功卡背景 | `#DFFAE4` | `#1A3825` |
| 成功图标 | `#36D167` | `#36D167` |
| 徽标文字 | `#164A29` | `#B8E8C5` |
| 徽标背景 | `#B8E8C5` | `#315D3E` |
| 错误卡背景 | `#F8E2E2` | `#310808` |
| 提示卡背景 | `#FFF0DB` | `#3E2F1B` |
| 错误文字 | `#F72727` | `#F72727` |
| 提示文字 | `#F5A623` | `#F5A623` |

出处：`ui/component/miuix/WarningCard.kt:60-85`、`ui/screen/home/HomeMiuix.kt:277-359`。

### 2.4 透明度惯例

| alpha | 用途 |
|---|---|
| 0.87 | 模糊条底色（`ui/util/BlurExt.kt:39`） |
| 0.8 | 标签底、图标 tint |
| 0.6 | 「更新」标签底 |
| 0.5 | 边框、分割线、未选中导航 |
| 0.38 | M3 禁用 thumb |
| 0.15 | 「已安装」标签底、选中胶囊底 |
| 0.12 | M3 禁用 track / 边框 |
| 0.1 | 阴影、浅分隔 |
| 0.03 | 按压叠加 |

### 2.5 WebUI 色板（161 个 CSS 变量）

`app/src/main/assets/webview/colors_dark.css` 与 `colors_light.css`，两层结构：

- **80 个原始色阶**：`--gray-000…1000`、`--blue/green/yellow/orange/red/pink/purple-000…900`
- **81 个语义变量**：`--textPrimary`、`--textSecondary`、`--border`、`--inputBackground`、`--link` 等

关键值：

| 变量 | 深色 | 浅色 |
|---|---|---|
| `--textPrimary` | `--gray-050` | `--gray-1000` |
| `--textSecondary` | `--gray-300` | `--gray-700` |
| `--appBackground` | `--gray-1000` | `--gray-000` |
| `--border` | `rgba(191,193,201,0.16)` | `rgba(65,67,78,0.25)` |
| `--inputBackground` | `rgba(191,193,201,0.12)` | `rgba(65,67,78,0.12)` |
| `--link` | `--blue-300` | `--blue-500` |

**设计系统纯度证据**：161 个变量中只有 15 个是纯字面 hex，其余全部走 `var()` 链；灰阶在明暗两套文件中几乎完全相同，靠语义层换档位实现明暗切换。

---

## 3. 字体排版

### 3.1 全局定义

只有一个覆盖：`bodyLarge = 16sp / lineHeight 24sp / letterSpacing 0.5sp / Normal`（`ui/theme/Type.kt:10-16`），其余继承 Material3 默认。

**行高几乎未被设计**：全项目仅 6 处 `lineHeight`，真实值只有 2 个（24sp、14sp）。12sp/15sp/17sp 全部走 Compose 默认行高（约 1.2×）。

### 3.2 两套模式策略相反

| | Material 模式 | Miuix 模式 |
|---|---|---|
| sp 字面量 | **0 个**（全引用 `MaterialTheme.typography.*`） | **69 个**（35 个是 `12.sp`） |
| 样式引用 | 78 次，13 种角色 | 仅 10 次 `MiuixTheme.textStyles.*` |
| 组装方式 | `fontSize = typography.labelMedium.fontSize` 取单维 | `fontSize = 12.sp, fontWeight = Medium` 全手写 |

Material 侧高频角色：`bodyMedium`(33) → `bodySmall`(16) → `labelMedium`(10) → `titleMedium`(5) → `titleLarge`(2)。

### 3.3 实测字号频次（Miuix 侧）

| sp | 次数 | 用途 |
|---|---|---|
| **12** | 35 | 摘要、元信息、说明（辅助文字主力） |
| 14 | 14 | 次级正文、警告卡说明 |
| 15 | 7 | 模块卡片正文、操作按钮 |
| 17 | 6 | 搜索输入框、模块名 |
| 16 | 5 | 卡片标题（= bodyLarge 基准） |
| 22 | 1 | 首页状态卡主数值 |
| 35 | 1 | About 页 Hero 标题 |
| 11 | 1 | 底部导航标签 |
| 9 | 1 | 角标（低于可读下限） |

### 3.4 字重

`Medium` 20 次（默认强调手段）> `SemiBold` 3 次（大数值、模块名）> `Bold` 3 次（Hero 标题）> `Normal` 1 次。

### 3.5 层级建议

| 层级 | 字号/行高 | 字重 | 颜色 |
|---|---|---|---|
| 页面主标题 | 22 / 28 | SemiBold | onSurface |
| 卡片标题 | 16–17 / 22 | Medium | onSurface |
| 正文 | 15 / 21 | Normal | onSurface |
| 次级正文 | 14 / 20 | Normal | onSurfaceVariant |
| 辅助/元信息 | 12 / 16 | Normal | onSurfaceVariant |
| 角标/标签 | 11 / 14 | 750 或 Medium | 语义容器色 |

---

## 4. 间距系统

### 4.1 基础单位 = 4dp

946 个 dp 字面量中：**99.6% 是 2 的倍数，73.9% 是 4 的倍数，仅 43.6% 是 8 的倍数**。

以 8dp 为基底无法解释出现 144 次的 `12dp` 与 48 次的 `20dp`；4dp 基底则 3×4、5×4 自然成立。

### 4.2 两套节奏

| | Material 模式 | Miuix 模式 |
|---|---|---|
| 主值 | **16**（108 次） | **12**（117 次） |
| 次值 | 8 / 4 / **13** | 6 / 16 / 8 / 20 |
| 页面左右边距 | 16dp | 12dp |
| 卡片垂直间距 | `spacedBy(13.dp)` | `spacedBy(12.dp)` |
| 卡片内边距 | 16dp | 16dp（`Card.insideMargin`） |

**13dp 是 Material 侧唯一的系统性脱网格值**（25 次中 22 次在 Material 文件），专用于卡片垂直间距与分组底距，与 Miuix 的 12dp 严格成对。成因**推测**为抵消 M3 卡片 tonal 边缘的视觉压缩做光学补偿，无法从源码证实。

### 4.3 层级实测值

| 层级 | 值 | 出处 |
|---|---|---|
| 页面边距 | 12dp（Miuix）/ 16dp（Material） | `HomeMiuix.kt:120`、`HomeMaterial.kt:93` |
| 区块间距 | 12dp / 13dp | 同上 |
| 卡片内边距 | 16dp | `HomeMiuix.kt` |
| 信息行间距 | 24dp | `HomeMiuix.kt` InfoText |
| 图标-文字间距 | 6dp（Miuix）/ 12dp（Material） | `HomeMiuix.kt` |
| 对话框内容 | 水平 24dp / 垂直 12–16dp | `SendLogDialog.kt:99` |
| 对话框按钮间距 | **20dp**（唯一值） | 4 处对话框完全一致 |
| 列表最大高 | 500dp | `ChooseKmiDialogMiuix.kt:55` |

### 4.4 触控目标三档

44dp（搜索图标命中区）/ 45dp（搜索框最小高）/ 56dp（设置行、底栏内容）。浮动底栏容器 64dp。低于 44dp 的只有非交互角标。

---

## 5. 布局规则

- **栅格**：无栅格系统，全部单列垂直流（`LazyColumn` / `Column` + `verticalScroll`），卡片 100% 宽
- **对齐**：左对齐为主，仅首页状态卡与对话框标题居中
- **信息密度**：高。12–16dp 边距 + 12sp 辅助文字
- **卡片形态**：① 状态卡（Hero，左文右图，图标 110dp + alpha 0.8）② 信息卡（图标 24dp + 标题 + 值）③ 导航卡（`BasicComponent` + 箭头）
- **导航**：4 个一级 tab（首页/超级用户/模块/设置），底部导航栏
- **响应式断点**：宽度 ≥840dp，或 ≥600dp 且高宽比 <1.2 时，底栏切换为侧边导航栏（`ui/util/WindowSize.kt:13`）
- **内容优先级**（首页自上而下）：更新提示 → PR/GKI 警告 → 安装状态 Hero 卡 → 系统信息卡 → 支持链接

---

## 6. 组件规范

| 组件 | 圆角 | 边框 | 阴影 | 内边距 | 尺寸 | 状态 |
|---|---|---|---|---|---|---|
| 卡片（Miuix） | 继承库默认 | 无 | 继承库默认 | 16dp | 随内容 | `Tilt` 倾斜 / `Sink` 下沉 |
| 卡片（Material） | 16dp 外 / 4dp 内 | 无 | 无显式 | 16dp | — | ripple |
| 对话框按钮 | 继承 | 无 | 无 | — | 成对 `weight(1f)` 等宽 | 确认按钮 `textButtonColorsPrimary()`；20dp 间隔 |
| 输入框（Miuix） | 无背景无圆角 | 状态色：合法=primary，非法=Red α0.3/0.6 | 无 | 16dp | 最小高 56dp | 禁用换 `disabledOnSecondaryVariant` |
| 搜索框 | `CircleShape` 全圆 | 无 | 无 | 外边距 12dp；图标区 16/8dp | 最小高 45dp；图标 44dp | 背景 `surfaceContainerHigh`；文字 17sp Medium |
| 分段列表 | 首末 16dp / 中间 4dp | 无 | 无 | 继承 ListItem | 组内 2dp 间距 | 展开 spring(0.9, 800) |
| 状态标签 | 6dp（Miuix）/ 4dp（Material） | 无 | 无 | 水平 4dp / 垂直 2dp | — | 9sp / `labelSmallEmphasized` |
| 对话框 | 继承（M3 extraLarge） | 无 | tonalElevation | `insideMargin=(0, 0~24dp)`；标题 top24/bottom12；按钮行 top12/bottom24 | 列表最大 500dp | 加载态不可点外部关闭 |
| 浮动底栏 | `CircleShape` | 无（1dp 白高光 α0.12） | `dropShadow` 10dp 黑 α0.1/0.2 | 容器 4dp | 容器 64dp / 内容 56dp | 按下缩放 1.39×；选中胶囊 α0.15 |

**四条硬规则**
1. 全目录零显式描边，唯一「边框」是输入框校验色
2. 圆角不统一（4/6/14/16/25/全圆并存），按组件类型分组，无全局 `--radius`
3. 阴影只出现在浮动底栏，其余靠容器色分层
4. 对话框底部按钮永远「等宽 + 20dp 间隔 + 取消左确认右」

---

## 7. 交互状态

| 状态 | 做法 | 出处 |
|---|---|---|
| **Hover** | **完全不存在**（`hoverable` / `HoverInteraction` 全项目 0 命中） | grep 确认 |
| **Focus** | **无视觉样式**，只有 `bringIntoView()` 滚动与键盘 Enter/Space 激活 | `SegmentedList.kt:610`、`FloatingBottomBar.kt:191` |
| **Press（卡片）** | Miuix 原生 `Tilt` / `Sink`，不用 ripple | `HomeMiuix.kt` |
| **Press（底栏）** | AGSL 着色器径向高光（白 α0.06 + α0.12）+ 双轴异步弹簧缩放（scaleX 0.6/250、scaleY 0.7/250，故意不同步） | `InteractiveHighlight.kt:57-78` |
| **Press（Material）** | 系统 ripple（唯一使用处 `ExpressiveTabRow.kt:57`） | — |
| **Disabled** | 换色而非整体降透明 | `ExpressiveSwitch.kt:52-58` |
| **Selected** | 缩放 1.1× + `scaleIn(0.8f)` 淡入 | `ColorPaletteScreenMaterial.kt:624-635` |
| **Loading** | 100dp 居中对话框，不可点外部关闭 | `DialogMaterial.kt:36` |

---

## 8. 动效

### 8.1 双档时长（tween 22 处，全部硬编码）

| 时长 | 次数 | 占比 | 用途 |
|---|---|---|---|
| **300ms** | 11 | 50% | 内容/尺寸变化主时长 |
| **150ms** | 6 | 27% | 淡出、占位符消失 |
| 350ms | 2 | 9% | FAB、底栏大幅位移 |
| 200 / 280 / 320ms | 各 1 | — | 特例 |

### 8.2 三档弹簧（spring 13 处）

| 参数 | 用途 |
|---|---|
| `0.9 / 800` | 分段列表展开（高刚度近临界阻尼） |
| `0.5 / 300` | 高光、速度衰减 |
| `1.0 / 1000` | 位移、按压进度 |

### 8.3 进出场

- 纵向：`expandVertically() + fadeIn()`（内容块自上而下展开）
- 横向：`slideInHorizontally(initialOffsetX = { it })`（从右侧滑入）
- 缩放：0.8 → 1.0（仅调色板选中点）

### 8.4 主题切换

40+ 个语义色同时 `animateColorAsState(spring())`，**无参默认 spring**——全项目最重的动画路径。

### 8.5 明确不存在

`AnimatedContent`（0）、`keyframes`（0）、`rememberInfiniteTransition`（0）、任何循环动画（0）。

---

## 9. 可复用设计原则

1. **语义色优先于色值**：UI 只引用语义名，换肤成本为零
2. **容器层级阶梯**：`surfaceContainer → High → Highest` 表达层级，不用阴影
3. **4dp 网格**：所有间距是 4 的倍数；脱网格值必须登记为例外
4. **两速动效**：内容 300ms、消失 150ms，超过两档就是没收敛
5. **卡片用物理反馈替代水波纹**：倾斜/下沉比 ripple 更适合大面卡片
6. **辅助文字 12sp 是密度锚点**
7. **警示信息前置**：任何状态下警告卡排在实际内容之前
8. **对话框固定骨架**：0 水平外边距 + 24dp 内容内边距 + 列表最大 500dp + 底部等宽双按钮 20dp 间隔
9. **标签底色四档 alpha**：0.8 / 0.6 / 0.15 / 0.1
10. **发丝分割线**：`outline @ 0.5 alpha` + `0.05dp`

---

## 10. 禁止复制项

| 项目 | 原因 |
|---|---|
| KernelSU 名称、Logo、启动图标 | 商标与品牌识别 |
| About 页 Logo 混合着色器（`0xe6a1a1a1` ColorDodge / `0xff1af500` Lab） | 独特视觉资产 |
| 液体玻璃折射实现（`lens(24dp,24dp)` + chromatic 0.5） | 独特技术实现 |
| `ColorBlendToken` 混合常量 | 专有调色配方 |
| GitHub Primer 色板整体（161 变量） | 品牌资产，只能借鉴双层结构 |
| 15 个主题种子色列表 | 产品决策 |
| 绿色系硬编码（`#36D167` 等） | 与「已激活」状态强绑定 |
| 全部文案与字符串资源 | 版权 |
| 图标组合（Cottage / Extension / Security / Settings） | 导航语义的独特搭配 |

**可放心迁移**：间距数值、动效时长、语义色角色划分、容器层级逻辑、对话框骨架、alpha 惯例——这些是方法，不是资产。

---

## 11. 不确定项

| 项 | 状态 |
|---|---|
| Miuix `textStyles` 各档具体 sp 值 | **未确认**——定义在 `miuix-ui` 库内，需读依赖源码 |
| Material3 除 `bodyLarge` 外 12 个 typography 角色具体值 | **未确认**——M3 库默认值 |
| Miuix `Card` / `Dialog` / `NavigationBar` 默认圆角与阴影 | **未确认**——继承库默认 |
| 13dp 垂直节奏成因 | **推测**：M3 tonal 边缘光学补偿，或 40px@3x 取整（13.33dp） |
| 动态取色实际生成的色板 | **无法静态确认**——运行期由壁纸计算 |
| `PressFeedbackType.Tilt` 动画参数 | **未确认**——库内部实现 |
| 9sp 角标真机可读性 | **推测**：偏低，建议不低于 11sp |
| dp 与像素换算 | **未验证**——依赖运行期 density，未上机 |

---

## 12. Design Tokens 草案

```css
:root {
  /* ---------- 间距（4dp 网格） ---------- */
  --space-0-5: 2px;  --space-1: 4px;   --space-1-5: 6px;
  --space-2: 8px;    --space-3: 12px;  --space-3-25: 13px; /* 光学补偿，仅卡片垂直节奏 */
  --space-4: 16px;   --space-5: 20px;  --space-6: 24px;
  --space-8: 32px;   --space-10: 40px; --space-12: 48px;
  --space-14: 56px;  --space-16: 64px;

  --page-margin:       var(--space-3);
  --section-gap:       var(--space-3);
  --card-padding:      var(--space-4);
  --row-gap:           var(--space-6);
  --icon-text-gap:     var(--space-1-5);
  --dialog-padding-x:  var(--space-6);
  --dialog-padding-y:  var(--space-3);
  --dialog-button-gap: var(--space-5);

  /* ---------- 字号 ---------- */
  --text-display-size: 35px;  --text-display-lh: 42px;
  --text-headline-size: 22px; --text-headline-lh: 28px;
  --text-title-size: 17px;    --text-title-lh: 22px;
  --text-subtitle-size: 16px; --text-subtitle-lh: 24px;
  --text-body-size: 15px;     --text-body-lh: 21px;
  --text-body-s-size: 14px;   --text-body-s-lh: 20px;
  --text-caption-size: 12px;  --text-caption-lh: 16px;
  --text-badge-size: 11px;    --text-badge-lh: 14px;

  --weight-normal: 400;
  --weight-medium: 500;
  --weight-semibold: 600;
  --weight-bold: 700;

  /* ---------- 圆角 ---------- */
  --radius-xs: 4px;     --radius-sm: 6px;
  --radius-md: 14px;    --radius-lg: 16px;
  --radius-xl: 25px;    --radius-full: 9999px;

  /* ---------- 动效 ---------- */
  --dur-fast: 150ms;
  --dur-base: 300ms;
  --dur-slow: 350ms;
  --ease-out:      cubic-bezier(0, 0, 0.2, 1);
  --ease-in-out:   cubic-bezier(0.4, 0, 0.2, 1);
  --ease-standard: cubic-bezier(0.2, 0, 0, 1);

  /* ---------- 尺寸 ---------- */
  --size-icon-sm: 16px;
  --size-icon-md: 24px;
  --size-touch-min: 44px;
  --size-row-min: 56px;
  --size-bar-height: 64px;
  --size-dialog-list-max: 500px;
  --border-hairline: 0.5px;
}

/* 明暗两套只换语义层，灰阶复用 */
:root, [data-theme="light"] {
  --color-bg: var(--gray-000);
  --color-surface-container: var(--gray-150);
  --color-text: var(--gray-1000);
  --color-text-secondary: var(--gray-700);
  --color-border: rgba(65, 67, 78, 0.25);
  --color-primary: var(--brand-500);
  --color-error: var(--red-500);
}

[data-theme="dark"] {
  --color-bg: var(--gray-1000);
  --color-surface-container: var(--gray-900);
  --color-text: var(--gray-050);
  --color-text-secondary: var(--gray-300);
  --color-border: rgba(191, 193, 201, 0.16);
  --color-primary: var(--brand-300);
  --color-error: #f72727;
}
```

---

## 13. 组件样式规范

```css
.card {
  background: var(--color-surface-container-high);
  border: none;
  border-radius: var(--radius-lg);
  padding: var(--card-padding);
  box-shadow: none;
  transition: transform var(--dur-fast) var(--ease-out);
}
.card:active { transform: scale(0.98); }   /* 物理下沉替代 ripple */

.segmented-group { display: flex; flex-direction: column; gap: 2px; }
.segmented-item  { border-radius: var(--radius-xs); padding: var(--space-4); }
.segmented-item:first-child { border-radius: var(--radius-lg) var(--radius-lg) var(--radius-xs) var(--radius-xs); }
.segmented-item:last-child  { border-radius: var(--radius-xs) var(--radius-xs) var(--radius-lg) var(--radius-lg); }

.btn {
  min-height: var(--size-touch-min);
  border: none;
  border-radius: var(--radius-full);
  font-size: var(--text-body-s-size);
  font-weight: var(--weight-medium);
  padding: 0 var(--space-4);
}
.btn-primary   { background: var(--color-primary); color: var(--color-on-primary); }
.btn-secondary { background: var(--color-surface-container-highest); color: var(--color-text); }
.btn:disabled  { opacity: 0.38; cursor: not-allowed; }

.dialog-actions { display: flex; gap: var(--dialog-button-gap); }
.dialog-actions > * { flex: 1; }

.input {
  min-height: var(--size-row-min);
  background: var(--color-surface-container-highest);
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  padding: 0 var(--space-4);
  font-size: var(--text-body-size);
}
.input:focus { border-color: var(--color-primary); }
.input[aria-invalid="true"] { border-color: var(--color-error); }

.tag {
  display: inline-flex;
  align-items: center;
  padding: 2px var(--space-1);
  border-radius: var(--radius-sm);
  font-size: var(--text-badge-size);
  font-weight: 600;
  white-space: nowrap;
}

.dialog {
  border-radius: var(--radius-xl);
  padding: var(--dialog-padding-y) 0;
  max-width: 560px;
}
.dialog__title   { padding: var(--space-6) var(--space-6) var(--space-3); font-size: var(--text-title-size); font-weight: var(--weight-medium); }
.dialog__body    { max-height: var(--size-dialog-list-max); overflow-y: auto; }
.dialog__row     { padding: var(--space-3) var(--space-6); }
.dialog__actions { padding: var(--space-3) var(--space-6) var(--space-6); }

.divider { height: var(--border-hairline); background: var(--color-border-hairline); }

.appbar {
  background: color-mix(in srgb, var(--color-surface) 87%, transparent);
  backdrop-filter: blur(25px);
}
.bottombar {
  height: var(--size-bar-height);
  border-radius: var(--radius-full);
  background: var(--color-surface-container);
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
  padding: var(--space-1);
}
```

---

## 14. 迁移规则清单

**配色**
- [ ] UI 只引用语义名，不写 hex
- [ ] 次级文字用**一个专门语义色**，不用 opacity
- [ ] 容器层级固定 3–4 档，不用阴影表达层级
- [ ] 状态色三档齐全：`error` → `error-container` → `on-error-container`
- [ ] 标签状态用底色 alpha 四档扩展，不新增色值

**间距**
- [ ] 基础单位 4px，所有间距是 4 的倍数
- [ ] 页面边距 12px（高密度）或 16px（宽松），二选一全局统一
- [ ] 卡片内边距 16px；卡片间距 = 页面边距
- [ ] 图标与文字间距 6px（紧）或 12px（松）
- [ ] 对话框：内容水平 24px、列表最大高 500px、按钮间隔固定 20px
- [ ] 触控目标不低于 44px
- [ ] 脱网格值必须登记为已命名例外

**排版**
- [ ] 只定义一套 8 级字号（11/12/14/15/16/17/22/35），不再临时加值
- [ ] 每级都写 `line-height`（源实现最大缺陷就是行高缺失）
- [ ] 字重只用 400/500/600 三档
- [ ] 辅助文字 12px 是密度锚点
- [ ] 版本号、ID、路径用等宽字体

**布局**
- [ ] 单列垂直流优先，不强行多列
- [ ] 警示信息永远排在业务内容之前
- [ ] 卡片形态收敛到 2–3 种
- [ ] 宽屏用断点切换导航形态（≥840px 或 ≥600px 且高宽比 <1.2）

**交互**
- [ ] 大面卡片用缩放/下沉替代 ripple
- [ ] 禁用态优先换色
- [ ] **必须补上 hover 与 focus 视觉样式**（源实现完全没有，这是缺陷，不要照抄）
- [ ] 列表项点击给触觉反馈

**动效**
- [ ] 只保留两档时长：300ms / 150ms；大幅位移 350ms
- [ ] 出现用「展开 + 淡入」，消失用「收起 + 淡出」
- [ ] 禁止无限循环动画
- [ ] 主题切换动画必须显式指定时长

**工程**
- [ ] 建单点 tokens 文件，禁止散落硬编码
- [ ] primitive（原始色阶）与 semantic（语义层）分两层
- [ ] 明暗两套共用灰阶，靠语义层换档位

---

## 15. 在 GestureEVO 中的落地情况

已按本规范迁移的页面（Miuix Compose 重写）：

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

基础设施：

- `ui/miuix/MiuixScreen.kt` —— 页面骨架（折叠顶栏 + 12dp 边距列表 + 卡片分组）
- `ui/compose/EvoTheme.kt` —— `MiuixEvoTheme`，把现有「明暗模式 + 动态取色」映射到 Miuix `ColorSchemeMode`，固定 `TonalSpot` + `Spec2025`

**尚未迁移**：自由窗口、图标编辑、日志管理、灵感设置、搜索添加、触发配置、手势添加动作、快速搜索、快速启动、取词浮层、图钉池等——这些页面含自绘控件或与手势运行时深度耦合，需要单独设计 Compose 实现。
