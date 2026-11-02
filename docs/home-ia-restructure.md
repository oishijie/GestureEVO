# 方案一首页 IA 落地记录

## 新底栏

- 总览：运行状态（无障碍与后台稳定性）、当前配置、使用概况（数据统计）
- 操控：触发与映射、轮盘与布局、指针与键盘、窗口行为
- 内容：识别与提取、搜索配置、Pin 池与快捷发送、分享与输出（含搜索 JSON 导入/导出）
- 管理：界面与导航、外部调用与授权、数据与维护、关于/版本

原 TAB_GESTURE/TAB_SCREEN/TAB_SEARCH/TAB_SETTINGS 常量保留为兼容别名，并分别映射到新分区；外部深链协议不变。搜索注册表的内容条目 breadcrumb 已切换至新 IA 文案。

## 明确不纳入

App Profile、SU、内核模块等并非本项目能力，本次没有加入，也没有制造禁用假入口。

## 验证

`:app:compileDebugKotlin :app:testDebugUnitTest --console=plain --max-workers=1` 成功。
36 suites / 181 tests：180 passed、1 skipped、0 failures/errors。

首次资源合并失败是英文引导字符串中未转义 apostrophe，已修复后重跑通过。仍有 AGP 9.1 与 compile SDK 37 警告。

## 未验证风险

- 当前无连接设备，底栏点击、深链滚动索引、不同语言布局和外部设置返回路径尚未真机验证。
- 旧设置页仍保留，首页只是重组入口；旧兼容函数暂留以避免外部调用回退。
- 管理页中的“更新检查”尚无现有业务入口，因此没有虚构该功能。
