package kr.recatch.proto;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * 좌우로 밀어 화면을 넘기는 틀 (웹 시연: 본문 50px 이상 밀면 전환).
 * 가로 레일처럼 tag가 "noswipe"인 뷰에서 시작한 손짓은 건드리지 않는다.
 */
public class SwipeFrame extends FrameLayout {
    interface OnSwipe { void onSwipe(int direction); } // +1 = 다음(왼쪽으로 밂), -1 = 이전

    private final int slop;
    private final int threshold;
    private float x0, y0;
    private boolean tracking, dragging;
    OnSwipe listener;

    public SwipeFrame(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        threshold = Ui.dp(c, 50);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                x0 = e.getX(); y0 = e.getY();
                tracking = !startsInNoSwipe(this, e.getRawX(), e.getRawY());
                dragging = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!tracking) return false;
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.5f) { dragging = true; return true; }
                if (Math.abs(dy) > slop) tracking = false; // 세로 스크롤 우선
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return e.getActionMasked() == MotionEvent.ACTION_DOWN;
        View page = getChildCount() > 0 ? getChildAt(getChildCount() - 1) : null;
        float dx = e.getX() - x0;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (page != null) page.setTranslationX(dx * 0.6f);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (Math.abs(dx) >= threshold && e.getActionMasked() == MotionEvent.ACTION_UP && listener != null) {
                    listener.onSwipe(dx < 0 ? 1 : -1);
                } else if (page != null) {
                    page.animate().translationX(0).setDuration(160).start();
                }
                return true;
            default:
                return true;
        }
    }

    private static boolean startsInNoSwipe(View v, float rx, float ry) {
        if (v.getVisibility() != VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        if (rx < loc[0] || ry < loc[1] || rx > loc[0] + v.getWidth() || ry > loc[1] + v.getHeight()) return false;
        if ("noswipe".equals(v.getTag())) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) if (startsInNoSwipe(g.getChildAt(i), rx, ry)) return true;
        }
        return false;
    }
}
