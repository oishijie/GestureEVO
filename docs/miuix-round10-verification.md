# Miuix 第 10 批验证

## 改动

- SearchResultManager 的历史清空确认改为共享 showMiuixSheet/Miuix Compose；清空、刷新与 Toast 仍在原 lifecycleScope/uiDispatcher 中，不改主搜索结果 UI。
- NotificationPermissionPromptActivity 使用 Bundle 保存 waitingForResult 与提示可见状态。恢复到等待权限结果时不重弹/重申请，交由已注册的 ActivityResult 回调继续原拒绝跳设置、最终 finish 流程；恢复到已取消提示时直接 finish。
- 将 ColorPickerDialog 实际 HEX 解析提取为 Android-free 的 ColorPickerHex.parsePickerHexColor，颜色选择器两处均调用该函数。RGB 默认不透明，ARGB保留alpha，禁用alpha仍由原 applyColor归一。ColorParsingTest 不再自测字符串转换副本，直接覆盖业务函数的6/8位、空白、大小写、透明色、非法输入。

## 验证

执行 :app:compileDebugKotlin :app:testDebugUnitTest --console=plain --max-workers=1，BUILD SUCCESSFUL。36 套件181项：180通过、1跳过、0失败/错误。AGP/SDK兼容警告保留。

静态检索 app/src/main/java 的 MaterialAlertDialogBuilder 零命中。这只表明该API残留已清，不代表旧View/所有Material组件迁移已完成。

## 未验证

没有设备验证：权限结果跨旋转/系统进程重建的投递、历史确认与透明QuickSearch/IME的焦点交接、颜色输入焦点丢失/确认/预览的实际操作。新增JVM测试不覆盖Android图形与窗口。

## 剩余

QuickSearch主体、图钉池主列表、Inspire及图片编辑器、通知/屏幕搜索/Pointer/轮盘等仍有旧View实现。先验证现有修改，再逐个设计高风险运行时迁移；不以零builder或编译成功宣称总体目标完成。
