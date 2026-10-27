package kr.recatch.proto;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 레이아웃 XML 없이 화면을 만드는 도우미. 색·모양은 웹 시연(index.html)의 토큰을 따른다:
 * 회색 바탕 위 흰 섹션, 주황 브랜드 한 색, 절약은 초록.
 */
final class Ui {
    static int BG, CARD, WELL, PRESS, INK, INK2, INK3, LINE;
    static int BRAND, BRAND_INK, BRAND_SOFT, BRAND_DEEP, SAVE, SAVE_SOFT, WARN, WARN_SOFT, MISS, MISS_SOFT;
    static boolean dark;

    private Ui() {}

    /** 시스템 다크 모드에 맞춰 색을 고른다 (웹 시연의 light/dark 토큰). */
    static void init(Context c) {
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (!dark) {
            BG = c("#F2F3F6"); CARD = c("#FFFFFF"); WELL = c("#F5F6F8"); PRESS = c("#EEF0F3");
            INK = c("#191A1F"); INK2 = c("#4E5260"); INK3 = c("#8B8F9C"); LINE = c("#ECEDF1");
            BRAND = c("#FF5A1F"); BRAND_INK = c("#FFFFFF"); BRAND_SOFT = c("#FFEEE6"); BRAND_DEEP = c("#D9420C");
            SAVE = c("#0E9F6E"); SAVE_SOFT = c("#E3F6EE"); WARN = c("#C97A00"); WARN_SOFT = c("#FFF3DC"); MISS = c("#E5484D"); MISS_SOFT = c("#FDECEC");
        } else {
            BG = c("#0E0F12"); CARD = c("#1A1B20"); WELL = c("#24262C"); PRESS = c("#2A2C33");
            INK = c("#F1F2F5"); INK2 = c("#B6B9C3"); INK3 = c("#7D818D"); LINE = c("#2A2C33");
            BRAND = c("#FF7440"); BRAND_INK = c("#1A0A03"); BRAND_SOFT = c("#3A2118"); BRAND_DEEP = c("#FF9568");
            SAVE = c("#38D39B"); SAVE_SOFT = c("#12332A"); WARN = c("#F2A63B"); WARN_SOFT = c("#3A2A10"); MISS = c("#FF6E74"); MISS_SOFT = c("#3B1A1D");
        }
    }

    static int c(String hex) { return Color.parseColor(hex); }

    /** 밝은 화면이면 상태바·내비게이션바 아이콘을 어둡게 (기본은 흰색이라 안 보임) */
    static void systemBars(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(CARD);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            int light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            w.getDecorView().post(() -> {
                android.view.WindowInsetsController ic = w.getInsetsController();
                if (ic != null) ic.setSystemBarsAppearance(dark ? 0 : light, light);
            });
        } else {
            int flags = w.getDecorView().getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            w.getDecorView().setSystemUiVisibility(dark ? flags & ~light : flags | light);
        }
    }

    static String won(long v) { return NumberFormat.getInstance(Locale.KOREA).format(v) + "원"; }
    static String num(long v) { return NumberFormat.getInstance(Locale.KOREA).format(v); }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setBaselineAligned(false);
        return l;
    }

    /** 세로 스크롤 화면 (탭 하나). 하단 탭바만큼 아래 여백. */
    static ScrollView page(Context c, LinearLayout[] out) {
        ScrollView sv = new ScrollView(c);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout col = vbox(c);
        col.setPadding(dp(c, 12), dp(c, 4), dp(c, 12), dp(c, 24));
        sv.addView(col);
        out[0] = col;
        return sv;
    }

    /** 흰 섹션 카드 (.sec) */
    static LinearLayout sec(LinearLayout parent) {
        Context c = parent.getContext();
        LinearLayout s = vbox(c);
        int p = dp(c, 20);
        s.setPadding(p, dp(c, 18), p, dp(c, 18));
        s.setBackground(round(c, CARD, 20));
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(c, 10);
        parent.addView(s, lp);
        return s;
    }

    /** 섹션 제목 + 오른쪽 작은 링크 (.sec-h) */
    static void secHeader(LinearLayout sec, String title, String more, View.OnClickListener onMore) {
        Context c = sec.getContext();
        LinearLayout h = hbox(c);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(INK);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (more != null) {
            TextView m = new TextView(c);
            m.setText(more);
            m.setTextSize(13.5f);
            m.setTextColor(INK3);
            m.setPadding(dp(c, 8), dp(c, 8), 0, dp(c, 8));
            if (onMore != null) m.setOnClickListener(onMore);
            h.addView(m);
        }
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(c, 6);
        sec.addView(h, lp);
    }

    /** 페이지 제목 (.page-h) */
    static void pageHeader(LinearLayout col, String title, String sub) {
        Context c = col.getContext();
        LinearLayout h = vbox(c);
        h.setPadding(dp(c, 8), dp(c, 10), dp(c, 8), dp(c, 4));
        TextView t = text(h, title, 24, true, INK);
        t.setPadding(0, 0, 0, 0);
        if (sub != null) text(h, sub, 13.5f, false, INK3).setPadding(0, dp(c, 2), 0, 0);
        col.addView(h);
    }

    static TextView text(LinearLayout parent, CharSequence s, float sp, boolean bold, int color) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.15f);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(c, 2), 0, dp(c, 2));
        parent.addView(t, matchWrap());
        return t;
    }

    static TextView text(LinearLayout parent, CharSequence s) { return text(parent, s, 15, false, INK); }

    static TextView note(LinearLayout parent, CharSequence s) {
        TextView t = text(parent, s, 12, false, INK3);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(parent.getContext(), 8), 0, dp(parent.getContext(), 4));
        return t;
    }

    /** 주황 버튼(brand) 또는 회색 버튼(soft) */
    static Button button(LinearLayout parent, String label, boolean brand, View.OnClickListener on) {
        Context c = parent.getContext();
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setTextColor(brand ? BRAND_INK : INK);
        b.setBackground(round(c, brand ? BRAND : WELL, 14));
        b.setOnClickListener(on);
        LinearLayout.LayoutParams lp = parent.getOrientation() == LinearLayout.HORIZONTAL
            ? new LinearLayout.LayoutParams(0, dp(c, 48), 1f)
            : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 52));
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) {
            // 가로 줄에서는 위 여백을 줄 자체에 준다 (버튼 margin은 줄 높이 계산에서 빠져 아래가 잘렸음)
            if (parent.getPaddingTop() == 0) parent.setPadding(parent.getPaddingLeft(), dp(c, 8), parent.getPaddingRight(), parent.getPaddingBottom());
            if (parent.getChildCount() > 0) lp.leftMargin = dp(c, 8);
        } else lp.topMargin = dp(c, 8);
        parent.addView(b, lp);
        return b;
    }

    /** 장부 한 줄 (.ledger .ln) */
    static void line(LinearLayout parent, String left, String right, int rightColor) {
        Context c = parent.getContext();
        LinearLayout h = hbox(c);
        h.setPadding(0, dp(c, 4), 0, dp(c, 4));
        TextView l = new TextView(c);
        l.setText(left);
        l.setTextSize(14);
        l.setTextColor(INK2);
        h.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView r = new TextView(c);
        r.setText(right);
        r.setTextSize(14);
        r.setTextColor(rightColor);
        r.setTypeface(Typeface.DEFAULT_BOLD);
        h.addView(r);
        parent.addView(h, matchWrap());
    }

    /** 초록 합계 상자 (.ln.total) */
    static void total(LinearLayout parent, String label, String value) {
        Context c = parent.getContext();
        LinearLayout b = vbox(c);
        b.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        b.setBackground(round(c, SAVE_SOFT, 14));
        text(b, label, 14, true, INK);
        text(b, value, 25, true, SAVE);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(c, 8);
        parent.addView(b, lp);
    }

    /** 가맹점 이니셜 원 (웹 시연의 MARK) */
    static TextView mark(Context c, String store) {
        String[][] marks = {{"스타벅스", "S", "#0B7A55"}, {"빕스", "V", "#A8232B"}, {"CGV", "C", "#D7262E"}, {"GS25", "GS", "#1D6FD8"},
            {"올리브영", "O", "#6FA334"}, {"맘스터치", "M", "#E3651D"}, {"파리바게뜨", "P", "#1E3F8F"}, {"CU", "CU", "#6A3FA0"},
            // 멤버십 (웹 시연의 MEMS 색)
            {"SKT", "T", "#E4002B"}, {"CJ", "CJ", "#6E3FA3"}, {"영남대", "YU", "#1B4F9C"}};
        String t = store == null || store.isEmpty() ? "?" : store.substring(0, 1);
        int color = c("#6A6F7D");
        for (String[] m : marks) if (store != null && store.startsWith(m[0])) { t = m[1]; color = c(m[2]); }
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextSize(t.length() > 1 ? 12 : 15);
        v.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        return v;
    }

    /** 목록 한 줄 (.row): 왼쪽 표시, 제목·부제, 오른쪽 금액·부제 */
    static LinearLayout row(LinearLayout parent, View lead, String title, String sub, String amt, int amtColor, String amtSub, View.OnClickListener on) {
        Context c = parent.getContext();
        LinearLayout r = hbox(c);
        r.setPadding(0, dp(c, 11), 0, dp(c, 11));
        if (lead != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42));
            lp.rightMargin = dp(c, 12);
            r.addView(lead, lp);
        }
        LinearLayout mid = vbox(c);
        TextView t = new TextView(c);
        t.setText(title);
        t.setTextSize(15.5f);
        t.setTextColor(INK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(t);
        if (sub != null) {
            TextView s = new TextView(c);
            s.setText(sub);
            s.setTextSize(13);
            s.setTextColor(INK3);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(s);
        }
        r.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (amt != null) {
            LinearLayout right = vbox(c);
            right.setGravity(Gravity.END);
            TextView a = new TextView(c);
            a.setText(amt);
            a.setTextSize(15.5f);
            a.setTypeface(Typeface.DEFAULT_BOLD);
            a.setTextColor(amtColor);
            a.setGravity(Gravity.END);
            right.addView(a);
            if (amtSub != null) {
                TextView s = new TextView(c);
                s.setText(amtSub);
                s.setTextSize(12.5f);
                s.setTextColor(INK3);
                s.setGravity(Gravity.END);
                right.addView(s);
            }
            r.addView(right);
        }
        if (on != null) {
            r.setOnClickListener(on);
            r.setBackground(pressable(c));
        }
        parent.addView(r, matchWrap());
        return r;
    }

    static android.graphics.drawable.Drawable pressable(Context c) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        return c.getDrawable(tv.resourceId);
    }

    /** 회색 알약 (.chip / facts) */
    static TextView pill(LinearLayout parent, String s, boolean on, View.OnClickListener click) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(13.5f);
        t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        t.setTextColor(on ? BRAND_INK : INK);
        t.setBackground(round(c, on ? INK : WELL, 18));
        t.setPadding(dp(c, 13), dp(c, 8), dp(c, 13), dp(c, 8));
        t.setGravity(Gravity.CENTER);
        if (on && !dark) t.setTextColor(Color.WHITE);
        if (on && dark) { t.setBackground(round(c, INK, 18)); t.setTextColor(BG); }
        if (click != null) t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(c, 6);
        lp.bottomMargin = dp(c, 6);
        parent.addView(t, lp);
        return t;
    }

    /** 가로 막대 (분석 화면) */
    static void bar(LinearLayout parent, String label, String value, float ratio, int color) {
        Context c = parent.getContext();
        LinearLayout h = hbox(c);
        h.setPadding(0, dp(c, 6), 0, dp(c, 2));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextSize(14);
        l.setTextColor(INK);
        h.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(14);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(INK);
        h.addView(v);
        parent.addView(h, matchWrap());
        FrameLayout track = new FrameLayout(c);
        track.setBackground(round(c, WELL, 4));
        View fill = new View(c);
        fill.setBackground(round(c, color, 4));
        track.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        track.addOnLayoutChangeListener((v1, l1, t1, r1, b1, ol, ot, or, ob) -> {
            int w = Math.round((r1 - l1) * Math.max(0.02f, Math.min(1f, ratio)));
            if (fill.getLayoutParams().width != w) { fill.getLayoutParams().width = w; fill.post(fill::requestLayout); }
        });
        parent.addView(track, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 8)));
    }

    static Switch switchRow(LinearLayout parent, String title, String sub, boolean checked) {
        Context c = parent.getContext();
        LinearLayout r = hbox(c);
        r.setPadding(0, dp(c, 10), 0, dp(c, 10));
        LinearLayout mid = vbox(c);
        text(mid, title, 15, true, INK).setPadding(0, 0, 0, 0);
        text(mid, sub, 12.5f, false, INK3).setPadding(0, 0, 0, 0);
        r.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch s = new Switch(c);
        s.setChecked(checked);
        r.addView(s);
        parent.addView(r, matchWrap());
        return s;
    }

    /** 이름표 + 입력칸. 숨길 때는 ((View) field.getParent()).setVisibility(...) */
    static EditText field(LinearLayout parent, String label, String hint, int inputType) {
        Context c = parent.getContext();
        LinearLayout box = vbox(c);
        text(box, label, 13, true, INK2).setPadding(0, dp(c, 10), 0, dp(c, 2));
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTextColor(INK);
        e.setHintTextColor(INK3);
        e.setTextSize(15);
        e.setBackground(round(c, WELL, 12));
        e.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        box.addView(e, matchWrap());
        parent.addView(box, matchWrap());
        return e;
    }

    /** 선택 상자. 다크 모드에서도 읽히도록 항목 색을 직접 칠한다. */
    static android.widget.Spinner spinner(LinearLayout parent, String label, java.util.List<String> items, int selected) {
        Context c = parent.getContext();
        if (label != null) text(parent, label, 13, true, INK2).setPadding(0, dp(c, 10), 0, dp(c, 2));
        android.widget.Spinner sp = new android.widget.Spinner(c, android.widget.Spinner.MODE_DIALOG);
        sp.setBackground(round(c, WELL, 12));
        sp.setAdapter(new android.widget.ArrayAdapter<String>(c, android.R.layout.simple_list_item_1, items) {
            private View paint(View v) {
                TextView t = (TextView) v;
                t.setTextColor(INK);
                t.setTextSize(15);
                t.setBackgroundColor(Color.TRANSPARENT);
                t.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
                return t;
            }
            @Override public View getView(int pos, View cv, ViewGroup p) { return paint(super.getView(pos, cv, p)); }
            @Override public View getDropDownView(int pos, View cv, ViewGroup p) {
                View v = paint(super.getDropDownView(pos, cv, p));
                v.setBackgroundColor(CARD);
                return v;
            }
        });
        if (selected >= 0 && selected < items.size()) sp.setSelection(selected);
        parent.addView(sp, matchWrap());
        return sp;
    }

    /** 세그먼트 버튼 (.seg) */
    static LinearLayout seg(LinearLayout parent, String[] labels, int selected, java.util.function.IntConsumer on) {
        Context c = parent.getContext();
        LinearLayout row = hbox(c);
        row.setBackground(round(c, WELL, 12));
        row.setPadding(dp(c, 4), dp(c, 4), dp(c, 4), dp(c, 4));
        for (int i = 0; i < labels.length; i++) {
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(14);
            t.setTypeface(i == selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setTextColor(i == selected ? INK : INK3);
            if (i == selected) t.setBackground(round(c, CARD, 9));
            t.setPadding(0, dp(c, 9), 0, dp(c, 9));
            int idx = i;
            t.setOnClickListener(v -> on.accept(idx));
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        parent.addView(row, matchWrap());
        return row;
    }

    /** 아래에서 올라오는 시트 (.sheet). content에 내용을 채운다. */
    static final class Sheet {
        final Dialog dialog;
        final LinearLayout content;
        Sheet(Dialog d, LinearLayout c) { dialog = d; content = c; }
        void show() { dialog.show(); }
        void dismiss() { dialog.dismiss(); }
    }

    static Sheet sheet(Activity a, String title, String lead) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(a);
        LinearLayout col = vbox(a);
        int p = dp(a, 20);
        col.setPadding(p, dp(a, 10), p, dp(a, 24));
        View grab = new View(a);
        grab.setBackground(round(a, LINE, 3));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(dp(a, 40), dp(a, 5));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = dp(a, 12);
        col.addView(grab, gl);
        if (title != null) text(col, title, 20, true, INK);
        if (lead != null) text(col, lead, 14, false, INK2);
        sv.addView(col);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        float r = dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sv.setBackground(bg);
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return new Sheet(d, col);
    }

    static void toast(Context c, String msg) { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); }

    static String statusName(String s) {
        switch (s) {
            case "waiting": return "안내 중";
            case "done": return "완료";
            case "pending": return "확인 중";
            case "missed": return "놓친 혜택";
            case "abandoned": return "취소만 됨";
            case "double_paid": return "이중 결제 주의";
            case "no_gain": return "추가 혜택 없음";
            default: return s;
        }
    }

    static int statusColor(String s) {
        switch (s) {
            case "done": return SAVE;
            case "waiting": return BRAND;
            case "pending": return WARN;
            case "missed": case "double_paid": case "abandoned": return MISS;
            default: return INK3;
        }
    }
}
