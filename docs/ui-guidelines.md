# GestureEVO UI 设计规范（方案 A · View 体系）

> 本规范是二级页重构（批次 1）的验收标准。后续所有页面改动以本文为准，
> 新增控件优先复用第 4 节的共享组件，不再逐页自绘。

## 0. 风格基准

视觉基准对齐 [KernelSU Manager](https://github.com/tiann/KernelSU) 的 Material 模式设置页：
tonal 卡（surfaceBright、16dp 圆角、无描边）、primary 色组标题、
24dp 裸前导图标、M3 ListItem 式行布局、16dp 页边距、13dp 分组间距。

## 1. 基本原则

1. **一屏两种组件**：hero/状态卡 + 分组列表。禁止"每行一张卡"的卡片汤。
2. **组标题在卡外，行在卡内**：组标题 14sp/600/primary；行与行之间用 1dp 分隔线（有前导图标时起点 66dp，无图标时通栏）。
3. **主色克制**：主色只用于主操作、选中态、hero 层；卡片层级用 surfaceContainer 系列。
4. **一套 token**：字号、圆角、间距一律引用 `dimens.xml`，禁止硬编码。
5. **动效统一**：页面进入 160ms 淡入；列表点击用 ripple；折叠标题跟手；不叠加花哨动画。

## 2. Token

### 字号（`evo_text_*`）
| Token | 值 | 用途 |
|---|---|---|
| `evo_text_display` | 20sp / 行高 28 | 页面大数字、强调标题 |
| `evo_text_title` | 16sp / 行高 24 | 行标题、卡标题 |
| `evo_text_body` | 14sp / 行高 22 | 正文、摘要 |
| `evo_text_label` | 12sp / 行高 16 | 徽章、标签、辅助说明 |

### 形状（`shape_corner_*`）
| Token | 值 | 用途 |
|---|---|---|
| `shape_corner_chip` | 8dp | 标签、键帽 |
| `shape_corner_small` | 12dp | 列表内小卡 |
| `shape_corner_card` | 16dp | 页面卡片 |
| `shape_corner_sheet` | 24dp | 弹层、hero 卡、圆形按钮 |

### 间距
- 页面左右留白：16dp（`settings_page_margin`）
- 卡片间距：12dp；组标题上 20dp / 下 8dp
- 卡内行高：64dp（最小）；行内左右留白 16dp
- 4dp 网格，禁止 5/7/9 这类散值

### 颜色
| 角色 | Token |
|---|---|
| 页面底色 | `colorSurface` |
| 卡片底色 | `colorSurfaceContainerLow`（无描边、无阴影） |
| 图标底块 | `colorSurfaceContainerHighest` |
| 分隔线 | `colorOutlineVariant`（1dp） |
| 组标题/副标题 | `colorOnSurfaceVariant` |
| hero 卡 | `colorPrimaryContainer` |
| 主操作 | `colorPrimary`（FilledButton / TonalButton） |

## 3. 页面结构模板

```
CoordinatorLayout
├─ AppBarLayout（elevation 0，底色 colorSurface）
│  └─ CollapsingToolbarLayout（expanded 30sp bold / collapsed 20sp bold）
│     └─ Toolbar（返回按钮 40dp 圆形底）
└─ NestedScrollView（clipToPadding=false）
   └─ LinearLayout（paddingH=16dp, paddingTop=8dp, paddingBottom=24dp）
      ├─ 可选：StatusCardView / hero 卡
      └─ SettingsSectionCard × N（间距 12dp）
```

页面标题即功能名，不写"设置"后缀；卡片标题不重复页面标题。

## 4. 共享组件

### 4.1 SettingsSectionCard（已有，本次升级）
- 卡底 `colorSurfaceBright`（暗色下比 surfaceContainer 亮一档），16dp 圆角，无描边、无阴影
- 组标题 14sp/600/`primary`（对齐 KernelSU），上 20dp/下 8dp；可选摘要 12sp
- 卡内容器左右 16dp、上下 4dp；自动在扁平行之间插入 1dp 分隔线

### 4.2 SettingsNavRow / SettingsSwitchRow / SettingsDropdownRow（已有，本次升级）
- 行高 ≥64dp，内边距 16/12dp
- Nav/Dropdown 行带前导图标：24dp 裸图标（无底块）、`colorOnSurface` 着色、右距 16dp；Switch 行不带前导图标
- 标题 16sp/`colorOnSurface`；摘要 14sp/`colorOnSurfaceVariant`
- 尾部控件：chevron 20dp 裸图标（不套圆钮）、Switch 用 M3 `MaterialSwitch`
- 整行可点，ripple 为唯一按压反馈

### 4.3 StatusCardView（本次新增）
- `colorPrimaryContainer` 底、24dp 圆角、无描边
- 内部：label 14sp + value 26sp/700 + summary 14sp（80% 透明度）+ 可选 TonalButton
- 用途：一屏一个主角的状态/入口卡（体检、主题、备份状态）

### 4.4 待批次 4 补充
- EmptyStateView（图标 48dp + 标题 16 + 描述 14 + 可选主按钮）
- 统一 BottomSheet 壳（24dp 顶部圆角 + 拖拽条 + 标题 20sp/600 + 主次按钮）

## 5. 弹层与反馈

- BottomSheet：顶部圆角 24dp，拖拽条 32×4dp/`colorOnSurfaceVariant` 40% 透明度
- 按钮层级：主操作 Filled/Tonal，次要 TextButton；同一弹层最多一个主按钮
- 输入框统一 OutlinedTextField / M3 TextInputLayout
- Toast 只用于瞬时确认；需要回执的用 Snackbar

## 6. 无障碍与适配

- 触控目标 ≥48dp；图标按钮必须有 contentDescription
- 对比度按 WCAG AA（正文 4.5:1）；不要只靠颜色表达状态（选中态加字重/形状）
- 深色模式、360dp 小屏、横屏、字体放大 1.3× 为每页验收项
- 列表行支持 TalkBack 整行播报（标题+摘要）

## 7. 反模式清单

- ❌ 每行一张卡（卡片汤）
- ❌ 白底 + 1dp 描边铺满全屏
- ❌ 同一页出现超过 4 档字号
- ❌ 图标混搭（双色品牌图标 + Material 填充 + 描边）
- ❌ 每行都有 chevron 但不可点
- ❌ 主色大面积铺底
- ❌ 空态只有一行"暂无数据"

## 8. 验收清单（每页）

- [ ] 页头：折叠大标题 30sp/20sp，返回按钮正常
- [ ] 间距：页边 16dp、卡距 12dp、行高 64dp
- [ ] 字号/圆角全部走 token，无硬编码
- [ ] 深色模式、小屏、横屏无溢出/截断
- [ ] 行点击 ripple、无重复 chevron、开关状态即时反馈
- [ ] TalkBack 可读、触控目标 ≥48dp

---

## 修订记录

- v2（批次 2）：视觉基准对齐 KernelSU Manager——组标题回 primary 色、卡底
  surfaceBright、行前导图标（Nav/Dropdown 行 24dp 裸图标；Switch 行不带，
  避免长开关组图标噪音）。11 个设置页统一页边距 16dp / 卡距 12dp /
  AppBar wrap_content，49 个行注入语义图标。
- v1（批次 1）：初始规范 + 外观/数据备份试点。
