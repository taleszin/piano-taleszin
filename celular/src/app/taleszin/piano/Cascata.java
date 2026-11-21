package app.taleszin.piano;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;

/**
 * Notas caindo (estilo Guitar Hero) alinhadas com as teclas, para duas mãos.
 *  - Aprender: a música espera na linha até você tocar o grupo inteiro (acorde = um alvo).
 *  - Desafio: a música não para; nota que passa vira erro.
 * A mão que você não toca é tocada pelo app (notas finas translúcidas). O teclado rola/dá zoom sozinho.
 */
final class Cascata extends View {
    static final int MAO_D = 1, MAO_E = 2, DUAS = 3;

    static final class Resultado {
        boolean demo, desafio, trecho;
        int pontos, erros, perfeitos, notas, acertos, maxCombo, longas, seguradas;
        final int[] errosPorNota = new int[128];
        float precisao() { return acertos + erros == 0 ? 0 : 100f * acertos / (acertos + erros); }
    }
    interface Fim { void fim(Resultado r); }

    private static final int VERDE = 0xFF66BB6A, VERMELHO = 0xFFE53935, OURO = 0xFFFFD54F;
    private static final float ANTECEDENCIA = 3000f;
    private static final float HORIZONTE = 2600f;
    private static final float JANELA = 450f;          // aprender: pode tocar até 450 ms antes
    private static final float JANELA_D = 260f;        // desafio: antes/depois
    private static final float LONGA = 450f;
    private static final float NMIN = 10;
    /** Zoom máximo: uma mão cabe em ~2 oitavas com teclas largas; duas mãos/autoplay abrem mais. */
    private float nmax() { return demo || maos == DUAS ? 21 : 15; }

    private final Teclado teclado;
    private final Sintetizador synth;
    private Fim fim;
    private final float dp;

    // música preparada
    private Musicas.Musica m;
    private int nEv;
    private float[] tEv, dEv;          // ms
    private int[] maoEv;
    private int[][] notasEv;
    private boolean[] jogavel;
    private boolean[][] acertou;
    private long[] acertoEm;
    private float[] secT; private String[] secR;
    // grupos jogáveis
    private int nG;
    private float[] tG;
    private int[][] gEv;               // eventos de cada grupo
    private int[] faltam;              // notas que faltam em cada grupo
    private float fimMs;

    private int pos, primeiro;
    private float t, vel = 1f;
    private long ultimoQuadro;
    private boolean rodando, demo, desafio, esperando, acabou, janelaFixa;
    private int maos = MAO_D;
    private long esperaDesde;
    // app tocando (acompanhamento / autoplay)
    private int autoProx;
    private final ArrayList<int[]> autoAtivas = new ArrayList<int[]>();  // {ev, midi}
    // segurar
    private final int[] holdEv = new int[16], holdMidi = new int[16];
    private final float[] holdDesde = new float[16];
    private int nHold;

    private Resultado res = new Resultado();
    private int combo;
    private String aviso; private int avisoCor; private long avisoEm;
    // janela do teclado
    private float alvoIni, alvoN;
    // partículas
    private static final int NP = 240;
    private final float[] px = new float[NP], py = new float[NP], pvx = new float[NP], pvy = new float[NP], pv = new float[NP];
    private final int[] pc = new int[NP];
    private int pProx;
    private long ultimoP;
    // feixes de luz subindo da tecla e textos de julgamento sobre a tecla
    private static final int NB = 24;
    private final int[] bMidi = new int[NB], bCor = new int[NB];
    private final long[] bEm = new long[NB];
    private final String[] bTxt = new String[NB];
    private int bProx;
    private long flashEm, ultimaFaisca;
    private final Paint pGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFeixe = new Paint();
    private final Paint pJulg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint pNota = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorda = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPlacar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAviso = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFaixa = new Paint();
    private final Paint pAnel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFundo = new Paint();
    private final Paint pPart = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), r2 = new RectF();
    private final float[] col = new float[3];
    private int alturaFundo = -1;

    Cascata(Context c, Teclado tec, Sintetizador s) {
        super(c);
        teclado = tec; synth = s;
        dp = c.getResources().getDisplayMetrics().density;
        pBorda.setStyle(Paint.Style.STROKE); pBorda.setStrokeWidth(2.5f * dp); pBorda.setColor(0xFFFFFFFF);
        pTexto.setTextAlign(Paint.Align.CENTER); pTexto.setFakeBoldText(true); pTexto.setColor(0xFFFFFFFF);
        pPlacar.setFakeBoldText(true);
        pPlacar.setShadowLayer(4 * dp, 0, 1 * dp, 0xFF000000);
        pAviso.setTextAlign(Paint.Align.CENTER); pAviso.setFakeBoldText(true);
        pAviso.setShadowLayer(6 * dp, 0, 2 * dp, 0xFF000000);
        pLinha.setStrokeWidth(4 * dp);
        pFaixa.setColor(0x10FFFFFF);
        pAnel.setStyle(Paint.Style.STROKE);
        pGlow.setMaskFilter(new BlurMaskFilter(10 * dp, BlurMaskFilter.Blur.NORMAL));
        pJulg.setTextAlign(Paint.Align.CENTER); pJulg.setFakeBoldText(true);
        pJulg.setShadowLayer(3 * dp, 0, 1 * dp, 0xFF000000);
    }

    private void feixe(int midi, int cor, String txt) {
        int i = bProx; bProx = (bProx + 1) % NB;
        bMidi[i] = midi; bCor[i] = cor; bEm[i] = SystemClock.uptimeMillis(); bTxt[i] = txt;
        flashEm = bEm[i];
    }

    void setFim(Fim f) { fim = f; }
    void setVelocidade(float v) { vel = v; }
    boolean demo() { return demo; }
    boolean ativo() { return m != null; }
    /** Seção em andamento (índice) para o rótulo. */
    String secaoAtual() {
        if (secT == null) return "";
        String s = "";
        for (int i = 0; i < secT.length; i++) if (t >= secT[i] - 400) s = secR[i];
        return s;
    }

    private static boolean longa(float dBeats, float dMs) { return dBeats >= 1.5f && dMs >= LONGA; }

    /** Prepara e começa. trechoIni/trechoFim em tempos (semínimas); fim < 0 = até o final. */
    void iniciar(Musicas.Musica musica, int maosJogadas, boolean modoDesafio, boolean modoDemo, float trechoIni, float trechoFim) {
        soltarAuto();
        m = musica; demo = modoDemo; desafio = modoDesafio && !modoDemo; maos = maosJogadas;
        float batida = 60000f / m.bpm;
        float b0 = trechoIni, b1 = trechoFim < 0 ? Float.MAX_VALUE : trechoFim;
        ArrayList<Integer> sel = new ArrayList<Integer>();
        for (int i = 0; i < m.ini.length; i++) if (m.ini[i] >= b0 - 1e-3f && m.ini[i] < b1 - 1e-3f) sel.add(i);
        nEv = sel.size();
        tEv = new float[nEv]; dEv = new float[nEv]; maoEv = new int[nEv]; notasEv = new int[nEv][];
        jogavel = new boolean[nEv]; acertou = new boolean[nEv][]; acertoEm = new long[nEv];
        fimMs = 0;
        res = new Resultado();
        res.demo = demo; res.desafio = desafio; res.trecho = trechoIni > 0 || trechoFim >= 0;
        for (int k = 0; k < nEv; k++) {
            int i = sel.get(k);
            tEv[k] = (m.ini[i] - b0) * batida + ANTECEDENCIA;
            float fimB = Math.min(m.ini[i] + m.dur[i], b1);
            dEv[k] = (fimB - m.ini[i]) * batida;
            maoEv[k] = m.mao[i]; notasEv[k] = m.notas[i];
            jogavel[k] = !demo && (maos & (1 << m.mao[i])) != 0;
            acertou[k] = new boolean[notasEv[k].length];
            fimMs = Math.max(fimMs, tEv[k] + dEv[k]);
            if (jogavel[k]) {
                res.notas += notasEv[k].length;
                if (longa(fimB - m.ini[i], dEv[k])) res.longas += notasEv[k].length;
            }
        }
        // seções visíveis no trecho
        ArrayList<Integer> ss = new ArrayList<Integer>();
        for (int i = 0; i < m.secIni.length; i++) if (m.secIni[i] >= b0 - 1e-3f && m.secIni[i] < b1) ss.add(i);
        secT = new float[ss.size()]; secR = new String[ss.size()];
        for (int k = 0; k < ss.size(); k++) { secT[k] = (m.secIni[ss.get(k)] - b0) * batida + ANTECEDENCIA; secR[k] = m.secRot[ss.get(k)]; }
        // grupos (eventos jogáveis com o mesmo início)
        ArrayList<float[]> gt = new ArrayList<float[]>();
        ArrayList<ArrayList<Integer>> ge = new ArrayList<ArrayList<Integer>>();
        for (int k = 0; k < nEv; k++) {
            if (!jogavel[k]) continue;
            if (!gt.isEmpty() && Math.abs(gt.get(gt.size() - 1)[0] - tEv[k]) < 2f) ge.get(ge.size() - 1).add(k);
            else { gt.add(new float[]{tEv[k]}); ArrayList<Integer> l = new ArrayList<Integer>(); l.add(k); ge.add(l); }
        }
        nG = gt.size();
        tG = new float[nG]; gEv = new int[nG][]; faltam = new int[nG];
        for (int g = 0; g < nG; g++) {
            tG[g] = gt.get(g)[0];
            ArrayList<Integer> l = ge.get(g);
            gEv[g] = new int[l.size()];
            for (int j = 0; j < l.size(); j++) { gEv[g][j] = l.get(j); faltam[g] += notasEv[l.get(j)].length; }
        }
        pos = 0; primeiro = 0; t = 0; esperando = false; acabou = false; combo = 0; nHold = 0; autoProx = 0;
        aviso = null;
        java.util.Arrays.fill(pv, 0);
        prepararJanela();
        atualizarGuias();
        retomar();
    }

    // ---------- janela do teclado ----------
    private void prepararJanela() {
        // janela estável: zoom único para a música toda; só desliza se a música não couber
        int lo = 127, hi = 0;
        for (int k = 0; k < nEv; k++) if (jogavel[k] || demo) for (int x : notasEv[k]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        if (lo > hi) { lo = 60; hi = 72; }
        int gl = Teclado.branca(lo), gh = Teclado.branca(hi) + (Teclado.preta(hi) ? 1 : 0);
        float span = gh - gl + 2;
        janelaFixa = span <= nmax();
        alvoN = Math.max(NMIN, Math.min(nmax(), span));
        if (janelaFixa) alvoIni = (gl + gh + 1) / 2f - alvoN / 2f;
        else { alvoIni = gl - 1; calcularAlvo(); }
        teclado.setJanela(alvoIni, alvoN);
    }

    /** Só desloca (sem mudar o zoom) quando uma nota próxima sai da janela; desloca de uma vez, com folga. */
    private void calcularAlvo() {
        int lo = 127, hi = 0;
        for (int g = pos; g < nG && tG[g] <= t + HORIZONTE; g++)
            for (int k : gEv[g]) for (int x : notasEv[k]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        if (demo || nG == 0) {
            for (int k = Math.max(0, autoProx - 1); k < nEv && tEv[k] <= t + HORIZONTE; k++)
                for (int x : notasEv[k]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
        }
        if (lo > hi) return;
        int gl = Teclado.branca(lo), gh = Teclado.branca(hi) + (Teclado.preta(hi) ? 1 : 0);
        if (gh - gl + 1 > alvoN && pos < nG) {   // trecho largo demais: garante o próximo grupo
            lo = 127; hi = 0;
            for (int k : gEv[pos]) for (int x : notasEv[k]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            gl = Teclado.branca(lo); gh = Teclado.branca(hi) + (Teclado.preta(hi) ? 1 : 0);
        }
        float folga = Math.min(3f, Math.max(0f, (alvoN - (gh - gl + 1)) / 2f));
        if (gl < alvoIni) alvoIni = gl - folga;
        else if (gh + 1 > alvoIni + alvoN) alvoIni = gh + 1 + folga - alvoN;
    }

    private void moverJanela(float dtReal) {
        if (janelaFixa) return;
        calcularAlvo();
        float k = 1f - (float) Math.exp(-dtReal / 160f);
        float ini = teclado.ini(), n = teclado.n();
        if (Math.abs(alvoIni - ini) > 0.01f || Math.abs(alvoN - n) > 0.01f)
            teclado.setJanela(ini + (alvoIni - ini) * k, n + (alvoN - n) * k);
    }

    // ---------- controle ----------
    void retomar() { if (m == null || acabou) return; rodando = true; ultimoQuadro = SystemClock.uptimeMillis(); invalidate(); }
    void pausar() { rodando = false; soltarAuto(); }
    void parar() { pausar(); m = null; teclado.limparGuias(); teclado.limparDemo(); }

    private void soltarAuto() {
        for (int[] a : autoAtivas) { synth.soltar(a[1]); teclado.setDemo(a[1], 0, false); }
        autoAtivas.clear();
    }

    private void avisar(String txt, int cor) { aviso = txt; avisoCor = cor; avisoEm = SystemClock.uptimeMillis(); }

    private void atualizarGuias() {
        teclado.limparGuias();
        if (demo || pos >= nG) return;
        for (int k : gEv[pos]) for (int j = 0; j < notasEv[k].length; j++)
            if (!acertou[k][j]) teclado.setGuia(notasEv[k][j], maoEv[k]);
    }

    private int mult() { return Math.min(4, 1 + combo / 8); }

    // ---------- toque ----------
    void soltou(int midi) {
        if (m == null || demo) return;
        for (int h = 0; h < nHold; h++) if (holdMidi[h] == midi) { fecharHold(h, false); h--; }
    }

    private void fecharHold(int h, boolean completa) {
        int k = holdEv[h];
        float frac = completa ? 1f : Math.max(0f, t - holdDesde[h]) / Math.max(1f, tEv[k] + dEv[k] - holdDesde[h]);
        if (frac >= 0.85f) { res.seguradas++; res.pontos += 50 * mult(); if (completa) avisar("Segurou certinho!", VERDE); }
        else if (frac >= 0.5f) { res.pontos += 20 * mult(); avisar("Quase! Segure mais", Teclado.AMBAR); }
        else avisar("Segure a nota!", Teclado.AMBAR);
        nHold--;
        holdEv[h] = holdEv[nHold]; holdMidi[h] = holdMidi[nHold]; holdDesde[h] = holdDesde[nHold];
    }

    /** O usuário tocou. Devolve se a nota deve soar (erro = silêncio). */
    boolean tocou(int midi) {
        if (m == null || demo || acabou) return true;
        if (pos >= nG) return true;
        long agora = SystemClock.uptimeMillis();
        // procura a nota no grupo atual (e, no desafio, nos próximos já dentro da janela)
        int ultimoG = desafio ? Math.min(nG - 1, pos + 3) : pos;
        for (int g = pos; g <= ultimoG; g++) {
            float falta = tG[g] - t;
            if (falta > (desafio ? JANELA_D : JANELA)) break;
            for (int k : gEv[g]) for (int j = 0; j < notasEv[k].length; j++) {
                if (notasEv[k][j] != midi || acertou[k][j]) continue;
                // pulou grupos no desafio: os anteriores viram erro
                while (pos < g) perderGrupo(pos);
                float erroMs;
                if (desafio) erroMs = Math.abs(falta);
                else erroMs = Math.max(falta, esperando ? (agora - esperaDesde) * vel : 0);
                int base;
                if (erroMs <= (desafio ? 70 : 170)) { base = 100; res.perfeitos++; avisar("Perfeito!", VERDE); }
                else if (erroMs <= (desafio ? 150 : 500)) { base = 60; avisar(desafio ? "Ótimo!" : "Boa!", Teclado.LARANJA); }
                else { base = 30; avisar(desafio ? "Boa!" : "Isso!", Teclado.AMBAR); }
                combo++; res.maxCombo = Math.max(res.maxCombo, combo);
                if (combo == 10 || combo == 25 || combo == 50 || combo % 100 == 0) avisar("Combo " + combo + "!", OURO);
                res.pontos += base * mult();
                res.acertos++;
                acertou[k][j] = true; acertoEm[k] = agora;
                faiscas(midi, maoEv[k], base == 100);
                feixe(midi, maoEv[k] == Musicas.E ? Teclado.AZUL : (base == 100 ? VERDE : Teclado.LARANJA),
                      base == 100 ? "Perfeito" : base == 60 ? (desafio ? "Ótimo" : "Boa") : "Ok");
                if (base == 100) teclado.vibrarAcerto();
                for (int h = 0; h < nHold; h++) if (holdMidi[h] == midi) { fecharHold(h, false); h--; }
                float dB = dEv[k] / (60000f / m.bpm);
                if (longa(dB, dEv[k]) && nHold < holdEv.length) {
                    holdEv[nHold] = k; holdMidi[nHold] = midi; holdDesde[nHold] = Math.max(t, tEv[k]); nHold++;
                }
                if (--faltam[g] == 0 && g == pos) { pos++; esperando = false; }
                atualizarGuias();
                invalidate();
                return true;
            }
        }
        // tecla já acertada do grupo atual ou segurando: sem penalidade
        for (int h = 0; h < nHold; h++) if (holdMidi[h] == midi) return true;
        if (t > tG[0] - JANELA) {
            res.erros++; combo = 0;
            teclado.piscarErro(midi);
            int esperado = -1;
            for (int k : gEv[pos]) for (int j = 0; j < notasEv[k].length; j++) if (!acertou[k][j] && esperado < 0) esperado = notasEv[k][j];
            if (esperado >= 0) {
                res.errosPorNota[esperado]++;
                boolean noGrupo = false;
                for (int k : gEv[pos]) for (int x : notasEv[k]) if (x == midi) noGrupo = true;
                avisar(noGrupo || isProxima(midi) ? "Calma, espere chegar" : "Ops! Era " + Teclado.nome(esperado, teclado.nomesCde()), VERMELHO);
            }
        }
        invalidate();
        return false;
    }

    private boolean isProxima(int midi) {
        if (pos + 1 >= nG) return false;
        for (int k : gEv[pos + 1]) for (int x : notasEv[k]) if (x == midi) return true;
        return false;
    }

    private void perderGrupo(int g) {
        for (int k : gEv[g]) for (int j = 0; j < notasEv[k].length; j++)
            if (!acertou[k][j]) { res.erros++; res.errosPorNota[notasEv[k][j]]++; }
        combo = 0;
        avisar("Passou!", VERMELHO);
        pos = g + 1;
        atualizarGuias();
    }

    // ---------- tempo ----------
    private void avancar() {
        long agora = SystemClock.uptimeMillis();
        float dtReal = Math.min(100, agora - ultimoQuadro);
        float dt = dtReal * vel;
        ultimoQuadro = agora;
        if (!desafio && !demo && pos < nG && t + dt >= tG[pos]) {
            t = tG[pos];
            if (!esperando) { esperando = true; esperaDesde = agora; }
        } else {
            t += dt;
        }
        if (desafio) while (pos < nG && t > tG[pos] + JANELA_D) perderGrupo(pos);
        // app toca o que não é seu (ou tudo, no autoplay)
        while (autoProx < nEv && tEv[autoProx] <= t) {
            int k = autoProx++;
            if (jogavel[k]) continue;
            for (int x : notasEv[k]) {
                synth.tocar(x, demo ? 0.72f : 0.5f);
                teclado.setDemo(x, maoEv[k], true);
                autoAtivas.add(new int[]{k, x});
                if (demo) { acertoEm[k] = agora; faiscas(x, maoEv[k], false); feixe(x, maoEv[k] == Musicas.E ? Teclado.AZUL : Teclado.LARANJA, null); }
            }
        }
        for (int i = autoAtivas.size() - 1; i >= 0; i--) {
            int[] a = autoAtivas.get(i);
            if (t >= tEv[a[0]] + dEv[a[0]] * 0.93f) {
                boolean outra = false;
                for (int j = 0; j < autoAtivas.size(); j++) if (j != i && autoAtivas.get(j)[1] == a[1] && tEv[autoAtivas.get(j)[0]] > tEv[a[0]]) outra = true;
                if (!outra) { synth.soltar(a[1]); teclado.setDemo(a[1], 0, false); }
                autoAtivas.remove(i);
            }
        }
        for (int h = 0; h < nHold; h++) if (t >= tEv[holdEv[h]] + dEv[holdEv[h]]) { fecharHold(h, true); h--; }
        moverJanela(dtReal);
        if (pos >= nG && t >= fimMs + 600) {
            acabou = true; rodando = false; soltarAuto();
            teclado.limparGuias();
            if (fim != null) fim.fim(res);
        }
    }

    // ---------- efeitos ----------
    private void faiscas(int midi, int mao, boolean forte) {
        if (!teclado.coluna(midi, col)) return;
        float cx = col[0] + col[1] / 2, cy = getHeight() - 6 * dp;
        int cor = mao == Musicas.E ? Teclado.AZUL : (forte ? OURO : Teclado.LARANJA);
        int n = forte ? 16 : 8;
        for (int i = 0; i < n; i++) {
            int p = pProx; pProx = (pProx + 1) % NP;
            double a = Math.PI * (1.1 + 0.8 * Math.random());
            float sp = (0.25f + 0.45f * (float) Math.random()) * dp;
            px[p] = cx; py[p] = cy; pvx[p] = (float) Math.cos(a) * sp; pvy[p] = (float) Math.sin(a) * sp * 1.6f;
            pv[p] = 1f; pc[p] = cor;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        if (alturaFundo != H) {
            alturaFundo = H;
            pFundo.setShader(new LinearGradient(0, 0, 0, H, 0xFF0B0B10, 0xFF1C1424, Shader.TileMode.CLAMP));
        }
        c.drawRect(0, 0, W, H, pFundo);
        if (m == null) return;
        if (rodando) avancar();
        long agora = SystemClock.uptimeMillis();
        float linhaY = H - 6 * dp;
        float pxMs = linhaY / HORIZONTE;

        // faixas das brancas (acompanham a janela do teclado)
        float wb = W / teclado.n();
        int g0 = (int) Math.floor(teclado.ini());
        for (int g = g0; g <= g0 + teclado.n() + 1; g++) if (g % 2 == 0) {
            float x = (g - teclado.ini()) * wb;
            c.drawRect(x, 0, x + wb, linhaY, pFaixa);
        }
        // separadores de seção
        if (secT != null) {
            pPlacar.setTextAlign(Paint.Align.LEFT);
            for (int i = 0; i < secT.length; i++) {
                float y = linhaY - (secT[i] - t) * pxMs;
                if (y < 0 || y > linhaY - 2 * dp) continue;
                pFaixa.setColor(0x33FFFFFF);
                c.drawRect(0, y - dp * 0.5f, W, y + dp * 0.5f, pFaixa);
                pFaixa.setColor(0x10FFFFFF);
                if (y < 52 * dp) continue;            // perto do topo: o rótulo da seção atual já aparece
                pPlacar.setColor(0x99FFFFFF); pPlacar.setTextSize(11 * dp);
                c.drawText("▸ " + secR[i], W - 8 * dp - pPlacar.measureText("▸ " + secR[i]), y - 4 * dp, pPlacar);
            }
        }

        // feixes de luz subindo da tecla acertada (atrás das notas)
        for (int i = 0; i < NB; i++) {
            if (bEm[i] == 0 || agora - bEm[i] > 650 || !teclado.coluna(bMidi[i], col)) continue;
            float q = (agora - bEm[i]) / 650f;
            float topo = linhaY - (120 + 100 * q) * dp;
            int a = (int) (170 * (1 - q) * (1 - q));
            int rgb = bCor[i] & 0x00FFFFFF;
            pFeixe.setShader(new LinearGradient(0, topo, 0, linhaY, rgb, rgb | (a << 24), Shader.TileMode.CLAMP));
            c.drawRect(col[0] + col[1] * 0.08f, topo, col[0] + col[1] * 0.92f, linhaY, pFeixe);
        }
        pFeixe.setShader(null);

        // linha de toque (dourada com multiplicador máximo; brilha a cada acerto)
        int corLinha = mult() >= 4 && !demo ? OURO : Teclado.LARANJA;
        float fl = Math.max(0, 1 - (agora - flashEm) / 220f);
        pGlow.setColor(corLinha); pGlow.setAlpha((int) (90 + 140 * fl));
        c.drawRect(0, linhaY - (3 + 5 * fl) * dp, W, linhaY + (3 + 5 * fl) * dp, pGlow);
        pLinha.setColor(corLinha);
        pLinha.setAlpha(esperando ? 255 : 210);
        pLinha.setStrokeWidth((3 + 2 * fl) * dp);
        c.drawLine(0, linhaY, W, linhaY, pLinha);

        // notas (pula as que já passaram há mais de 1,5 s)
        float batidaMs = 60000f / m.bpm;
        while (primeiro < nEv && tEv[primeiro] + dEv[primeiro] < t - 1500 && (demo || !jogavel[primeiro] || pos >= nG || tEv[primeiro] < tG[pos])) primeiro++;
        for (int k = primeiro; k < nEv; k++) {
            float baseY = linhaY - (tEv[k] - t) * pxMs;
            if (baseY < 0) break;
            float alt = Math.max(dEv[k] * pxMs - 4 * dp, 22 * dp);
            if (baseY - alt > H && acertoEm[k] == 0 && !jogavel[k]) continue;
            float dB = dEv[k] / batidaMs;
            boolean ehLonga = longa(dB, dEv[k]);
            for (int j = 0; j < notasEv[k].length; j++) {
                int midi = notasEv[k][j];
                if (!teclado.coluna(midi, col)) continue;
                int corBase = maoEv[k] == Musicas.E ? (col[2] > 0 ? Teclado.AZUL_ESC : Teclado.AZUL) : (col[2] > 0 ? Teclado.AMBAR : Teclado.LARANJA);
                if (!jogavel[k] && !demo) {          // acompanhamento: fino e translúcido
                    if (baseY - alt > linhaY) continue;
                    float pad = col[1] * 0.3f;
                    r.set(col[0] + pad, baseY - alt, col[0] + col[1] - pad, Math.min(baseY, linhaY));
                    pNota.setColor((corBase & 0x00FFFFFF) | 0x55000000);
                    c.drawRoundRect(r, 4 * dp, 4 * dp, pNota);
                    continue;
                }
                boolean hit = demo ? acertoEm[k] != 0 : acertou[k][j];
                float frac = -1;
                if (hit) {
                    if (demo) { if (t < tEv[k] + dEv[k] * 0.93f) frac = (t - tEv[k]) / dEv[k]; }
                    else for (int h = 0; h < nHold; h++) if (holdEv[h] == k && holdMidi[h] == midi)
                        frac = (t - holdDesde[h]) / Math.max(1f, tEv[k] + dEv[k] - holdDesde[h]);
                }
                float cx = col[0] + col[1] / 2;
                if (hit && frac >= 0) {              // segurando: a cauda é consumida na linha
                    float topo = baseY - alt;
                    if (topo < linhaY) {
                        float pad = col[1] * 0.06f;
                        r.set(col[0] + pad, Math.max(0, topo), col[0] + col[1] - pad, linhaY);
                        pGlow.setColor(corBase); pGlow.setAlpha(200);
                        c.drawRoundRect(r, 7 * dp, 7 * dp, pGlow);
                        pNota.setColor(maoEv[k] == Musicas.E ? 0xFFB3E1FF : 0xFFFFE0B2);
                        c.drawRoundRect(r, 7 * dp, 7 * dp, pNota);
                        pAnel.setColor(corBase); pAnel.setAlpha(255); pAnel.setStrokeWidth(2.5f * dp);
                        c.drawRoundRect(r, 7 * dp, 7 * dp, pAnel);
                        // anel de progresso do segurar em volta da tecla
                        float rr = Math.max(14 * dp, col[1] * 0.42f);
                        r2.set(cx - rr, linhaY - rr, cx + rr, linhaY + rr);
                        pAnel.setColor(0x55FFFFFF); pAnel.setStrokeWidth(4 * dp);
                        c.drawCircle(cx, linhaY, rr, pAnel);
                        pAnel.setColor(frac > 0.85f ? VERDE : OURO); pAnel.setAlpha(255);
                        c.drawArc(r2, -90, 360 * Math.min(1f, frac), false, pAnel);
                        if (agora - ultimaFaisca > 60) { ultimaFaisca = agora; faiscas(midi, maoEv[k], false); }
                        if (!demo) { pJulg.setColor(0xFFFFFFFF); pJulg.setTextSize(12 * dp);
                            c.drawText("SEGURE", cx, linhaY - rr - 6 * dp, pJulg); }
                    }
                    continue;
                }
                if (hit) {                           // anel que se expande
                    long dtA = agora - acertoEm[k];
                    if (dtA < 350) {
                        float q = dtA / 350f;
                        pAnel.setColor(corBase); pAnel.setAlpha((int) (255 * (1 - q)));
                        pAnel.setStrokeWidth(4 * dp * (1 - q) + 1);
                        c.drawCircle(cx, linhaY, col[1] * 0.3f + 30 * dp * q, pAnel);
                    }
                    continue;
                }
                float topo = baseY - alt;
                if (topo > H) continue;
                float pad = col[1] * 0.08f;
                float fundo = Math.min(baseY, linhaY);
                boolean atual = !demo && pos < nG && Math.abs(tEv[k] - tG[pos]) < 2f;
                float cab = ehLonga ? Math.min(alt, 30 * dp) : alt;      // nota longa = cabeça + cauda
                if (ehLonga) {
                    float lw = col[1] * 0.3f;
                    r2.set(cx - lw, topo, cx + lw, baseY - cab + 6 * dp);
                    if (r2.bottom > r2.top) {
                        pNota.setColor((corBase & 0x00FFFFFF) | 0xB0000000);
                        c.drawRoundRect(r2, lw, lw, pNota);
                        pAnel.setColor(0xFFFFFFFF); pAnel.setAlpha(150); pAnel.setStrokeWidth(1.5f * dp);
                        c.drawRoundRect(r2, lw, lw, pAnel);
                        pNota.setColor(0x99FFFFFF);            // um risco por tempo: mostra quanto segurar
                        for (int b = 1; b < (int) Math.ceil(dB - 0.01f); b++) {
                            float yb = baseY - b * batidaMs * pxMs;
                            if (yb < r2.top + 3 * dp || yb > r2.bottom - 2 * dp) continue;
                            c.drawRect(cx - lw * 0.7f, yb - dp * 0.75f, cx + lw * 0.7f, yb + dp * 0.75f, pNota);
                        }
                        if (r2.height() > 64 * dp && lw * 2 > 11 * dp) {
                            c.save(); c.rotate(-90, cx, (r2.top + r2.bottom) / 2);
                            pJulg.setColor(0xDDFFFFFF); pJulg.setTextSize(Math.min(11 * dp, lw * 1.3f));
                            c.drawText("segure " + Math.round(dB) + (Math.round(dB) == 1 ? " tempo" : " tempos"), cx, (r2.top + r2.bottom) / 2 + 4 * dp, pJulg);
                            c.restore();
                        }
                    }
                }
                r.set(col[0] + pad, baseY - cab, col[0] + col[1] - pad, fundo);
                if (r.bottom <= r.top) continue;
                if (atual) {                          // brilho no alvo da vez
                    pGlow.setColor(corBase); pGlow.setAlpha(230);
                    r2.set(r.left - 3 * dp, r.top - 3 * dp, r.right + 3 * dp, r.bottom + 3 * dp);
                    c.drawRoundRect(r2, 9 * dp, 9 * dp, pGlow);
                }
                pNota.setColor(corBase);
                if (!atual) pNota.setAlpha(215);
                c.drawRoundRect(r, 7 * dp, 7 * dp, pNota);
                pNota.setColor(0x33FFFFFF);               // reflexo no topo
                r2.set(r.left + 2 * dp, r.top + 2 * dp, r.right - 2 * dp, r.top + Math.min(r.height() * 0.4f, 10 * dp));
                c.drawRoundRect(r2, 5 * dp, 5 * dp, pNota);
                if (atual) c.drawRoundRect(r, 7 * dp, 7 * dp, pBorda);
                if (r.height() > 20 * dp && r.width() > 16 * dp) {
                    pTexto.setTextSize(Math.min(14 * dp, r.width() * 0.42f));
                    c.drawText(Teclado.nome(midi, teclado.nomesCde()), cx, r.bottom - 7 * dp, pTexto);
                }
            }
        }

        // partículas
        float dtP = ultimoP == 0 ? 16 : Math.min(50, agora - ultimoP);
        ultimoP = agora;
        boolean vivas = false, vivasFx = agora - flashEm < 650;
        for (int i = 0; i < NP; i++) {
            if (pv[i] <= 0) continue;
            vivas = true;
            px[i] += pvx[i] * dtP; py[i] += pvy[i] * dtP; pvy[i] += 0.0022f * dp * dtP;
            pv[i] -= dtP / 520f;
            pPart.setColor(pc[i]); pPart.setAlpha((int) (255 * Math.max(0, pv[i])));
            c.drawCircle(px[i], py[i], 2.6f * dp * (0.4f + pv[i]), pPart);
        }

        // julgamento sobre a tecla ("Perfeito", "Boa"...)
        for (int i = 0; i < NB; i++) {
            if (bTxt[i] == null || agora - bEm[i] > 650 || !teclado.coluna(bMidi[i], col)) continue;
            float q = (agora - bEm[i]) / 650f;
            pJulg.setColor(bCor[i]); pJulg.setAlpha((int) (255 * (1 - q * q)));
            pJulg.setTextSize(13 * dp * (1.1f - 0.1f * q));
            c.drawText(bTxt[i], col[0] + col[1] / 2, linhaY - (34 + 46 * q) * dp, pJulg);
            vivasFx = true;
        }
        // borda dourada com multiplicador máximo
        if (!demo && mult() >= 4) {
            int a = (int) (60 + 40 * Math.sin(agora / 260.0));
            pFeixe.setShader(new LinearGradient(0, 0, 18 * dp, 0, (a << 24) | (OURO & 0xFFFFFF), OURO & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 18 * dp, linhaY, pFeixe);
            pFeixe.setShader(new LinearGradient(W, 0, W - 18 * dp, 0, (a << 24) | (OURO & 0xFFFFFF), OURO & 0xFFFFFF, Shader.TileMode.CLAMP));
            c.drawRect(W - 18 * dp, 0, W, linhaY, pFeixe);
            pFeixe.setShader(null);
        }

        // placar
        pPlacar.setTextAlign(Paint.Align.LEFT);
        if (!demo) {
            pPlacar.setColor(0xFFFFFFFF); pPlacar.setTextSize(22 * dp);
            c.drawText(String.valueOf(res.pontos), 12 * dp, 30 * dp, pPlacar);
            if (combo >= 2) {
                pPlacar.setColor(mult() >= 4 ? OURO : Teclado.LARANJA); pPlacar.setTextSize(15 * dp);
                c.drawText("combo " + combo + (mult() > 1 ? "  ×" + mult() : ""), 12 * dp, 52 * dp, pPlacar);
            }
            if (res.acertos + res.erros > 0) {
                pPlacar.setColor(0xFFBBBBBB); pPlacar.setTextSize(12 * dp);
                c.drawText(Math.round(res.precisao()) + "% de precisão" + (desafio ? "  ·  DESAFIO" : ""), 12 * dp, 70 * dp, pPlacar);
            }
        } else {
            pPlacar.setColor(0xFFBBBBBB); pPlacar.setTextSize(15 * dp);
            c.drawText("Autoplay: só assistir. Depois é sua vez", 12 * dp, 28 * dp, pPlacar);
        }
        String sec = secaoAtual();
        if (sec.length() > 0) {
            pPlacar.setTextAlign(Paint.Align.RIGHT);
            pPlacar.setColor(0xCCFFFFFF); pPlacar.setTextSize(14 * dp);
            c.drawText(sec, W - 10 * dp, 26 * dp, pPlacar);
        }
        // progresso com marcas de seção
        pPlacar.setColor(0xFF2A2A32);
        c.drawRect(0, 0, W, 4 * dp, pPlacar);
        float prog = fimMs > ANTECEDENCIA ? Math.max(0, Math.min(1f, (t - ANTECEDENCIA) / (fimMs - ANTECEDENCIA))) : 0;
        pPlacar.setColor(Teclado.LARANJA);
        c.drawRect(0, 0, W * prog, 4 * dp, pPlacar);
        if (secT != null) {
            pPlacar.setColor(0xAAFFFFFF);
            for (float st : secT) {
                float x = W * (st - ANTECEDENCIA) / Math.max(1, fimMs - ANTECEDENCIA);
                if (x > 1) c.drawRect(x - dp, 0, x + dp, 6 * dp, pPlacar);
            }
        }

        // contagem 3, 2, 1
        float falta0 = (nG > 0 ? tG[0] : (nEv > 0 ? tEv[0] : 0)) - t;
        if (falta0 > 0 && pos == 0 && t < ANTECEDENCIA + 10) {
            int n = (int) Math.ceil(falta0 / 1000f);
            pAviso.setColor(0xFFFFFFFF); pAviso.setTextSize(64 * dp);
            if (n <= 3) c.drawText(String.valueOf(n), W / 2f, H * 0.5f, pAviso);
        } else if (aviso != null && agora - avisoEm < 700) {
            float q = (agora - avisoEm) / 700f;
            pAviso.setColor(avisoCor);
            pAviso.setAlpha((int) (255 * (1 - q * q)));
            pAviso.setTextSize((aviso.length() > 12 ? 24 : 34) * dp * (1.15f - 0.15f * q));
            c.drawText(aviso, W / 2f, H * 0.42f, pAviso);
        }

        if (rodando || nHold > 0 || vivas || vivasFx || (aviso != null && agora - avisoEm < 700)) postInvalidateOnAnimation();
    }
}
