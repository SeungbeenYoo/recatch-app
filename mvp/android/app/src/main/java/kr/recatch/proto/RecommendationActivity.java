package kr.recatch.proto;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 재결제 화면 (웹 시연의 결제 화면 ④ → 완료 ⑤): 혜택, 멤버십 바코드, 남은 시간, 직원 안내, 수수료.
 * 상태는 서버가 취소·재승인 알림으로 판정한다. "다시 결제했어요"는 확인을 기다리는 표시일 뿐이다.
 */
public class RecommendationActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::load;
    private LinearLayout col;
    private int id;
    private boolean claimed;
    private String shown = "";      // 마지막으로 그린 서버 응답 (같으면 다시 그리지 않음)
    private String shownStatus = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Ui.systemBars(this);
        id = getIntent().getIntExtra("id", -1);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        col = Ui.vbox(this);
        col.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 24));
        sv.addView(col);
        sv.setOnApplyWindowInsetsListener((v, insets) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = insets.getInsets(WindowInsets.Type.systemBars());
                top = s.top; bottom = s.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            v.setPadding(0, top, 0, bottom);
            return insets;
        });
        setContentView(sv);
        Ui.text(col, "불러오는 중…", 14, false, Ui.INK3);
    }

    @Override protected void onResume() { super.onResume(); load(); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(poll); }

    private void load() {
        ui.removeCallbacks(poll);
        io.execute(() -> {
            try {
                Api api = new Api(this);
                if (Repo.catalog == null) Repo.catalog = api.catalog();
                if (Repo.wallet == null) Repo.wallet = api.wallet();
                JSONObject r = api.recommendation(id);
                ui.post(() -> renderIfChanged(r));
            } catch (Exception e) {
                ui.post(() -> { col.removeAllViews(); Ui.text(col, "불러오지 못했어요: " + e.getMessage(), 14, false, Ui.MISS); });
            }
        });
    }

    private void act(Callable<JSONObject> call, String toast) {
        io.execute(() -> {
            try {
                JSONObject r = call.call();
                ui.post(() -> { if (toast != null) Ui.toast(this, toast); render(r); });
            } catch (Exception e) {
                ui.post(() -> Ui.toast(this, "실패: " + e.getMessage()));
            }
        });
    }

    private void setBrightness(boolean max) {
        WindowManager.LayoutParams wl = getWindow().getAttributes();
        wl.screenBrightness = max ? 1f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(wl);
    }

    private JSONObject walletMembership(String productId) {
        JSONArray ms = Repo.wallet == null ? null : Repo.wallet.optJSONArray("memberships");
        for (int i = 0; ms != null && i < ms.length(); i++) if (productId.equals(ms.optJSONObject(i).optString("productId"))) return ms.optJSONObject(i);
        return null;
    }

    /** 폴링 결과가 같으면 화면을 건드리지 않는다 (다시 그리는 순간 누른 버튼이 사라지는 문제) */
    private void renderIfChanged(JSONObject r) {
        if (r.toString().equals(shown)) {
            if ("waiting".equals(r.optString("status"))) { ui.removeCallbacks(poll); ui.postDelayed(poll, 3000); }
            return;
        }
        render(r);
    }

    private void render(JSONObject r) {
        shown = r.toString();
        ui.removeCallbacks(poll);
        boolean statusChanged = !r.optString("status").equals(shownStatus);
        shownStatus = r.optString("status");
        if (statusChanged && col.getParent() instanceof ScrollView) ((ScrollView) col.getParent()).scrollTo(0, 0);
        col.removeAllViews();
        String status = r.optString("status");
        JSONObject best = r.optJSONObject("best"), base = r.optJSONObject("baseline");
        JSONObject mem = best.optJSONObject("membership");
        ZonedDateTime t = Insights.local(r.optString("at"));

        // 머리: 가맹점 · 결제 시각 · 닫기
        LinearLayout head = Ui.hbox(this);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42));
        ml.rightMargin = Ui.dp(this, 12);
        head.addView(Ui.mark(this, r.optString("store")), ml);
        LinearLayout ht = Ui.vbox(this);
        Ui.text(ht, r.optString("store") + (r.isNull("branch") ? "" : " " + r.optString("branch")), 16, true, Ui.INK);
        Ui.text(ht, String.format("%02d:%02d 결제 · %s", t.getHour(), t.getMinute(), Ui.won(r.optLong("amount"))), 13, false, Ui.INK3);
        head.addView(ht, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(this);
        close.setText("닫기");
        close.setTextColor(Ui.INK2);
        close.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10));
        close.setOnClickListener(v -> finish());
        head.addView(close);
        col.addView(head);

        boolean waiting = status.equals("waiting");
        boolean won = status.equals("done") || status.equals("pending");
        setBrightness(waiting && mem != null && Prefs.maxBrightness(this));

        if (won) { success(r, status); return; }

        LinearLayout s = Ui.sec(col);
        Parts.badge(s, Repo.benefitLabel(r), Ui.BRAND_INK, Ui.BRAND);
        if (waiting) {
            Ui.text(s, "다시 결제하면", 20, true, Ui.INK).setPadding(0, Ui.dp(this, 10), 0, 0);
            Ui.text(s, Ui.won(r.optLong("delta")) + " 더 아낄 수 있어요", 26, true, Ui.BRAND);
        } else {
            Ui.text(s, Ui.statusName(status), 20, true, Ui.statusColor(status)).setPadding(0, Ui.dp(this, 10), 0, 0);
            Ui.text(s, statusExplain(status), 14, false, Ui.INK2);
        }

        // 멤버십 바코드 (안내 중일 때만)
        if (mem != null && waiting) {
            JSONObject wm = walletMembership(mem.optString("productId"));
            LinearLayout pass = Ui.vbox(this);
            pass.setBackground(Ui.round(this, Color.WHITE, 16));
            pass.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
            TextView pn = new TextView(this);
            pn.setText(mem.optString("name") + " 바코드를 먼저 보여 주세요");
            pn.setTextColor(Ui.c("#191A1F"));
            pn.setTypeface(Typeface.DEFAULT_BOLD);
            pass.addView(pn);
            if (wm != null && wm.has("code")) {
                LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 96));
                bl.topMargin = Ui.dp(this, 10);
                pass.addView(new BarcodeView(this, wm.optString("code"), wm.optString("format", "numeric")), bl);
                TextView num = new TextView(this);
                num.setText(wm.optString("code").replaceAll("(\\d{4})(?=\\d)", "$1 "));
                num.setTypeface(Typeface.MONOSPACE);
                num.setTextColor(Ui.c("#191A1F"));
                num.setGravity(Gravity.CENTER);
                pass.addView(num);
                if (waiting && Prefs.maxBrightness(this)) {
                    TextView br = new TextView(this);
                    br.setText("화면 밝기를 최대로 올렸어요");
                    br.setTextColor(Ui.c("#8B8F9C"));
                    br.setTextSize(12);
                    br.setGravity(Gravity.CENTER);
                    pass.addView(br);
                }
            } else {
                TextView no = new TextView(this);
                no.setText("지갑에 바코드 번호가 없어요. 멤버십 앱에서 바코드를 열어 주세요.");
                no.setTextColor(Ui.c("#4E5260"));
                pass.addView(no);
            }
            LinearLayout.LayoutParams pl = Ui.matchWrap();
            pl.topMargin = Ui.dp(this, 12);
            s.addView(pass, pl);
        }

        // 남은 시간 + 직원 안내
        if (waiting) {
            TextView timer = Ui.text(s, "", 20, true, Ui.BRAND);
            timer.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 4));
            Parts.countdown(timer, r.optString("expiresAt"), "");
            JSONArray steps = r.optJSONArray("steps");
            for (int i = 0; steps != null && i < steps.length(); i++) Ui.text(s, (i + 1) + ". " + steps.optString(i), 14.5f, false, Ui.INK);
        }

        // 지금 vs 재결제
        LinearLayout cmp = Ui.sec(col);
        Ui.line(cmp, "지금 " + base.optString("cardName") + "(" + base.optString("cardLast4") + ")", Ui.won(base.optLong("total")), Ui.INK);
        Ui.line(cmp, "재결제 " + (mem != null ? mem.optString("name") + " + " : "") + best.optString("cardName") + "(" + best.optString("cardLast4") + ")", Ui.won(best.optLong("total")), Ui.SAVE);
        if (best.optLong("atPOS") > 0) Ui.text(cmp, "현장 할인 " + Ui.won(best.optLong("atPOS")) + "은 다시 결제한 금액으로 바로 확인돼요.", 12.5f, false, Ui.INK3);
        if (best.optLong("later") > 0) Ui.text(cmp, "캐시백·청구할인·적립 " + Ui.won(best.optLong("later")) + "은 카드사 확정 후 반영돼요.", 12.5f, false, Ui.INK3);
        JSONArray warns = r.optJSONArray("warnings");
        for (int i = 0; warns != null && i < warns.length(); i++) {
            if ("confirmed_later".equals(warns.optJSONObject(i).optString("code"))) continue;
            Ui.text(cmp, "⚠ " + warns.optJSONObject(i).optString("message"), 13, false, Ui.WARN);
        }

        LinearLayout fee = Ui.sec(col);
        if (waiting) {
            Ui.line(fee, "더 아낀 금액", Ui.won(r.optLong("delta")), Ui.INK);
            Ui.line(fee, "리캐치 수수료 20% · 월말 정산", Ui.won(r.optLong("fee")), Ui.INK);
            Ui.total(fee, "내 순절약", Ui.won(r.optLong("net")));
            Ui.text(fee, "원래 결제로 받는 혜택은 빼고 더 아낀 금액에만 수수료가 붙어요. 재결제하지 않으면 0원이에요.", 12, false, Ui.INK3);
        } else {
            Ui.line(fee, status.equals("no_gain") ? "더 아낄 수 있었던 금액" : "받을 수 있었던 혜택", Ui.won(r.optLong("delta")), Ui.INK3);
            Ui.line(fee, "리캐치 수수료", "0원 (청구 없음)", Ui.INK);
        }

        if (waiting) {
            if (claimed) {
                TextView wait = Ui.text(col, "결제 알림으로 취소·재결제를 확인하고 있어요…", 14, true, Ui.WARN);
                wait.setGravity(Gravity.CENTER);
                wait.setPadding(0, Ui.dp(this, 12), 0, 0);
            } else Ui.button(col, "다시 결제했어요", true, v -> { claimed = true; render(r); });
            Ui.button(col, "이번엔 넘어갈게요", false, v -> act(() -> new Api(this).skip(id), "놓친 혜택으로 기록했어요. 수수료는 없어요"));
            if (mem != null) Ui.button(col, "이미 " + mem.optString("name") + "을(를) 썼어요", false,
                v -> act(() -> new Api(this).baseline(id, mem.optString("productId")), "원래 결제 기준을 다시 계산했어요"));
            Ui.note(col, "시연용: 실제 결제 대신 취소·재결제 알림을 흉내 낼 수 있어요.");
            Ui.button(col, "체험: 취소·재결제 알림 보내기", false, v -> { claimed = true; Demo.simulateRepay(this, r); render(r); });
            ui.postDelayed(poll, 3000);
        } else Ui.button(col, "확인", true, v -> finish());
    }

    private void success(JSONObject r, String status) {
        LinearLayout s = Ui.sec(col);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView ok = new TextView(this);
        ok.setText("✓");
        ok.setTextSize(30);
        ok.setTextColor(Color.WHITE);
        ok.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(Ui.SAVE);
        ok.setBackground(g);
        s.addView(ok, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        TextView where = Ui.text(s, r.optString("store") + "에서", 16, false, Ui.INK2);
        where.setGravity(Gravity.CENTER);
        TextView big = Ui.text(s, Ui.won(r.optLong("delta")) + "\n" + (status.equals("done") ? "되찾았어요" : "되찾는 중이에요"), 28, true, Ui.INK);
        big.setGravity(Gravity.CENTER);
        if (status.equals("pending")) {
            TextView p = Ui.text(s, "재결제는 확인됐어요. 캐시백·청구할인 " + Ui.won(r.optLong("awaitingConfirmation")) + "이 카드사에서 확정되면 완료돼요.", 13, false, Ui.INK2);
            p.setGravity(Gravity.CENTER);
        }
        LinearLayout slip = Ui.sec(col);
        Ui.line(slip, "더 아낀 금액", Ui.won(r.optLong("delta")), Ui.INK);
        Ui.line(slip, "리캐치 수수료 20% · 월말 정산", Ui.won(r.optLong("fee")), Ui.INK);
        Ui.total(slip, "내 순절약", Ui.won(r.optLong("net")));
        if (status.equals("pending")) {
            LinearLayout cf = Ui.sec(col);
            Ui.text(cf, "적립·청구할인이 들어왔나요?", 16, true, Ui.INK);
            EditText amt = Ui.field(cf, "확정된 금액 (원)", "", InputType.TYPE_CLASS_NUMBER);
            amt.setText(String.valueOf(r.optLong("awaitingConfirmation")));
            Ui.button(cf, "이 금액이 들어왔어요", true, v -> {
                long n;
                try { n = Long.parseLong(amt.getText().toString().trim()); } catch (NumberFormatException e) { Ui.toast(this, "숫자로 입력해 주세요"); return; }
                act(() -> new Api(this).confirm(id, n), "확정했어요");
            });
        }
        Ui.button(col, "확인", status.equals("done"), v -> finish());
    }

    static String statusExplain(String s) {
        switch (s) {
            case "missed": return "재결제하지 않아 놓친 혜택으로 기록했어요. 수수료는 없어요.";
            case "abandoned": return "원래 결제 취소만 확인됐어요. 다시 결제하셨다면 카드 알림이 오는지 확인해 주세요. 수수료는 없어요.";
            case "double_paid": return "원래 결제가 취소되지 않은 채 다시 결제됐어요. 매장이나 카드사에서 원래 결제를 취소해 주세요.";
            case "no_gain": return "이미 쓰신 혜택을 빼면 더 아낄 금액이 없어요. 수수료는 없어요.";
            default: return "";
        }
    }
}
