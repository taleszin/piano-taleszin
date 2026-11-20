package app.taleszin.piano;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Sintetizador do celular (sem amostras): estéreo em float, baixa latência, 32 vozes, força do toque.
 *  - ACUSTICO: piano por modos ressonantes (parciais inarmônicos com decaimentos próprios, 2 cordas
 *    levemente desafinadas nos graves, ruído de martelo, abafador ao soltar).
 *  - ELETRICO: FM 1:1 com sininho 14:1 (estilo E.Piano), mais brilhante com mais força.
 * Reverb leve (Freeverb reduzido) e pan pela altura da nota.
 */
final class Sintetizador implements Runnable {
    static final int ACUSTICO = 0, ELETRICO = 1;
    private static final int VOZES = 32;
    private static final int P = 12;           // ressonadores por voz (acústico)
    private static final int TAB = 4096;

    private final int taxa;
    private final int bloco;
    private final AudioTrack trilha;
    private volatile boolean rodando;
    private Thread thread;
    private final float[] seno = new float[TAB + 1];

    // estado por voz (protegido por "this")
    private final int[] nota = new int[VOZES];
    private final int[] tim = new int[VOZES];
    private final long[] idade = new long[VOZES];
    private final boolean[] solta = new boolean[VOZES];
    private final float[] ganho = new float[VOZES];      // envelope de saída (abafador / fade de roubo)
    private final float[] queda = new float[VOZES];      // multiplicador do ganho por amostra (1 = parado)
    private final float[] nivel = new float[VOZES];      // estimativa de amplitude (para liberar a voz)
    private final float[] qNivel = new float[VOZES];
    private final float[] gL = new float[VOZES], gR = new float[VOZES];
    private final float[] forca = new float[VOZES];
    // acústico
    private final double[][] c1 = new double[VOZES][P], r2 = new double[VOZES][P], y1 = new double[VOZES][P], y2 = new double[VOZES][P];
    private final int[] nP = new int[VOZES];
    private final float[] ruido = new float[VOZES], qRuido = new float[VOZES], lpRuido = new float[VOZES], aRuido = new float[VOZES];
    private int semente = 0x1234567;
    // elétrico
    private final float[] fase = new float[VOZES], passo = new float[VOZES], indice = new float[VOZES], qIndice = new float[VOZES];
    private final float[] sino = new float[VOZES], qSino = new float[VOZES], fSino = new float[VOZES], pSino = new float[VOZES];
    private final float[] env = new float[VOZES], ataque = new float[VOZES];

    private long relogio;
    private boolean sustentar;
    private int timbre = ACUSTICO;
    private volatile boolean reverbLigado = true;
    private final Reverb reverb;
    private int underruns;

    Sintetizador(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int t = 48000, fpb = 256;
        try { t = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)); } catch (Exception ignored) { }
        try { fpb = Integer.parseInt(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)); } catch (Exception ignored) { }
        if (t < 22050 || t > 96000) t = 48000;
        taxa = t;
        while (fpb > 512) fpb /= 2;
        bloco = Math.max(64, fpb);
        for (int i = 0; i <= TAB; i++) seno[i] = (float) Math.sin(2 * Math.PI * i / TAB);
        for (int v = 0; v < VOZES; v++) nota[v] = -1;
        reverb = new Reverb(taxa);
        AudioTrack tr;
        int min = AudioTrack.getMinBufferSize(taxa, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        tr = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(taxa)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(Math.max(min, bloco * 8 * 4))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();
        trilha = tr;
        try { trilha.setBufferSizeInFrames(bloco * 2); } catch (Exception ignored) { }
    }

    void iniciar() {
        if (rodando) return;
        rodando = true;
        trilha.play();
        thread = new Thread(this, "piano-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void parar() {
        rodando = false;
        if (thread != null) try { thread.join(500); } catch (InterruptedException ignored) { }
        thread = null;
        try { trilha.pause(); trilha.flush(); } catch (IllegalStateException ignored) { }
        synchronized (this) { for (int v = 0; v < VOZES; v++) nota[v] = -1; }
        reverb.limpar();
    }

    void liberar() { parar(); trilha.release(); }

    synchronized void setSustentar(boolean s) {
        sustentar = s;
        if (!s) for (int v = 0; v < VOZES; v++) if (nota[v] >= 0 && solta[v]) abafar(v);
    }

    synchronized void setTimbre(int t) { timbre = t; }
    void setReverb(boolean r) { reverbLigado = r; }

    private float fq(float segundos) { return (float) Math.exp(Math.log(0.001) / (segundos * taxa)); }

    void tocar(int midi) { tocar(midi, 0.75f); }

    /** v = força 0..1 */
    synchronized void tocar(int midi, float v) {
        if (midi < 12 || midi > 120) return;
        v = Math.max(0.05f, Math.min(1f, v));
        // escolhe voz: mesma tecla > livre > a mais fraca
        int livre = -1; float menor = Float.MAX_VALUE;
        for (int k = 0; k < VOZES; k++) {
            if (nota[k] == midi) { livre = k; break; }
            if (nota[k] < 0) { if (menor > -1) { livre = k; menor = -1; } }
            else if (menor > -1) {
                float n = nivel[k] * ganho[k] * (solta[k] ? 0.5f : 1f);
                if (n < menor) { menor = n; livre = k; }
            }
        }
        int k = livre;
        double f0 = 440.0 * Math.pow(2, (midi - 69) / 12.0);
        nota[k] = midi; tim[k] = timbre; solta[k] = false; idade[k] = ++relogio; forca[k] = v;
        float g = (float) Math.pow(v, 1.6);
        float p = Math.max(-0.6f, Math.min(0.6f, (midi - 60) / 48f * 0.9f));
        gL[k] = (float) Math.cos((p + 1) * Math.PI / 4) * g;
        gR[k] = (float) Math.sin((p + 1) * Math.PI / 4) * g;
        ganho[k] = 1f; queda[k] = 1f;
        if (timbre == ACUSTICO) {
            double B = 1.2e-4 * Math.exp(0.045 * (midi - 60));
            double tau1 = Math.max(1.6, Math.min(12, 7.5 * Math.pow(2, -(midi - 48) / 22.0)));
            double lim = Math.min(0.45 * taxa, 9000);
            int n = 0;
            double amax = 0;
            for (int h = 1; h <= 10 && n < P; h++) {
                double fh = h * f0 * Math.sqrt(1 + B * h * h);
                if (fh > lim) break;
                double a = Math.abs(Math.sin(h * Math.PI / 7.3)) / Math.pow(h, 0.9) * Math.exp(-h * 0.35 * (1 - v));
                double tau = tau1 / (1 + 0.22 * (h - 1)) * (h == 1 ? 1 : 0.8);
                n = ressonador(k, n, fh, tau, a);
                amax += a;
                if (h <= 2 && n < P) n = ressonador(k, n, fh * Math.pow(2, 0.9 / 1200), tau * 0.35, a * 0.55); // 2ª corda
            }
            nP[k] = n;
            double norm = 0.55 / Math.max(0.6, amax);
            for (int i = 0; i < n; i++) { y2[k][i] *= norm; } // (y1 = 0; a amplitude está em y2)
            ruido[k] = 0.09f * v; qRuido[k] = fq(0.012f);
            aRuido[k] = (float) Math.min(0.9, (1500 + 3500 * v) * 2 * Math.PI / taxa);
            lpRuido[k] = 0;
            nivel[k] = 1f; qNivel[k] = fq((float) tau1 * 6.9f);
        } else {
            fase[k] = 0;
            passo[k] = (float) (f0 * TAB / taxa);
            float dur = (float) Math.max(1.0, 4.5 - (midi - 48) * 0.07);
            float ind = (float) (Math.max(0.5, 1.9 - (midi - 60) * 0.03) * (0.5 + v));
            indice[k] = (float) (ind * TAB / (2 * Math.PI));
            qIndice[k] = fq(1.4f);
            pSino[k] = (float) (f0 * 14 * TAB / taxa);
            sino[k] = f0 * 14 < taxa * 0.42 ? 0.18f * v * v + 0.04f : 0f;
            qSino[k] = fq(0.22f);
            fSino[k] = 0;
            env[k] = 0.0001f; ataque[k] = 1f / (0.003f * taxa);
            nivel[k] = 1f; qNivel[k] = fq(dur);
        }
    }

    private int ressonador(int k, int i, double f, double tau, double a) {
        double w = 2 * Math.PI * f / taxa;
        double r = Math.exp(-1.0 / (tau * taxa));
        c1[k][i] = 2 * r * Math.cos(w);
        r2[k][i] = r * r;
        y1[k][i] = 0;
        y2[k][i] = -a * Math.sin(w) / r;     // y[-1], para que y[n] = a·rⁿ·sen(wn)
        return i + 1;
    }

    private void abafar(int k) {
        int m = nota[k];
        if (tim[k] == ACUSTICO) {
            if (m > 88) return;                       // agudos sem abafador, como no piano
            queda[k] = fq(m < 48 ? 0.45f : 0.28f);
        } else queda[k] = fq(0.25f);
    }

    synchronized void soltar(int midi) {
        for (int k = 0; k < VOZES; k++) {
            if (nota[k] == midi && !solta[k]) {
                solta[k] = true;
                if (!sustentar) abafar(k);
            }
        }
    }

    synchronized void soltarTudo() {
        for (int k = 0; k < VOZES; k++) if (nota[k] >= 0) { solta[k] = true; queda[k] = fq(0.2f); }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        final int n = bloco;
        float[] L = new float[n], R = new float[n], mono = new float[n], saida = new float[n * 2];
        while (rodando) {
            java.util.Arrays.fill(L, 0f); java.util.Arrays.fill(R, 0f); java.util.Arrays.fill(mono, 0f);
            synchronized (this) {
                for (int k = 0; k < VOZES; k++) {
                    if (nota[k] < 0) continue;
                    if (tim[k] == ACUSTICO) vozAcustica(k, mono, n); else vozEletrica(k, mono, n);
                    // mixa a voz (está em mono[]) no estéreo e zera para a próxima
                    float a = gL[k], b = gR[k], g = ganho[k], q = queda[k];
                    for (int i = 0; i < n; i++) {
                        float s = mono[i] * g;
                        L[i] += s * a; R[i] += s * b;
                        mono[i] = 0f;
                        g *= q;
                    }
                    ganho[k] = g;
                    nivel[k] *= (float) Math.pow(qNivel[k], n);
                    if (g * nivel[k] < 2e-4f) nota[k] = -1;
                }
            }
            if (reverbLigado) reverb.processar(L, R, n);
            for (int i = 0; i < n; i++) {
                float l = L[i] * 0.55f, r = R[i] * 0.55f;
                saida[2 * i] = l / (1f + Math.abs(l) * 0.5f);
                saida[2 * i + 1] = r / (1f + Math.abs(r) * 0.5f);
            }
            trilha.write(saida, 0, n * 2, AudioTrack.WRITE_BLOCKING);
            int u = trilha.getUnderrunCount();
            if (u > underruns) {
                underruns = u;
                int atual = trilha.getBufferSizeInFrames();
                if (atual < bloco * 8) try { trilha.setBufferSizeInFrames(atual + bloco); } catch (Exception ignored) { }
            }
        }
    }

    private void vozAcustica(int k, float[] out, int n) {
        int np = nP[k];
        double[] c = c1[k], rr = r2[k], a1 = y1[k], a2 = y2[k];
        for (int p = 0; p < np; p++) {
            double cc = c[p], r = rr[p], s1 = a1[p], s2 = a2[p];
            if (Math.abs(s1) + Math.abs(s2) < 1e-6) continue;
            for (int i = 0; i < n; i++) {
                double y = cc * s1 - r * s2;
                s2 = s1; s1 = y;
                out[i] += (float) y;
            }
            a1[p] = s1; a2[p] = s2;
        }
        // martelo: ruído filtrado bem curto
        float rn = ruido[k];
        if (rn > 1e-4f) {
            float q = qRuido[k], al = aRuido[k], lp = lpRuido[k];
            int sd = semente;
            for (int i = 0; i < n; i++) {
                sd ^= sd << 13; sd ^= sd >>> 17; sd ^= sd << 5;
                float w = (sd & 0xFFFF) / 32768f - 1f;
                lp += al * (w - lp);
                out[i] += lp * rn;
                rn *= q;
            }
            semente = sd; ruido[k] = rn; lpRuido[k] = lp;
        }
    }

    private void vozEletrica(int k, float[] out, int n) {
        float f = fase[k], p = passo[k], e = env[k], at = ataque[k];
        float ind = indice[k], qi = qIndice[k], sa = sino[k], qs = qSino[k], fs = fSino[k], ps = pSino[k];
        float qn = qNivel[k];
        boolean subindo = at > 0;
        final int M = TAB - 1;
        for (int i = 0; i < n; i++) {
            int fi = (int) f; float fr = f - fi;
            float mod = (seno[fi] + (seno[fi + 1] - seno[fi]) * fr) * ind;
            float g = f + mod; g -= (float) Math.floor(g / TAB) * TAB;
            int gi = (int) g; float gr = g - gi; gi &= M;
            float s = seno[gi] + (seno[gi + 1] - seno[gi]) * gr + sa * seno[(int) fs];
            out[i] += s * e * 0.7f;
            f += p; if (f >= TAB) f -= TAB;
            fs += ps; while (fs >= TAB) fs -= TAB;
            if (subindo) { e += at; if (e >= 1f) { e = 1f; subindo = false; } }
            else e *= qn;
            ind *= qi; sa *= qs;
        }
        fase[k] = f; env[k] = e; ataque[k] = subindo ? at : 0;
        indice[k] = ind; sino[k] = sa; fSino[k] = fs;
        nivel[k] = subindo ? 1f : e / (float) Math.pow(qn, n);  // run() aplica qⁿ de novo
    }

    /** Freeverb reduzido: 6 combs + 2 allpass por canal. */
    static final class Reverb {
        private final float[][] cb = new float[12][], ap = new float[4][];
        private final int[] ci = new int[12], ai = new int[4];
        private final float[] cf = new float[12];
        private static final float FEED = 0.80f, DAMP = 0.35f, WET = 0.16f;

        Reverb(int taxa) {
            int[] c = {1116, 1188, 1277, 1356, 1422, 1491};
            int[] a = {556, 441};
            float k = taxa / 44100f;
            for (int i = 0; i < 6; i++) { cb[i] = new float[(int) (c[i] * k)]; cb[i + 6] = new float[(int) ((c[i] + 23) * k)]; }
            for (int i = 0; i < 2; i++) { ap[i] = new float[(int) (a[i] * k)]; ap[i + 2] = new float[(int) ((a[i] + 23) * k)]; }
        }

        void limpar() {
            for (float[] b : cb) java.util.Arrays.fill(b, 0);
            for (float[] b : ap) java.util.Arrays.fill(b, 0);
            java.util.Arrays.fill(cf, 0);
        }

        void processar(float[] L, float[] R, int n) {
            for (int i = 0; i < n; i++) {
                float in = (L[i] + R[i]) * 0.5f * 0.3f;
                float ol = 0, or = 0;
                for (int j = 0; j < 6; j++) { ol += comb(j, in); or += comb(j + 6, in); }
                ol = allpass(1, allpass(0, ol));
                or = allpass(3, allpass(2, or));
                L[i] += ol * WET; R[i] += or * WET;
            }
        }

        private float comb(int j, float in) {
            float[] b = cb[j]; int i = ci[j];
            float o = b[i];
            cf[j] = o * (1 - DAMP) + cf[j] * DAMP;
            b[i] = in + cf[j] * FEED;
            ci[j] = (i + 1 == b.length) ? 0 : i + 1;
            return o;
        }

        private float allpass(int j, float in) {
            float[] b = ap[j]; int i = ai[j];
            float bo = b[i];
            b[i] = in + bo * 0.5f;
            ai[j] = (i + 1 == b.length) ? 0 : i + 1;
            return bo - in;
        }
    }
}
