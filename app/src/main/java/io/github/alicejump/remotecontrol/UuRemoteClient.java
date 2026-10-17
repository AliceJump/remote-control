package io.github.alicejump.remotecontrol;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.UUID;

/** Thin integration with UU Remote's documented Android Deeplink protocol. */
public final class UuRemoteClient {
    private static final String UU_PACKAGE = "com.netease.uuremote";
    private static final String PREFS = "uu_remote";
    private static final String KEY_DEVICE_ID = "anonymous_device_id";
    private static final String KEY_BIND_NONCE = "pending_bind_nonce";
    private static final String KEY_CONTROL_REQUEST = "pending_control_request";
    private static final String CALLBACK_SCHEME = "okefremote";
    private static final String CALLBACK_HOST = "uuremote";

    private final Context context;
    private final SharedPreferences preferences;

    public UuRemoteClient(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isInstalled() {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("uuremote://external"));
        probe.setPackage(UU_PACKAGE);
        return probe.resolveActivity(context.getPackageManager()) != null;
    }

    public boolean hasBoundDevice() {
        return !isBlank(preferences.getString(KEY_DEVICE_ID, ""));
    }

    public void clearBoundDevice() {
        preferences.edit()
                .remove(KEY_DEVICE_ID)
                .remove(KEY_BIND_NONCE)
                .remove(KEY_CONTROL_REQUEST)
                .apply();
    }

    public LaunchResult startBinding() {
        if (!isInstalled()) {
            return LaunchResult.error("未检测到 UU 远程，请先安装 4.42.0 或更高版本。");
        }

        String nonce = UUID.randomUUID().toString();
        Uri callback = new Uri.Builder()
                .scheme(CALLBACK_SCHEME)
                .authority(CALLBACK_HOST)
                .appendPath("bind")
                .build();
        Uri request = new Uri.Builder()
                .scheme("uuremote")
                .authority("external")
                .appendPath("device")
                .appendPath("authorize")
                .appendQueryParameter("callback", callback.toString())
                .appendQueryParameter("nonce", nonce)
                .build();

        preferences.edit().putString(KEY_BIND_NONCE, nonce).apply();
        if (!launch(request)) {
            preferences.edit().remove(KEY_BIND_NONCE).apply();
            return LaunchResult.error("无法打开 UU 远程绑定页面。");
        }
        return LaunchResult.ok("已交给 UU 远程选择设备。");
    }

    public LaunchResult startControl(boolean pictureInPicture) {
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        if (isBlank(deviceId)) {
            return LaunchResult.error("请先绑定电脑。");
        }
        if (!isInstalled()) {
            return LaunchResult.error("未检测到 UU 远程，请先安装 4.42.0 或更高版本。");
        }

        Uri.Builder request = new Uri.Builder()
                .scheme("uuremote")
                .authority("external")
                .appendPath("device")
                .appendPath("control")
                .appendQueryParameter("anonymous_device_id", deviceId)
                .appendQueryParameter("window_type", pictureInPicture ? "1" : "0");

        String requestId = null;
        if (pictureInPicture) {
            requestId = UUID.randomUUID().toString();
            Uri callback = new Uri.Builder()
                    .scheme(CALLBACK_SCHEME)
                    .authority(CALLBACK_HOST)
                    .appendPath("control")
                    .appendQueryParameter("request_id", requestId)
                    .build();
            request.appendQueryParameter("callback", callback.toString());
            preferences.edit().putString(KEY_CONTROL_REQUEST, requestId).apply();
        }

        if (!launch(request.build())) {
            if (requestId != null) {
                preferences.edit().remove(KEY_CONTROL_REQUEST).apply();
            }
            return LaunchResult.error("无法打开 UU 远程控制页面。");
        }
        return LaunchResult.ok(pictureInPicture
                ? "已请求 UU 远程 PiP；等待 UU 回调最终结果。"
                : "已请求 UU 远程全屏接管；全屏模式按官方协议不会回调结果。");
    }

    public CallbackResult handleCallback(Intent intent) {
        Uri uri = intent == null ? null : intent.getData();
        if (uri == null || !CALLBACK_SCHEME.equals(uri.getScheme()) || !CALLBACK_HOST.equals(uri.getHost())) {
            return CallbackResult.ignored();
        }
        if ("/bind".equals(uri.getPath())) {
            return handleBindCallback(uri);
        }
        if ("/control".equals(uri.getPath())) {
            return handleControlCallback(uri);
        }
        return CallbackResult.handled("收到未知 UU 回调路径，已忽略。");
    }

    private CallbackResult handleBindCallback(Uri uri) {
        String expected = preferences.getString(KEY_BIND_NONCE, "");
        String actual = uri.getQueryParameter("nonce");
        if (isBlank(expected) || actual == null || !expected.equals(actual)) {
            return CallbackResult.handled("收到不匹配当前绑定请求的回调，已忽略。");
        }

        String deviceId = uri.getQueryParameter("anonymous_device_id");
        String error = uri.getQueryParameter("error");
        SharedPreferences.Editor editor = preferences.edit().remove(KEY_BIND_NONCE);
        if (!isBlank(deviceId) && isBlank(error)) {
            editor.putString(KEY_DEVICE_ID, deviceId).apply();
            return CallbackResult.handled("电脑绑定成功。");
        }
        editor.apply();
        if (isBlank(error)) {
            return CallbackResult.handled("UU 返回了无法识别的绑定结果。");
        }
        return CallbackResult.handled("绑定未完成：" + bindingErrorText(error));
    }

    private CallbackResult handleControlCallback(Uri uri) {
        String expected = preferences.getString(KEY_CONTROL_REQUEST, "");
        String actual = uri.getQueryParameter("request_id");
        if (isBlank(expected) || actual == null || !expected.equals(actual)) {
            return CallbackResult.handled("收到不匹配当前 PiP 请求的回调，已忽略。");
        }

        preferences.edit().remove(KEY_CONTROL_REQUEST).apply();
        String status = uri.getQueryParameter("status");
        if ("success".equals(status)) {
            return CallbackResult.handled("UU 远程 PiP 已启动。");
        }
        if (!"failure".equals(status)) {
            return CallbackResult.handled("UU 返回了无法识别的 PiP 结果。");
        }

        String reason = uri.getQueryParameter("reason");
        if (isBlank(reason)) {
            reason = "connect_failed";
        }
        if ("device_not_found".equals(reason)) {
            preferences.edit().remove(KEY_DEVICE_ID).apply();
        }
        return CallbackResult.handled("PiP 未启动：" + controlErrorText(reason));
    }

    private boolean launch(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.setPackage(UU_PACKAGE);
        try {
            context.startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String bindingErrorText(String error) {
        return switch (error) {
            case "no_device" -> "当前 UU 账号没有可关联的 Windows/macOS 设备";
            case "user_cancel" -> "用户取消";
            case "timeout" -> "登录或设备列表等待超时";
            case "not_logined" -> "UU 登录态或首页条件不满足";
            case "superseded" -> "请求被新的绑定请求替代";
            case "logout" -> "等待期间退出了 UU 登录";
            case "internal_error" -> "UU 远程内部错误";
            default -> "未知错误（" + error + "）";
        };
    }

    private static String controlErrorText(String reason) {
        return switch (reason) {
            case "busy" -> "UU 正在远控其他设备或处于互斥流程";
            case "device_offline" -> "目标设备离线";
            case "device_not_found" -> "设备已解绑、切换账号或绑定已失效；本地绑定已清除";
            case "pip_unsupported" -> "当前系统或设备不支持 PiP，可改用全屏";
            case "login_timeout" -> "UU 登录或首页初始化超时";
            case "network_unavailable" -> "网络不可用";
            case "not_allowed" -> "设备未允许被控或目标平台不支持";
            case "connect_timeout" -> "建连、首帧或 PiP 启动超时";
            case "connect_failed" -> "连接失败";
            case "user_cancel" -> "用户取消";
            case "invalid_request" -> "请求参数无效";
            case "internal_error" -> "UU 远程内部错误";
            default -> "连接失败（未知原因 " + reason + "）";
        };
    }

    public static final class LaunchResult {
        private final boolean launched;
        private final String message;

        private LaunchResult(boolean launched, String message) {
            this.launched = launched;
            this.message = message;
        }

        public boolean launched() {
            return launched;
        }

        public String message() {
            return message;
        }

        static LaunchResult ok(String message) {
            return new LaunchResult(true, message);
        }

        static LaunchResult error(String message) {
            return new LaunchResult(false, message);
        }
    }

    public static final class CallbackResult {
        private final boolean handled;
        private final String message;

        private CallbackResult(boolean handled, String message) {
            this.handled = handled;
            this.message = message;
        }

        public boolean handled() {
            return handled;
        }

        public String message() {
            return message;
        }

        static CallbackResult ignored() {
            return new CallbackResult(false, "");
        }

        static CallbackResult handled(String message) {
            return new CallbackResult(true, message);
        }
    }
}
