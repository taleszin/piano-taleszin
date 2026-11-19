package app.taleszin.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.View;

/** Teclado multitoque: várias teclas ao mesmo tempo e deslizar o dedo troca de nota. */
final class Teclado extends View {
    static final int NOMES_DO_RE_MI = 0, NOMES_CDE = 1, NOMES_NENHUM = 2;
    private static final String[] DO_RE_MI = {"Dó", "Ré", "Mi", "Fá", "Sol", "Lá", "Si"};
    private static final String[] CDE = {"C", "D", "E", "F", "G", "A", "B"};
    private static final int[] SEMI_BRANCA = {0, 2, 4, 5, 7, 9, 11};
    private static final int LARANJA = 0xFFFF7A1E;

    interface Ouvinte { void tocou(int midi); }

    private final Sintetizador synth;
    private Ouvinte ouvinte;
    private int guia = -1;       // próxima tecla da música (acende)
    private int demo = -1;       // tecla tocada pelo "Ouvir"
    private final Paint pGuia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGuiaBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray dedoNota = new SparseIntArray();
    private final int[] apertadas = new int[128];
    private int oitavaInicial = 4;
    private int brancas = 15;
    private int nomes = NOMES_DO_RE_MI;

    private final Paint pBranca = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPreta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAceso = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;

    Teclado(Context c, Sintetizador s) {
        super(c);
        synth = s;
        dp = c.getResources().getDisplayMetrics().density;
        pPreta.setColor(0xFF1E1E22);
        pGuia.setColor(0x66FF7A1E);
        pGuiaBorda.setColor(LARANJA);
        pGuiaBorda.setStyle(Paint.Style.STROKE);
        pGuiaBorda.setStrokeWidth(3 * dp);
        pAceso.setColor(LARANJA);
        pLinha.setColor(0xFF9A9A9A);
        pLinha.setStrokeWidth(Math.max(1, dp));
        pTexto.setTextAlign(Paint.Align.CENTER);
        pTexto.setFakeBoldText(true);
        pSub.setTextAlign(Paint.Align.CENTER);
        pSub.setColor(0xFF8A8A8A);
        setBackgroundColor(0xFF101014);
    }

    void configurar(int oitava, int nBrancas, int modoNomes) {
        soltarTudo();
        oitavaInicial = oitava; brancas = nBrancas; nomes = modoNomes;
        invalidate();
    }

    int brancas() { return brancas; }
    void setOuvinte(Ouvinte o) { ouvinte = o; }
    void setGuia(int midi) { guia = midi; invalidate(); }
    void setDemo(int midi) { demo = midi; invalidate(); }
    int oitava() { return oitavaInicial; }
    /** Menor oitava inicial que mostra a faixa [min, max]; -1 se não cabe. */
    static int oitavaPara(int min, int max, int brancas) {
        int o = min / 12 - 1;
        int topo = 12 * (o + 1 + (brancas - 1) / 7) + SEMI_BRANCA[(brancas - 1) % 7];
        return max <= topo ? o : -1;
    }
    static String nome(int midi, boolean cde) {
        String[] n = cde ? new String[]{"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"}
                         : new String[]{"Dó","Dó#","Ré","Ré#","Mi","Fá","Fá#","Sol","Sol#","Lá","Lá#","Si"};
        return n[midi % 12];
    }

    String faixa() {
        int ultimaOit = oitavaInicial + (brancas - 1) / 7;
        String[] n = nomes == NOMES_CDE ? CDE : DO_RE_MI;
        int ultima = (brancas - 1) % 7;
        return n[0] + oitavaInicial + " – " + n[ultima] + ultimaOit;
    }

    private int midiBranca(int i) { return 12 * (oitavaInicial + 1 + i / 7) + SEMI_BRANCA[i % 7]; }

    /** Tecla preta à direita da branca i? (não existe depois de Mi e Si) */
    private boolean temPreta(int i) { int k = i % 7; return k != 2 && k != 6 && i < brancas - 1; }

    private float largura() { return getWidth() / (float) brancas; }
    private float alturaPreta() { return getHeight() * 0.60f; }

    private int notaEm(float x, float y) {
        float w = largura();
        if (x < 0 || x >= getWidth() || y < 0 || y > getHeight()) return -1;
        int i = (int) (x / w);
        if (y < alturaPreta()) {
            float bw = w * 0.30f;
            if (temPreta(i) && x > (i + 1) * w - bw) return midiBranca(i) + 1;
            if (i > 0 && temPreta(i - 1) && x < i * w + bw) return midiBranca(i - 1) + 1;
        }
        return midiBranca(Math.min(i, brancas - 1));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = largura(), h = getHeight(), bh = alturaPreta();
        float raio = 6 * dp;
        if (pBranca.getShader() == null || h > 0) {
            pBranca.setShader(new LinearGradient(0, 0, 0, h, 0xFFF4F4F2, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        }
        String[] n = nomes == NOMES_CDE ? CDE : DO_RE_MI;
        pTexto.setTextSize(Math.min(w * 0.36f, 22 * dp));
        pSub.setTextSize(Math.min(w * 0.22f, 12 * dp));
        for (int i = 0; i < brancas; i++) {
            float x = i * w;
            r.set(x + dp, -raio, x + w - dp, h - 2 * dp);
            int m = midiBranca(i);
            boolean on = apertadas[m] > 0 || m == demo;
            c.drawRoundRect(r, raio, raio, on ? pAceso : pBranca);
            if (m == guia && !on) {
                c.drawRoundRect(r, raio, raio, pGuia);
                r.inset(2 * dp, 2 * dp);
                c.drawRoundRect(r, raio, raio, pGuiaBorda);
                c.drawCircle(x + w / 2, h - 48 * dp, Math.min(w * 0.22f, 10 * dp), pAceso);
            }
            if (nomes != NOMES_NENHUM) {
                pTexto.setColor(on ? Color.WHITE : (i % 7 == 0 ? LARANJA : 0xFF3A3A3A));
                c.drawText(n[i % 7], x + w / 2, h - 18 * dp, pTexto);
                if (i % 7 == 0) c.drawText(String.valueOf(oitavaInicial + i / 7), x + w / 2, h - 5 * dp, pSub);
            }
        }
        float bw = w * 0.60f;
        for (int i = 0; i < brancas; i++) {
            if (!temPreta(i)) continue;
            float cx = (i + 1) * w;
            r.set(cx - bw / 2, -raio, cx + bw / 2, bh);
            int m = midiBranca(i) + 1;
            boolean on = apertadas[m] > 0 || m == demo;
            c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, on ? pAceso : pPreta);
            if (m == guia && !on) {
                c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pGuia);
                r.inset(2 * dp, 2 * dp);
                c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pGuiaBorda);
                c.drawCircle(cx, bh - 22 * dp, Math.min(bw * 0.25f, 8 * dp), pAceso);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int acao = e.getActionMasked();
        switch (acao) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int k = e.getActionIndex();
                trocar(e.getPointerId(k), notaEm(e.getX(k), e.getY(k)));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < e.getPointerCount(); k++) {
                    trocar(e.getPointerId(k), notaEm(e.getX(k), e.getY(k)));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int k = e.getActionIndex();
                trocar(e.getPointerId(k), -1);
                dedoNota.delete(e.getPointerId(k));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                soltarTudo();
                break;
        }
        return true;
    }

    private void trocar(int dedo, int nova) {
        int antiga = dedoNota.get(dedo, -1);
        if (antiga == nova) return;
        if (antiga >= 0) {
            if (--apertadas[antiga] <= 0) { apertadas[antiga] = 0; synth.soltar(antiga); }
        }
        if (nova >= 0) {
            apertadas[nova]++;
            synth.tocar(nova);
            if (ouvinte != null) ouvinte.tocou(nova);
        }
        dedoNota.put(dedo, nova);
        invalidate();
    }

    void soltarTudo() {
        dedoNota.clear();
        java.util.Arrays.fill(apertadas, 0);
        synth.soltarTudo();
        invalidate();
    }
}
