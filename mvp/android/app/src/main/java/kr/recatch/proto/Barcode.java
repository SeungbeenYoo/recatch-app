package kr.recatch.proto;

/**
 * 멤버십 바코드를 실제 스캐너가 읽을 수 있는 막대 배열(true=검정 모듈)로 만든다.
 * - ean13 / upca: GS1 규칙 (검증 숫자 포함 번호)
 * - 그 밖(numeric, member16, student12): Code 128 (숫자는 C 세트로 압축)
 * Android 의존성이 없어 tools/check-barcode.js가 JDK로 다른 구현과 비교한다.
 */
public final class Barcode {
    private Barcode() {}

    // Code 128 기호 0~106의 막대·공백 폭
    private static final String[] C128 = {
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    };
    private static final int START_B = 104, START_C = 105, CODE_C = 99, STOP = 106;

    static int c128SymbolCount() { return C128.length; }

    public static boolean[] encode(String digits, String format) {
        if ("ean13".equals(format)) return ean13(digits);
        if ("upca".equals(format)) return ean13("0" + digits);
        return code128(digits);
    }

    /** 문자열 표현 ("1"/"0") — 비교 도구용 */
    public static String modules(String digits, String format) {
        boolean[] m = encode(digits, format);
        StringBuilder sb = new StringBuilder(m.length);
        for (boolean b : m) sb.append(b ? '1' : '0');
        return sb.toString();
    }

    static boolean[] code128(String s) {
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        boolean allDigits = !s.isEmpty() && s.chars().allMatch(Character::isDigit);
        int i = 0;
        if (allDigits && s.length() >= 2) {
            if (s.length() % 2 == 1) {
                // 홀수 자리: 첫 숫자는 B 세트, 나머지는 C 세트로 두 자리씩
                codes.add(START_B);
                codes.add(s.charAt(0) - 32);
                codes.add(CODE_C);
                i = 1;
            } else {
                codes.add(START_C);
            }
            for (; i < s.length(); i += 2) codes.add(Integer.parseInt(s.substring(i, i + 2)));
        } else {
            codes.add(START_B);
            for (char ch : s.toCharArray()) {
                if (ch < 32 || ch > 126) throw new IllegalArgumentException("unsupported char");
                codes.add(ch - 32);
            }
        }
        int sum = codes.get(0);
        for (int k = 1; k < codes.size(); k++) sum += codes.get(k) * k;
        codes.add(sum % 103);
        codes.add(STOP);

        StringBuilder bits = new StringBuilder();
        for (int code : codes) {
            String w = C128[code];
            for (int k = 0; k < w.length(); k++) {
                char bit = k % 2 == 0 ? '1' : '0';
                for (int n = 0; n < w.charAt(k) - '0'; n++) bits.append(bit);
            }
        }
        return toBools(bits);
    }

    private static final String[] L = {"0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011"};
    private static final String[] G = {"0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111"};
    private static final String[] R = {"1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100"};
    private static final String[] PARITY = {"LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"};

    static boolean[] ean13(String s) {
        if (s.length() != 13 || !s.chars().allMatch(Character::isDigit)) throw new IllegalArgumentException("EAN-13 needs 13 digits");
        String parity = PARITY[s.charAt(0) - '0'];
        StringBuilder bits = new StringBuilder("101");
        for (int k = 1; k <= 6; k++) {
            int d = s.charAt(k) - '0';
            bits.append(parity.charAt(k - 1) == 'L' ? L[d] : G[d]);
        }
        bits.append("01010");
        for (int k = 7; k <= 12; k++) bits.append(R[s.charAt(k) - '0']);
        bits.append("101");
        return toBools(bits);
    }

    private static boolean[] toBools(CharSequence bits) {
        boolean[] out = new boolean[bits.length()];
        for (int k = 0; k < out.length; k++) out[k] = bits.charAt(k) == '1';
        return out;
    }
}
