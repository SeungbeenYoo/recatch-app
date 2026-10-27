package kr.recatch.proto;

import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/** 혜택·결제 내역: 상태별 개수, 작은 탭(전체·완료·확인 중·놓친 혜택), 목록. 좌우로 밀면 작은 탭이 바뀐다. */
final class HistoryTab {
    private HistoryTab() {}

    static final String[] FILTERS = {"all", "done", "pending", "missed"};
    static final String[] FILTER_NAMES = {"전체", "완료", "확인 중", "놓친 혜택"};
    static int filter = 0;

    /** 밀었을 때: 작은 탭 안에서 움직이면 true, 끝이면 false (옆 화면으로) */
    static boolean shift(MainActivity a, int dir) {
        int next = filter + dir;
        if (next < 0 || next >= FILTERS.length) return false;
        filter = next;
        a.redraw();
        return true;
    }

    static boolean matches(JSONObject r) {
        String s = r.optString("status");
        switch (FILTERS[filter]) {
            case "done": return s.equals("done");
            case "pending": return s.equals("pending") || s.equals("waiting");
            case "missed": return s.equals("missed") || s.equals("abandoned") || s.equals("double_paid");
            default: return true;
        }
    }

    static void render(MainActivity a, LinearLayout col) {
        Ui.pageHeader(col, "혜택·결제 내역", "결제 알림으로 감지한 결제와 재결제 결과예요.");
        if (a.showConnectionHint(col)) return;
        JSONArray all = Repo.recs;
        int done = 0, pending = 0, missed = 0;
        for (int i = 0; i < all.length(); i++) {
            String s = all.optJSONObject(i).optString("status");
            if (s.equals("done")) done++;
            else if (s.equals("pending") || s.equals("waiting")) pending++;
            else if (s.equals("missed") || s.equals("abandoned") || s.equals("double_paid")) missed++;
        }
        LinearLayout sum = Ui.sec(col);
        LinearLayout row = Ui.hbox(a);
        stat(row, "재결제 완료", done, Ui.SAVE);
        stat(row, "확인 중", pending, Ui.WARN);
        stat(row, "놓친 혜택", missed, Ui.MISS);
        sum.addView(row);

        LinearLayout chips = Parts.rail(col);
        ((android.view.View) chips.getParent()).setTag(null); // 작은 탭 줄을 밀어도 탭이 바뀌게
        chips.setPadding(0, Ui.dp(a, 12), 0, 0);
        for (int i = 0; i < FILTERS.length; i++) {
            int idx = i;
            Ui.pill(chips, FILTER_NAMES[i], i == filter, v -> { filter = idx; a.redraw(); });
        }
        Ui.note(col, "목록을 좌우로 밀면 작은 탭이 바뀌어요. 전체에서 오른쪽은 홈, 놓친 혜택에서 왼쪽은 분석으로 이어져요.");

        LinearLayout list = Ui.sec(col);
        list.setMinimumHeight(Ui.dp(a, 260));
        int n = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject r = all.optJSONObject(i);
            if (!matches(r)) continue;
            Parts.recRow(a, list, r);
            n++;
        }
        if (n == 0) Ui.note(list, "아무것도 없어요");
    }

    private static void stat(LinearLayout row, String label, int n, int color) {
        LinearLayout c = Ui.vbox(row.getContext());
        c.setGravity(android.view.Gravity.CENTER);
        Ui.text(c, label, 12.5f, false, Ui.INK3).setGravity(android.view.Gravity.CENTER);
        Ui.text(c, String.valueOf(n), 22, true, color).setGravity(android.view.Gravity.CENTER);
        row.addView(c, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
    }
}
