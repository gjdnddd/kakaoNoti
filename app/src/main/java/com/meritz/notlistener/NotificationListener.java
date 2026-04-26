package com.meritz.notlistener;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "MeritzListener";
    private static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static final String TRADES_FILENAME = "meritz_trades.json";
    private static final String RULES_FILENAME = "parser_rules.json";

    private JSONArray cachedRules = null;

    @Override
    public void onCreate() {
        super.onCreate();
        new Thread(this::loadRules).start();
    }

    private void loadRules() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String gistToken = prefs.getString("gist_token", "");
        String gistId = prefs.getString("gist_id", "");
        if (gistToken.isEmpty() || gistId.isEmpty()) return;

        try {
            URL url = new URL("https://api.github.com/gists/" + gistId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "token " + gistToken);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject gistData = new JSONObject(sb.toString());
            JSONObject files = gistData.getJSONObject("files");

            if (files.has(RULES_FILENAME)) {
                String content = files.getJSONObject(RULES_FILENAME).getString("content");
                cachedRules = new JSONArray(content);
                Log.d(TAG, "파싱 규칙 로드 완료: " + cachedRules.length() + "개");
            }
        } catch (Exception e) {
            Log.e(TAG, "규칙 파일 로드 실패", e);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!KAKAO_PACKAGE.equals(sbn.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String gistToken = prefs.getString("gist_token", "");
        String gistId = prefs.getString("gist_id", "");
        if (gistToken.isEmpty() || gistId.isEmpty()) return;

        if (cachedRules == null) {
            loadRules();
            if (cachedRules == null) return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String text = null;
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence normalText = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (bigText != null) text = bigText.toString();
        else if (normalText != null) text = normalText.toString();
        if (text == null) return;

        final String finalText = text;
        final String finalToken = gistToken;
        final String finalId = gistId;

        try {
            for (int i = 0; i < cachedRules.length(); i++) {
                JSONObject rule = cachedRules.getJSONObject(i);
                String trigger = rule.getString("trigger");

                if (finalText.contains(trigger)) {
                    Log.d(TAG, "규칙 매칭: " + rule.getString("name"));
                    Map<String, String> parsed = parseWithRule(finalText, rule);
                    if (!parsed.isEmpty()) {
                        parsed.put("증권사", rule.getString("name"));
                        new Thread(() -> appendToGist(parsed, finalToken, finalId)).start();
                    }
                    return;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "규칙 매칭 실패", e);
        }
    }

    private Map<String, String> parseWithRule(String text, JSONObject rule) {
        Map<String, String> result = new HashMap<>();
        try {
            JSONObject fields = rule.getJSONObject("fields");
            java.util.Iterator<String> keys = fields.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String pattern = fields.getString(key);
                Matcher m = Pattern.compile(pattern).matcher(text);
                if (m.find()) result.put(key, m.group(1).trim());
            }
        } catch (Exception e) {
            Log.e(TAG, "파싱 실패", e);
        }
        return result;
    }

    private void appendToGist(Map<String, String> data, String gistToken, String gistId) {
        try {
            long timestamp = System.currentTimeMillis();
            String id = String.valueOf(timestamp);

            JSONArray existing = fetchExistingArray(gistToken, gistId);

            for (int i = 0; i < existing.length(); i++) {
                if (id.equals(existing.getJSONObject(i).optString("id"))) {
                    Log.d(TAG, "중복 id 스킵: " + id);
                    return;
                }
            }

            JSONObject newTrade = new JSONObject(data);
            newTrade.put("id", id);
            newTrade.put("timestamp", timestamp);
            newTrade.put("status", "pending");
            existing.put(newTrade);

            JSONObject fileContent = new JSONObject();
            fileContent.put("content", existing.toString(2));
            JSONObject files = new JSONObject();
            files.put(TRADES_FILENAME, fileContent);
            JSONObject body = new JSONObject();
            body.put("files", files);

            URL url = new URL("https://api.github.com/gists/" + gistId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("PATCH");
            conn.setRequestProperty("Authorization", "token " + gistToken);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            Log.d(TAG, "Gist 저장 결과: " + code);
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "Gist 저장 실패", e);
        }
    }

    private JSONArray fetchExistingArray(String gistToken, String gistId) {
        try {
            URL url = new URL("https://api.github.com/gists/" + gistId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "token " + gistToken);

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            conn.disconnect();

            JSONObject gistData = new JSONObject(sb.toString());
            JSONObject files = gistData.getJSONObject("files");
            if (files.has(TRADES_FILENAME)) {
                String content = files.getJSONObject(TRADES_FILENAME).getString("content");
                return new JSONArray(content);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gist GET 실패", e);
        }
        return new JSONArray();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
