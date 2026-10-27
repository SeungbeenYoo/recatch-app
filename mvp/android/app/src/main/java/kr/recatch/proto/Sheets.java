package kr.recatch.proto;

import android.content.Intent;
import android.graphics.Typeface;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;

/** 웹 시연의 시트들: 정산 명세서, 설정, 지갑에 추가, 카드 정보·수정, 멤버십 바코드, 조건 변경 */
final class Sheets {
    private Sheets() {}

    // ---------------- 정산 명세서 ----------------
    static void statement(MainActivity a) {
        JSONObject st = Repo.statement;
        if (st == null) return;
        int mo = Integer.parseInt(st.optString("month").substring(5));
        LocalDate due = LocalDate.parse(st.optString("dueDate"));
        Ui.Sheet s = Ui.sheet(a, mo + "월 정산 명세서", "재결제로 원래 결제보다 더 아낀 금액에서만 20%를 받아요. 아낀 게 없으면 수수료도 0원이에요.");
        LinearLayout slip = Ui.vbox(a);
        slip.setBackground(Ui.round(a, Ui.WELL, 16));
        slip.setPadding(Ui.dp(a, 16), Ui.dp(a, 14), Ui.dp(a, 16), Ui.dp(a, 14));
        Ui.line(slip, "되찾은 돈", Ui.won(st.optLong("recovered")), Ui.INK);
        Ui.line(slip, "성공 수수료 20%", Ui.won(st.optLong("fee")), Ui.INK);
        if (st.optLong("coupon") > 0) Ui.line(slip, "첫 달 할인 쿠폰", "−" + Ui.won(st.optLong("coupon")), Ui.SAVE);
        if (st.optLong("subscription") > 0) Ui.line(slip, "프리미엄 구독", Ui.won(st.optLong("subscription")), Ui.INK);
        if (st.optLong("carriedIn") > 0) Ui.line(slip, "지난달에서 넘어온 금액", Ui.won(st.optLong("carriedIn")), Ui.INK);
        boolean billed = st.optBoolean("billed");
        String totalLabel = billed ? due.getMonthValue() + "월 " + due.getDayOfMonth() + "일 청구 예정"
            : st.optLong("totalDue") == 0 ? "이번 달 청구할 금액이 없어요" : "1만 원이 안 돼 다음 달로 넘겨요";
        Ui.total(slip, totalLabel,
            Ui.won(st.optLong("totalDue")));
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(a, 12);
        s.content.addView(slip, lp);
        Ui.text(s.content, "수수료를 내고도 " + Ui.won(st.optLong("net")) + "이 남아요. 누적 수수료가 " + Ui.won(st.optLong("threshold")) + "을 넘으면 그때 한 번에 청구해요.", 13.5f, false, Ui.INK2)
            .setPadding(0, Ui.dp(a, 12), 0, 0);
        if (st.optLong("pending") > 0) Ui.text(s.content, "확인 중 " + Ui.won(st.optLong("pending")) + "은 카드사 확정 후 다음 명세서에 반영돼요.", 12.5f, false, Ui.INK3);
        Ui.button(s.content, "확인", true, v -> s.dismiss());
        s.show();
    }

    // ---------------- 설정 ----------------
    static void settings(MainActivity a) {
        Ui.Sheet s = Ui.sheet(a, "설정", Prefs.token(a) != null ? "이 기기는 서버에 등록돼 있어요." : "서버에 연결하면 실제 계산 결과가 표시돼요.");

        Ui.text(s.content, "서버 연결", 15, true, Ui.INK).setPadding(0, Ui.dp(a, 14), 0, 0);
        EditText url = Ui.field(s.content, "서버 주소", "http://192.168.0.10:8787", InputType.TYPE_TEXT_VARIATION_URI);
        url.setText(Prefs.serverUrl(a));
        EditText invite = Ui.field(s.content, "초대 코드 (서버에 설정한 경우만)", "", InputType.TYPE_CLASS_TEXT);
        TextView status = Ui.text(s.content, "", 13, false, Ui.INK3);
        Ui.button(s.content, "연결하고 등록", true, v -> {
            String u = url.getText().toString().trim();
            if (!u.startsWith("http://") && !u.startsWith("https://")) { status.setText("http:// 또는 https://로 시작해야 해요"); return; }
            boolean changed = !u.replaceAll("/+$", "").equals(Prefs.serverUrl(a));
            Prefs.setServerUrl(a, u);
            if (changed) { Prefs.setToken(a, null); Repo.catalog = null; }
            String code = invite.getText().toString().trim();
            status.setText("연결 중…");
            Executors.newSingleThreadExecutor().execute(() -> {
                String msg;
                try {
                    Api api = new Api(a);
                    api.health();
                    api.ensureRegistered(code);
                    msg = "연결됨 · 등록 완료";
                } catch (Exception e) { msg = "연결 실패: " + e.getMessage(); }
                String m = msg;
                a.runOnUiThread(() -> { status.setText(m); a.refresh(); });
            });
        });
        Ui.text(s.content, "같은 Wi-Fi: PC 서버가 알려 준 주소 · USB: adb reverse 후 http://127.0.0.1:8787 · 에뮬레이터: http://10.0.2.2:8787", 12, false, Ui.INK3);

        Ui.text(s.content, "알림", 15, true, Ui.INK).setPadding(0, Ui.dp(a, 18), 0, 0);
        Ui.text(s.content, "결제 알림을 켜면 문자앱·카드사 앱 알림 중 카드 승인·취소 알림만 폰 안에서 읽어요.\n"
            + "• 서버로 보내는 것: 승인/취소, 카드사, 카드 끝 4자리, 금액, 가맹점명, 시각, 누적 실적\n"
            + "• 보내지도 저장하지도 않는 것: 알림 원문, 이름, 전체 카드번호, 다른 앱 알림", 12.5f, false, Ui.INK2);
        Switch detect = Ui.switchRow(s.content, "결제 알림 실시간 감지", "카드 승인 문자·앱 알림을 읽어요", Prefs.consent(a) && a.listenerEnabled());
        detect.setOnCheckedChangeListener((b, on) -> {
            Prefs.setConsent(a, on);
            Ui.toast(a, on ? "설정에서 '리캐치 시제품'의 알림 접근을 켜 주세요" : "설정에서 알림 접근도 꺼 주세요");
            a.startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });
        Ui.switchRow(s.content, "재결제 가능 시간에만 알림", "결제 후 2분이 지나면 알림을 지워요", Prefs.windowOnly(a))
            .setOnCheckedChangeListener((b, on) -> Prefs.setWindowOnly(a, on));
        Ui.switchRow(s.content, "바코드 열 때 화면 최대 밝기", "스캐너가 한 번에 읽도록", Prefs.maxBrightness(a))
            .setOnCheckedChangeListener((b, on) -> Prefs.setMaxBrightness(a, on));
        Ui.switchRow(s.content, "정산일 미리 알림", "청구 3일 전, 앱을 열 때 알려드려요", Prefs.billingReminder(a))
            .setOnCheckedChangeListener((b, on) -> Prefs.setBillingReminder(a, on));
        Ui.text(s.content, "APK로 직접 설치했고 Android 13 이상에서 알림 접근이 막히면: 설정 → 앱 → 리캐치 시제품 → ⋮ → 제한된 설정 허용", 12, false, Ui.INK3);

        Ui.text(s.content, "테스트 문자 (개발·시연용)", 15, true, Ui.INK).setPadding(0, Ui.dp(a, 18), 0, 0);
        Ui.text(s.content, "실제 결제 없이 같은 경로(폰 파싱 → 서버 계산 → 알림)로 처리해요. 시각은 지금으로 채워져요.", 12.5f, false, Ui.INK3);
        EditText test = Ui.field(s.content, "알림 내용", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        test.setMinLines(4);
        test.setGravity(Gravity.TOP);
        LinearLayout r1 = Ui.hbox(a), r2 = Ui.hbox(a);
        s.content.addView(r1);
        s.content.addView(r2);
        preset(r1, test, "현대 스타벅스 승인", "[현대카드]승인\n김*캐\n13,000원 일시불\n%s\n스타벅스영남대정");
        preset(r1, test, "현대 취소", "[현대카드]취소\n김*캐\n13,000원\n%s\n스타벅스영남대정");
        preset(r2, test, "KB 재결제", "[Web발신]\nKB국민체크(9421)\n김*캐님\n%s\n13,000원\n스타벅스영남대정 사용");
        preset(r2, test, "KB 빕스 승인", "[Web발신]\nKB국민체크(9421)\n김*캐님\n%s\n74,000원\n빕스동대구역점 사용");
        Ui.button(s.content, "이 문자 처리하기", false, v -> {
            boolean ok = EventSender.submit(a, test.getText().toString());
            Ui.toast(a, ok ? "폰에서 파싱 완료 → 서버로 보내는 중" : "카드 승인·취소 알림으로 인식하지 못했어요");
        });
        int out = Prefs.outbox(a).length();
        if (out > 0) Ui.text(s.content, "서버로 못 보낸 결제 " + out + "건 (다음 전송 때 다시 보내요)", 12.5f, false, Ui.WARN);
        Ui.button(s.content, "닫기", false, v -> s.dismiss());
        s.dialog.setOnDismissListener(d -> a.updateDetect());
        s.show();
    }

    private static void preset(LinearLayout row, EditText target, String label, String template) {
        Ui.button(row, label, false, v -> target.setText(String.format(template, Demo.now()))).setTextSize(12);
    }

    // ---------------- 지갑에 추가 ----------------
    static final String[] KINDS = {"membership", "card", "telecom"};
    static final String[] KIND_NAMES = {"멤버십", "카드", "통신사"};

    static void add(MainActivity a, String kind) {
        if (!Repo.ready()) { settings(a); return; }
        Ui.Sheet s = Ui.sheet(a, "혜택 지갑에 추가", "등록할수록 결제 직후 찾아드리는 혜택이 정확해져요.");
        int k = java.util.Arrays.asList(KINDS).indexOf(kind);
        Ui.seg(s.content, KIND_NAMES, k, i -> { s.dismiss(); add(a, KINDS[i]); });
        if (kind.equals("card")) cardForm(a, s, -1);
        else membershipForm(a, s, -1, kind);
        s.show();
    }

    /** 카드 추가(index=-1) / 수정 */
    static void cardForm(MainActivity a, Ui.Sheet s, int index) {
        JSONArray products = Repo.catalog.optJSONArray("cards");
        JSONObject cur = index >= 0 ? Repo.wallet.optJSONArray("cards").optJSONObject(index) : null;
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < products.length(); i++) {
            JSONObject p = products.optJSONObject(i);
            names.add(Repo.issuerName(p.optString("issuer")) + " · " + p.optString("name"));
            if (cur != null && p.optString("id").equals(cur.optString("productId"))) sel = i;
        }
        Spinner product = Ui.spinner(s.content, "카드 상품", names, sel);
        EditText last4 = Ui.field(s.content, "카드 끝 4자리", "승인 문자에 나오는 끝자리", InputType.TYPE_CLASS_NUMBER);
        last4.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        EditText prev = Ui.field(s.content, "전월 실적 (원)", "예: 300000", InputType.TYPE_CLASS_NUMBER);
        EditText month = Ui.field(s.content, "이번 달 사용액 (원, 선택)", "모르면 비워 두세요. 누적 금액이 오는 문자는 자동으로 맞춰요", InputType.TYPE_CLASS_NUMBER);
        if (cur != null) {
            last4.setText(cur.optString("last4"));
            prev.setText(String.valueOf(cur.optLong("prevMonthSpend")));
            if (!cur.isNull("monthSpend")) month.setText(String.valueOf(cur.optLong("monthSpend")));
        }
        // 월 한도가 있는 혜택: 이번 달 이미 받은 금액
        LinearLayout usageBox = Ui.vbox(a);
        s.content.addView(usageBox);
        List<EditText> usageFields = new ArrayList<>();
        List<String> usageIds = new ArrayList<>();
        Runnable buildUsage = () -> {
            usageBox.removeAllViews();
            usageFields.clear();
            usageIds.clear();
            JSONObject p = products.optJSONObject(product.getSelectedItemPosition());
            JSONArray bs = p.optJSONArray("benefits");
            JSONObject usage = cur != null && cur.optString("productId").equals(p.optString("id")) ? cur.optJSONObject("usage") : null;
            for (int i = 0; bs != null && i < bs.length(); i++) {
                JSONObject b = bs.optJSONObject(i);
                if (b.isNull("monthlyCap")) continue;
                EditText e = Ui.field(usageBox, b.optString("label") + " · 이번 달 받은 금액 (한도 " + Ui.won(b.optLong("monthlyCap")) + ")", "0", InputType.TYPE_CLASS_NUMBER);
                if (usage != null && usage.has(b.optString("id"))) e.setText(String.valueOf(usage.optLong(b.optString("id"))));
                usageFields.add(e);
                usageIds.add(b.optString("id"));
            }
        };
        buildUsage.run();
        product.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { buildUsage.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        TextView err = Ui.text(s.content, "", 13, true, Ui.MISS);
        Ui.button(s.content, index >= 0 ? "변경 저장" : "지갑에 추가", true, v -> {
            String l4 = last4.getText().toString().trim();
            if (!l4.matches("\\d{4}")) { err.setText("카드 끝 4자리를 숫자로 입력해 주세요"); return; }
            try {
                JSONObject c = new JSONObject();
                c.put("productId", products.optJSONObject(product.getSelectedItemPosition()).optString("id"));
                c.put("last4", l4);
                c.put("prevMonthSpend", parseLong(prev, 0));
                String ms = month.getText().toString().trim();
                c.put("monthSpend", ms.isEmpty() ? JSONObject.NULL : Long.parseLong(ms));
                JSONObject usage = new JSONObject();
                for (int i = 0; i < usageFields.size(); i++) {
                    long u = parseLong(usageFields.get(i), 0);
                    if (u > 0) usage.put(usageIds.get(i), u);
                }
                c.put("usage", usage);
                JSONObject w = Repo.walletCopy();
                JSONArray cards = w.optJSONArray("cards");
                if (index >= 0) cards.put(index, c); else cards.put(c);
                save(a, s, w, err, index >= 0 ? "카드 정보를 고쳤어요" : "카드를 추가했어요");
            } catch (NumberFormatException | JSONException e) {
                err.setText("금액은 숫자로 입력해 주세요");
            }
        });
        if (index >= 0) Ui.button(s.content, "이 카드 삭제", false, v -> {
            JSONObject w = Repo.walletCopy();
            w.optJSONArray("cards").remove(index);
            save(a, s, w, err, "카드를 지웠어요");
        });
    }

    /** 멤버십·통신사 추가(index=-1) / 수정 */
    static void membershipForm(MainActivity a, Ui.Sheet s, int index, String kind) {
        JSONArray all = Repo.catalog.optJSONArray("memberships");
        JSONObject cur = index >= 0 ? Repo.wallet.optJSONArray("memberships").optJSONObject(index) : null;
        boolean telecom = kind.equals("telecom");
        List<JSONObject> options = new ArrayList<>();
        List<String> names = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject p = all.optJSONObject(i);
            if (telecom != p.optString("kind").equals("telecom")) continue;
            // 이미 지갑에 있는 멤버십은 추가 목록에서 뺀다 (지금 고치는 항목은 남김)
            boolean owned = false;
            JSONArray mine = Repo.wallet.optJSONArray("memberships");
            for (int j = 0; mine != null && j < mine.length(); j++) {
                if (j != index && p.optString("id").equals(mine.optJSONObject(j).optString("productId"))) owned = true;
            }
            if (owned) continue;
            if (cur != null && p.optString("id").equals(cur.optString("productId", ""))) sel = options.size();
            options.add(p);
            names.add(p.optString("name") + (p.optBoolean("verified") ? "" : " (혜택 예시)"));
        }
        options.add(null);
        names.add("직접 입력 (혜택 정보 없음 · 바코드만)");
        if (cur != null && cur.isNull("productId")) sel = options.size() - 1;

        Spinner product = Ui.spinner(s.content, telecom ? "통신사 멤버십" : "멤버십", names, sel);
        LinearLayout dyn = Ui.vbox(a);
        s.content.addView(dyn);
        EditText name = Ui.field(s.content, "이름", "예: 해피포인트, 투썸 멤버십", InputType.TYPE_CLASS_TEXT);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        JSONObject formats = Repo.catalog.optJSONObject("barcodeFormats");
        List<String> fKeys = new ArrayList<>(), fNames = new ArrayList<>();
        for (Iterator<String> it = formats.keys(); it.hasNext(); ) { String key = it.next(); fKeys.add(key); fNames.add(formats.optString(key)); }
        Spinner format = Ui.spinner(s.content, "바코드 형식", fNames, Math.max(0, fKeys.indexOf(cur != null ? cur.optString("format", "numeric") : "numeric")));
        EditText code = Ui.field(s.content, "바코드 번호", "앱에 표시된 바코드 번호", InputType.TYPE_CLASS_NUMBER);
        if (cur != null) {
            if (cur.has("code")) code.setText(cur.optString("code"));
            if (cur.isNull("productId")) name.setText(cur.optString("name"));
        }
        Ui.text(s.content, "앱에 표시된 번호를 입력해 주세요. 공백·하이픈은 자동으로 정리해요. 멤버십 번호에는 상품용 바코드(EAN·UPC)의 검증 규칙을 적용하지 않아요.", 12.5f, false, Ui.INK3);

        Spinner[] tier = new Spinner[1];
        Runnable onProduct = () -> {
            dyn.removeAllViews();
            JSONObject p = options.get(product.getSelectedItemPosition());
            ((View) name.getParent()).setVisibility(p == null ? View.VISIBLE : View.GONE);
            tier[0] = null;
            if (p != null && p.optJSONArray("tiers") != null) {
                JSONArray ts = p.optJSONArray("tiers");
                List<String> tl = new ArrayList<>();
                for (int i = 0; i < ts.length(); i++) tl.add(ts.optString(i));
                tier[0] = Ui.spinner(dyn, "등급", tl, Math.max(0, tl.indexOf(cur != null ? cur.optString("tier") : "")));
            }
            if (p != null) Ui.text(dyn, "혜택: " + join(p.optJSONArray("benefits")), 12.5f, false, Ui.INK3);
        };
        onProduct.run();
        product.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { onProduct.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });

        TextView err = Ui.text(s.content, "", 13, true, Ui.MISS);
        Ui.button(s.content, index >= 0 ? "변경 저장" : "지갑에 추가", true, v -> {
            try {
                JSONObject p = options.get(product.getSelectedItemPosition());
                JSONObject m = new JSONObject();
                if (p != null) {
                    m.put("productId", p.optString("id"));
                    if (tier[0] != null) m.put("tier", tier[0].getSelectedItem().toString());
                } else {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { err.setText("이름을 입력해 주세요"); return; }
                    m.put("productId", JSONObject.NULL);
                    m.put("name", n);
                    m.put("kind", telecom ? "telecom" : "membership");
                }
                String c = code.getText().toString().trim();
                if (!c.isEmpty()) { m.put("code", c); m.put("format", fKeys.get(format.getSelectedItemPosition())); }
                JSONObject w = Repo.walletCopy();
                JSONArray ms = w.optJSONArray("memberships");
                if (index >= 0) ms.put(index, m); else ms.put(m);
                save(a, s, w, err, index >= 0 ? "멤버십을 고쳤어요" : "지갑에 추가했어요");
            } catch (JSONException e) { err.setText(e.getMessage()); }
        });
        if (index >= 0) Ui.button(s.content, "이 멤버십 삭제", false, v -> {
            JSONObject w = Repo.walletCopy();
            w.optJSONArray("memberships").remove(index);
            save(a, s, w, err, "멤버십을 지웠어요");
        });
    }

    private static String join(JSONArray a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; a != null && i < a.length(); i++) sb.append(i > 0 ? " · " : "").append(a.optString(i));
        return sb.length() == 0 ? "제휴 혜택 확인 전" : sb.toString();
    }

    private static long parseLong(EditText e, long dflt) {
        String t = e.getText().toString().trim();
        return t.isEmpty() ? dflt : Long.parseLong(t);
    }

    private static void save(MainActivity a, Ui.Sheet s, JSONObject w, TextView err, String ok) {
        err.setText("저장 중…");
        Repo.saveWallet(a, w, e -> {
            if (e != null) { err.setText(e); return; }
            s.dismiss();
            Ui.toast(a, ok);
            a.refresh();
        });
    }

    // ---------------- 카드 정보 ----------------
    static void cardDetail(MainActivity a, int index) {
        JSONObject c = Repo.wallet.optJSONArray("cards").optJSONObject(index);
        JSONObject p = Repo.cardProduct(c.optString("productId"));
        Ui.Sheet s = Ui.sheet(a, Repo.cardName(c), Repo.issuerName(c.optString("issuer")) + " · 끝자리 " + c.optString("last4"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 170));
        lp.topMargin = Ui.dp(a, 10);
        s.content.addView(Parts.cardTile(a, c, false), lp);
        cardFacts(a, s.content, c, p);
        Ui.text(s.content, "결제는 실물 카드나 카드사 앱·페이로 해요. 리캐치는 카드 결제 바코드를 만들지 않아요.", 12.5f, false, Ui.INK3)
            .setPadding(0, Ui.dp(a, 10), 0, 0);
        LinearLayout row = Ui.hbox(a);
        s.content.addView(row);
        Ui.button(row, "정보 수정", true, v -> { s.dismiss(); Ui.Sheet e = Ui.sheet(a, "카드 정보 수정", null); cardForm(a, e, index); e.show(); });
        Ui.button(row, "닫기", false, v -> s.dismiss());
        s.show();
    }

    /** 전월 실적·이번 달 사용·혜택 한도 (지갑 화면과 카드 시트에서 같이 씀) */
    static void cardFacts(MainActivity a, LinearLayout parent, JSONObject c, JSONObject p) {
        long need = p == null ? 0 : p.optLong("prevMonthMin");
        long prev = c.optLong("prevMonthSpend");
        if (need > 0) {
            Ui.bar(parent, "전월 실적 " + Ui.won(prev), prev >= need ? "충족 (" + Ui.won(need) + ")" : Ui.won(need - prev) + " 부족", prev / (float) need, prev >= need ? Ui.SAVE : Ui.WARN);
            if (prev < need) Ui.text(parent, "전월 실적이 모자라 이번 달 혜택이 적용되지 않아요.", 12.5f, false, Ui.WARN);
        } else Ui.line(parent, "전월 실적 조건", "없음", Ui.INK);
        if (!c.isNull("monthSpend")) {
            long ms = c.optLong("monthSpend");
            if (need > 0) Ui.bar(parent, "이번 달 사용 " + Ui.won(ms), ms >= need ? "다음 달 혜택 확보" : Ui.won(need - ms) + " 남음", ms / (float) need, ms >= need ? Ui.SAVE : Ui.BRAND);
            else Ui.line(parent, "이번 달 사용", Ui.won(ms), Ui.INK);
        }
        JSONArray bs = p == null ? null : p.optJSONArray("benefits");
        JSONObject usage = c.optJSONObject("usage");
        for (int i = 0; bs != null && i < bs.length(); i++) {
            JSONObject b = bs.optJSONObject(i);
            if (b.isNull("monthlyCap")) Ui.line(parent, b.optString("label"), "한도 없음", Ui.INK3);
            else {
                long cap = b.optLong("monthlyCap"), used = usage == null ? 0 : usage.optLong(b.optString("id"));
                Ui.bar(parent, b.optString("label"), Ui.won(used) + " / " + Ui.won(cap), used / (float) cap, Ui.SAVE);
            }
        }
    }

    // ---------------- 멤버십 바코드 ----------------
    static void pass(MainActivity a, int index) {
        JSONArray ms = Repo.wallet.optJSONArray("memberships");
        if (ms == null || ms.length() == 0) return;
        int i = Math.floorMod(index, ms.length());
        JSONObject m = ms.optJSONObject(i);
        Ui.Sheet s = Ui.sheet(a, null, null);
        LinearLayout head = Ui.hbox(a);
        TextView t = new TextView(a);
        t.setText("멤버십 바코드");
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Ui.INK);
        head.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView idx = new TextView(a);
        idx.setText((i + 1) + " / " + ms.length());
        idx.setTextColor(Ui.INK3);
        head.addView(idx);
        s.content.addView(head);

        LinearLayout card = Ui.vbox(a);
        card.setBackground(Ui.round(a, android.graphics.Color.WHITE, 18));
        card.setPadding(Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18), Ui.dp(a, 18));
        TextView nm = new TextView(a);
        nm.setText(Repo.membershipName(m));
        nm.setTextSize(18);
        nm.setTypeface(Typeface.DEFAULT_BOLD);
        nm.setTextColor(Ui.c("#191A1F"));
        card.addView(nm);
        if (m.has("code")) {
            BarcodeView bv = new BarcodeView(a, m.optString("code"), m.optString("format", "numeric"));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(a, 110));
            bl.topMargin = Ui.dp(a, 14);
            card.addView(bv, bl);
            TextView num = new TextView(a);
            num.setText(m.optString("code").replaceAll("(\\d{4})(?=\\d)", "$1 "));
            num.setTypeface(Typeface.MONOSPACE);
            num.setTextSize(16);
            num.setTextColor(Ui.c("#191A1F"));
            num.setGravity(Gravity.CENTER);
            num.setPadding(0, Ui.dp(a, 8), 0, 0);
            card.addView(num);
            TextView hint = new TextView(a);
            hint.setText("직원에게 이 화면을 보여 주세요");
            hint.setTextColor(Ui.c("#8B8F9C"));
            hint.setGravity(Gravity.CENTER);
            card.addView(hint);
            if (Prefs.maxBrightness(a) && s.dialog.getWindow() != null) {
                WindowManager.LayoutParams wl = s.dialog.getWindow().getAttributes();
                wl.screenBrightness = 1f;
                s.dialog.getWindow().setAttributes(wl);
                hint.setText("직원에게 이 화면을 보여 주세요 · 화면 밝기를 최대로 올렸어요");
            }
        } else {
            TextView none = new TextView(a);
            none.setText("바코드 번호가 아직 없어요. 수정에서 등록해 주세요.");
            none.setTextColor(Ui.c("#4E5260"));
            none.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 14));
            card.addView(none);
        }
        LinearLayout.LayoutParams cl = Ui.matchWrap();
        cl.topMargin = Ui.dp(a, 12);
        s.content.addView(card, cl);

        LinearLayout nav = Ui.hbox(a);
        s.content.addView(nav);
        if (ms.length() > 1) {
            Ui.button(nav, "‹ 이전", false, v -> { s.dismiss(); pass(a, i - 1); });
            Ui.button(nav, "다음 ›", false, v -> { s.dismiss(); pass(a, i + 1); });
        }
        LinearLayout row = Ui.hbox(a);
        s.content.addView(row);
        Ui.button(row, "수정", false, v -> {
            s.dismiss();
            Ui.Sheet e = Ui.sheet(a, "멤버십 수정", null);
            membershipForm(a, e, i, Repo.membershipKind(m).equals("telecom") ? "telecom" : "membership");
            e.show();
        });
        Ui.button(row, "닫기", true, v -> s.dismiss());
        s.show();
    }

    // ---------------- 나에게 해당하는 조건 ----------------
    static final String[][] CONDITIONS = {
        // key, 이름, 기본 설명, 안내, 선택지
        {"student", "학생 상태", "청년 우대", "현재 재학 상태를 선택해 저장하세요. 실제 학생 할인은 제휴사에서 학생증·재학 인증이 필요할 수 있어요.", "대학생|대학원생|해당 없음"},
        {"telecom", "통신사·등급", "통신사 우대", "가입한 통신사 앱에서 등급을 확인한 후 선택하세요. 등록된 바코드나 과거 결제 기록은 바뀌지 않아요.", ""},
        {"region", "주 활동 지역", "주 활동 지역", "주로 이용하는 지역으로 바꾸세요. 기타 지역을 고르면 직접 입력할 수 있어요.", "경산시|대구시|서울시|부산시|기타 지역|해당 없음"},
        {"age", "연령대", "연령 우대", "현재 연령대를 선택하세요. 추천용 프로필만 바꾸며 실제 제휴 자격 인증을 대신하지 않아요.", "10대|20대|30대|40대|50대|60대 이상|해당 없음"}
    };

    static String conditionValue(String key) {
        if (key.equals("telecom")) {
            JSONArray ms = Repo.wallet.optJSONArray("memberships");
            for (int i = 0; ms != null && i < ms.length(); i++) {
                JSONObject m = ms.optJSONObject(i);
                if (Repo.membershipKind(m).equals("telecom")) return Repo.membershipName(m);
            }
            return "해당 없음";
        }
        JSONObject p = Repo.wallet.optJSONObject("profile");
        return p != null && p.has(key) ? p.optString(key) : "해당 없음";
    }

    static void condition(MainActivity a, String[] def) {
        String key = def[0];
        Ui.Sheet s = Ui.sheet(a, def[1], "처음 값은 예시 지갑에서 온 값이에요. 조회해서 확인한 값이 아니에요.");
        Ui.text(s.content, def[3], 13.5f, false, Ui.INK2);
        List<String> opts = new ArrayList<>();
        List<String[]> telecomOpts = new ArrayList<>(); // {productId, tier}
        if (key.equals("telecom")) {
            JSONArray all = Repo.catalog.optJSONArray("memberships");
            for (int i = 0; i < all.length(); i++) {
                JSONObject p = all.optJSONObject(i);
                if (!p.optString("kind").equals("telecom") || p.optJSONArray("tiers") == null) continue;
                JSONArray ts = p.optJSONArray("tiers");
                for (int k = 0; k < ts.length(); k++) { opts.add(p.optString("name") + " " + ts.optString(k)); telecomOpts.add(new String[]{p.optString("id"), ts.optString(k)}); }
            }
            opts.add("해당 없음");
            telecomOpts.add(null);
        } else for (String o : def[4].split("\\|")) opts.add(o);
        String curV = conditionValue(key);
        if (!opts.contains(curV)) { opts.add(0, curV); if (key.equals("telecom")) telecomOpts.add(0, null); }
        Spinner sp = Ui.spinner(s.content, "조건 변경", opts, opts.indexOf(curV));
        EditText custom = Ui.field(s.content, "지역 이름 (기타 지역)", "예: 포항시", InputType.TYPE_CLASS_TEXT);
        ((View) custom.getParent()).setVisibility(key.equals("region") ? View.VISIBLE : View.GONE);
        TextView err = Ui.text(s.content, "", 13, true, Ui.MISS);
        Ui.button(s.content, "변경 저장", true, v -> {
            JSONObject w = Repo.walletCopy();
            try {
                String val = sp.getSelectedItem().toString();
                if (key.equals("telecom")) {
                    String[] t = telecomOpts.get(sp.getSelectedItemPosition());
                    JSONArray ms = w.optJSONArray("memberships");
                    int at = -1;
                    for (int i = 0; i < ms.length(); i++) if (Repo.membershipKind(ms.optJSONObject(i)).equals("telecom")) at = i;
                    if (t == null && at >= 0 && val.equals("해당 없음")) ms.remove(at);
                    else if (t != null) {
                        JSONObject m = at >= 0 ? ms.optJSONObject(at) : new JSONObject();
                        if (at >= 0 && !t[0].equals(m.optString("productId"))) { m.remove("code"); m.remove("format"); }
                        m.put("productId", t[0]);
                        m.put("tier", t[1]);
                        m.remove("name");
                        m.remove("kind");
                        if (at >= 0) ms.put(at, m); else ms.put(m);
                    }
                } else {
                    if (val.equals("기타 지역")) {
                        val = custom.getText().toString().trim();
                        if (val.isEmpty()) { err.setText("지역 이름을 입력해 주세요"); return; }
                    }
                    JSONObject p = w.optJSONObject("profile");
                    if (p == null) { p = new JSONObject(); w.put("profile", p); }
                    p.put(key, val);
                }
                save(a, s, w, err, def[1] + "을(를) 바꿨어요");
            } catch (JSONException e) { err.setText(e.getMessage()); }
        });
        s.show();
    }
}
