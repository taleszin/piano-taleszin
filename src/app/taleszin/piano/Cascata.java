package app.taleszin.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Notas caindo (estilo Guitar Hero/Synthesia) alinhadas com as teclas.
 * Modo fácil: a nota espera na linha até você tocar a tecla certa.
 */
final class Cascata extends View {
    interface Fim { void fim(boolean demo, int pontos, int erros, int perfeitos, int total, int maxCombo); }

    private static final int LARANJA = 0xFFFF7A1E, AMBAR = 0xFFFFB300, VERDE = 0xFF66BB6A, VERMELHO = 0xFFE53935;
    private static final float ANTECEDENCIA = 3000f;   // contagem antes da 1ª nota (ms de música)
    private static final float HORIZONTE = 2600f;      // quanto da música cabe na tela (ms)
    private static final float JANELA = 450f;          // pode tocar até 450 ms antes da chegada

    private final Teclado teclado;
    private final Sintetizador synth;
    private Fim fim;
    private final float dp;

    private Musicas.Musica m;
    private float[] inicio;
    private float batida;
    private long[] acertoEm;
    private int pos;               // próxima nota a tocar
    private float t;               // tempo da música (ms)
    private float vel = 1f;
    private long ultimoQuadro;
    private boolean rodando, demo, esperando, acabou;
    private long esperaDesde;
    private int demoTocando = -1, demoProx;

    // placar
    private int pontos, combo, maxCombo, erros, perfeitos;
    private String aviso; private int avisoCor; private long avisoEm;

    private final Paint pNota = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPlacar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAviso = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBrilho = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFaixa = new Paint();
    private final Paint pAnel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFundo = new Paint();
    private final RectF r = new RectF();

    Cascata(Context c, Teclado tec, Sintetizador s) {
        super(c);
        teclado = tec; synth = s;
        dp = c.getResources().getDisplayMetrics().density;
        pBorda.setStyle(Paint.Style.STROKE); pBorda.setStrokeWidth(3 * dp); pBorda.setColor(0xFFFFFFFF);
        pTexto.setTextAlign(Paint.Align.CENTER); pTexto.setFakeBoldText(true); pTexto.setColor(0xFFFFFFFF);
        pPlacar.setFakeBoldText(true);
        pAviso.setTextAlign(Paint.Align.CENTER); pAviso.setFakeBoldText(true);
        pAviso.setShadowLayer(6 * dp, 0, 2 * dp, 0xFF000000);
        pPlacar.setShadowLayer(4 * dp, 0, 1 * dp, 0xFF000000);
        pLinha.setColor(LARANJA); pLinha.setStrokeWidth(4 * dp);
        pFaixa.setColor(0x14FFFFFF);
        pAnel.setStyle(Paint.Style.STROKE);
    }

    void setFim(Fim f) { fim = f; }
    void setVelocidade(float v) { vel = v; }
    boolean demo() { return demo; }

    void iniciar(Musicas.Musica musica, boolean modoDemo) {
        m = musica; demo = modoDemo;
        batida = 60000f / m.bpm;
        inicio = new float[m.notas.length];
        acertoEm = new long[m.notas.length];
        float acc = ANTECEDENCIA;
        for (int i = 0; i < inicio.length; i++) { acc += m.pausaAntes[i] * batida; inicio[i] = acc; acc += m.duracoes[i] * batida; }
        pos = 0; t = 0; esperando = false; acabou = false;
        pontos = combo = maxCombo = erros = perfeitos = 0;
        aviso = null; demoProx = 0;
        soltarDemo();
        teclado.setGuia(demo ? -1 : m.notas[0]);
        retomar();
    }

    void retomar() { if (m == null || acabou) return; rodando = true; ultimoQuadro = SystemClock.uptimeMillis(); invalidate(); }
    void pausar() { rodando = false; soltarDemo(); }
    void parar() { pausar(); m = null; teclado.setGuia(-1); }

    private void soltarDemo() {
        if (demoTocando >= 0) { synth.soltar(demoTocando); teclado.setDemo(-1); demoTocando = -1; }
    }

    private void avisar(String txt, int cor) { aviso = txt; avisoCor = cor; avisoEm = SystemClock.uptimeMillis(); }

    /** Chamado quando o usuário toca uma tecla. Devolve se a nota deve soar (erro = silêncio). */
    boolean tocou(int midi) {
        if (m == null || demo || acabou || pos >= m.notas.length) return true;
        long agora = SystemClock.uptimeMillis();
        float falta = inicio[pos] - t;
        if (midi == m.notas[pos] && falta <= JANELA) {
            float atraso = esperando ? (agora - esperaDesde) * vel : 0;
            float erroMs = Math.max(falta, atraso);
            int base;
            if (erroMs <= 170) { base = 100; perfeitos++; avisar("Perfeito!", VERDE); }
            else if (erroMs <= 500) { base = 60; avisar("Boa!", LARANJA); }
            else { base = 30; avisar("Isso!", AMBAR); }
            combo++; maxCombo = Math.max(maxCombo, combo);
            pontos += base * Math.min(4, 1 + combo / 8);
            acertoEm[pos] = agora;
            pos++;
            esperando = false;
            teclado.setGuia(pos < m.notas.length ? m.notas[pos] : -1);
            invalidate();
            return true;
        } else if (t > inicio[0] - JANELA) {
            erros++; combo = 0;
            teclado.piscarErro(midi);
            avisar(midi == m.notas[pos] ? "Calma, espere chegar" : "Ops! Era " + Teclado.nome(m.notas[pos], false), VERMELHO);
        }
        invalidate();
        return false;
    }

    private void avancar() {
        long agora = SystemClock.uptimeMillis();
        float dt = Math.min(100, agora - ultimoQuadro) * vel;
        ultimoQuadro = agora;
        if (demo) {
            t += dt;
            while (demoProx < m.notas.length && inicio[demoProx] <= t) {
                soltarDemo();
                demoTocando = m.notas[demoProx];
                synth.tocar(demoTocando); teclado.setDemo(demoTocando);
                acertoEm[demoProx] = agora;
                demoProx++; pos = demoProx;
            }
            if (demoTocando >= 0) {
                int i = demoProx - 1;
                if (t >= inicio[i] + m.duracoes[i] * batida * 0.85f) soltarDemo();
            }
        } else if (pos < m.notas.length && t + dt >= inicio[pos]) {
            t = inicio[pos];
            if (!esperando) { esperando = true; esperaDesde = agora; }
        } else {
            t += dt;
        }
        int ult = m.notas.length - 1;
        if (pos > ult && t >= inicio[ult] + m.duracoes[ult] * batida + 600) {
            acabou = true; rodando = false; soltarDemo();
            if (fim != null) fim.fim(demo, pontos, erros, perfeitos, m.notas.length, maxCombo);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        if (pFundo.getShader() == null)
            pFundo.setShader(new LinearGradient(0, 0, 0, H, 0xFF0B0B10, 0xFF1C1424, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, pFundo);
        if (m == null) return;
        if (rodando) avancar();
        long agora = SystemClock.uptimeMillis();
        float linhaY = H - 6 * dp;
        float pxMs = linhaY / HORIZONTE;

        // faixas das teclas brancas (ajudam a mirar)
        float wb = W / (float) teclado.brancas();
        for (int i = 0; i < teclado.brancas(); i += 2) c.drawRect(i * wb, 0, (i + 1) * wb, linhaY, pFaixa);

        // linha de toque
        pBrilho.setShader(new LinearGradient(0, linhaY - 26 * dp, 0, linhaY, 0x00FF7A1E, esperando ? 0x88FF7A1E : 0x44FF7A1E, Shader.TileMode.CLAMP));
        c.drawRect(0, linhaY - 26 * dp, W, linhaY, pBrilho);
        c.drawLine(0, linhaY, W, linhaY, pLinha);

        // notas
        pTexto.setTextSize(14 * dp);
        for (int i = 0; i < m.notas.length; i++) {
            float baseY = linhaY - (inicio[i] - t) * pxMs;
            float alt = Math.max(m.duracoes[i] * batida * pxMs - 5 * dp, 26 * dp);
            if (baseY < 0) break;                       // as próximas estão mais acima
            float[] col = teclado.coluna(m.notas[i]);
            if (col == null) continue;
            if (acertoEm[i] != 0) {                     // acertou: anel que se expande
                long dtA = agora - acertoEm[i];
                if (dtA < 350) {
                    float k = dtA / 350f;
                    pAnel.setColor(col[2] > 0 ? AMBAR : LARANJA);
                    pAnel.setAlpha((int) (255 * (1 - k)));
                    pAnel.setStrokeWidth(4 * dp * (1 - k) + 1);
                    c.drawCircle(col[0] + col[1] / 2, linhaY, col[1] * 0.3f + 34 * dp * k, pAnel);
                }
                continue;
            }
            float topo = baseY - alt;
            if (topo > H) continue;
            float pad = col[1] * 0.08f;
            r.set(col[0] + pad, topo, col[0] + col[1] - pad, Math.min(baseY, linhaY));
            pNota.setColor(col[2] > 0 ? AMBAR : LARANJA);
            if (i != pos) pNota.setAlpha(200);
            c.drawRoundRect(r, 8 * dp, 8 * dp, pNota);
            if (i == pos && !demo) c.drawRoundRect(r, 8 * dp, 8 * dp, pBorda);
            if (r.height() > 20 * dp)
                c.drawText(Teclado.nome(m.notas[i], false), r.centerX(), r.bottom - 8 * dp, pTexto);
        }

        // placar
        if (!demo) {
            pPlacar.setTextAlign(Paint.Align.LEFT);
            pPlacar.setColor(0xFFFFFFFF); pPlacar.setTextSize(22 * dp);
            c.drawText(String.valueOf(pontos), 12 * dp, 30 * dp, pPlacar);
            if (combo >= 2) {
                pPlacar.setColor(LARANJA); pPlacar.setTextSize(15 * dp);
                int mult = Math.min(4, 1 + combo / 8);
                c.drawText("combo " + combo + (mult > 1 ? "  ×" + mult : ""), 12 * dp, 52 * dp, pPlacar);
            }
        } else {
            pPlacar.setTextAlign(Paint.Align.LEFT);
            pPlacar.setColor(0xFFBBBBBB); pPlacar.setTextSize(15 * dp);
            c.drawText("Ouvindo... depois é sua vez", 12 * dp, 28 * dp, pPlacar);
        }
        // progresso
        pPlacar.setColor(0xFF2A2A32);
        c.drawRect(0, 0, W, 4 * dp, pPlacar);
        pPlacar.setColor(LARANJA);
        c.drawRect(0, 0, W * Math.min(1f, pos / (float) m.notas.length), 4 * dp, pPlacar);

        // contagem 3, 2, 1
        float falta0 = inicio[0] - t;
        if (falta0 > 0 && pos == 0) {
            int n = (int) Math.ceil(falta0 / 1000f);
            pAviso.setColor(0xFFFFFFFF); pAviso.setTextSize(64 * dp);
            if (n <= 3) c.drawText(String.valueOf(n), W / 2f, H * 0.5f, pAviso);
        } else if (aviso != null && agora - avisoEm < 700) {
            float k = (agora - avisoEm) / 700f;
            pAviso.setColor(avisoCor);
            pAviso.setAlpha((int) (255 * (1 - k * k)));
            pAviso.setTextSize((aviso.length() > 12 ? 24 : 34) * dp * (1.15f - 0.15f * k));
            c.drawText(aviso, W / 2f, H * 0.42f, pAviso);
        }

        if (rodando || (aviso != null && agora - avisoEm < 700)) postInvalidateOnAnimation();
    }
}
