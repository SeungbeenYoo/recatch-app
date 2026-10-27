package kr.recatch.proto;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZonedDateTime;

/** 여러 화면에서 같이 쓰는 조각: 추천 한 줄, 카드 그림, 멤버십 한 줄, 남은 시간. */
final class Parts {
    private Parts() {}

    static void recRow(MainActivity a, LinearLayout parent, JSONObject r) {
        String st = r.optString("status");
        ZonedDateTime t = Insights.local(r.optString("at"));
        String when = String.format("%02d.%02d %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
        String amt, amtSub;
        int color;
        switch (st) {
            case "done": amt = "+" + Ui.won(r.optLong("delta")); amtSub = Ui.won(r.optLong("amount")) + " 결제"; color = Ui.SAVE; break;
            case "pending": amt = "+" + Ui.won(r.optLong("delta")); amtSub = "확인 중"; color = Ui.INK; break;
            case "waiting": amt = "+" + Ui.won(r.optLong("delta")); amtSub = "안내 중"; color = Ui.BRAND; break;
            case "missed": amt = Ui.won(r.optLong("delta")); amtSub = "놓침"; color = Ui.INK3; break;
            default: amt = Ui.won(r.optLong("delta")); amtSub = Ui.statusName(st); color = Ui.INK3;
        }
        Ui.row(parent, Ui.mark(a, r.optString("store")), r.optString("store") + (r.isNull("branch") ? "" : " " + r.optString("branch")),
            when + " · " + Repo.benefitLabel(r), amt, color, amtSub, v -> a.openRec(r));
    }

    /** 카드사 색 카드 그림 (웹 시연의 art-kb/sh/hd 느낌) */
    static View cardTile(Context c, JSONObject card, boolean small) {
        JSONObject p = Repo.cardProduct(card.optString("productId"));
        String issuer = card.optString("issuer");
        int bg, fg = Color.WHITE;
        switch (issuer) {
            case "kb": bg = Ui.c("#FFBC00"); fg = Ui.c("#3B2F1E"); break;
            case "sh": bg = Ui.c("#2B5BE8"); break;
            case "hd": bg = Ui.c("#22252B"); break;
            case "ss": bg = Ui.c("#1C3E94"); break;
            case "lt": bg = Ui.c("#E2231A"); break;
            default: bg = Ui.c("#4E5260");
        }
        LinearLayout t = Ui.vbox(c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{bg, darker(bg)});
        g.setCornerRadius(Ui.dp(c, small ? 14 : 18));
        t.setBackground(g);
        int pad = Ui.dp(c, small ? 12 : 18);
        t.setPadding(pad, pad, pad, pad);
        TextView bank = new TextView(c);
        bank.setText(Repo.issuerName(issuer));
        bank.setTextColor(fg);
        bank.setTextSize(small ? 12 : 14);
        bank.setTypeface(Typeface.DEFAULT_BOLD);
        t.addView(bank);
        TextView name = new TextView(c);
        name.setText(p != null ? p.optString("name") : "카드");
        name.setTextColor(fg);
        name.setTextSize(small ? 13 : 17);
        name.setPadding(0, Ui.dp(c, small ? 10 : 26), 0, 0);
        t.addView(name);
        TextView last = new TextView(c);
        last.setText("•••• " + card.optString("last4"));
        last.setTextColor(fg);
        last.setAlpha(0.85f);
        last.setTextSize(small ? 12 : 14);
        last.setTypeface(Typeface.MONOSPACE);
        t.addView(last);
        return t;
    }

    private static int darker(int c) {
        return Color.rgb((int) (Color.red(c) * 0.8), (int) (Color.green(c) * 0.8), (int) (Color.blue(c) * 0.8));
    }

    /** 가로로 넘기는 카드 레일 (스와이프로 화면이 넘어가지 않게 noswipe) */
    static LinearLayout rail(LinearLayout parent) {
        Context c = parent.getContext();
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setTag("noswipe");
        LinearLayout row = Ui.hbox(c);
        hs.addView(row);
        parent.addView(hs, Ui.matchWrap());
        return row;
    }

    static void membershipRow(MainActivity a, LinearLayout parent, JSONObject m, int index) {
        String name = Repo.membershipName(m);
        JSONObject p = Repo.membershipProduct(m.optString("productId", null));
        String sub;
        if (p != null) {
            JSONArray bs = p.optJSONArray("benefits");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; bs != null && i < Math.min(2, bs.length()); i++) sb.append(i > 0 ? " · " : "").append(bs.optString(i));
            sub = sb.length() > 0 ? sb.toString() : "제휴 혜택 확인 전";
        } else sub = "혜택 정보 없음 · 바코드만 보관";
        if (!m.has("code")) sub = "바코드 번호 미등록 · " + sub;
        TextView lead = Ui.mark(a, name);
        Ui.row(parent, lead, name, sub, m.has("code") ? "바코드" : "등록", Ui.BRAND, null, v -> Sheets.pass(a, index));
    }

    /** 남은 시간 표시: 뷰가 붙어 있는 동안 0.5초마다 갱신 */
    static void countdown(TextView tv, String expiresAt, String prefix) {
        long end = Instant.parse(expiresAt).toEpochMilli();
        Runnable tick = new Runnable() {
            @Override public void run() {
                long left = end - System.currentTimeMillis();
                if (left <= 0) { tv.setText(prefix + "안내 시간이 지났어요"); return; }
                tv.setText(String.format("%s%d:%02d 남음", prefix, left / 60000, (left / 1000) % 60));
                if (tv.isAttachedToWindow()) tv.postDelayed(this, 500);
            }
        };
        tick.run();
        tv.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { v.removeCallbacks(tick); v.post(tick); }
            @Override public void onViewDetachedFromWindow(View v) { v.removeCallbacks(tick); }
        });
    }

    static TextView badge(LinearLayout parent, String s, int fg, int bg) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(fg);
        t.setBackground(Ui.round(c, bg, 8));
        t.setPadding(Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 9), Ui.dp(c, 4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.START;
        parent.addView(t, lp);
        return t;
    }

    /** 첫 waiting 추천 (아직 시간 안) */
    static JSONObject activeRec() {
        JSONArray a = Repo.recs;
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (r.optString("status").equals("waiting") && Instant.parse(r.optString("expiresAt")).toEpochMilli() > System.currentTimeMillis()) return r;
        }
        return null;
    }
}
