package com.dalong.cnbridge;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Foreground entry also lets Android launch a newly installed companion normally. */
public final class SetupActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setPadding(48, 72, 48, 48);
        text.setTextSize(18);
        text.setText("国内化按应用适配\n\n在 LSPosed 中启用本模块并勾选推荐应用。\n\n各应用的国内化开关在 KernelSU 国行组件模块的 WebUI 中设置，保存后手动重启生效。\n\n本模块不会作用于系统框架或其他用户。");
        setContentView(text);
    }
}
