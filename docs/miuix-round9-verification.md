# Miuix 第 9 批验证

## 范围

- DataBackActivity：确认、WebDAV 文件选择、不可取消进度、错误/成功全部替换为 Miuix WindowDialog。私有 BackupDialogHandle 保留消息更新与 dismiss 契约。备份恢复/IO/WebDAV/catch/finally 不改。
- NotificationPermissionPromptActivity、TempDndPermissionRequester：Miuix 权限提示，保留权限请求、设置页 fallback、waitingForResult 与取消回调差异。
- ExternalActionProxyActivity：授权界面 Compose 化，unknown caller 仍不可持久授权，ACTION/LINK scope 与写权限后执行顺序保留。

## 审查修正

- ExternalActionProxyActivity 各授权入口共用 actionStarted，避免等待异步保存期间跨按钮或重复提交。取消仍保留旧行为：结束 Activity 并取消其 lifecycleScope；未擅自改为后台继续授权/执行。保存失败后的解锁/错误UI仍沿用原实现缺失，未纳入本批。
- TempDnd 提交前占用 visible 防重入；保持先调用原授权/继续回调后 dismiss 的顺序。“稍后”独立全宽，下方取消/授权等宽20dp间距，避免三按钮挤在窄屏。
- 通知提示先请求并设置 waiting，再关闭提示；退场时只在不等待权限结果时结束 Activity。

## 验证

所有子任务停止写入，最终防重修正后执行 compileDebugKotlin + testDebugUnitTest（--max-workers=1），BUILD SUCCESSFUL。无设备运行验证。AGP/SDK 警告保留。

## 未验证风险

- 备份进度对 Back/外点不关闭的设备表现、旧进度退场与新结果弹窗重叠、长文件列表滚动。
- 通知运行时权限与设置页交接；waitingForResult 为原有非保存字段，重建期间可能重现提示，尚未修正/验证。
- 授权持久化中取消可导致已写权限但未执行动作，这是原取消/lifecycleScope窗口，尚未改变。
- 英文/大字体/窄屏权限按钮、透明 Activity 的退出与 Dialog生命周期释放。

## 尚未完成

QuickSearch 与搜索结果 UI、PinPool 主内容、Inspire/图片编辑器、Notification/ScreenSearch/Pointer/完整轮盘等运行时界面仍有旧 View 实现。不能把设置对话框零残留当作整个应用迁移完成。下一批应优先隔离的 Activity 内容或补测试，不在没有设备验证时整体改写高风险窗口。
