package com.meritz.notlistener;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;

public class MainActivity extends Activity {

    static final String PREFS = "meritz_prefs";
    private EditText etTrigger, etToken, etGistId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 80, 48, 48);

        // 제목
        TextView title = new TextView(this);
        title.setText("메리츠 체결 알림 수집기");
        title.setTextSize(20);
        title.setPadding(0, 0, 0, 32);
        layout.addView(title);

        // 트리거 단어
        layout.addView(makeLabel("트리거 단어 (이 단어 포함된 알림만 수집)"));
        etTrigger = makeInput(prefs.getString("trigger", "체결"));
        layout.addView(etTrigger);

        // Gist Token
        layout.addView(makeLabel("GitHub Gist Token"));
        etToken = makeInput(prefs.getString("gist_token", ""));
        layout.addView(etToken);

        // Gist ID
        layout.addView(makeLabel("Gist ID"));
        etGistId = makeInput(prefs.getString("gist_id", ""));
        layout.addView(etGistId);

        // 저장 버튼
        Button btnSave = new Button(this);
        btnSave.setText("설정 저장");
        btnSave.setOnClickListener(v -> {
            prefs.edit()
                .putString("trigger", etTrigger.getText().toString().trim())
                .putString("gist_token", etToken.getText().toString().trim())
                .putString("gist_id", etGistId.getText().toString().trim())
                .apply();
            Toast.makeText(this, "저장되었습니다.", Toast.LENGTH_SHORT).show();
        });
        layout.addView(btnSave);

        // 구분선
        TextView divider = new TextView(this);
        divider.setPadding(0, 32, 0, 16);
        divider.setText("──────────────────────");
        layout.addView(divider);

        // 권한 안내
        TextView guide = new TextView(this);
        guide.setText("알림 접근 권한이 필요합니다.\n아래 버튼 → MeritzNotifier 켜기");
        guide.setPadding(0, 0, 0, 16);
        layout.addView(guide);

        // 권한 설정 버튼
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
