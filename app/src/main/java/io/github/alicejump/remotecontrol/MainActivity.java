package io.github.alicejump.remotecontrol;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private UuRemoteClient remote;
    private TextView stateView;
    private TextView eventView;
    private Button fullButton;
    private Button pipButton;
    private Button clearButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remote = new UuRemoteClient(this);
        setContentView(buildContent());
        consumeCallback(getIntent());
        refreshState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        consumeCallback(intent);
        refreshState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshState();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        root.setPadding(padding, padding, padding, padding);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("OK-EF Remote Companion");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView boundary = new TextView(this);
        boundary.setText("本应用只负责通过 UU 官方协议绑定设备并发起远控。真正的桌面画面和输入控制仍由 UU 远程负责。");
        boundary.setTextSize(15);
        LinearLayout.LayoutParams boundaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boundaryParams.topMargin = dp(12);
        root.addView(boundary, boundaryParams);

        stateView = new TextView(this);
        stateView.setTextSize(16);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        stateParams.topMargin = dp(20);
        root.addView(stateView, stateParams);

        eventView = new TextView(this);
        eventView.setTextSize(15);
        LinearLayout.LayoutParams eventParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        eventParams.topMargin = dp(10);
        root.addView(eventView, eventParams);

        Button bindButton = button("绑定 / 重新绑定电脑", () -> run(remote.startBinding()));
        addButton(root, bindButton);

        fullButton = button("UU 全屏接管", () -> run(remote.startControl(false)));
        addButton(root, fullButton);

        pipButton = button("UU PiP 接管", () -> run(remote.startControl(true)));
        addButton(root, pipButton);

        clearButton = button("清除本地绑定", () -> {
            remote.clearBoundDevice();
            eventView.setText("已清除本地保存的 UU 匿名设备标识。");
            refreshState();
        });
        addButton(root, clearButton);

        TextView note = new TextView(this);
        note.setText("全屏模式按 UU 官方协议不会回调连接结果；PiP 模式会回调成功或失败原因。若 UU 返回 device_not_found，本应用会自动清除旧绑定。");
        note.setTextSize(13);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(18);
        root.addView(note, noteParams);
        return scroll;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void addButton(LinearLayout root, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        root.addView(button, params);
    }

    private void run(UuRemoteClient.LaunchResult result) {
        eventView.setText(result.message());
        if (!result.launched()) {
            Toast.makeText(this, result.message(), Toast.LENGTH_LONG).show();
        }
        refreshState();
    }

    private void consumeCallback(Intent intent) {
        UuRemoteClient.CallbackResult result = remote.handleCallback(intent);
        if (result.handled()) {
            eventView.setText(result.message());
        }
    }

    private void refreshState() {
        boolean installed = remote.isInstalled();
        boolean bound = remote.hasBoundDevice();
        stateView.setText("UU 远程：" + (installed ? "可调用（要求 4.42.0+）" : "未检测到")
                + "\n绑定电脑：" + (bound ? "已绑定" : "未绑定"));
        fullButton.setEnabled(installed && bound);
        pipButton.setEnabled(installed && bound);
        clearButton.setEnabled(bound);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
