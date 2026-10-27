package kr.recatch.proto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 카드 승인·취소 알림 파서. mvp/core/parser.js를 그대로 옮긴 것이다.
 * 두 구현의 결과는 mvp/android/tools/check-parser.js가 같은 샘플로 비교한다.
 * 판단에 필요한 최소 필드만 돌려주고 원문·이름은 남기지 않는다.
 * (Android 의존성이 없어서 일반 JDK로도 컴파일된다.)
 */
public final class CardParser {
    private CardParser() {}

    public static final class Parsed {
        public String type;        // approve | cancel
        public String issuer;      // kb, sh, hd ...
        public String last4;       // null 가능
        public long amount;
        public String merchantRaw; // null 가능
        public String at;          // ISO-8601 (UTC), null 가능
        public String installment; // null 가능
        public Long cumulative;    // null 가능
    }

    private static final String[][] ISSUERS = {
        {"kb", "KB국민|국민카드|KB카드"},
        {"sh", "신한"},
        {"hd", "현대카드"},
        {"ss", "삼성카드"},
        {"lt", "롯데카드"},
        {"hn", "하나카드"},
        {"wr", "우리카드"},
        {"nh", "NH농협|농협카드"},
        {"bc", "BC카드|비씨카드"}
    };
    private static final Pattern[] ISSUER_P = new Pattern[ISSUERS.length];
    static {
        for (int i = 0; i < ISSUERS.length; i++) ISSUER_P[i] = Pattern.compile(ISSUERS[i][1]);
    }

    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");
    private static final Pattern LAST4 = Pattern.compile("\\((\\d{4})\\)|(\\d{4})\\s*\\*{0,4}\\s*(?=승인|취소|사용)|\\*{2,}(\\d{4})");
    private static final Pattern DATETIME = Pattern.compile("(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})");
    private static final Pattern CUMULATIVE = Pattern.compile("누적\\s*:?\\s*(\\d{1,3}(?:,\\d{3})+|\\d+)\\s*원");
    private static final Pattern INSTALLMENT = Pattern.compile("(일시불|\\d{1,2}\\s*개월)");
    private static final Pattern BALANCE = Pattern.compile("(누적|잔액)\\s*:?\\s*[\\d,]+\\s*원");
    private static final Pattern CANCEL = Pattern.compile("승인\\s*취소|취소");
    private static final Pattern APPROVE = Pattern.compile("승인|사용|결제");
    private static final Pattern TIME_THEN_TEXT = Pattern.compile("\\d{1,2}:\\d{2}.+\\S");
    private static final Pattern LETTER = Pattern.compile("[가-힣A-Za-z]");

    private static final Pattern[] NOISE = {
        Pattern.compile("(KB국민|국민|신한|현대|삼성|롯데|하나|우리|NH농협|농협|BC|비씨)(카드|체크|신용)[가-힣A-Za-z0-9]*"),
        Pattern.compile("\\[[^\\]]*\\]"),
        Pattern.compile("승인|취소|사용|결제"),
        Pattern.compile("일시불|\\d{1,2}\\s*개월"),
        Pattern.compile("누적\\s*:?\\s*[\\d,]+\\s*원"),
        Pattern.compile("잔액\\s*:?\\s*[\\d,]+\\s*원"),
        Pattern.compile("[\\d,]+\\s*원"),
        Pattern.compile("\\d{1,2}/\\d{1,2}\\s+\\d{1,2}:\\d{2}"),
        Pattern.compile("\\(\\d{4}\\)"),
        Pattern.compile("[가-힣A-Za-z]\\*[가-힣A-Za-z]*님?"),
        Pattern.compile("[가-힣]{2,4}님"),
        Pattern.compile("[()]")
    };

    private static long toNumber(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    private static String detectIssuer(String text) {
        for (int i = 0; i < ISSUERS.length; i++) if (ISSUER_P[i].matcher(text).find()) return ISSUERS[i][0];
        return null;
    }

    private static boolean hasIssuer(String text) {
        return detectIssuer(text) != null;
    }

    private static String pickMerchant(String text) {
        List<String> candidates = new ArrayList<>();
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (hasIssuer(line) && !TIME_THEN_TEXT.matcher(line).find()) continue;
            String rest = line;
            for (Pattern n : NOISE) rest = n.matcher(rest).replaceAll(" ");
            rest = rest.replaceAll("\\s+", " ").trim();
            if (rest.length() >= 2 && LETTER.matcher(rest).find()) candidates.add(rest);
        }
        // 한 줄짜리 문자는 마지막 덩어리가 가맹점인 경우가 많다.
        return candidates.isEmpty() ? null : candidates.get(candidates.size() - 1);
    }

    /** 카드 승인·취소 알림이 아니면 null. now는 연도 보정용 기준 시각. */
    public static Parsed parse(String text, LocalDateTime now) {
        if (text == null || text.trim().isEmpty()) return null;
        String type = CANCEL.matcher(text).find() ? "cancel" : APPROVE.matcher(text).find() ? "approve" : null;
        Matcher am = AMOUNT.matcher(BALANCE.matcher(text).replaceAll(""));
        if (type == null || !am.find()) return null;
        String issuer = detectIssuer(text);
        if (issuer == null) return null;

        Parsed p = new Parsed();
        p.type = type;
        p.issuer = issuer;
        p.amount = toNumber(am.group(1));

        Matcher l4 = LAST4.matcher(text);
        if (l4.find()) p.last4 = l4.group(1) != null ? l4.group(1) : l4.group(2) != null ? l4.group(2) : l4.group(3);

        Matcher dt = DATETIME.matcher(text);
        if (dt.find()) {
            int mo = Integer.parseInt(dt.group(1)), d = Integer.parseInt(dt.group(2));
            int h = Integer.parseInt(dt.group(3)), mi = Integer.parseInt(dt.group(4));
            int year = now.getYear();
            if (mo > now.getMonthValue() + 1) year -= 1; // 1월 초에 받은 12월 거래 문자
            try {
                p.at = LocalDateTime.of(year, mo, d, h, mi).atZone(ZoneId.systemDefault()).toInstant().toString();
            } catch (java.time.DateTimeException ignored) {
                p.at = null;
            }
        }
        Matcher cum = CUMULATIVE.matcher(text);
        if (cum.find()) p.cumulative = toNumber(cum.group(1));
        Matcher inst = INSTALLMENT.matcher(text);
        if (inst.find()) p.installment = inst.group(1).replaceAll("\\s+", "");
        p.merchantRaw = pickMerchant(text);
        return p;
    }
}
