package com.meritz.notlistener;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONObject;

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
    private static final String GIST_FILENAME = "meritz_trades.json";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!KAKAO_PACKAGE.equals(sbn.getPackageName())) return;

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String triggerWord = prefs.getString("trigger", "체결");
        String gistToken = prefs.getString("gist_token", "");
        String gistId = prefs.getString("gist_id", "");

        if (gistToken.isEmpty() || gistId.isEmpty()) {
            Log.w(TAG, "Gist 설정이 없습니다. 앱에서 설정해주세요.");
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        String text = null;
        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence normalText = extras.getCharSequence(Notification.EXTRA_TEXT);

        if (bigText != null) text = bigText.toString();
        else if (normalText != null) text = normalText.toString();

        if (text == null || !text.contains(triggerWord)) return;

        Log.d(TAG, "체결 알림 감지: " + text);

        Map<String, String> parsed = parseTradeMessage(text);
        if (parsed.isEmpty()) return;

        final String finalToken = gistToken;
        final String finalId = gistId;
        final Map<String, String> finalParsed = parsed;
        new Thread(() -> sendToGist(finalParsed, finalToken, finalId)).start();
    }

    private Map<String, String> parseTradeMessage(String text) {
        Map<String, String> result = new HashMap<>();
        extractField(result, text, "계좌명",   "계좌명 : (.+)");
        extractField(result, text, "계좌번호", "계좌번호 : (.+)");
        extractField(result, text, "종목명",   "종목명 : (.+)");
        extractField(result, text, "매매구분", "매매구분 : (.+)");
        extractField(result, text, "체결단가", "체결단가 : (.+)");
        extractField(result, text, "주문수량", "주문수량 : (.+)");
        extractField(result, text, "체결수량", "체결수량 : (.+)");
        extractField(result, text, "체결금액", "체결금액 : (.+)");
        extractField(result, text, "체결일자", "체결일자 : (.+)");
        result.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return result;
    }

    private void extractField(Map<String, String> map, String text, String key, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        if (m.find()) map.put(key, m.group(1).trim());
    }

    private void sendToGist(Map<String, String> data, String gistToken, String gistId) {
        try {
            JSONObject trade = new JSONObject(data);
            JSONObject fileContent = new JSONObject();
            fileContent.put("content", trade.toString(2));

            JSONObject files = new JSONObject();
            files.put(GIST_FILENAME, fileContent);

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
            Log.d(TAG, "Gist 전송 결과: " + code);
            conn.disconnect();

        } catch (Exception e) {
            Log.e(TAG, "Gist 전송 실패", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {}
}
