package app.taleszin.piano;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Acervo v2 em assets/musicas/<n-categoria>/<slug>.txt (compilado por tools/m7s.py).
 * Cabeçalho chave=valor até "---"; depois um evento por linha: "inicio dur mao midi+midi" (tempos em semínimas).
 */
final class Musicas {
    static final int D = 0, E = 1;

    static final class Musica {
        final String id, arquivo, categoria;
        String titulo = "", nivel = "Médio", tom = "";
        int bpm = 100;
        float compasso = 4, anacruse = 0;
        // carregados sob demanda
        float[] ini, dur; int[] mao; int[][] notas;
        float[] secIni; String[] secRot;
        int nD, nE;                 // nº de notas por mão
        float total;                // duração em tempos
        int minD = 127, maxD = 0, minE = 127, maxE = 0;

        Musica(String id, String arquivo, String categoria) { this.id = id; this.arquivo = arquivo; this.categoria = categoria; }

        boolean temE() { return nE > 0; }
        int segundos() { return Math.round(total * 60f / bpm); }
    }

    private static final String[][] CATEGORIAS = {
        {"infantis", "Infantis e primeiras"}, {"classicas", "Clássicas"}, {"brasileiras", "Brasileiras"},
        {"cantoras", "Cantoras"}, {"populares", "Pop e piano moderno"}, {"beatles", "Beatles"}, {"rock", "Rock e pop clássico"},
        {"trilhas", "Filmes e jogos"}, {"natal", "Natal"},
    };

    private static Musica[] cache;

    static int peso(String nivel) { return nivel.equals("Fácil") ? 0 : nivel.equals("Médio") ? 1 : 2; }

    static ArrayList<String> categorias(AssetManager am) {
        ArrayList<String> cs = new ArrayList<String>();
        for (Musica m : todas(am)) if (!cs.contains(m.categoria)) cs.add(m.categoria);
        return cs;
    }

    /** Lista vinda de assets/musicas/indice.tsv (gerado pelo m7s.py): id, título, nível, bpm, nD, nE, total. */
    static synchronized Musica[] todas(AssetManager am) {
        if (cache != null) return cache;
        ArrayList<Musica> lista = new ArrayList<Musica>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(am.open("musicas/indice.tsv"), "UTF-8"));
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split("\t");
                if (p.length < 7) continue;
                String dir = p[0].substring(0, p[0].indexOf('/'));
                String chave = dir.replaceAll("^\\d+-", "");
                String cat = chave;
                for (String[] c : CATEGORIAS) if (c[0].equals(chave)) cat = c[1];
                Musica m = new Musica(p[0], "musicas/" + p[0] + ".txt", cat);
                m.titulo = p[1]; m.nivel = p[2]; m.bpm = Integer.parseInt(p[3]);
                m.nD = Integer.parseInt(p[4]); m.nE = Integer.parseInt(p[5]); m.total = Float.parseFloat(p[6]);
                if (p.length > 7) m.tom = p[7];
                lista.add(m);
            }
            br.close();
        } catch (Exception e) {
            android.util.Log.e("Piano", "sem acervo", e);
        }
        // por categoria (ordem dos diretórios), e dentro dela Fácil → Difícil, depois título
        Collections.sort(lista, new Comparator<Musica>() {
            public int compare(Musica a, Musica b) {
                int c = a.id.substring(0, a.id.indexOf('/')).compareTo(b.id.substring(0, b.id.indexOf('/')));
                if (c != 0) return c;
                int p = peso(a.nivel) - peso(b.nivel);
                return p != 0 ? p : a.titulo.compareToIgnoreCase(b.titulo);
            }
        });
        cache = lista.toArray(new Musica[0]);
        return cache;
    }

    private static void cab(Musica m, String l) {
        int i = l.indexOf('=');
        if (i < 0) return;
        String k = l.substring(0, i), v = l.substring(i + 1);
        if (k.equals("compasso")) m.compasso = Float.parseFloat(v);
        else if (k.equals("anacruse")) m.anacruse = Float.parseFloat(v);
    }

    /** Carrega os eventos (uma vez). */
    static synchronized void carregar(AssetManager am, Musica m) {
        if (m.ini != null) return;
        ArrayList<float[]> ev = new ArrayList<float[]>();
        ArrayList<int[]> ns = new ArrayList<int[]>();
        ArrayList<Float> si = new ArrayList<Float>(); ArrayList<String> sr = new ArrayList<String>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(am.open(m.arquivo), "UTF-8"));
            String l; boolean corpo = false;
            while ((l = br.readLine()) != null) {
                if (!corpo) {
                    if (l.equals("---")) corpo = true;
                    else if (!l.startsWith("secao=")) cab(m, l);
                    else {
                        String v = l.substring(6); int i = v.indexOf(' ');
                        si.add(Float.parseFloat(i < 0 ? v : v.substring(0, i))); sr.add(i < 0 ? "" : v.substring(i + 1));
                    }
                    continue;
                }
                String[] p = l.split(" ");
                if (p.length < 4) continue;
                String[] ms = p[3].split("\\+");
                int[] arr = new int[ms.length];
                for (int k = 0; k < ms.length; k++) arr[k] = Integer.parseInt(ms[k]);
                ev.add(new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), p[2].equals("D") ? D : E});
                ns.add(arr);
            }
            br.close();
        } catch (java.io.IOException e) {
            android.util.Log.e("Piano", "falha ao ler " + m.arquivo, e);
        }
        int n = ev.size();
        m.ini = new float[n]; m.dur = new float[n]; m.mao = new int[n]; m.notas = new int[n][];
        for (int i = 0; i < n; i++) {
            float[] e = ev.get(i);
            m.ini[i] = e[0]; m.dur[i] = e[1]; m.mao[i] = (int) e[2]; m.notas[i] = ns.get(i);
            for (int x : m.notas[i]) {
                if (m.mao[i] == D) { m.minD = Math.min(m.minD, x); m.maxD = Math.max(m.maxD, x); }
                else { m.minE = Math.min(m.minE, x); m.maxE = Math.max(m.maxE, x); }
            }
        }
        if (si.isEmpty()) { si.add(0f); sr.add("Música"); }
        m.secIni = new float[si.size()]; m.secRot = new String[si.size()];
        for (int i = 0; i < si.size(); i++) { m.secIni[i] = si.get(i); m.secRot[i] = sr.get(i); }
    }
}
