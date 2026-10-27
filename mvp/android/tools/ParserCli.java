import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import kr.recatch.proto.CardParser;

/** check-parser.js 전용: 한 줄에 하나씩 (\n 이스케이프된) 알림을 받아 파싱 결과를 탭으로 구분해 출력. */
public class ParserCli {
    private static String s(Object o) { return o == null ? "" : o.toString(); }

    public static void main(String[] args) throws Exception {
        LocalDateTime now = LocalDateTime.parse(args[0]);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        String line;
        while ((line = in.readLine()) != null) {
            String text = line.replace("\\n", "\n");
            CardParser.Parsed p = CardParser.parse(text, now);
            if (p == null) { out.println("NULL"); continue; }
            out.println(String.join("\t", p.type, s(p.issuer), s(p.last4), s(p.amount), s(p.merchantRaw), s(p.at), s(p.installment), s(p.cumulative)));
        }
    }
}
