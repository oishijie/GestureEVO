# Miuix 第12批：共享编辑器整合与打包检查

## 改动

- 图片编辑器 showTextEditor 重新复用已经Compose化的 showInspireEditBottomSheet，删除上一批局部重复实现及无用imports。保留trim、空输入删除既有文字、更新/新增、坐标/颜色/字号回调；灵感内容编辑和图片编辑共用5–10行、末尾选区、窗口focus后拉起IME、外点限制及Back可取消的UI路径。
- 共享编辑器解包ContextWrapper寻找Activity，支持主题包装上下文；identity visited集合防循环。无Activity的Context仍不显示，未引入服务overlay权限行为。
- 显式multiline/句首大写；取消和确认共享submitted保护，先占用再回调，键盘关闭。

## 验证

执行 :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleDebug --console=plain --max-workers=1。编译与单测阶段通过：36套件181项，180通过、1跳过、0失败/错误。完整命令 BUILD SUCCESSFUL（8m1s），APK已生成 app/build/outputs/apk/debug/app-debug.apk。两项native库无法strip而按原样打包的非致命警告保留。

adb devices -l 仍空列表；未运行设备或模拟器测试。AGP 9.1.0 / compileSDK37兼容警告未隐藏。

## 本轮未验证与总体剩余

- 两个编辑调用方的IME、短屏/横屏/大字体、Back与外点交接、主题包装Context行为仅静态检查。
- 保存菜单仍采用上一批居中对话框而非按钮旁锚定菜单，位置变化仍存在。
- 整体目标尚未完成：QuickSearch主体、PinPool主列表、Inspire主视图、图片编辑器主页面、Notification/ScreenSearch/Pointer/完整轮盘仍有旧View UI。
- 不因12轮自动续行结束或编译通过而标记迁移完成。后续首先安装设备验证现有改动，再逐个设计最高风险运行时窗口。可参考round8–11记录的具体行为边界。
