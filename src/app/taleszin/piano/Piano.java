package app.taleszin.piano;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class Piano extends Activity implements Teclado.Ouvinte {
    private static final int LARANJA = 0xFFFF7A1E;
    private Sintetizador synth;
    private Teclado teclado;
    private Partitura partitura;
    private SharedPreferences prefs;
    private TextView tFaixa, bNomes, bTam, bSust, bSom, tMusica, bOuvir;
    private LinearLayout painel;
    private int oitava, nomes, brancas, timbre;
    private boolean sustentar;
    private float dp;
    private final Handler h = new Handler();

    // Modo música
    private Musicas.Musica musica;
    private int pos;
    private boolean ouvindo;
    private int notaDemo = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("piano", MODE_PRIVATE);
        oitava = prefs.getInt("oitava", 4);
        nomes = prefs.getInt("nomes", Teclado.NOMES_DO_RE_MI);
        brancas = prefs.getInt("brancas", 15);
        timbre = prefs.getInt("timbre", Sintetizador.ELETRICO);

        synth = new Sintetizador();
        synth.setTimbre(timbre);
        teclado = new Teclado(this, synth);
        teclado.setOuvinte(this);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(0xFF101014);

        LinearLayout barra = linha();
        TextView titulo = new TextView(this);
        titulo.setText("Piano");
        titulo.setTextColor(LARANJA);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titulo.getPaint().setFakeBoldText(true);
        barra.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));

        barra.addView(botao("−", new View.OnClickListener() {
            public void onClick(View v) { if (oitava > 1) { oitava--; aplicar(); } }
        }));
        tFaixa = new TextView(this);
        tFaixa.setTextColor(Color.WHITE);
        tFaixa.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tFaixa.setGravity(Gravity.CENTER);
        tFaixa.setMinWidth((int) (90 * dp));
        barra.addView(tFaixa);
        barra.addView(botao("+", new View.OnClickListener() {
            public void onClick(View v) { if (oitava + (brancas - 1) / 7 < 7) { oitava++; aplicar(); } }
        }));
        espaco(barra);
        bTam = botao("", new View.OnClickListener() {
            public void onClick(View v) {
                brancas = brancas == 15 ? 8 : 15;
                if (oitava + (brancas - 1) / 7 > 7) oitava = 7 - (brancas - 1) / 7;
                aplicar();
            }
        });
        barra.addView(bTam);
        espaco(barra);
        bNomes = botao("", new View.OnClickListener() {
            public void onClick(View v) { nomes = (nomes + 1) % 3; aplicar(); }
        });
        barra.addView(bNomes);
        espaco(barra);
        bSom = botao("", new View.OnClickListener() {
            public void onClick(View v) {
                timbre = timbre == Sintetizador.ELETRICO ? Sintetizador.CLASSICO : Sintetizador.ELETRICO;
                synth.setTimbre(timbre);
                aplicar();
            }
        });
        barra.addView(bSom);
        espaco(barra);
        bSust = botao("Pedal", new View.OnClickListener() {
            public void onClick(View v) { sustentar = !sustentar; synth.setSustentar(sustentar); aplicar(); }
        });
        barra.addView(bSust);
        espaco(barra);
        TextView bMus = botao("Músicas", new View.OnClickListener() {
            public void onClick(View v) { escolherMusica(); }
        });
        bMus.setBackground(fundo(true));
        barra.addView(bMus);
        raiz.addView(barra, new LinearLayout.LayoutParams(-1, (int) (50 * dp)));

        // Painel da música: título + botões, e a partitura
        painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        painel.setVisibility(View.GONE);
        LinearLayout cab = linha();
        tMusica = new TextView(this);
        tMusica.setTextColor(Color.WHITE);
        tMusica.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tMusica.setSingleLine(true);
        cab.addView(tMusica, new LinearLayout.LayoutParams(0, -2, 1f));
        bOuvir = botao("Ouvir", new View.OnClickListener() {
            public void onClick(View v) { if (ouvindo) pararDemo(); else ouvir(); }
        });
        cab.addView(bOuvir);
        espaco(cab);
        cab.addView(botao("Recomeçar", new View.OnClickListener() {
            public void onClick(View v) { pararDemo(); irPara(0); }
        }));
        espaco(cab);
        cab.addView(botao("Fechar", new View.OnClickListener() {
            public void onClick(View v) { fecharMusica(); }
        }));
        painel.addView(cab, new LinearLayout.LayoutParams(-1, (int) (44 * dp)));
        partitura = new Partitura(this);
        painel.addView(partitura, new LinearLayout.LayoutParams(-1, (int) (54 * dp)));
        raiz.addView(painel, new LinearLayout.LayoutParams(-1, -2));

        raiz.addView(teclado, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(raiz);
        aplicar();
    }

    private LinearLayout linha() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (8 * dp);
        l.setPadding(pad, pad / 2, pad, pad / 2);
        return l;
    }

    private void espaco(LinearLayout l) { l.addView(new View(this), new LinearLayout.LayoutParams((int) (6 * dp), 1)); }

    private TextView botao(String txt, View.OnClickListener ouvinte) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, txt.length() == 1 ? 22 : 14);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth((int) (42 * dp));
        t.setPadding((int) (11 * dp), 0, (int) (11 * dp), 0);
        t.setBackground(fundo(false));
        t.setOnClickListener(ouvinte);
        t.setLayoutParams(new LinearLayout.LayoutParams(-2, (int) (38 * dp)));
        return t;
    }

    private GradientDrawable fundo(boolean ligado) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(19 * dp);
        g.setColor(ligado ? LARANJA : 0xFF2A2A30);
        return g;
    }

    private void aplicar() {
        teclado.configurar(oitava, brancas, nomes);
        tFaixa.setText(teclado.faixa());
        bTam.setText(brancas == 15 ? "2 oitavas" : "1 oitava");
        bNomes.setText(nomes == Teclado.NOMES_DO_RE_MI ? "Dó Ré Mi" : nomes == Teclado.NOMES_CDE ? "C D E" : "Sem nomes");
        bSom.setText(timbre == Sintetizador.ELETRICO ? "Elétrico" : "Clássico");
        bSust.setBackground(fundo(sustentar));
        if (musica != null) partitura.set(musica, pos, nomes == Teclado.NOMES_CDE);
        prefs.edit().putInt("oitava", oitava).putInt("nomes", nomes).putInt("brancas", brancas)
                .putInt("timbre", timbre).apply();
    }

    // ---------- Músicas ----------

    private void escolherMusica() {
        String[] titulos = new String[Musicas.TODAS.length];
        for (int i = 0; i < titulos.length; i++) titulos[i] = Musicas.TODAS[i].titulo;
        new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
            .setTitle("Escolha uma música")
            .setItems(titulos, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { abrirMusica(Musicas.TODAS[i]); }
            })
            .show();
    }

    private void abrirMusica(Musicas.Musica m) {
        pararDemo();
        musica = m;
        int o = Teclado.oitavaPara(m.min, m.max, 15);
        if (o < 0) o = Teclado.oitavaPara(m.min, m.max, 22);
        brancas = 15;
        if (o > 0) oitava = o;
        tMusica.setText(m.titulo);
        painel.setVisibility(View.VISIBLE);
        aplicar();
        irPara(0);
        Toast.makeText(this, "Toque a tecla que está acesa. Ou toque em Ouvir.", Toast.LENGTH_SHORT).show();
    }

    private void fecharMusica() {
        pararDemo();
        musica = null;
        painel.setVisibility(View.GONE);
        teclado.setGuia(-1);
    }

    private void irPara(int i) {
        if (musica == null) return;
        pos = i;
        teclado.setGuia(pos < musica.notas.length ? musica.notas[pos] : -1);
        partitura.set(musica, pos, nomes == Teclado.NOMES_CDE);
    }

    @Override
    public void tocou(int midi) {
        if (musica == null) return;
        if (ouvindo) pararDemo();
        if (pos >= musica.notas.length || midi != musica.notas[pos]) return;
        irPara(pos + 1);
        if (pos >= musica.notas.length) {
            Toast.makeText(this, "Muito bem! Você tocou \"" + musica.titulo + "\" inteira.", Toast.LENGTH_LONG).show();
            h.postDelayed(new Runnable() { public void run() { irPara(0); } }, 2500);
        }
    }

    private void ouvir() {
        if (musica == null) return;
        ouvindo = true;
        bOuvir.setText("Parar");
        bOuvir.setBackground(fundo(true));
        teclado.setGuia(-1);
        passoDemo(0);
    }

    private void passoDemo(final int i) {
        if (!ouvindo) return;
        if (i >= musica.notas.length) { pararDemo(); return; }
        final int n = musica.notas[i];
        final long ms = (long) (60000f / musica.bpm * musica.duracoes[i]);
        notaDemo = n;
        synth.tocar(n);
        teclado.setDemo(n);
        partitura.set(musica, i, nomes == Teclado.NOMES_CDE);
        h.postDelayed(new Runnable() { public void run() {
            synth.soltar(n);
            if (notaDemo == n) { teclado.setDemo(-1); notaDemo = -1; }
        } }, (long) (ms * 0.85f));
        h.postDelayed(new Runnable() { public void run() { passoDemo(i + 1); } }, ms);
    }

    private void pararDemo() {
        if (!ouvindo) return;
        ouvindo = false;
        h.removeCallbacksAndMessages(null);
        if (notaDemo >= 0) synth.soltar(notaDemo);
        notaDemo = -1;
        teclado.setDemo(-1);
        bOuvir.setText("Ouvir");
        bOuvir.setBackground(fundo(false));
        irPara(pos);
    }

    @Override protected void onResume() { super.onResume(); synth.iniciar(); }
    @Override protected void onPause() { pararDemo(); teclado.soltarTudo(); synth.parar(); super.onPause(); }
    @Override protected void onDestroy() { synth.liberar(); super.onDestroy(); }
}
