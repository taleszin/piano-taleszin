package app.taleszin.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** 3 estrelas desenhadas (a fonte do 4.4 não garante o ★). */
final class Estrelas extends View {
    private int cheias;
    private final float tam;
    private final Paint pOn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pOff = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path p = new Path();

    Estrelas(Context c, float tamanhoDp) {
        super(c);
        tam = tamanhoDp * c.getResources().getDisplayMetrics().density;
        pOn.setColor(0xFFFFB300);
        pOff.setColor(0xFF3A3A42);
    }

    void set(int n) { cheias = n; invalidate(); }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) (tam * 3.3f), (int) tam);
    }

    @Override protected void onDraw(Canvas c) {
        for (int i = 0; i < 3; i++) {
            float cx = tam * 0.5f + i * tam * 1.15f, cy = tam * 0.52f, R = tam * 0.5f, r = R * 0.45f;
            p.reset();
            for (int k = 0; k < 10; k++) {
                double a = -Math.PI / 2 + k * Math.PI / 5;
                float rr = k % 2 == 0 ? R : r;
                float x = cx + (float) (rr * Math.cos(a)), y = cy + (float) (rr * Math.sin(a));
                if (k == 0) p.moveTo(x, y); else p.lineTo(x, y);
            }
            p.close();
            c.drawPath(p, i < cheias ? pOn : pOff);
        }
    }
}
