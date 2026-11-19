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

public class Piano extends Activity implements Teclado.Ouvinte, Cascata.Fim {
    private static final int LARANJA = 0xFFFF7A1E;
    private Sintetizador synth;
    private Teclado teclado;
    private Cascata cascata;
    private LinearLayout barra, barraJogo;
    private int vel;   // velocidade da música em % (30–150)
    private SharedPreferences prefs;
    private TextView tFaixa, bNomes, bTam, bSust, bSom, tMusica, bOuvir;

    private int oitava, nomes, brancas, timbre;
    private boolean sustentar;
    private float dp;
    private final Handler h = new Handler();

    private Musicas.Musica musica;

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

        vel = prefs.getInt("velPct", 60);
        barra = linha();
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

        // Barra do jogo (troca com a barra livre)
        barraJogo = linha();
        barraJogo.setVisibility(View.GONE);
        barraJogo.addView(botao("Sair", new View.OnClickListener() {
            public void onClick(View v) { fecharMusica(); }
        }));
        espaco(barraJogo);
        tMusica = new TextView(this);
        tMusica.setTextColor(Color.WHITE);
        tMusica.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        tMusica.getPaint().setFakeBoldText(true);
        tMusica.setSingleLine(true);
        barraJogo.addView(tMusica, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView tVel = new TextView(this);
        tVel.setTextColor(Color.WHITE);
        tVel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tVel.setGravity(Gravity.CENTER);
        tVel.setMinWidth((int) (64 * dp));
        tVel.setText("vel. " + vel + "%");
        View.OnClickListener mudaVel = new View.OnClickListener() {
            public void onClick(View v) {
                vel = Math.max(30, Math.min(150, vel + ((Integer) v.getTag())));
                tVel.setText("vel. " + vel + "%");
                cascata.setVelocidade(vel / 100f);
                prefs.edit().putInt("velPct", vel).apply();
            }
        };
        TextView vMenos = botao("−", mudaVel); vMenos.setTag(-10);
        TextView vMais = botao("+", mudaVel); vMais.setTag(10);
        barraJogo.addView(vMenos);
        barraJogo.addView(tVel);
        barraJogo.addView(vMais);
        espaco(barraJogo);
        bOuvir = botao("Ouvir", new View.OnClickListener() {
            public void onClick(View v) { jogar(!cascata.demo()); }
        });
        barraJogo.addView(bOuvir);
        espaco(barraJogo);
        barraJogo.addView(botao("Recomeçar", new View.OnClickListener() {
            public void onClick(View v) { jogar(false); }
        }));
        espaco(barraJogo);
        TextView bLista = botao("Músicas", new View.OnClickListener() {
            public void onClick(View v) { escolherMusica(); }
        });
        bLista.setBackground(fundo(true));
        barraJogo.addView(bLista);
        raiz.addView(barraJogo, new LinearLayout.LayoutParams(-1, (int) (50 * dp)));

        cascata = new Cascata(this, teclado, synth);
        cascata.setFim(this);
        cascata.setVisibility(View.GONE);
        raiz.addView(cascata, new LinearLayout.LayoutParams(-1, 0, 1.25f));
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
        prefs.edit().putInt("oitava", oitava).putInt("nomes", nomes).putInt("brancas", brancas)
                .putInt("timbre", timbre).apply();
    }

    // ---------- Músicas (jogo) ----------

    private void escolherMusica() {
        final Musicas.Musica[] ms = Musicas.TODAS;
        android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            public int getCount() { return ms.length; }
            public Object getItem(int i) { return ms[i]; }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, android.view.ViewGroup pai) {
                LinearLayout l = new LinearLayout(Piano.this);
                l.setGravity(Gravity.CENTER_VERTICAL);
                l.setPadding((int) (16 * dp), (int) (10 * dp), (int) (16 * dp), (int) (10 * dp));
                LinearLayout txt = new LinearLayout(Piano.this);
                txt.setOrientation(LinearLayout.VERTICAL);
                TextView t = new TextView(Piano.this);
                t.setText(ms[i].titulo); t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
                TextView sub = new TextView(Piano.this);
                int rec = prefs.getInt("recorde_" + ms[i].titulo, 0);
                sub.setText(ms[i].nivel + (rec > 0 ? "  ·  recorde " + rec : "  ·  nova"));
                sub.setTextColor(ms[i].nivel.equals("Fácil") ? 0xFF66BB6A : ms[i].nivel.equals("Médio") ? 0xFFFFB300 : 0xFFFF7043);
                sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                txt.addView(t); txt.addView(sub);
                l.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                Estrelas e = new Estrelas(Piano.this, 20);
                e.set(prefs.getInt("estrelas_" + ms[i].titulo, 0));
                l.addView(e);
                return l;
            }
        };
        new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
            .setTitle("Escolha uma música")
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { abrirMusica(ms[i]); }
            })
            .show();
    }

    private void abrirMusica(Musicas.Musica m) {
        musica = m;
        int o = Math.max(1, m.min / 12 - 1);
        int nb = Teclado.brancasPara(o, m.max);
        teclado.configurar(o, nb, nomes == Teclado.NOMES_NENHUM ? Teclado.NOMES_DO_RE_MI : nomes);
        tMusica.setText(m.titulo);
        barra.setVisibility(View.GONE);
        barraJogo.setVisibility(View.VISIBLE);
        cascata.setVisibility(View.VISIBLE);
        cascata.setVelocidade(vel / 100f);
        jogar(false);
    }

    private void jogar(boolean demo) {
        cascata.iniciar(musica, demo);
        bOuvir.setText(demo ? "Parar" : "Ouvir");
        bOuvir.setBackground(fundo(demo));
    }

    private void fecharMusica() {
        cascata.parar();
        musica = null;
        cascata.setVisibility(View.GONE);
        barraJogo.setVisibility(View.GONE);
        barra.setVisibility(View.VISIBLE);
        aplicar();
    }

    @Override
    public boolean tocou(int midi) { return musica == null || cascata.tocou(midi); }

    @Override
    public void fim(boolean demo, int pontos, int erros, int perfeitos, int total, int maxCombo) {
        if (demo) {
            Toast.makeText(this, "Agora é sua vez!", Toast.LENGTH_SHORT).show();
            jogar(false);
            return;
        }
        int est = erros <= 1 ? 3 : erros <= 4 ? 2 : 1;
        String k = musica.titulo;
        boolean recorde = pontos > prefs.getInt("recorde_" + k, 0);
        SharedPreferences.Editor ed = prefs.edit();
        if (recorde) ed.putInt("recorde_" + k, pontos);
        if (est > prefs.getInt("estrelas_" + k, 0)) ed.putInt("estrelas_" + k, est);
        ed.apply();

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));
        Estrelas e = new Estrelas(this, 44); e.set(est);
        v.addView(e);
        TextView t1 = new TextView(this);
        t1.setText(est == 3 ? "Incrível!" : est == 2 ? "Muito bem!" : "Boa! Tente de novo");
        t1.setTextColor(LARANJA); t1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        t1.getPaint().setFakeBoldText(true); t1.setGravity(Gravity.CENTER);
        v.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(pontos + " pontos" + (recorde ? "  ·  NOVO RECORDE!" : "") + "\n"
                + perfeitos + " de " + total + " perfeitas  ·  maior combo " + maxCombo + "  ·  " + erros + " erro" + (erros == 1 ? "" : "s"));
        t2.setTextColor(Color.WHITE); t2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        t2.setGravity(Gravity.CENTER);
        v.addView(t2);

        int idx = 0;
        for (int i = 0; i < Musicas.TODAS.length; i++) if (Musicas.TODAS[i] == musica) idx = i;
        final Musicas.Musica prox = Musicas.TODAS[(idx + 1) % Musicas.TODAS.length];
        new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
            .setView(v)
            .setCancelable(false)
            .setPositiveButton("Próxima música", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { abrirMusica(prox); } })
            .setNeutralButton("Jogar de novo", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { jogar(false); } })
            .setNegativeButton("Lista", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { escolherMusica(); } })
            .show();
    }

    @Override protected void onResume() { super.onResume(); synth.iniciar(); if (musica != null) cascata.retomar(); }
    @Override protected void onPause() { cascata.pausar(); teclado.soltarTudo(); synth.parar(); super.onPause(); }
    @Override protected void onDestroy() { synth.liberar(); super.onDestroy(); }
}
