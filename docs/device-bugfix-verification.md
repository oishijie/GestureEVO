# 真机缺陷修复记录（2026-11-03）

## 1. 二次启动闪退（OCR 原生库缺失）

设备崩溃日志确证（本应用进程，非系统应用）：

```
FATAL EXCEPTION: DefaultDispatcher-worker-3
Process: io.github.lumiagg.gestureevo.debug
java.lang.UnsatisfiedLinkError: dlopen failed: library "libppocrv5_jni.so" not found
  at me.fleey.ppocrv5.ocr.OcrEngine.<clinit>
  at io.github.lumiagg.gestureevo.utils.MlKitOcr.createEngine(MlKitOcr.kt:170)
  at io.github.lumiagg.gestureevo.utils.MlKitOcr.preload(MlKitOcr.kt:45)
  at io.github.lumiagg.gestureevo.inspire.InspireFloating$preloadOcrModel$1
```

两处修复：

- **构建配置**：`app/build.gradle.kts` 中原生构建（`ndkVersion`、`ndk { abiFilters }`、`externalNativeBuild.cmake`）此前被 `TEMP-VERIFY:` 注释整体停用，导致 APK 不含 JNI 库。已恢复。
- **运行时防护**：`MlKitOcr.createEngine()` 捕获 `LinkageError`。原实现只处理 `Result.getOrElse`，但 `loadLibrary` 发生在 `OcrEngine` 类初始化阶段，早于 `create()` 返回，异常直接冲出协程使进程崩溃。

校验：APK 内已包含 `lib/arm64-v8a/libppocrv5_jni.so`（3169472 字节）与 `libLiteRt.so`。

## 2. 窗口行为页点击即崩

崩溃栈：

```
java.lang.RuntimeException: Unable to start activity
  ComponentInfo{.../settings.FreeFormSettingsActivity}
Caused by: java.lang.NullPointerException:
  Attempt to invoke virtual method
  'android.view.WindowInsetsController
   com.android.internal.policy.DecorView.getWindowInsetsController()'
  on a null object reference
  at com.android.internal.policy.PhoneWindow.getInsetsController
  at settings.FreeFormSettingsActivity.enableFullScreen(FreeFormSettingsActivity.kt:208)
```

`window.insetsController` 在本机型上会解引用尚未创建的 DecorView。改为 `window.decorView.windowInsetsController`（`getDecorView()` 会强制创建），并加注释说明原因。

## 3. 总览「当前配置」跳错页面

该行副标题展示的是小窗模式（`home_badge_freeform_mode`），但点击进入的是外观设置。已改为 `FreeFormSettingsActivity`，与展示内容一致。

## 4. 主题模式点击无反应

`ui/miuix/MiuixScreen.kt` 的 `Scaffold` 传入了空实现 `popupHost = { }`。Miuix 的下拉/弹层内容渲染在该插槽中，空实现导致 `OverlayDropdownPreference`（主题模式、语言、指针形状）点击后无任何可见效果。已移除该参数，回落到 Scaffold 的默认弹层宿主。

## 验证

`assembleDebug` + `testDebugUnitTest`：BUILD SUCCESSFUL。
36 套件 / 181 测试：180 通过、1 跳过、0 失败。

## 未完成验证

设备在安装本轮修复包前断开（`adb devices` 已空），因此以上 4 项**均未在真机复验**：

- 二次启动稳定性、OCR 识别实际可用性；
- 窗口行为页能否正常进入；
- 主题模式下拉能否展开并真正切换深浅色；
- 总览配置跳转。

下次连接后应优先执行：冷启动 → 退出 → 再启动 ×3；进入窗口行为页；切换主题模式三次（跟随系统/浅色/深色）。
