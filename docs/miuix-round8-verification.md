# Miuix 迁移第 8 批验证记录

## 本批范围

- `app/src/main/java/io/github/lumiagg/gestureevo/pin/PinPoolFloating.kt`：仅清空确认与提醒候选内容迁移至 Miuix WindowDialog；主列表、透明 Activity、拖发、日期时间 picker 不变。
- `app/src/main/java/io/github/lumiagg/gestureevo/gesture/ui/MainActionView.kt`：配置提示的两个图标内容为局部 Compose；保留 XML 根约束和 native hit bounds。选中提示仍保留 RoundImageTextView 原渲染器，仅局部 Compose/AndroidView 承载，并非图标绘制已全面重写。
- `app/src/main/java/io/github/lumiagg/gestureevo/settings/InspireSettingsActivity.kt`：五个残留 MaterialAlertDialogBuilder 选择器复用 showMiuixSheet；保留选项、持久化与键盘重载事件。

## 静态与构建验证

三个子任务均停止写入后执行：

```powershell
$env:GRADLE_USER_HOME='D:\Development\gradle-home'
& '.\gradlew.bat' :app:compileDebugKotlin :app:testDebugUnitTest --console=plain --max-workers=1
```

结果：BUILD SUCCESSFUL。36 个套件共 178 项测试，0 失败、0 错误、1 跳过（177 实际通过）。构建验证不证明窗口运行态等价。

`adb devices -l` 返回空列表；未做任何设备或模拟器验证。AGP 9.1.0 对 compile SDK 37 的支持范围警告保留，未隐藏。

## 已读回确认的行为边界

- 清空仍调用 clearPins(currentCategory)，Store 仅删除对应分类未置顶项；排序、缺失图片缓存清理和 updatePins 链保留。
- 候选卡点击进入预填日期 picker，不直接安排提醒；原权限、OCR、UTC/本地时间与未来检查保留。
- 轮盘 WindowManager、raw 坐标和手势状态机未改；80dp 中心命中范围、50dp 完成按钮、20dp 间距、主子轮盘定位保留。
- 局部轮盘 Compose 宿主每次 attach 新建 lifecycle/saved-state owners 与显式 Recomposer；detach dispose/cancel。不能据此声称其设备生命周期已验证。

## 待验证风险

1. 无障碍服务窗口中的 Compose attach/detach/reattach，特别是隐藏后再显示、主题变化、服务销毁。
2. 轮盘完成按钮可点击性、中心命中区域与实际绘制是否完全重合。
3. 100dp 选中图标 AndroidView 跨 composition 重建时的父节点清理、裁剪、150ms fade；保持复杂渲染器是暂时兼容措施。
4. 图钉候选弹窗与日期 picker 的并行/串行切换、关闭后异步 OCR 返回、退场动画资源释放。
5. 灵感设置选择后关闭父弹层再打开应用选择器/链接编辑器的实机交接。

## 剩余范围（非完成声明）

QuickSearch、PinPool 主列表、完整轮盘、Pointer 双窗口、运行时其他浮层尚未整体迁移。静态检索还发现 DataBackActivity、权限提示等残留 Material 对话框，可先处理这些 Activity 范围，避免无真机验证时继续扩大高风险运行时改动面。
