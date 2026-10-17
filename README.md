# remote-control

OK Endfield 的轻量手机 Companion。首个版本仅面向 Android，使用 UU 远程官方 URL Scheme / Deeplink 完成设备绑定与人工远程接管。

## 当前能力

- 检测 Android 上是否可调用 UU 远程。
- 通过 `device/authorize` 让用户选择并绑定 Windows/macOS 设备。
- 仅保存 UU 返回的 `anonymous_device_id`，界面和日志不展示该值。
- 一键请求 UU 全屏远控。
- 一键请求 UU 画中画（PiP）远控并接收最终成功/失败回调。
- `device_not_found` 时自动清除失效绑定，引导重新绑定。
- 使用 UUID `nonce` / `request_id` 校验外部回调，拒绝无关 Deeplink 伪造状态。

> 边界：真正的视频流、键鼠/触控输入与远程桌面会话仍由 UU 远程客户端负责。本项目不会读取 UU 的视频流，也没有 UU 键鼠注入 API。

## 开发环境

- Android Studio Rabbit 1 (2026.2.1) 或兼容版本
- Android Gradle Plugin 9.4.1
- Gradle 9.6.0
- JDK 17
- compileSdk / targetSdk 36
- minSdk 26

仓库不提交 Gradle Wrapper 二进制文件；Android Studio 可直接导入，CI 使用固定 Gradle 9.6.0 构建。

## 运行

1. Android 设备安装 UU 远程 4.42.0 或更高版本，并登录。
2. 安装本应用。
3. 点击“绑定电脑”，在 UU 远程中选择 Windows/macOS 设备。
4. 返回后可选择“全屏接管”或“PiP 接管”。

协议与安全边界见 [`docs/uu-remote.md`](docs/uu-remote.md)。
