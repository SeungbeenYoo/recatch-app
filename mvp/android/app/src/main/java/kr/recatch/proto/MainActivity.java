package kr.recatch.proto;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * 웹 시연과 같은 구조: 상단 로고·결제 감지 상태·설정, 홈 → 내역 → 분석 → 지갑 4개 화면(좌우로 밀어 이동), 하단 탭.
 * 화면 숫자는 모두 서버(Repo)에서 받은 실제 계산 결과다.
 */
public class MainActivity extends Activity {
    static final String[] TABS = {"home", "history", "stats", "wallet"};
    static final String[] TAB_NAMES = {"홈", "내역", "분석", "지갑"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::refresh;
    private SwipeFrame frame;
    private ScrollView page;
    private TextView detect;
    private final TextView[] tabViews = new TextView[4];
    private int tab = 0;
    private String lastDataKey = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Ui.systemBars(this);
        Notifier.ensureChannel(this);
        if (b != null) tab = b.getInt("tab", 0);

        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                top = s.top; bottom = s.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        // 상단: recatch. · 결제 감지 중 · 설정
        LinearLayout top = Ui.hbox(this);
        top.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 6));
        TextView logo = new TextView(this);
        SpannableStringBuilder sb = new SpannableStringBuilder("recatch.");
        sb.setSpan(new ForegroundColorSpan(Ui.BRAND), 7, 8, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        logo.setText(sb);
        logo.setTextSize(23);
        logo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        logo.setTextColor(Ui.INK);
        top.addView(logo);
        detect = new TextView(this);
        detect.setTextSize(12);
        detect.setTypeface(Typeface.DEFAULT_BOLD);
        detect.setPadding(Ui.dp(this, 10), 0, 0, 0);
        detect.setOnClickListener(v -> Sheets.settings(this));
        top.addView(detect, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = new TextView(this);
        gear.setText("설정");
        gear.setTextSize(14);
        gear.setTypeface(Typeface.DEFAULT_BOLD);
        gear.setTextColor(Ui.INK2);
        gear.setGravity(Gravity.CENTER);
        gear.setBackground(Ui.round(this, Ui.WELL, 12));
        gear.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        gear.setOnClickListener(v -> Sheets.settings(this));
        top.addView(gear);
        root.addView(top);

        frame = new SwipeFrame(this);
        frame.listener = this::onSwipe;
        root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 하단 탭
        LinearLayout bar = Ui.hbox(this);
        bar.setBackgroundColor(Ui.CARD);
        bar.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        for (int i = 0; i < 4; i++) {
            TextView t = new TextView(this);
            t.setText(TAB_NAMES[i]);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            int idx = i;
            t.setOnClickListener(v -> go(idx));
            tabViews[i] = t;
            bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        root.addView(bar);
        setContentView(root);
        render(0);

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateDetect();
        EventSender.listener = (r, err) -> ui.post(() -> {
            if (err != null) Ui.toast(this, "서버 전송 실패: " + err);
            else if (r.optJSONObject("recommendation") == null && (r.optJSONArray("updates") == null || r.optJSONArray("updates").length() == 0)) {
                Ui.toast(this, reasonText(r.optString("reason", "")));
            }
            refresh();
        });
        EventSender.flushOutbox(this);
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventSender.listener = null;
        ui.removeCallbacks(poll);
    }

    static String reasonText(String reason) {
        switch (reason) {
            case "below_threshold": return "더 아낄 수 있는 금액이 1,000원 미만이라 알리지 않았어요";
            case "window_passed": return "결제 후 2분이 지나 안내하지 않았어요";
            case "unknown_merchant": return "가맹점을 알아보지 못했어요";
            case "unknown_card": return "지갑에 없는 카드로 결제됐어요. 지갑에 카드를 추가해 주세요";
            default: return "결제를 기록했어요";
        }
    }

    boolean listenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(new ComponentName(this, CardNotificationListener.class).flattenToString());
    }

    void updateDetect() {
        boolean on = listenerEnabled() && Prefs.consent(this);
        detect.setText(on ? "● 결제 감지 중" : "○ 결제 감지 꺼짐");
        detect.setTextColor(on ? Ui.SAVE : Ui.INK3);
    }

    /** 서버에서 다시 받아 바뀐 게 있으면 지금 화면을 다시 그린다. */
    void refresh() {
        ui.removeCallbacks(poll);
        Repo.refresh(this, err -> {
            String key = Repo.recs + "|" + Repo.wallet + "|" + Repo.statement + "|" + err;
            if (!key.equals(lastDataKey)) { lastDataKey = key; redraw(); }
            BillingReminder.check(this);
            ui.postDelayed(poll, 5000);
        });
    }

    /** 지금 탭을 스크롤 위치를 지키며 다시 그린다. */
    void redraw() {
        int y = page != null ? page.getScrollY() : 0;
        render(0);
        page.post(() -> page.scrollTo(0, y));
    }

    void go(int idx) {
        if (idx == tab) { if (page != null) page.smoothScrollTo(0, 0); return; }
        int dir = idx > tab ? 1 : -1;
        tab = idx;
        render(dir);
    }

    void go(String name) {
        for (int i = 0; i < TABS.length; i++) if (TABS[i].equals(name)) go(i);
    }

    private void onSwipe(int dir) {
        // 내역 화면은 작은 탭(전체·완료·확인 중·놓친 혜택)을 먼저 넘기고, 끝에서 옆 화면으로
        if (TABS[tab].equals("history") && HistoryTab.shift(this, dir)) return;
        int next = tab + dir;
        if (next < 0 || next >= TABS.length) { if (page != null) page.animate().translationX(0).setDuration(160).start(); return; }
        tab = next;
        render(dir);
    }

    /** dir: 0 = 제자리 다시 그리기, ±1 = 옆에서 밀려 들어옴 */
    void render(int dir) {
        LinearLayout[] col = new LinearLayout[1];
        ScrollView next = Ui.page(this, col);
        switch (TABS[tab]) {
            case "home": HomeTab.render(this, col[0]); break;
            case "history": HistoryTab.render(this, col[0]); break;
            case "stats": StatsTab.render(this, col[0]); break;
            default: WalletTab.render(this, col[0]); break;
        }
        View old = page;
        page = next;
        frame.addView(next, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (old != null) {
            if (dir == 0) frame.removeView(old);
            else {
                float w = frame.getWidth();
                next.setTranslationX(dir * w);
                next.animate().translationX(0).setDuration(260).start();
                old.animate().translationX(-dir * w * 0.3f).alpha(0f).setDuration(260).withEndAction(() -> frame.removeView(old)).start();
            }
        }
        for (int i = 0; i < 4; i++) {
            tabViews[i].setTextColor(i == tab ? Ui.BRAND : Ui.INK3);
            tabViews[i].setTypeface(i == tab ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    /** 연결 전·오류 안내 (각 화면 맨 위) */
    boolean showConnectionHint(LinearLayout col) {
        if (Repo.ready() && Repo.lastError == null) return false;
        LinearLayout s = Ui.sec(col);
        s.setBackground(Ui.round(this, Ui.WARN_SOFT, 20));
        Ui.text(s, Prefs.token(this) == null ? "서버에 연결해 주세요" : "서버 응답이 없어요", 16, true, Ui.INK);
        Ui.text(s, Prefs.token(this) == null ? "설정에서 PC 서버 주소를 넣고 연결하면 실제 계산 결과가 표시돼요." : String.valueOf(Repo.lastError), 13, false, Ui.INK2);
        Ui.button(s, "설정 열기", true, v -> Sheets.settings(this));
        return !Repo.ready();
    }

    void openRec(int id) {
        startActivity(new android.content.Intent(this, RecommendationActivity.class).putExtra("id", id));
    }

    void openRec(JSONObject rec) { openRec(rec.optInt("id")); }
}
