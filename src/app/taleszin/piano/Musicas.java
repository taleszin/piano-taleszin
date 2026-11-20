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
        String titulo = "", nivel = "Médio";
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

    static synchronized Musica[] todas(AssetManager am) {
        if (cache != null) return cache;
        ArrayList<Musica> lista = new ArrayList<Musica>();
        try {
            String[] dirs = am.list("musicas");
            Arrays.sort(dirs);
            for (String dir : dirs) {
                String chave = dir.replaceAll("^\\d+-", "");
                String cat = chave;
                for (String[] c : CATEGORIAS) if (c[0].equals(chave)) cat = c[1];
                String[] arqs = am.list("musicas/" + dir);
                if (arqs == null || arqs.length == 0) continue;
                ArrayList<Musica> daCat = new ArrayList<Musica>();
                for (String arq : arqs) {
                    if (!arq.endsWith(".txt")) continue;
                    Musica m = new Musica(dir + "/" + arq.replace(".txt", ""), "musicas/" + dir + "/" + arq, cat);
                    try { lerCabecalho(am, m); daCat.add(m); }
                    catch (Exception e) { android.util.Log.w("Piano", "música inválida: " + arq, e); }
                }
                Collections.sort(daCat, new Comparator<Musica>() {
                    public int compare(Musica a, Musica b) {
                        int p = peso(a.nivel) - peso(b.nivel);
                        return p != 0 ? p : a.titulo.compareToIgnoreCase(b.titulo);
                    }
                });
                lista.addAll(daCat);
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("Piano", "sem acervo", e);
        }
        cache = lista.toArray(new Musica[0]);
        return cache;
    }

    private static void cab(Musica m, String l) {
        int i = l.indexOf('=');
        if (i < 0) return;
        String k = l.substring(0, i), v = l.substring(i + 1);
        if (k.equals("titulo")) m.titulo = v;
        else if (k.equals("nivel")) m.nivel = v;
        else if (k.equals("bpm")) m.bpm = Integer.parseInt(v);
        else if (k.equals("compasso")) m.compasso = Float.parseFloat(v);
        else if (k.equals("anacruse")) m.anacruse = Float.parseFloat(v);
    }

    private static void lerCabecalho(AssetManager am, Musica m) throws java.io.IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(am.open(m.arquivo), "UTF-8"));
        try {
            String l;
            int nD = 0, nE = 0; float fim = 0;
            boolean corpo = false;
            while ((l = br.readLine()) != null) {
                if (!corpo) { if (l.equals("---")) corpo = true; else cab(m, l); continue; }
                // contagem rápida para a lista (sem guardar)
                int a = l.indexOf(' '), b = l.indexOf(' ', a + 1);
                if (a < 0 || b < 0) continue;
                float ini = Float.parseFloat(l.substring(0, a)), d = Float.parseFloat(l.substring(a + 1, b));
                int n = 1; for (int k = b + 3; k < l.length(); k++) if (l.charAt(k) == '+') n++;
                if (l.charAt(b + 1) == 'D') nD += n; else nE += n;
                fim = Math.max(fim, ini + d);
            }
            m.nD = nD; m.nE = nE; m.total = fim;
        } finally { br.close(); }
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
                    else if (l.startsWith("secao=")) {
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
