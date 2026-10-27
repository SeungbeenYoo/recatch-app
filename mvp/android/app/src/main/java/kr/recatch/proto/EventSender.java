package kr.recatch.proto;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 알림 원문 → (폰에서) 파싱 → 파싱된 필드만 서버로 전송 → 추천이 오면 알림 표시.
 * 원문은 이 메서드 밖으로 나가지 않고 저장하지도 않는다.
 * 전송에 실패하면 파싱된 필드만 outbox에 넣고 다음 전송 때 다시 보낸다.
 */
final class EventSender {
    private static final String TAG = "ReCatch";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    /** 화면 갱신용 (MainActivity가 등록) */
    interface Listener { void onServerResult(JSONObject result, String error); }
    static volatile Listener listener;

    private EventSender() {}

    /** @return 카드 승인·취소 알림으로 인식했으면 true */
    static boolean submit(Context ctx, String text) {
        CardParser.Parsed p = CardParser.parse(text, LocalDateTime.now());
        if (p == null) return false;
        JSONObject e = new JSONObject();
        try {
            e.put("type", p.type);
            e.put("issuer", p.issuer);
            e.put("last4", p.last4 == null ? JSONObject.NULL : p.last4);
            e.put("amount", p.amount);
            e.put("merchantRaw", p.merchantRaw == null ? JSONObject.NULL : p.merchantRaw);
            e.put("at", p.at == null ? JSONObject.NULL : p.at);
            e.put("cumulative", p.cumulative == null ? JSONObject.NULL : p.cumulative);
            e.put("receivedAt", Instant.now().toString());
        } catch (JSONException ex) {
            return false;
        }
        Context app = ctx.getApplicationContext();
        IO.execute(() -> send(app, e));
        return true;
    }

    /** 미전송 이벤트 재전송 (앱을 열 때) */
    static void flushOutbox(Context ctx) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> drain(app));
    }

    private static void send(Context ctx, JSONObject event) {
        Api api = new Api(ctx);
        try {
            api.ensureRegistered(null);
            drain(ctx);
            JSONObject r = api.postEvent(event);
            Notifier.handleServerResult(ctx, r);
            Listener l = listener;
            if (l != null) l.onServerResult(r, null);
        } catch (IOException ex) {
            Log.w(TAG, "send failed: " + ex.getMessage());
            // 서버 DB가 초기화돼 토큰이 무효면 다음 전송 때 다시 등록한다.
            if (ex instanceof Api.ApiException && ((Api.ApiException) ex).status == 401) Prefs.setToken(ctx, null);
            if (!(ex instanceof Api.ApiException) || ((Api.ApiException) ex).status >= 500 || ((Api.ApiException) ex).status == 401) {
                Prefs.pushOutbox(ctx, event);
            }
            Listener l = listener;
            if (l != null) l.onServerResult(null, ex.getMessage());
        }
    }

    private static void drain(Context ctx) {
        JSONArray box = Prefs.outbox(ctx);
        if (box.length() == 0 || Prefs.token(ctx) == null) return;
        Api api = new Api(ctx);
        JSONArray left = new JSONArray();
        for (int i = 0; i < box.length(); i++) {
            JSONObject e = box.optJSONObject(i);
            if (e == null) continue;
            try {
                // 늦게 도착한 건은 서버가 추천 없이 기록만 한다 (재결제 판정·실적 반영용)
                api.postEvent(e);
            } catch (Api.ApiException ex) {
                if (ex.status >= 500) left.put(e); // 400대는 다시 보내도 같으니 버린다
            } catch (IOException ex) {
                left.put(e);
            }
        }
        Prefs.setOutbox(ctx, left);
    }
}
