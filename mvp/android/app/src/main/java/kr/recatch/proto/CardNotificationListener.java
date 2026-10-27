package kr.recatch.proto;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayDeque;
import java.util.regex.Pattern;

/**
 * 문자앱·카드사 앱 알림 중 카드 승인·취소로 보이는 것만 골라 EventSender로 넘긴다.
 * 문자 권한(READ_SMS)은 쓰지 않는다 (Google Play 제한 권한).
 * 사용자가 앱 안에서 동의하기 전에는 아무것도 처리하지 않는다.
 */
public class CardNotificationListener extends NotificationListenerService {
    private static final Pattern KEYWORD = Pattern.compile("승인|취소|사용|결제");
    // 같은 알림이 갱신돼 여러 번 올라오는 경우를 거른다 (해시만 메모리에 잠깐 보관)
    private final ArrayDeque<long[]> recent = new ArrayDeque<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (getPackageName().equals(sbn.getPackageName())) return;
        if (!Prefs.consent(this)) return;
        String text = extractText(sbn.getNotification());
        if (text == null || !text.contains("원") || !KEYWORD.matcher(text).find()) return;
        if (isDuplicate(text.hashCode())) return;
        EventSender.submit(this, text);
    }

    private boolean isDuplicate(int hash) {
        long now = System.currentTimeMillis();
        while (!recent.isEmpty() && now - recent.peekFirst()[1] > 15_000) recent.pollFirst();
        for (long[] r : recent) if (r[0] == hash) return true;
        recent.addLast(new long[]{hash, now});
        return false;
    }

    /** 제목(보낸 사람·카드사) + 본문. 대화형 알림이면 마지막 메시지. */
    static String extractText(Notification n) {
        Bundle ex = n.extras;
        if (ex == null) return null;
        CharSequence title = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence body = null;
        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0 && msgs[msgs.length - 1] instanceof Bundle) {
            body = ((Bundle) msgs[msgs.length - 1]).getCharSequence("text");
        }
        if (body == null) body = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (body == null) body = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (body == null) return null;
        return (title != null ? title + "\n" : "") + body;
    }
}
