package kr.recatch.proto;

import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/** 내 혜택 지갑: 카드(실적·한도), 멤버십·바코드, 나에게 해당하는 조건. 모두 서버 지갑에 저장된다. */
final class WalletTab {
    private WalletTab() {}

    static int selected = 0;

    static void render(MainActivity a, LinearLayout col) {
        Ui.pageHeader(col, "내 혜택 지갑", "등록한 카드·멤버십 안에서만 혜택을 골라요.");
        if (a.showConnectionHint(col)) return;

        // 카드 레일
        JSONArray cards = Repo.wallet.optJSONArray("cards");
        int n = cards == null ? 0 : cards.length();
        if (selected >= n) selected = Math.max(0, n - 1);
        LinearLayout rail = Parts.rail(col);
        rail.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
        for (int i = 0; i < n; i++) {
            JSONObject c = cards.optJSONObject(i);
            android.view.View tile = Parts.cardTile(a, c, false);
            tile.setAlpha(i == selected ? 1f : 0.55f);
            int idx = i;
            tile.setOnClickListener(v -> { selected = idx; a.redraw(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 250), Ui.dp(a, 150));
            lp.rightMargin = Ui.dp(a, 10);
            rail.addView(tile, lp);
        }
        android.widget.TextView add = new android.widget.TextView(a);
        add.setText("+ 카드 추가");
        add.setGravity(android.view.Gravity.CENTER);
        add.setTextColor(Ui.INK2);
        add.setTextSize(15);
        add.setBackground(Ui.round(a, Ui.WELL, 18));
        add.setOnClickListener(v -> Sheets.add(a, "card"));
        rail.addView(add, new LinearLayout.LayoutParams(Ui.dp(a, 130), Ui.dp(a, 150)));

        // 선택한 카드 정보
        if (n > 0) {
            JSONObject c = cards.optJSONObject(selected);
            JSONObject p = Repo.cardProduct(c.optString("productId"));
            LinearLayout meta = Ui.sec(col);
            Ui.secHeader(meta, Repo.cardName(c), "수정", v -> {
                Ui.Sheet e = Ui.sheet(a, "카드 정보 수정", null);
                Sheets.cardForm(a, e, selected);
                e.show();
            });
            Ui.text(meta, Repo.issuerName(c.optString("issuer")) + " · 끝자리 " + c.optString("last4")
                + (p != null && "check".equals(p.optString("kind")) ? " · 체크카드" : " · 신용카드")
                + (p != null && !p.optBoolean("verified") ? " · 혜택 예시" : ""), 13, false, Ui.INK3);
            Sheets.cardFacts(a, meta, c, p);
        } else {
            LinearLayout empty = Ui.sec(col);
            Ui.text(empty, "등록한 카드가 없어요", 16, true, Ui.INK);
            Ui.text(empty, "결제 알림의 카드 끝자리로 어떤 카드로 결제했는지 알아봐요. 쓰는 카드를 추가해 주세요.", 13, false, Ui.INK3);
            Ui.button(empty, "카드 추가", true, v -> Sheets.add(a, "card"));
        }

        // 멤버십·바코드
        LinearLayout ms = Ui.sec(col);
        Ui.secHeader(ms, "멤버십·바코드", "+ 추가", v -> Sheets.add(a, "membership"));
        JSONArray mems = Repo.wallet.optJSONArray("memberships");
        for (int i = 0; mems != null && i < mems.length(); i++) Parts.membershipRow(a, ms, mems.optJSONObject(i), i);
        if (mems == null || mems.length() == 0) Ui.text(ms, "멤버십·통신사 바코드를 추가하면 재결제 때 바로 보여 줄 수 있어요.", 13, false, Ui.INK3);

        // 나에게 해당하는 조건
        LinearLayout facts = Ui.sec(col);
        Ui.secHeader(facts, "나에게 해당하는 조건", null, null);
        Ui.text(facts, "누르면 추가된 이유와 바꾸는 방법을 볼 수 있어요.", 12.5f, false, Ui.INK3);
        LinearLayout wrap = Ui.vbox(a);
        LinearLayout line = null;
        for (int i = 0; i < Sheets.CONDITIONS.length; i++) {
            if (i % 2 == 0) { line = Ui.hbox(a); wrap.addView(line); }
            String[] def = Sheets.CONDITIONS[i];
            Ui.pill(line, Sheets.conditionValue(def[0]) + " · " + def[2], false, v -> Sheets.condition(a, def));
        }
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 8);
        facts.addView(wrap, lp);
        Ui.note(col, "혜택 이름·비율·한도는 예시 데이터예요 (카드 약관 확인 전).");
    }
}
