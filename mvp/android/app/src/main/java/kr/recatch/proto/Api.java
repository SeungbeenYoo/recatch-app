package kr.recatch.proto;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 백엔드 호출. 모든 메서드는 백그라운드 스레드에서 부른다. */
final class Api {
    static final class ApiException extends IOException {
        final int status;
        final String code;
        final String userMessage; // 서버가 준 사용자용 문구 (바코드 형식 오류 등)
        ApiException(int status, String code, String userMessage) {
            super(userMessage != null ? userMessage : status + " " + code);
            this.status = status; this.code = code; this.userMessage = userMessage;
        }
    }

    private final Context ctx;

    Api(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    private String call(String method, String path, JSONObject body) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(Prefs.serverUrl(ctx) + path).openConnection();
        con.setConnectTimeout(5000);
        con.setReadTimeout(8000);
        con.setRequestMethod(method);
        con.setRequestProperty("Accept", "application/json");
        String token = Prefs.token(ctx);
        if (token != null) con.setRequestProperty("Authorization", "Bearer " + token);
        if (body != null) {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = con.getOutputStream()) { os.write(bytes); }
        }
        int status = con.getResponseCode();
        InputStream is = status >= 400 ? con.getErrorStream() : con.getInputStream();
        String text = "";
        if (is != null) {
            try (InputStream in = is) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                text = bo.toString("UTF-8");
            }
        }
        con.disconnect();
        if (status >= 400) {
            String code = "http_" + status, msg = null;
            try {
                JSONObject e = new JSONObject(text);
                code = e.optString("error", code);
                msg = e.has("message") ? e.optString("message") : null;
            } catch (JSONException ignored) {}
            throw new ApiException(status, code, msg);
        }
        return text;
    }

    private JSONObject obj(String method, String path, JSONObject body) throws IOException {
        try { return new JSONObject(call(method, path, body)); } catch (JSONException e) { throw new IOException("bad_json", e); }
    }

    JSONObject health() throws IOException { return obj("GET", "/api/health", null); }

    /** 토큰이 없으면 등록해서 저장한다. */
    void ensureRegistered(String invite) throws IOException {
        if (Prefs.token(ctx) != null) return;
        JSONObject body = new JSONObject();
        try { if (invite != null && !invite.isEmpty()) body.put("invite", invite); } catch (JSONException ignored) {}
        JSONObject r = obj("POST", "/api/register", body);
        Prefs.setToken(ctx, r.optString("token"));
    }

    JSONObject postEvent(JSONObject event) throws IOException { return obj("POST", "/api/events", event); }

    JSONArray recommendations() throws IOException {
        try { return new JSONArray(call("GET", "/api/recommendations", null)); } catch (JSONException e) { throw new IOException("bad_json", e); }
    }

    JSONObject recommendation(int id) throws IOException { return obj("GET", "/api/recommendations/" + id, null); }

    JSONObject baseline(int id, String membershipId) throws IOException {
        JSONObject b = new JSONObject();
        try { b.put("membershipId", membershipId); } catch (JSONException ignored) {}
        return obj("POST", "/api/recommendations/" + id + "/baseline", b);
    }

    JSONObject confirm(int id, long confirmedLater) throws IOException {
        JSONObject b = new JSONObject();
        try { b.put("confirmedLater", confirmedLater); } catch (JSONException ignored) {}
        return obj("POST", "/api/recommendations/" + id + "/confirm", b);
    }

    JSONObject statement() throws IOException { return obj("GET", "/api/statement", null); }

    JSONObject skip(int id) throws IOException { return obj("POST", "/api/recommendations/" + id + "/skip", new JSONObject()); }

    JSONObject catalog() throws IOException { return obj("GET", "/api/catalog", null); }

    JSONObject wallet() throws IOException { return obj("GET", "/api/wallet", null); }

    JSONObject putWallet(JSONObject w) throws IOException { return obj("PUT", "/api/wallet", w); }
}
