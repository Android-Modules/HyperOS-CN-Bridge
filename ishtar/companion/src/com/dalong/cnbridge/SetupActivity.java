package com.dalong.cnbridge;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.TextView;

/** Foreground entry also lets Android launch a newly installed companion normally. */
public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_setup);

        View scroll = findViewById(R.id.setup_scroll);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets safeArea = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom);
            return WindowInsets.CONSUMED;
        });
        scroll.requestApplyInsets();

        if ("songyuan".equals(Build.DEVICE)) {
            TextView summary = findViewById(R.id.setup_summary);
            TextView enable = findViewById(R.id.setup_enable_body);
            TextView nextTitle = findViewById(R.id.setup_next_title);
            TextView nextBody = findViewById(R.id.setup_next_body);
            TextView scope = findViewById(R.id.setup_scope_body);
            summary.setText(R.string.songyuan_summary);
            enable.setText(R.string.songyuan_enable_body);
            nextTitle.setText(R.string.songyuan_next_title);
            nextBody.setText(R.string.songyuan_next_body);
            scope.setText(R.string.songyuan_scope_body);
        }
    }
}
