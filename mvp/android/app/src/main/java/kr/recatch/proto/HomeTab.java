package kr.recatch.proto;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** 홈: 이번 달 되찾은 돈, 지금 안내 중인 재결제(또는 체험), 내 지갑, 내 소비 한눈에, 최근 리캐치, 이번 주 챙길 혜택 */
final class HomeTab {
    private HomeTab() {}

    static void render(MainActivity a, LinearLayout col) {
        if (a.showConnectionHint(col)) return;
        JSONObject st = Repo.statement;
        String month = Insights.currentMonth();
        int mo = Integer.parseInt(month.substring(5));
        List<JSONObject> rs = Insights.list(month);

        // 1. 되찾은 돈 (hero)
        LinearLayout hero = Ui.sec(col);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{Ui.BRAND_SOFT, Ui.CARD, Ui.CARD});
        g.setCornerRadius(Ui.dp(a, 20));
        hero.setBackground(g);
        LinearLayout top = Ui.hbox(a);
        LinearLayout lab = Ui.vbox(a);
        Ui.text(lab, mo + "월에 되찾은 돈", 16, true, Ui.INK);
        Ui.text(lab, "재결제 완료 기준 · 수수료 차감 전", 11.5f, false, Ui.INK3);
        top.addView(lab, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView stamp = new TextView(a);
        stamp.setText("재결제\n" + (Insights.count(rs, "done") + Insights.count(rs, "pending")) + "회");
        stamp.setGravity(Gravity.CENTER);
        stamp.setTextColor(Ui.BRAND);
        stamp.setTypeface(Typeface.DEFAULT_BOLD);
        stamp.setTextSize(12);
        GradientDrawable sb = new GradientDrawable();
        sb.setStroke(Ui.dp(a, 2), Ui.BRAND);
        sb.setCornerRadius(Ui.dp(a, 10));
        stamp.setBackground(sb);
        stamp.setPadding(Ui.dp(a, 9), Ui.dp(a, 4), Ui.dp(a, 9), Ui.dp(a, 4));
        stamp.setRotation(-7);
        top.addView(stamp);
        hero.addView(top);
        TextView amt = Ui.text(hero, Ui.num(st.optLong("recovered")) + "원", 40, true, Ui.BRAND_DEEP);
        amt.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 2));
        Ui.text(hero, "확인 중 " + Ui.won(st.optLong("pending")) + " · 놓친 혜택 " + Ui.won(st.optLong("missed")), 13.5f, false, Ui.INK3);
        LinearLayout ledger = Ui.vbox(a);
        ledger.setPadding(0, Ui.dp(a, 12), 0, 0);
        Ui.line(ledger, "할인·적립으로 되찾은 돈", Ui.won(st.optLong("recovered")), Ui.INK);
        Ui.line(ledger, "리캐치 성공 수수료 20%", "−" + Ui.won(st.optLong("fee")), Ui.INK);
        Ui.total(ledger, "수수료 차감 후 남은 돈", Ui.won(st.optLong("recovered") - st.optLong("fee")));
        hero.addView(ledger);
        LinearLayout btns = Ui.hbox(a);
        Ui.button(btns, "정산 명세서", false, v -> Sheets.statement(a));
        Ui.button(btns, "내역 전체", false, v -> a.go("history"));
        hero.addView(btns);

        // 2. 안내 중인 재결제 / 체험
        JSONObject live = Parts.activeRec();
        if (live != null) {
            LinearLayout s = Ui.sec(col);
            s.setBackground(Ui.round(a, Ui.BRAND_SOFT, 20));
            Parts.badge(s, Repo.benefitLabel(live), Ui.BRAND_INK, Ui.BRAND);
            Ui.text(s, "방금 결제한 " + Ui.won(live.optLong("amount")) + " · " + live.optString("store"), 14, false, Ui.INK2);
            Ui.text(s, Ui.won(live.optLong("delta")) + " 더 아낄 수 있어요", 22, true, Ui.INK);
            TextView left = Ui.text(s, "", 15, true, Ui.BRAND);
            Parts.countdown(left, live.optString("expiresAt"), "재결제 ");
            Ui.button(s, "재결제 방법 보기", true, v -> a.openRec(live));
        } else {
            LinearLayout s = Ui.sec(col);
            Ui.text(s, "결제하고 2분 안에\n놓친 할인을 알려드려요", 19, true, Ui.INK);
            Ui.text(s, "예시 결제 문자를 폰에서 처리해 알림부터 재결제까지 해보세요. 체험 결제도 내역에 남아요.", 13.5f, false, Ui.INK2);
            Ui.button(s, "재결제 체험하기", true, v -> Demo.start(a));
        }

        // 3. 내 지갑 바로 쓰기
        LinearLayout w = Ui.sec(col);
        Ui.secHeader(w, "내 지갑 바로 쓰기", "+ 추가", v -> Sheets.add(a, "membership"));
        Ui.text(w, "카드를 누르면 카드 정보가, 멤버십을 누르면 바코드가 열려요.", 12.5f, false, Ui.INK3);
        JSONArray cards = Repo.wallet.optJSONArray("cards");
        LinearLayout rail = Parts.rail(w);
        rail.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 4));
        for (int i = 0; cards != null && i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 150), Ui.dp(a, 96));
            lp.rightMargin = Ui.dp(a, 8);
            android.view.View tile = Parts.cardTile(a, c, true);
            int idx = i;
            tile.setOnClickListener(v -> Sheets.cardDetail(a, idx));
            rail.addView(tile, lp);
        }
        JSONArray mems = Repo.wallet.optJSONArray("memberships");
        for (int i = 0; mems != null && i < Math.min(3, mems.length()); i++) Parts.membershipRow(a, w, mems.optJSONObject(i), i);
        if (mems != null && mems.length() > 3) Ui.secHeader(w, "", "전체보기 " + mems.length() + "개 · +" + (mems.length() - 3) + "개", v -> a.go("wallet"));

        // 4. 내 소비 한눈에
        LinearLayout an = Ui.sec(col);
        Ui.secHeader(an, "내 소비 한눈에", rs.size() + "건 분석", v -> a.go("stats"));
        LinearLayout kpis = Ui.hbox(a);
        kpi(kpis, "재결제 성공률", Insights.successRate(rs) + "%", Ui.INK, v -> a.go("stats"));
        kpi(kpis, "놓친 혜택", Ui.won(Insights.missedSum(rs)), Ui.MISS, v -> a.go("history"));
        an.addView(kpis);
        List<Map.Entry<String, Long>> cats = Insights.byCategory(rs);
        long max = cats.isEmpty() ? 1 : cats.get(0).getValue();
        for (int i = 0; i < Math.min(3, cats.size()); i++) Ui.bar(an, cats.get(i).getKey(), Ui.won(cats.get(i).getValue()), cats.get(i).getValue() / (float) max, Ui.SAVE);
        List<Map.Entry<String, Long>> ben = Insights.byBenefit(rs);
        if (!ben.isEmpty()) {
            LinearLayout best = Ui.vbox(a);
            best.setBackground(Ui.round(a, Ui.WELL, 14));
            best.setPadding(Ui.dp(a, 14), Ui.dp(a, 12), Ui.dp(a, 14), Ui.dp(a, 12));
            Ui.text(best, "가장 많이 아껴준 혜택 · 수수료 차감 전", 12, false, Ui.INK3);
            Ui.line(best, ben.get(0).getKey(), Ui.won(ben.get(0).getValue()), Ui.SAVE);
            LinearLayout.LayoutParams lp = Ui.matchWrap();
            lp.topMargin = Ui.dp(a, 12);
            an.addView(best, lp);
        }
        if (cats.isEmpty()) Ui.text(an, "아직 재결제로 아낀 내역이 없어요.", 13, false, Ui.INK3);

        // 5. 최근 리캐치
        LinearLayout feed = Ui.sec(col);
        Ui.secHeader(feed, "최근 리캐치", "전체 ›", v -> a.go("history"));
        JSONArray all = Repo.recs;
        for (int i = 0; i < Math.min(4, all.length()); i++) Parts.recRow(a, feed, all.optJSONObject(i));
        if (all.length() == 0) Ui.text(feed, "카드 결제 알림이 오면 여기에 쌓여요.", 13, false, Ui.INK3);

        // 6. 이번 주 챙길 혜택
        LinearLayout todo = Ui.sec(col);
        Ui.secHeader(todo, "이번 주 챙길 혜택", null, null);
        List<String[]> todos = Insights.todos();
        for (String[] t : todos) Ui.row(todo, null, t[0], t[1], "›", Ui.INK3, null, v -> a.go(t[2]));
        if (todos.isEmpty()) Ui.text(todo, "지금은 챙길 조건이 없어요. 지갑의 실적·한도를 최신으로 맞춰 두세요.", 13, false, Ui.INK3);

        Ui.note(col, "혜택 이름·비율·한도는 예시 데이터예요 (카드 약관 확인 전).");
    }

    static void kpi(LinearLayout row, String label, String value, int color, android.view.View.OnClickListener on) {
        LinearLayout k = Ui.vbox(row.getContext());
        k.setBackground(Ui.round(row.getContext(), Ui.WELL, 14));
        k.setPadding(Ui.dp(row.getContext(), 14), Ui.dp(row.getContext(), 12), Ui.dp(row.getContext(), 14), Ui.dp(row.getContext(), 12));
        Ui.text(k, label, 12.5f, false, Ui.INK3);
        Ui.text(k, value, 20, true, color);
        k.setOnClickListener(on);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = Ui.dp(row.getContext(), 8);
        lp.bottomMargin = Ui.dp(row.getContext(), 6);
        row.addView(k, lp);
    }
}
