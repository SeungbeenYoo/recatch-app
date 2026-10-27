package kr.recatch.proto;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/** 소비 분석: 성공률·놓친 혜택, 월별 되찾은 돈, 어디서 아꼈나요, 많이 아껴준 혜택, 자주 놓치는 순간 */
final class StatsTab {
    private StatsTab() {}

    static void render(MainActivity a, LinearLayout col) {
        String month = Insights.currentMonth();
        int mo = Integer.parseInt(month.substring(5));
        Ui.pageHeader(col, "소비 분석", mo + "월 1일부터 오늘까지");
        if (a.showConnectionHint(col)) return;
        List<JSONObject> rs = Insights.list(month);

        LinearLayout kpis = Ui.hbox(a);
        kpis.setPadding(0, Ui.dp(a, 10), 0, 0);
        int rate = Insights.successRate(rs);
        LinearLayout k1 = kpiSec(kpis, "재결제 성공률", rate + "%", Ui.INK,
            (Insights.count(rs, "done") + Insights.count(rs, "pending")) + "건 성공 · 안내 " + rs.size() + "건");
        Ui.bar(k1, "", "", rate / 100f, Ui.BRAND);
        kpiSec(kpis, "놓친 혜택", Ui.won(Insights.missedSum(rs)), Ui.MISS, Insights.count(rs, "missed") + "건 · 수수료 없음");
        col.addView(kpis);

        // 월별 되찾은 돈 (세로 막대)
        LinearLayout tr = Ui.sec(col);
        Ui.secHeader(tr, "월별 되찾은 돈", null, null);
        List<Map.Entry<String, Long>> months = Insights.byMonth();
        long max = 1;
        for (Map.Entry<String, Long> e : months) max = Math.max(max, e.getValue());
        LinearLayout chart = Ui.hbox(a);
        chart.setGravity(Gravity.BOTTOM);
        for (Map.Entry<String, Long> e : months) {
            LinearLayout c = Ui.vbox(a);
            c.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            TextView v = new TextView(a);
            v.setText(e.getValue() > 0 ? Ui.num(e.getValue()) : "");
            v.setTextSize(10.5f);
            v.setTextColor(Ui.INK2);
            v.setGravity(Gravity.CENTER);
            c.addView(v);
            View barV = new View(a);
            boolean cur = e.getKey().equals(month);
            barV.setBackground(Ui.round(a, cur ? Ui.BRAND : Ui.LINE, 6));
            int h = Math.max(Ui.dp(a, 4), Math.round(Ui.dp(a, 120) * e.getValue() / (float) max));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(a, 26), h);
            blp.gravity = Gravity.CENTER_HORIZONTAL;
            c.addView(barV, blp);
            TextView l = new TextView(a);
            l.setText(Integer.parseInt(e.getKey().substring(5)) + "월");
            l.setTextSize(12);
            l.setTextColor(cur ? Ui.INK : Ui.INK3);
            l.setGravity(Gravity.CENTER);
            c.addView(l);
            chart.addView(c, new LinearLayout.LayoutParams(0, Ui.dp(a, 170), 1f));
        }
        tr.addView(chart);

        // 어디서 아꼈나요
        List<Map.Entry<String, Long>> cats = Insights.byCategory(rs);
        LinearLayout cs = Ui.sec(col);
        Ui.secHeader(cs, "어디서 아꼈나요", "합계 " + Ui.won(Insights.totalOf(cats)), null);
        long cmax = cats.isEmpty() ? 1 : cats.get(0).getValue();
        for (Map.Entry<String, Long> e : cats) Ui.bar(cs, e.getKey(), Ui.won(e.getValue()), e.getValue() / (float) cmax, Ui.SAVE);
        if (cats.isEmpty()) Ui.text(cs, "재결제로 아낀 내역이 생기면 업종별로 보여드려요.", 13, false, Ui.INK3);

        // 많이 아껴준 혜택
        List<Map.Entry<String, Long>> ben = Insights.byBenefit(rs);
        LinearLayout rk = Ui.sec(col);
        Ui.secHeader(rk, "많이 아껴준 혜택", null, null);
        for (int i = 0; i < ben.size(); i++) {
            TextView no = new TextView(a);
            no.setText(String.valueOf(i + 1));
            no.setGravity(Gravity.CENTER);
            no.setTextColor(i == 0 ? Ui.BRAND : Ui.INK3);
            no.setTextSize(18);
            Ui.row(rk, no, ben.get(i).getKey(), null, Ui.won(ben.get(i).getValue()), Ui.SAVE, "수수료 차감 전", null);
        }
        if (ben.isEmpty()) Ui.text(rk, "아직 없어요.", 13, false, Ui.INK3);

        // 자주 놓치는 순간
        List<Map.Entry<String, Long>> mm = Insights.missedMoments(rs);
        LinearLayout ms = Ui.sec(col);
        Ui.secHeader(ms, "자주 놓치는 순간", null, null);
        long missedTotal = Insights.totalOf(mm);
        for (int i = 0; i < Math.min(3, mm.size()); i++) {
            long pct = missedTotal == 0 ? 0 : Math.round(mm.get(i).getValue() * 100.0 / missedTotal);
            Ui.row(ms, null, mm.get(i).getKey(), "이번 달 놓친 혜택의 " + pct + "%", "+" + Ui.won(mm.get(i).getValue()), Ui.MISS, null, v -> a.go("history"));
        }
        if (mm.isEmpty()) Ui.text(ms, "이번 달 놓친 혜택이 없어요.", 13, false, Ui.INK3);
    }

    private static LinearLayout kpiSec(LinearLayout row, String label, String value, int color, String sub) {
        LinearLayout k = Ui.vbox(row.getContext());
        k.setBackground(Ui.round(row.getContext(), Ui.CARD, 20));
        int p = Ui.dp(row.getContext(), 16);
        k.setPadding(p, p, p, p);
        Ui.text(k, label, 12.5f, false, Ui.INK3);
        Ui.text(k, value, 24, true, color);
        Ui.text(k, sub, 12, false, Ui.INK3);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, 1f);
        if (row.getChildCount() > 0) lp.leftMargin = Ui.dp(row.getContext(), 10);
        row.addView(k, lp);
        return k;
    }
}
