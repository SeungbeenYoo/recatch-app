package kr.recatch.proto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 서버에서 받은 화면 데이터(카탈로그·지갑·추천 목록·명세서)를 들고 있다. 모든 화면이 이걸 그린다. */
final class Repo {
    interface Done { void run(String error); }

    static JSONObject catalog;
    static JSONObject wallet;
    static JSONArray recs = new JSONArray();
    static JSONObject statement;
    static String lastError;

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Repo() {}

    static boolean ready() { return wallet != null && catalog != null && statement != null; }

    static void refresh(Context ctx, Done done) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            String err = null;
            try {
                Api api = new Api(app);
                if (Prefs.token(app) == null) throw new Api.ApiException(401, "not_registered", "서버에 연결해 주세요 (설정)");
                if (catalog == null) catalog = api.catalog();
                JSONObject w = api.wallet();
                JSONArray r = api.recommendations();
                JSONObject s = api.statement();
                wallet = w; recs = r; statement = s;
            } catch (Exception e) {
                err = e.getMessage();
            }
            lastError = err;
            String e2 = err;
            MAIN.post(() -> done.run(e2));
        });
    }

    /** 지갑 저장. 실패하면 서버의 사용자용 문구를 돌려준다. */
    static void saveWallet(Context ctx, JSONObject w, Done done) {
        Context app = ctx.getApplicationContext();
        IO.execute(() -> {
            String err = null;
            try { wallet = new Api(app).putWallet(w); } catch (Api.ApiException e) {
                err = e.userMessage != null ? e.userMessage : walletError(e.code);
            } catch (Exception e) { err = "서버에 연결하지 못했어요: " + e.getMessage(); }
            String e2 = err;
            MAIN.post(() -> done.run(e2));
        });
    }

    static String walletError(String code) {
        switch (code) {
            case "duplicate_last4": return "같은 끝자리 카드가 이미 있어요";
            case "duplicate_code": return "이미 등록된 바코드 번호예요";
            case "duplicate_membership": return "이미 등록된 멤버십이에요";
            case "invalid_last4": return "카드 끝 4자리를 숫자로 입력해 주세요";
            case "invalid_spend": return "실적 금액을 확인해 주세요";
            case "invalid_name": return "이름을 1~40자로 입력해 주세요";
            case "code_required": return "바코드 번호를 입력해 주세요";
            case "invalid_tier": return "등급을 선택해 주세요";
            default: return "저장하지 못했어요 (" + code + ")";
        }
    }

    /** 지갑을 고칠 때 쓰는 깊은 복사본 */
    static JSONObject walletCopy() {
        try { return new JSONObject(wallet.toString()); } catch (JSONException e) { return new JSONObject(); }
    }

    static JSONObject cardProduct(String id) { return find(catalog.optJSONArray("cards"), id); }
    static JSONObject membershipProduct(String id) { return find(catalog.optJSONArray("memberships"), id); }

    static JSONObject find(JSONArray a, String id) {
        if (a == null || id == null) return null;
        for (int i = 0; i < a.length(); i++) if (id.equals(a.optJSONObject(i).optString("id"))) return a.optJSONObject(i);
        return null;
    }

    /** 멤버십 표시 이름: 카탈로그 상품명 또는 직접 입력한 이름 (+등급) */
    static String membershipName(JSONObject m) {
        JSONObject p = membershipProduct(m.optString("productId", null));
        String name = p != null ? p.optString("name") : m.optString("name", "멤버십");
        return m.has("tier") ? name + " " + m.optString("tier") : name;
    }

    static String membershipKind(JSONObject m) {
        JSONObject p = membershipProduct(m.optString("productId", null));
        return p != null ? p.optString("kind") : m.optString("kind", "membership");
    }

    static String cardName(JSONObject card) {
        JSONObject p = cardProduct(card.optString("productId"));
        return p != null ? p.optString("name") : "카드";
    }

    static String issuerName(String issuer) {
        switch (issuer) {
            case "kb": return "KB국민카드";
            case "sh": return "신한카드";
            case "hd": return "현대카드";
            case "ss": return "삼성카드";
            case "lt": return "롯데카드";
            case "hn": return "하나카드";
            case "wr": return "우리카드";
            case "nh": return "NH농협카드";
            case "bc": return "BC카드";
            default: return issuer;
        }
    }

    static String categoryName(String cat) {
        JSONObject cats = catalog == null ? null : catalog.optJSONObject("categories");
        return cats != null && cats.has(cat) ? cats.optString(cat) : "기타";
    }

    static JSONObject recById(int id) {
        for (int i = 0; i < recs.length(); i++) if (recs.optJSONObject(i).optInt("id") == id) return recs.optJSONObject(i);
        return null;
    }

    /** 추천의 대표 혜택 이름 (멤버십 혜택 우선) */
    static String benefitLabel(JSONObject rec) {
        JSONObject best = rec.optJSONObject("best");
        if (best == null) return "";
        JSONObject mem = best.optJSONObject("membership");
        if (mem != null) return mem.optString("name") + " " + mem.optString("label");
        // org.json의 optString은 JSON null을 "null" 문자열로 돌려주므로 isNull로 먼저 거른다
        return best.optString("cardName") + (best.isNull("cardBenefit") ? "" : " " + best.optString("cardBenefit"));
    }
}
