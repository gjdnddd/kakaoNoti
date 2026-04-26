package com.meritz.notlistener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

public class MainActivity extends Activity {

    static final String PREFS = "meritz_prefs";
    private EditText etToken, etGistId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 80, 48, 48);

        TextView title = new TextView(this);
        title.setText("체결 알림 수집기");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 8);
        layout.addView(title);

        TextView desc = new TextView(this);
        desc.setText("파싱 규칙은 Gist의 parser_rules.json 에서 관리합니다.");
        desc.setTextSize(12);
        desc.setPadding(0, 0, 0, 32);
        layout.addView(desc);

        layout.addView(makeLabel("GitHub Gist Token"));
        etToken = makeInput(prefs.getString("gist_token", ""));
        layout.addView(etToken);

        layout.addView(makeLabel("Gist ID"));
        etGistId = makeInput(prefs.getString("gist_id", ""));
        layout.addView(etGistId);

        Button btnSave = new Button(this);
        btnSave.setText("설정 저장");
        btnSave.setOnClickListener(v -> {
            prefs.edit()
                .putString("gist_token", etToken.getText().toString().trim())
                .putString("gist_id", etGistId.getText().toString().trim())
                .apply();
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnSave);

        TextView divider = new TextView(this);
        divider.setPadding(0, 32, 0, 16);
        divider.setText("──────────────────────");
        layout.addView(divider);

        TextView guide = new TextView(this);
        guide.setText("알림 접근 권한이 필요합니다.\n아래 버튼 → 체결알림수집기 켜기");
        guide.setPadding(0, 0, 0, 16);
        layout.addView(guide);

        Button btnPerm = new Button(this);
        btnPerm.setText("알림 접근 권한 설정 열기");
        btnPerm.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        );
        layout.addView(btnPerm);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(0, 24, 0, 4);
        return tv;
    }

    private EditText makeInput(String defaultVal) {
        EditText et = new EditText(this);
        et.setText(defaultVal);
        et.setPadding(8, 8, 8, 8);
        return et;
    }
}
