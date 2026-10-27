package kr.recatch.proto;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.time.LocalDate;

/** 정산일 미리 알림: 청구 3일 전부터, 앱을 열 때 한 달에 한 번 알린다 (시제품 범위). */
final class BillingReminder {
    private BillingReminder() {}

    static void check(Context ctx) {
        JSONObject st = Repo.statement;
        if (st == null || !Prefs.billingReminder(ctx) || !st.optBoolean("billed") || st.optLong("totalDue") <= 0) return;
        String month = st.optString("month");
        if (month.equals(Prefs.lastReminderMonth(ctx))) return;
        LocalDate due = LocalDate.parse(st.optString("dueDate"));
        if (LocalDate.now().isBefore(due.minusDays(3))) return;
        Notifier.ensureChannel(ctx);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(ctx, Notifier.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(due.getMonthValue() + "월 " + due.getDayOfMonth() + "일에 " + Ui.won(st.optLong("totalDue")) + " 청구 예정")
            .setContentText("리캐치 성공 수수료 · 되찾은 돈 " + Ui.won(st.optLong("recovered")))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build();
        ctx.getSystemService(NotificationManager.class).notify(900000, n);
        Prefs.setLastReminderMonth(ctx, month);
    }
}
