package app.taleszin.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Teclado multitoque com janela móvel: mostra "n" teclas brancas a partir da branca "ini" (fracionário),
 * para o jogo poder rolar e dar zoom acompanhando a música. Brancas indexadas a partir do C0 (midi 12).
 * A força do toque vem da altura onde o dedo encosta na tecla (perto da ponta = forte).
 */
final class Teclado extends View {
    static final int NOMES_DO_RE_MI = 0, NOMES_CDE = 1, NOMES_NENHUM = 2;
    private static final String[] DO_RE_MI = {"Dó", "Ré", "Mi", "Fá", "Sol", "Lá", "Si"};
    private static final String[] CDE = {"C", "D", "E", "F", "G", "A", "B"};
    private static final int[] SEMI_BRANCA = {0, 2, 4, 5, 7, 9, 11};
    static final int LARANJA = 0xFFFF7A1E, AZUL = 0xFF3FA9F5, AMBAR = 0xFFFFB300, AZUL_ESC = 0xFF1E88E5;
    static final int MIN_G = 5, MAX_G = 56;   // A0 .. C8

    /** Devolve false para silenciar a tecla (erro no modo música). */
    interface Ouvinte { boolean tocou(int midi); void soltou(int midi); }

    private final Sintetizador synth;
    private Ouvinte ouvinte;
    private final int[] guia = new int[128];      // 0 = nada, 1 = mão direita, 2 = esquerda
    private final int[] demo = new int[128];      // idem: tocada pelo app
    private int erro = -1;
    private final Runnable limpaErro = new Runnable() { public void run() { erro = -1; invalidate(); } };
    private final SparseIntArray dedoNota = new SparseIntArray();
    private final int[] apertadas = new int[128];
    private float ini = 28, n = 15;              // janela: C4, 15 brancas
    private int nomes = NOMES_DO_RE_MI;
    private boolean forcaFixa, vibrar = true;

    private final Paint pBranca = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPreta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pCor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGuia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGuiaBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pSub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBolha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float dp;
    private int alturaShader = -1;

    Teclado(Context c, Sintetizador s) {
        super(c);
        synth = s;
        dp = c.getResources().getDisplayMetrics().density;
        pPreta.setShader(null);
        pPreta.setColor(0xFF1E1E22);
        pGuiaBorda.setStyle(Paint.Style.STROKE);
        pGuiaBorda.setStrokeWidth(3 * dp);
        pTexto.setTextAlign(Paint.Align.CENTER);
        pTexto.setFakeBoldText(true);
        pSub.setTextAlign(Paint.Align.CENTER);
        pSub.setColor(0xFF8A8A8A);
        pBolha.setTextAlign(Paint.Align.CENTER);
        pBolha.setFakeBoldText(true);
        pBolha.setColor(Color.WHITE);
        setBackgroundColor(0xFF101014);
        setHapticFeedbackEnabled(true);
    }

    // ---------- janela ----------
    static int branca(int midi) {          // índice da branca (ou da branca à esquerda, se preta)
        int o = midi / 12 - 1, s = midi % 12;
        int k = 0;
        for (int i = 0; i < 7; i++) if (SEMI_BRANCA[i] <= s) k = i;
        return o * 7 + k;
    }
    static boolean preta(int midi) { int s = midi % 12; return s == 1 || s == 3 || s == 6 || s == 8 || s == 10; }
    private static int midiBranca(int g) { return 12 * (g / 7 + 1) + SEMI_BRANCA[g % 7]; }
    private static boolean temPreta(int g) { int k = g % 7; return k != 2 && k != 6 && g < MAX_G; }

    /** Modo livre: oitava inicial e nº de brancas. */
    void configurar(int oitava, int nBrancas, int modoNomes) {
        soltarTudo();
        n = nBrancas; ini = Math.max(MIN_G, Math.min(MAX_G - n + 1, oitava * 7)); nomes = modoNomes;
        invalidate();
    }
    void setNomes(int modo) { nomes = modo; invalidate(); }
    void setJanela(float inicio, float brancas) {
        n = Math.max(6, Math.min(30, brancas));
        ini = Math.max(MIN_G, Math.min(MAX_G - n + 1, inicio));
        invalidate();
    }
    float ini() { return ini; }
    float n() { return n; }
    void setForcaFixa(boolean f) { forcaFixa = f; }
    void setVibrar(boolean v) { vibrar = v; }

    String faixa() {
        String[] nm = nomes == NOMES_CDE ? CDE : DO_RE_MI;
        int g0 = Math.round(ini), g1 = Math.round(ini + n) - 1;
        return nm[g0 % 7] + (g0 / 7) + " – " + nm[g1 % 7] + (g1 / 7);
    }

    void setOuvinte(Ouvinte o) { ouvinte = o; }
    void limparGuias() { java.util.Arrays.fill(guia, 0); invalidate(); }
    void setGuia(int midi, int mao) { if (midi >= 0 && midi < 128) guia[midi] = mao + 1; invalidate(); }
    void setDemo(int midi, int mao, boolean on) { if (midi >= 0 && midi < 128) demo[midi] = on ? mao + 1 : 0; invalidate(); }
    void limparDemo() { java.util.Arrays.fill(demo, 0); invalidate(); }
    void piscarErro(int midi) {
        erro = midi; removeCallbacks(limpaErro); postDelayed(limpaErro, 350); invalidate();
        if (vibrar) performHapticFeedback(HapticFeedbackConstants.REJECT);
    }
    void vibrarAcerto() { if (vibrar) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); }

    private float largura() { return getWidth() / n; }
    private float alturaPreta() { return getHeight() * 0.62f; }

    /** Coluna da nota na tela: out = {esquerda, largura, preta?1:0}. false se fora da janela. */
    boolean coluna(int midi, float[] out) {
        float w = largura();
        int g = branca(midi);
        float x;
        if (preta(midi)) { x = (g + 1 - ini) * w - w * 0.3f; out[1] = w * 0.6f; out[2] = 1; }
        else { x = (g - ini) * w; out[1] = w; out[2] = 0; }
        out[0] = x;
        return x + out[1] > 0 && x < getWidth();
    }

    static String nome(int midi, boolean cde) {
        String[] nm = cde ? new String[]{"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"}
                          : new String[]{"Dó","Dó#","Ré","Ré#","Mi","Fá","Fá#","Sol","Sol#","Lá","Lá#","Si"};
        return nm[midi % 12];
    }
    boolean nomesCde() { return nomes == NOMES_CDE; }

    private int notaEm(float x, float y) {
        if (x < 0 || x >= getWidth() || y < 0 || y > getHeight()) return -1;
        float w = largura();
        float gf = ini + x / w;
        int g = (int) Math.floor(gf);
        float loc = gf - g;
        if (y < alturaPreta()) {
            if (temPreta(g) && loc > 0.7f) return midiBranca(g) + 1;
            if (temPreta(g - 1) && loc < 0.3f) return midiBranca(g - 1) + 1;
        }
        return midiBranca(Math.max(MIN_G, Math.min(MAX_G, g)));
    }

    private float forca(int midi, float y) {
        if (forcaFixa) return 0.75f;
        float h = preta(midi) ? alturaPreta() : getHeight();
        float k = Math.max(0f, Math.min(1f, y / h));
        return 0.35f + 0.65f * k;
    }

    private int cor(int estado, boolean pretaK) {
        return estado == 2 ? (pretaK ? AZUL_ESC : AZUL) : (pretaK ? AMBAR : LARANJA);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = largura(), h = getHeight(), bh = alturaPreta();
        float raio = 6 * dp;
        if (alturaShader != (int) h) {
            alturaShader = (int) h;
            pBranca.setShader(new LinearGradient(0, 0, 0, h, 0xFFEDEDEA, 0xFFFFFFFF, Shader.TileMode.CLAMP));
        }
        String[] nm = nomes == NOMES_CDE ? CDE : DO_RE_MI;
        pTexto.setTextSize(Math.min(w * 0.34f, 20 * dp));
        pSub.setTextSize(Math.min(w * 0.22f, 11 * dp));
        int g0 = (int) Math.floor(ini), g1 = (int) Math.ceil(ini + n);
        for (int g = g0; g <= g1 && g <= MAX_G; g++) {
            float x = (g - ini) * w;
            r.set(x + dp, -raio, x + w - dp, h - 2 * dp);
            int m = midiBranca(g);
            int est = apertadas[m] > 0 ? (guia[m] != 0 ? guia[m] : 1) : demo[m];
            if (m == erro) { pCor.setColor(0xFFE53935); c.drawRoundRect(r, raio, raio, pCor); }
            else if (est != 0) { pCor.setColor(cor(est, false)); c.drawRoundRect(r, raio, raio, pCor); }
            else c.drawRoundRect(r, raio, raio, pBranca);
            if (guia[m] != 0 && est == 0 && m != erro) {
                int cg = cor(guia[m], false);
                pGuia.setColor((cg & 0x00FFFFFF) | 0x55000000);
                c.drawRoundRect(r, raio, raio, pGuia);
                r.inset(2 * dp, 2 * dp);
                pGuiaBorda.setColor(cg);
                c.drawRoundRect(r, raio, raio, pGuiaBorda);
                bolha(c, x + w / 2, h - 58 * dp, Math.min(w * 0.42f, 18 * dp), m, cg);
            }
            if (nomes != NOMES_NENHUM && w > 22 * dp) {
                pTexto.setColor(est != 0 ? Color.WHITE : (g % 7 == 0 ? LARANJA : 0xFF3A3A3A));
                c.drawText(nm[g % 7], x + w / 2, h - 17 * dp, pTexto);
                if (g % 7 == 0) c.drawText(String.valueOf(g / 7), x + w / 2, h - 5 * dp, pSub);
            } else if (g % 7 == 0) {
                pSub.setColor(LARANJA);
                c.drawText("C" + (g / 7), x + w / 2, h - 6 * dp, pSub);
                pSub.setColor(0xFF8A8A8A);
            }
        }
        float bw = w * 0.60f;
        for (int g = g0 - 1; g <= g1 && g <= MAX_G; g++) {
            if (g < MIN_G || !temPreta(g)) continue;
            float cx = (g + 1 - ini) * w;
            r.set(cx - bw / 2, -raio, cx + bw / 2, bh);
            int m = midiBranca(g) + 1;
            int est = apertadas[m] > 0 ? (guia[m] != 0 ? guia[m] : 1) : demo[m];
            if (m == erro) { pCor.setColor(0xFFE53935); c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pCor); }
            else if (est != 0) { pCor.setColor(cor(est, true)); c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pCor); }
            else c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pPreta);
            if (guia[m] != 0 && est == 0 && m != erro) {
                int cg = cor(guia[m], true);
                pGuia.setColor((cg & 0x00FFFFFF) | 0x66000000);
                c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pGuia);
                r.inset(2 * dp, 2 * dp);
                pGuiaBorda.setColor(cg);
                c.drawRoundRect(r, raio * 0.7f, raio * 0.7f, pGuiaBorda);
                bolha(c, cx, bh - 24 * dp, Math.min(bw * 0.55f, 18 * dp), m, cg);
            }
        }
    }

    private void bolha(Canvas c, float cx, float cy, float raioB, int midi, int cor) {
        pCor.setColor(cor);
        c.drawCircle(cx, cy, raioB, pCor);
        String s = nome(midi, nomes == NOMES_CDE);
        pBolha.setTextSize(raioB * (s.length() > 3 ? 0.62f : 0.8f));
        c.drawText(s, cx, cy + pBolha.getTextSize() * 0.36f, pBolha);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int k = e.getActionIndex();
                trocar(e.getPointerId(k), notaEm(e.getX(k), e.getY(k)), e.getY(k));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int k = 0; k < e.getPointerCount(); k++) trocar(e.getPointerId(k), notaEm(e.getX(k), e.getY(k)), e.getY(k));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int k = e.getActionIndex();
                trocar(e.getPointerId(k), -1, 0);
                dedoNota.delete(e.getPointerId(k));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                soltarTudo();
                break;
        }
        return true;
    }

    private void trocar(int dedo, int nova, float y) {
        int antiga = dedoNota.get(dedo, -1);
        if (antiga == nova) return;
        if (antiga >= 0 && --apertadas[antiga] <= 0) {
            apertadas[antiga] = 0; synth.soltar(antiga);
            if (ouvinte != null) ouvinte.soltou(antiga);
        }
        if (nova >= 0) {
            apertadas[nova]++;
            if (ouvinte == null || ouvinte.tocou(nova)) synth.tocar(nova, forca(nova, y));
        }
        dedoNota.put(dedo, nova);
        invalidate();
    }

    boolean apertada(int midi) { return apertadas[midi] > 0; }

    void soltarTudo() {
        dedoNota.clear();
        java.util.Arrays.fill(apertadas, 0);
        synth.soltarTudo();
        invalidate();
    }
}
