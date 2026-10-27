package kr.recatch.proto;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** 흰 바탕에 검정 막대. 다크 모드에서도 스캐너가 읽도록 색을 바꾸지 않는다. */
public class BarcodeView extends View {
    private final boolean[] modules;
    private final Paint paint = new Paint();

    public BarcodeView(Context c, String digits, String format) {
        super(c);
        boolean[] m;
        try { m = Barcode.encode(digits, format); } catch (RuntimeException e) { m = new boolean[0]; }
        modules = m;
        paint.setColor(Color.BLACK);
        paint.setAntiAlias(false);
        setBackgroundColor(Color.WHITE);
        setContentDescription("바코드 " + digits);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (modules.length == 0) return;
        int quiet = 10; // 양쪽 여백 (모듈 단위)
        int total = modules.length + quiet * 2;
        // 모듈 폭을 정수 픽셀로 맞춰야 인식이 잘 된다
        float unit = Math.max(1, (float) Math.floor((float) getWidth() / total));
        float x = (getWidth() - unit * modules.length) / 2f;
        int top = getPaddingTop(), bottom = getHeight() - getPaddingBottom();
        for (boolean b : modules) {
            if (b) canvas.drawRect(x, top, x + unit, bottom, paint);
            x += unit;
        }
    }
}
