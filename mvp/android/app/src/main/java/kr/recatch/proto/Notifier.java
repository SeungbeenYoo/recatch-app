package kr.recatch.proto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;

/** 서버 계산 결과를 폰 알림으로 보여 준다. */
final class Notifier {
    static final String CHANNEL = "recatch_reco";
    private Notifier() {}

    static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "재결제 추천", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("결제 직후 더 아낄 수 있는 방법과 재결제 확인 결과");
        nm.createNotificationChannel(ch);
    }

    static void handleServerResult(Context ctx, JSONObject r) {
        JSONObject rec = r.optJSONObject("recommendation");
        if (rec != null) showRecommendation(ctx, rec);
        JSONArray updates = r.optJSONArray("updates");
        if (updates != null) for (int i = 0; i < updates.length(); i++) showUpdate(ctx, updates.optJSONObject(i));
    }

    private static PendingIntent open(Context ctx, int id) {
        Intent i = new Intent(ctx, RecommendationActivity.class).putExtra("id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void showRecommendation(Context ctx, JSONObject rec) {
        ensureChannel(ctx);
        int id = rec.optInt("id");
        JSONObject best = rec.optJSONObject("best");
        JSONObject mem = best == null ? null : best.optJSONObject("membership");
        String how = (mem != null ? mem.optString("name") + " + " : "") + (best == null ? "" : best.optString("cardName"));
        long ttl = Math.max(5000, Instant.parse(rec.optString("expiresAt")).toEpochMilli() - System.currentTimeMillis());
        Notification.Builder nb = new Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(Ui.won(rec.optLong("delta")) + " 더 아낄 수 있어요")
            .setContentText(rec.optString("store") + " " + Ui.won(rec.optLong("amount")) + " → " + how + "로 재결제")
            .setStyle(new Notification.BigTextStyle().bigText(
                rec.optString("store") + " " + Ui.won(rec.optLong("amount")) + " 결제\n"
                    + how + "로 다시 결제하면 " + Ui.won(rec.optLong("delta")) + " 더 아껴요.\n"
                    + "수수료 " + Ui.won(rec.optLong("fee")) + " · 순절약 " + Ui.won(rec.optLong("net")) + " · 2분 안에"))
            .setCategory(Notification.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(open(ctx, id));
        // 설정 "재결제 가능 시간에만 알림": 2분이 지나면 알림을 지운다
        if (Prefs.windowOnly(ctx)) nb.setTimeoutAfter(ttl);
        ctx.getSystemService(NotificationManager.class).notify(id, nb.build());
    }

    static void showUpdate(Context ctx, JSONObject rec) {
        if (rec == null) return;
        ensureChannel(ctx);
        int id = rec.optInt("id");
        String status = rec.optString("status");
        String title;
        switch (status) {
            case "done": title = "재결제 확인: " + Ui.won(rec.optLong("delta")) + " 아꼈어요"; break;
            case "pending": title = "재결제 확인: 적립·청구할인 확정 대기"; break;
            case "abandoned": title = "취소만 확인됐어요. 다시 결제하셨나요?"; break;
            case "double_paid": title = "원래 결제 취소 없이 다시 결제됐어요"; break;
            default: return; // missed 등은 알리지 않는다
        }
        Notification n = new Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(rec.optString("store") + " " + Ui.won(rec.optLong("amount")))
            .setAutoCancel(true)
            .setContentIntent(open(ctx, id))
            .build();
        ctx.getSystemService(NotificationManager.class).notify(id, n);
    }
}
