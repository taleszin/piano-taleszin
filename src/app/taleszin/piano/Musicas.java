package app.taleszin.piano;

import java.util.ArrayList;

/** Repertório de domínio público. Nota = letra [#] oitava [:duração em tempos]; "|" separa frases. */
final class Musicas {
    static final class Musica {
        final String titulo, nivel; final int bpm;
        final int[] notas; final float[] duracoes; final boolean[] fimDeFrase;
        int min = 127, max = 0;
        Musica(String titulo, String nivel, int bpm, String texto) {
            this.titulo = titulo; this.nivel = nivel; this.bpm = bpm;
            ArrayList<int[]> ns = new ArrayList<int[]>();
            ArrayList<Float> ds = new ArrayList<Float>();
            String[] t = texto.trim().split("\\s+");
            ArrayList<Integer> frases = new ArrayList<Integer>();
            for (String tok : t) {
                if (tok.equals("|")) { if (!ns.isEmpty()) frases.add(ns.size() - 1); continue; }
                String[] p = tok.split(":");
                ns.add(new int[]{midi(p[0])});
                ds.add(p.length > 1 ? Float.parseFloat(p[1]) : 1f);
            }
            notas = new int[ns.size()]; duracoes = new float[ns.size()]; fimDeFrase = new boolean[ns.size()];
            for (int i = 0; i < notas.length; i++) {
                notas[i] = ns.get(i)[0]; duracoes[i] = ds.get(i);
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

    static final Musica[] TODAS = {
        new Musica("Brilha, Brilha Estrelinha", "Fácil", 100,
            "C4 C4 G4 G4 A4 A4 G4:2 | F4 F4 E4 E4 D4 D4 C4:2 | G4 G4 F4 F4 E4 E4 D4:2 | "
          + "G4 G4 F4 F4 E4 E4 D4:2 | C4 C4 G4 G4 A4 A4 G4:2 | F4 F4 E4 E4 D4 D4 C4:2"),
        new Musica("Mary Had a Little Lamb", "Fácil", 110,
            "E4 D4 C4 D4 E4 E4 E4:2 | D4 D4 D4:2 E4 G4 G4:2 | E4 D4 C4 D4 E4 E4 E4 E4 | D4 D4 E4 D4 C4:4"),
        new Musica("Frère Jacques", "Fácil", 110,
            "C4 D4 E4 C4 | C4 D4 E4 C4 | E4 F4 G4:2 | E4 F4 G4:2 | "
          + "G4:.5 A4:.5 G4:.5 F4:.5 E4 C4 | G4:.5 A4:.5 G4:.5 F4:.5 E4 C4 | C4 G3 C4:2 | C4 G3 C4:2"),
        new Musica("Ode à Alegria (Beethoven)", "Médio", 120,
            "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4:1.5 D4:.5 D4:2 | "
          + "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4:1.5 C4:.5 C4:2 | "
          + "D4 D4 E4 C4 D4 E4:.5 F4:.5 E4 C4 D4 E4:.5 F4:.5 E4 D4 C4 D4 G3:2 | "
          + "E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 D4:1.5 C4:.5 C4:2"),
        new Musica("Parabéns pra Você", "Médio", 110,
            "C4:.75 C4:.25 D4 C4 F4 E4:2 | C4:.75 C4:.25 D4 C4 G4 F4:2 | "
          + "C4:.75 C4:.25 C5 A4 F4 E4 D4:2 | A#4:.75 A#4:.25 A4 F4 G4 F4:2"),
        new Musica("Bate o Sino (Jingle Bells)", "Médio", 140,
            "E4 E4 E4:2 E4 E4 E4:2 E4 G4 C4:1.5 D4:.5 E4:4 | "
          + "F4 F4 F4:1.5 F4:.5 F4 E4 E4 E4:.5 E4:.5 E4 D4 D4 E4 D4:2 G4:2 | "
          + "E4 E4 E4:2 E4 E4 E4:2 E4 G4 C4:1.5 D4:.5 E4:4 | "
          + "F4 F4 F4 F4 F4 E4 E4 E4:.5 E4:.5 G4 G4 F4 D4 C4:4"),
        new Musica("Noite Feliz", "Difícil", 90,
            "G4:1.5 A4:.5 G4 E4:3 | G4:1.5 A4:.5 G4 E4:3 | D5:2 D5 B4:3 | C5:2 C5 G4:3 | "
          + "A4:2 A4 C5:1.5 B4:.5 A4 G4:1.5 A4:.5 G4 E4:3 | A4:2 A4 C5:1.5 B4:.5 A4 G4:1.5 A4:.5 G4 E4:3 | "
          + "D5:2 D5 F5:1.5 D5:.5 B4 C5:3 E5:3 | C5:1.5 G4:.5 E4 G4:1.5 F4:.5 D4 C4:3"),
        new Musica("Für Elise (início)", "Difícil", 120,
            "E5:.5 D#5:.5 E5:.5 D#5:.5 E5:.5 B4:.5 D5:.5 C5:.5 A4:1 | C4:.5 E4:.5 A4:.5 B4:1 | "
          + "E4:.5 G#4:.5 B4:.5 C5:1 | E4:.5 E5:.5 D#5:.5 E5:.5 D#5:.5 E5:.5 B4:.5 D5:.5 C5:.5 A4:1 | "
          + "C4:.5 E4:.5 A4:.5 B4:1 | E4:.5 C5:.5 B4:.5 A4:2"),
    };
}
