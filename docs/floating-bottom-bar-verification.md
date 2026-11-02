# 四入口悬浮底栏与模式开关

## 已实现

- 首页保持总览/操控/内容/管理四入口；只替换bottomBar展示，选页和深链处理沿用原实现。
- 默认悬浮：90%可用宽度、最大约396dp（440dp约束再取90%）、胶囊圆角，距系统导航安全区24dp；顶部留12dp阴影空间。
- 关闭后为全宽贴底纯色导航栏，不保留悬浮阴影/边框与底部24dp间隔。
- 跟随实际Miuix主题背景明度，支持系统和应用强制深浅色。浅白/深#1E1E1E、边框、低饱和蓝紫选中态、300ms颜色过渡，无毛玻璃。
- 图标在文字上方，四等宽触摸目标、至少56dp高，使用selectableGroup/Role.Tab/selected语义，图标不重复朗读文本。
- 管理页末新增“悬浮底栏”开关，立即切换且保持当前选页；使用home_navigation SharedPreferences独立保存floating_bottom_bar，默认true；中英文文案已资源化。
- bottomBar占用真实测量高度，消费navigationBarsPadding；列表沿用Scaffold innerPadding，不通过offset制造悬浮。

## 验证

compileDebugKotlin + testDebugUnitTest：BUILD SUCCESSFUL，36 suites，181 tests，180 passed、1 skipped、0失败/错误。
assembleDebug：BUILD SUCCESSFUL（57s），已生成 app/build/outputs/apk/debug/app-debug.apk。

## 未验证与限制

adb devices -l无设备，未进行真机深浅色切换、三键导航/手势导航、横屏、小屏和大字体的视觉验收。颜色动画代码通过编译不等于动态切换已真机验证。
新开关使用独立SharedPreferences；本次未扩展现有DataStore备份导入导出，因此不承诺此视觉偏好随应用配置备份迁移。
旧IA首页现有入口/文案/锚点问题不在本次底栏任务中扩大改动。
