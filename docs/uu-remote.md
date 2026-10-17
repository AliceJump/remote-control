# UU 远程接入说明

本项目使用网易 UU 远程公开的第三方 App URL Scheme / Deeplink 协议：

- 官方仓库：`netease-uuyc/uuremote-integration-guide`
- UU 远程 Scheme：`uuremote`
- Android 正式包名：`com.netease.uuremote`
- 最低 UU 远程版本：4.42.0
- 调用端：Android（本项目第一版）
- 被控端：仅 Windows / macOS

## 绑定

应用发起：

```text
uuremote://external/device/authorize?callback=<url>&nonce=<uuid>
```

回调：

```text
okefremote://uuremote/bind?anonymous_device_id=<opaque>&nonce=<uuid>
```

或：

```text
okefremote://uuremote/bind?error=<code>&nonce=<uuid>
```

本项目只保存最新 `anonymous_device_id`，不展示或记录它。绑定结果必须携带与当前请求一致的 `nonce` 才会被接受。

## 全屏远控

```text
uuremote://external/device/control?anonymous_device_id=<opaque>&window_type=0
```

官方协议规定全屏模式不会回调第三方 App；连接失败、用户取消或超时也由 UU 客户端自身展示和处理。因此本项目只能确认“请求已交给 UU”，不能声明远控已成功建立。

## PiP 远控

本项目把随机 `request_id` 放进 callback 原参数：

```text
okefremote://uuremote/control?request_id=<uuid>
```

然后请求：

```text
uuremote://external/device/control?anonymous_device_id=<opaque>&window_type=1&callback=<encoded callback>
```

UU 会保留 callback 原参数并追加：

```text
status=success
```

或：

```text
status=failure&reason=<reason>
```

只有 `request_id` 与当前请求一致时，本项目才接受结果。`device_not_found` 会清除旧设备标识，要求重新绑定。

## 能力边界

UU 本次开放的是跨 App handoff，而不是远控 SDK：

- 本项目拿不到 UU 视频流。
- 本项目不能调用 UU 的键鼠、触摸、剪贴板或截图 API。
- 真正的桌面控制发生在 UU 远程客户端中。
- 将来若 OK-EF 增加自己的远程任务 API，应作为独立控制面实现；UU 只负责人工远程桌面接管。

## 安全约束

- Android Manifest 通过 `<queries>` 声明 `uuremote` Scheme 可见性。
- 发起 Intent 时显式限定官方包名 `com.netease.uuremote`，避免把设备标识交给其他 Scheme handler。
- 回调使用不可预测 UUID 做请求关联。
- 不记录 `anonymous_device_id`、nonce 或 request_id。
- 未匹配当前请求的外部回调只显示“已忽略”，不会修改绑定状态。
