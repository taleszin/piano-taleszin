package app.taleszin.piano;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Sintetizador leve: sem amostras (o SoundPool do 4.4 recusa PCM > 1 MB).
 * Cada nota mistura um timbre "brilhante" (muitos harmônicos) que some rápido
 * com um timbre "redondo" que dura mais — soa como corda martelada.
 */
final class Sintetizador implements Runnable {
    private static final int VOZES = 10;
    private static final int TAB = 2048;
    private static final int BLOCO = 256;

    private final float[] brilho = new float[TAB];
    private final float[] redondo = new float[TAB];
    private final int taxa;
    private final AudioTrack trilha;
    private volatile boolean rodando;
    private Thread thread;

    // Estado das vozes (protegido por "this")
    private final int[] nota = new int[VOZES];
    private final float[] fase = new float[VOZES];
    private final float[] passo = new float[VOZES];
    private final float[] env = new float[VOZES];
    private final float[] mixBrilho = new float[VOZES];
    private final float[] queda = new float[VOZES];
    private final float[] quedaBrilho = new float[VOZES];
    private final float[] ataque = new float[VOZES];
    private final boolean[] solta = new boolean[VOZES];
    private final long[] idade = new long[VOZES];
    private long relogio;
    private boolean sustentar;

    Sintetizador() {
        int nativa = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        taxa = (nativa > 0 && nativa <= 48000) ? nativa : 44100;
        float[] hb = {1f, 0.55f, 0.38f, 0.22f, 0.16f, 0.10f, 0.07f, 0.05f};
        float[] hr = {1f, 0.30f, 0.08f};
        for (int i = 0; i < TAB; i++) {
            double t = 2 * Math.PI * i / TAB;
            double a = 0, b = 0;
            for (int h = 0; h < hb.length; h++) a += hb[h] * Math.sin((h + 1) * t);
            for (int h = 0; h < hr.length; h++) b += hr[h] * Math.sin((h + 1) * t);
            brilho[i] = (float) (a / 1.9);
            redondo[i] = (float) (b / 1.2);
        }
        for (int v = 0; v < VOZES; v++) nota[v] = -1;
        int min = AudioTrack.getMinBufferSize(taxa, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        trilha = new AudioTrack(AudioManager.STREAM_MUSIC, taxa, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(min, BLOCO * 4 * 2), AudioTrack.MODE_STREAM);
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
    }

    void liberar() { parar(); trilha.release(); }

    synchronized void setSustentar(boolean s) {
        sustentar = s;
        if (!s) for (int v = 0; v < VOZES; v++) if (nota[v] >= 0 && solta[v]) queda[v] = fatorQueda(0.18f);
    }

    private float fatorQueda(float segundos) {
        // multiplicador por amostra para cair ~60 dB em "segundos"
        return (float) Math.exp(Math.log(0.001) / (segundos * taxa));
    }

    synchronized void tocar(int midi) {
        int livre = -1;
        long maisVelha = Long.MAX_VALUE;
        for (int v = 0; v < VOZES; v++) {
            if (nota[v] == midi) { livre = v; break; }               // re-ataque da mesma tecla
            if (nota[v] < 0) { livre = v; maisVelha = Long.MIN_VALUE; }
            else if (maisVelha != Long.MIN_VALUE && idade[v] < maisVelha) { maisVelha = idade[v]; livre = v; }
        }
        double freq = 440.0 * Math.pow(2, (midi - 69) / 12.0);
        nota[livre] = midi;
        passo[livre] = (float) (freq * TAB / taxa);
        if (env[livre] < 0.0001f) fase[livre] = 0;
        // graves soam mais tempo que agudos
        float dur = (float) Math.max(1.2, 6.0 - (midi - 48) * 0.09);
        queda[livre] = fatorQueda(dur);
        quedaBrilho[livre] = fatorQueda(dur * 0.25f);
        mixBrilho[livre] = 1f;
        ataque[livre] = 1f / (0.004f * taxa);
        env[livre] = Math.max(env[livre], 0.0001f);
        solta[livre] = false;
        idade[livre] = ++relogio;
    }

    synchronized void soltar(int midi) {
        for (int v = 0; v < VOZES; v++) {
            if (nota[v] == midi && !solta[v]) {
                solta[v] = true;
                if (!sustentar) queda[v] = fatorQueda(0.18f);
            }
        }
    }

    synchronized void soltarTudo() {
        for (int v = 0; v < VOZES; v++) if (nota[v] >= 0) { solta[v] = true; queda[v] = fatorQueda(0.18f); }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] saida = new short[BLOCO];
        float[] acc = new float[BLOCO];
        while (rodando) {
            java.util.Arrays.fill(acc, 0f);
            boolean algum = false;
            synchronized (this) {
                for (int v = 0; v < VOZES; v++) {
                    if (nota[v] < 0) continue;
                    algum = true;
                    float f = fase[v], p = passo[v], e = env[v], mb = mixBrilho[v];
                    float q = queda[v], qb = quedaBrilho[v], at = ataque[v];
                    boolean subindo = at > 0;
                    for (int i = 0; i < BLOCO; i++) {
                        int idx = (int) f;
                        float s = mb * brilho[idx] + (1f - mb) * redondo[idx];
                        acc[i] += s * e;
                        f += p;
                        if (f >= TAB) f -= TAB;
                        if (subindo) { e += at; if (e >= 1f) { e = 1f; subindo = false; } }
                        else e *= q;
                        mb *= qb;
                    }
                    fase[v] = f; env[v] = e; mixBrilho[v] = mb; ataque[v] = subindo ? at : 0;
                    if (!subindo && e < 0.0005f) nota[v] = -1;
                }
            }
            for (int i = 0; i < BLOCO; i++) {
                float x = acc[i] * 0.32f;
                x = x / (1f + Math.abs(x) * 0.6f);          // limitador suave: vários dedos não estouram
                saida[i] = (short) (x * 30000f);
            }
            if (!algum) java.util.Arrays.fill(saida, (short) 0);
            trilha.write(saida, 0, BLOCO);
        }
    }
}
