package kr.recatch.proto;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 서버 주소·토큰·동의 여부·미전송 이벤트(파싱된 필드만)를 보관. */
final class Prefs {
    private static final String NAME = "recatch";
    private static final int OUTBOX_MAX = 50;

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static String serverUrl(Context c) { return sp(c).getString("serverUrl", "http://10.0.2.2:8787"); }
    static void setServerUrl(Context c, String url) { sp(c).edit().putString("serverUrl", url.replaceAll("/+$", "")).apply(); }

    static String token(Context c) { return sp(c).getString("token", null); }
    static void setToken(Context c, String token) { sp(c).edit().putString("token", token).apply(); }

    static boolean consent(Context c) { return sp(c).getBoolean("consent", false); }
    static void setConsent(Context c, boolean v) { sp(c).edit().putBoolean("consent", v).apply(); }

    // 설정 시트의 스위치 (웹 시연의 설정과 같은 항목)
    static boolean windowOnly(Context c) { return sp(c).getBoolean("windowOnly", true); }
    static void setWindowOnly(Context c, boolean v) { sp(c).edit().putBoolean("windowOnly", v).apply(); }
    static boolean maxBrightness(Context c) { return sp(c).getBoolean("maxBrightness", true); }
    static void setMaxBrightness(Context c, boolean v) { sp(c).edit().putBoolean("maxBrightness", v).apply(); }
    static boolean billingReminder(Context c) { return sp(c).getBoolean("billingReminder", false); }
    static void setBillingReminder(Context c, boolean v) { sp(c).edit().putBoolean("billingReminder", v).apply(); }
    static String lastReminderMonth(Context c) { return sp(c).getString("lastReminderMonth", ""); }
    static void setLastReminderMonth(Context c, String v) { sp(c).edit().putString("lastReminderMonth", v).apply(); }

    static synchronized JSONArray outbox(Context c) {
        try { return new JSONArray(sp(c).getString("outbox", "[]")); } catch (JSONException e) { return new JSONArray(); }
    }

    static synchronized void pushOutbox(Context c, JSONObject event) {
        JSONArray a = outbox(c);
        a.put(event);
        JSONArray trimmed = new JSONArray();
        for (int i = Math.max(0, a.length() - OUTBOX_MAX); i < a.length(); i++) trimmed.put(a.opt(i));
        sp(c).edit().putString("outbox", trimmed.toString()).apply();
    }

    static synchronized void setOutbox(Context c, JSONArray a) { sp(c).edit().putString("outbox", a.toString()).apply(); }
}
