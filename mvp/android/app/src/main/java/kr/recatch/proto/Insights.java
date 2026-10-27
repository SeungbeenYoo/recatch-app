package kr.recatch.proto;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 서버가 준 추천 목록·지갑으로 분석 화면 숫자를 계산 (웹 시연의 소비 분석과 같은 항목). */
final class Insights {
    private Insights() {}

    static ZonedDateTime local(String iso) { return Instant.parse(iso).atZone(ZoneId.systemDefault()); }

    static String month(JSONObject rec) {
        ZonedDateTime t = local(rec.optString("at"));
        return String.format("%04d-%02d", t.getYear(), t.getMonthValue());
    }

    static String currentMonth() {
        return Repo.statement != null ? Repo.statement.optString("month") : java.time.YearMonth.now().toString();
    }

    static List<JSONObject> list(String month) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = Repo.recs;
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject r = a.optJSONObject(i);
            if (month == null || month(r).equals(month)) out.add(r);
        }
        return out;
    }

    static boolean saved(JSONObject r) { String s = r.optString("status"); return s.equals("done") || s.equals("pending"); }
    static boolean lost(JSONObject r) { String s = r.optString("status"); return s.equals("missed") || s.equals("abandoned") || s.equals("double_paid"); }

    /** 재결제 성공률 (안내 중·추가 혜택 없음 제외) */
    static int successRate(List<JSONObject> rs) {
        int ok = 0, all = 0;
        for (JSONObject r : rs) { if (saved(r)) { ok++; all++; } else if (lost(r)) all++; }
        return all == 0 ? 0 : Math.round(ok * 100f / all);
    }

    static long missedSum(List<JSONObject> rs) {
        long s = 0;
        for (JSONObject r : rs) if (r.optString("status").equals("missed")) s += r.optLong("delta");
        return s;
    }

    static int count(List<JSONObject> rs, String status) {
        int n = 0;
        for (JSONObject r : rs) if (r.optString("status").equals(status)) n++;
        return n;
    }

    /** 업종별 아낀 금액 (완료+확인 중), 큰 순 */
    static List<Map.Entry<String, Long>> byCategory(List<JSONObject> rs) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (JSONObject r : rs) if (saved(r)) m.merge(Repo.categoryName(r.optString("category")), r.optLong("delta"), Long::sum);
        return sorted(m);
    }

    /** 혜택별 아낀 금액 */
    static List<Map.Entry<String, Long>> byBenefit(List<JSONObject> rs) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (JSONObject r : rs) if (saved(r)) m.merge(Repo.benefitLabel(r), r.optLong("delta"), Long::sum);
        return sorted(m);
    }

    /** 월별 되찾은 돈 (완료 건), 최근 6개월 */
    static List<Map.Entry<String, Long>> byMonth() {
        TreeMap<String, Long> m = new TreeMap<>();
        m.put(currentMonth(), 0L);
        for (JSONObject r : list(null)) if (r.optString("status").equals("done")) m.merge(month(r), r.optLong("delta"), Long::sum);
        List<Map.Entry<String, Long>> all = new ArrayList<>(m.entrySet());
        return all.subList(Math.max(0, all.size() - 6), all.size());
    }

    static String timeBlock(int h) {
        if (h >= 6 && h < 11) return "오전";
        if (h >= 11 && h < 14) return "점심";
        if (h >= 14 && h < 18) return "오후";
        if (h >= 18 && h < 22) return "저녁";
        return "밤";
    }

    private static final String[] DOW = {"월", "화", "수", "목", "금", "토", "일"};

    /** 자주 놓치는 순간: 요일·시간대별 놓친 혜택 (큰 순) */
    static List<Map.Entry<String, Long>> missedMoments(List<JSONObject> rs) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (JSONObject r : rs) {
            if (!lost(r)) continue;
            ZonedDateTime t = local(r.optString("at"));
            String key = DOW[t.getDayOfWeek().getValue() - 1] + "요일 " + timeBlock(t.getHour()) + " " + Repo.categoryName(r.optString("category"));
            m.merge(key, r.optLong("delta"), Long::sum);
        }
        return sorted(m);
    }

    static long totalOf(List<Map.Entry<String, Long>> l) {
        long s = 0;
        for (Map.Entry<String, Long> e : l) s += e.getValue();
        return s;
    }

    private static List<Map.Entry<String, Long>> sorted(Map<String, Long> m) {
        List<Map.Entry<String, Long>> l = new ArrayList<>(m.entrySet());
        l.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return l;
    }

    /** 이번 주 챙길 혜택: 전월 실적 남은 금액, 월 한도 거의 다 씀, 자주 놓치는 순간 */
    static List<String[]> todos() {
        List<String[]> out = new ArrayList<>(); // {제목, 설명, 이동할 탭}
        JSONArray cards = Repo.wallet == null ? null : Repo.wallet.optJSONArray("cards");
        for (int i = 0; cards != null && i < cards.length(); i++) {
            JSONObject c = cards.optJSONObject(i);
            JSONObject p = Repo.cardProduct(c.optString("productId"));
            if (p == null) continue;
            long need = p.optLong("prevMonthMin");
            if (need > 0 && !c.isNull("monthSpend") && c.optLong("monthSpend") < need) {
                out.add(new String[]{p.optString("name") + " 실적 " + Ui.won(need - c.optLong("monthSpend")) + " 남음",
                    "채우면 다음 달에도 " + firstBenefit(p) + " 혜택을 받아요", "wallet"});
            }
            JSONArray bs = p.optJSONArray("benefits");
            JSONObject usage = c.optJSONObject("usage");
            for (int k = 0; bs != null && k < bs.length(); k++) {
                JSONObject b = bs.optJSONObject(k);
                if (b.isNull("monthlyCap") || usage == null) continue;
                long cap = b.optLong("monthlyCap"), used = usage.optLong(b.optString("id"));
                if (cap > 0 && used >= cap * 0.8) {
                    out.add(new String[]{b.optString("label") + " 한도 " + Ui.won(Math.max(0, cap - used)) + " 남음",
                        "이번 달 한도를 거의 썼어요. 다른 카드 혜택을 먼저 찾아드려요", "wallet"});
                }
            }
        }
        List<Map.Entry<String, Long>> moments = missedMoments(list(currentMonth()));
        if (!moments.isEmpty()) {
            out.add(new String[]{moments.get(0).getKey() + "엔 더 챙겨 보세요",
                "이번 달 놓친 혜택 " + Ui.won(moments.get(0).getValue()) + "이 이때 생겼어요", "stats"});
        }
        return out;
    }

    private static String firstBenefit(JSONObject p) {
        JSONArray bs = p.optJSONArray("benefits");
        return bs != null && bs.length() > 0 ? bs.optJSONObject(0).optString("label") : "카드";
    }
}
