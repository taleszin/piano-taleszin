package app.taleszin.piano;

import android.content.res.AssetManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Acervo em assets/musicas/N-categoria.txt. Linha: Título | Nível | bpm | notas.
 * Nota = letra [#] oitava [:duração em semínimas]; R:x = pausa; "|" separa frases.
 */
final class Musicas {
    static final class Musica {
        final String titulo, nivel, categoria; final int bpm;
        final int[] notas; final float[] duracoes, pausaAntes; final boolean[] fimDeFrase;
        int min = 127, max = 0;

        Musica(String titulo, String nivel, String categoria, int bpm, String texto) {
            this.titulo = titulo; this.nivel = nivel; this.categoria = categoria; this.bpm = bpm;
            ArrayList<Integer> ns = new ArrayList<Integer>();
            ArrayList<Float> ds = new ArrayList<Float>(), ps = new ArrayList<Float>();
            ArrayList<Integer> frases = new ArrayList<Integer>();
            float pausa = 0;
            for (String tok : texto.trim().split("\\s+")) {
                if (tok.equals("|")) { if (!ns.isEmpty()) frases.add(ns.size() - 1); continue; }
                String[] p = tok.split(":");
                float d = p.length > 1 ? Float.parseFloat(p[1]) : 1f;
                if (p[0].equals("R")) { pausa += d; continue; }
                ns.add(midi(p[0])); ds.add(d); ps.add(pausa); pausa = 0;
            }
            notas = new int[ns.size()]; duracoes = new float[ns.size()];
            pausaAntes = new float[ns.size()]; fimDeFrase = new boolean[ns.size()];
            for (int i = 0; i < notas.length; i++) {
                notas[i] = ns.get(i); duracoes[i] = ds.get(i); pausaAntes[i] = ps.get(i);
                min = Math.min(min, notas[i]); max = Math.max(max, notas[i]);
            }
            for (int i : frases) fimDeFrase[i] = true;
        }
    }

    private static int midi(String n) {
        int[] semi = {9, 11, 0, 2, 4, 5, 7}; // A B C D E F G
        int s = semi[n.charAt(0) - 'A'];
        int i = 1;
        if (n.charAt(1) == '#') { s++; i = 2; }
        return 12 * (Integer.parseInt(n.substring(i)) + 1) + s;
    }

    private static final String[][] CATEGORIAS = {
        {"infantis", "Infantis e primeiras"}, {"classicas", "Clássicas"}, {"brasileiras", "Brasileiras"},
        {"cantoras", "Cantoras"}, {"populares", "Pop atual"}, {"rock", "Rock e pop clássico"},
        {"trilhas", "Filmes e jogos"}, {"natal", "Natal"},
    };

    private static Musica[] cache;

    static int peso(String nivel) { return nivel.equals("Fácil") ? 0 : nivel.equals("Médio") ? 1 : 2; }

    private static void ordenarPorNivel(ArrayList<Musica> lista, String cat) {
        int ini = lista.size();
        while (ini > 0 && lista.get(ini - 1).categoria.equals(cat)) ini--;
        java.util.List<Musica> sub = lista.subList(ini, lista.size());
        java.util.Collections.sort(sub, new java.util.Comparator<Musica>() {
            public int compare(Musica a, Musica b) { return peso(a.nivel) - peso(b.nivel); }
        });
    }

    /** Categorias na ordem do acervo. */
    static ArrayList<String> categorias(AssetManager am) {
        ArrayList<String> cs = new ArrayList<String>();
        for (Musica m : todas(am)) if (!cs.contains(m.categoria)) cs.add(m.categoria);
        return cs;
    }

    static synchronized Musica[] todas(AssetManager am) {
        if (cache != null) return cache;
        ArrayList<Musica> lista = new ArrayList<Musica>();
        try {
            String[] arqs = am.list("musicas");
            Arrays.sort(arqs);
            for (String arq : arqs) {
                String chave = arq.replaceAll("^\\d+-|\\.txt$", "");
                String cat = chave;
                for (String[] c : CATEGORIAS) if (c[0].equals(chave)) cat = c[1];
                BufferedReader br = new BufferedReader(new InputStreamReader(am.open("musicas/" + arq), "UTF-8"));
                String l;
                while ((l = br.readLine()) != null) {
                    l = l.trim();
                    if (l.isEmpty() || l.startsWith("#")) continue;
                    String[] p = l.split("\\|", 4);
                    if (p.length < 4) continue;
                    try {
                        Musica m = new Musica(p[0].trim(), p[1].trim(), cat, Integer.parseInt(p[2].trim()), p[3]);
                        if (m.notas.length > 0) lista.add(m);
                    } catch (RuntimeException e) {
                        android.util.Log.w("Piano", "música inválida: " + l, e);
                    }
                }
                br.close();
                // dentro da categoria: Fácil → Médio → Difícil (ordem estável)
                ordenarPorNivel(lista, cat);
            }
        } catch (java.io.IOException e) {
            android.util.Log.e("Piano", "sem acervo", e);
        }
        cache = lista.toArray(new Musica[0]);
        return cache;
    }
}
