package kr.recatch.proto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 재결제 체험: 실제 결제 없이 카드 승인 "문자"를 폰에서 처리해 서버로 보낸다.
 * 알림 리스너가 받는 것과 같은 경로(폰 파싱 → 서버 계산 → 알림)를 탄다.
 */
final class Demo {
    private Demo() {}
    private static int next = 0;

    // {카드사, 가맹점(문자 표기), 금액}
    private static final String[][] SCENARIOS = {
        {"hd", "스타벅스영남대정", "13000"},
        {"hd", "파리바게뜨경산영대", "18000"},
        {"kb", "빕스동대구역점", "74000"},
        {"hd", "맘스터치영남대점", "9800"}
    };

    static String now() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")); }

    static String issuerLabel(String issuer) { return Repo.issuerName(issuer); }

    /** 문자 한 통 (카드사 공통 형식: "현대카드(5530)승인 김*캐 13,000원 일시불 10/16 12:30 가맹점") */
    static String message(String issuer, String last4, String type, long amount, String merchant) {
        return "[Web발신]\n" + issuerLabel(issuer) + "(" + last4 + ")" + type + " 김*캐 " + Ui.num(amount) + "원"
            + ("승인".equals(type) ? " 일시불" : "") + " " + now() + " " + merchant;
    }

    static void start(MainActivity a) {
        JSONArray cards = Repo.wallet == null ? null : Repo.wallet.optJSONArray("cards");
        if (cards == null || cards.length() == 0) { Ui.toast(a, "지갑에 카드를 먼저 추가해 주세요"); return; }
        for (int k = 0; k < SCENARIOS.length; k++) {
            String[] s = SCENARIOS[(next + k) % SCENARIOS.length];
            JSONObject card = cardOf(cards, s[0]);
            if (card == null) continue;
            next = (next + k + 1) % SCENARIOS.length;
            EventSender.submit(a, message(s[0], card.optString("last4"), "승인", Long.parseLong(s[2]), s[1]));
            Ui.toast(a, Repo.issuerName(s[0]) + " " + Ui.won(Long.parseLong(s[2])) + " 결제 문자를 처리했어요");
            return;
        }
        Ui.toast(a, "체험하려면 지갑에 현대카드나 KB국민카드가 있어야 해요");
    }

    private static JSONObject cardOf(JSONArray cards, String issuer) {
        for (int i = 0; i < cards.length(); i++) if (issuer.equals(cards.optJSONObject(i).optString("issuer"))) return cards.optJSONObject(i);
        return null;
    }

    /** 추천대로 "취소 → 재결제" 했을 때 오는 두 문자를 흉내 낸다 (발표·시연용). */
    static void simulateRepay(android.content.Context c, JSONObject rec) {
        JSONArray cards = Repo.wallet.optJSONArray("cards");
        JSONObject orig = byLast4(cards, rec.optJSONObject("baseline").optString("cardLast4"));
        JSONObject best = byLast4(cards, rec.optJSONObject("best").optString("cardLast4"));
        if (orig == null || best == null) { Ui.toast(c, "지갑에서 카드를 찾지 못했어요"); return; }
        String merchant = rec.optString("store") + (rec.isNull("branch") ? "" : rec.optString("branch"));
        long amount = rec.optLong("amount");
        long repay = amount - rec.optJSONObject("best").optLong("atPOS");
        EventSender.submit(c, message(orig.optString("issuer"), orig.optString("last4"), "취소", amount, merchant));
        EventSender.submit(c, message(best.optString("issuer"), best.optString("last4"), "승인", repay, merchant));
    }

    private static JSONObject byLast4(JSONArray cards, String last4) {
        for (int i = 0; cards != null && i < cards.length(); i++) if (last4.equals(cards.optJSONObject(i).optString("last4"))) return cards.optJSONObject(i);
        return null;
    }
}
