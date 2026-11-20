package app.taleszin.piano;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;

public class Piano extends Activity implements Teclado.Ouvinte, Cascata.Fim {
    private static final int LARANJA = 0xFFFF7A1E, OURO = 0xFFFFD54F;
    private Sintetizador synth;
    private Teclado teclado;
    private Cascata cascata;
    private LinearLayout barra, barraJogo;
    private SharedPreferences prefs;
    private TextView tFaixa, bNomes, bTam, bSust, bSom, tMusica, bOuvir, bMao, bModo, bTrecho, tVel;

    private int oitava, nomes, brancas, timbre, vel, maos;
    private boolean sustentar, desafio;
    private float dp;

    private Musicas.Musica musica;
    private int trecho = -1;          // seção em repetição (-1 = música inteira)

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28)
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("piano", MODE_PRIVATE);
        oitava = prefs.getInt("oitava", 4);
        nomes = prefs.getInt("nomes", Teclado.NOMES_DO_RE_MI);
        brancas = prefs.getInt("brancas", 15);
        timbre = prefs.getInt("timbre", Sintetizador.ACUSTICO);
        vel = prefs.getInt("velPct", 70);
        maos = prefs.getInt("maos", Cascata.MAO_D);
        desafio = prefs.getBoolean("desafio", false);

        synth = new Sintetizador(this);
        synth.setTimbre(timbre);
        synth.setReverb(prefs.getBoolean("reverb", true));
        teclado = new Teclado(this, synth);
        teclado.setOuvinte(this);
        teclado.setForcaFixa(!prefs.getBoolean("forca", true));
        teclado.setVibrar(prefs.getBoolean("vibrar", true));

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(0xFF101014);

        // ---- barra do modo livre ----
        barra = linha();
        TextView titulo = new TextView(this);
        titulo.setText("Piano");
        titulo.setTextColor(LARANJA);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        titulo.getPaint().setFakeBoldText(true);
        barra.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));
        barra.addView(botao("−", new View.OnClickListener() {
            public void onClick(View v) { if (oitava > 1) { oitava--; aplicar(); } }
        }));
        tFaixa = new TextView(this);
        tFaixa.setTextColor(Color.WHITE);
        tFaixa.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tFaixa.setGravity(Gravity.CENTER);
        tFaixa.setMinWidth((int) (84 * dp));
        barra.addView(tFaixa);
        barra.addView(botao("+", new View.OnClickListener() {
            public void onClick(View v) { if (oitava * 7 + brancas - 1 < Teclado.MAX_G) { oitava++; aplicar(); } }
        }));
        espaco(barra);
        bTam = botao("", new View.OnClickListener() {
            public void onClick(View v) { brancas = brancas == 8 ? 15 : brancas == 15 ? 22 : 8; aplicar(); }
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
                timbre = timbre == Sintetizador.ACUSTICO ? Sintetizador.ELETRICO : Sintetizador.ACUSTICO;
                synth.setTimbre(timbre); aplicar();
            }
        });
        barra.addView(bSom);
        espaco(barra);
        bSust = botao("Pedal", new View.OnClickListener() {
            public void onClick(View v) { sustentar = !sustentar; synth.setSustentar(sustentar); aplicar(); }
        });
        barra.addView(bSust);
        espaco(barra);
        barra.addView(botao("Ajustes", new View.OnClickListener() { public void onClick(View v) { ajustes(); } }));
        espaco(barra);
        TextView bMus = botao("Músicas", new View.OnClickListener() { public void onClick(View v) { escolherMusica(); } });
        bMus.setBackground(fundo(LARANJA));
        barra.addView(bMus);
        raiz.addView(barra, new LinearLayout.LayoutParams(-1, (int) (46 * dp)));

        // ---- barra do jogo ----
        barraJogo = linha();
        barraJogo.setVisibility(View.GONE);
        barraJogo.addView(botao("Sair", new View.OnClickListener() { public void onClick(View v) { fecharMusica(); } }));
        espaco(barraJogo);
        tMusica = new TextView(this);
        tMusica.setTextColor(Color.WHITE);
        tMusica.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tMusica.getPaint().setFakeBoldText(true);
        tMusica.setSingleLine(true);
        tMusica.setEllipsize(android.text.TextUtils.TruncateAt.END);
        barraJogo.addView(tMusica, new LinearLayout.LayoutParams(0, -2, 1f));
        bMao = botao("", new View.OnClickListener() {
            public void onClick(View v) {
                if (musica == null || !musica.temE()) { Toast.makeText(Piano.this, "Esta música só tem a melodia", Toast.LENGTH_SHORT).show(); return; }
                maos = maos == Cascata.MAO_D ? Cascata.MAO_E : maos == Cascata.MAO_E ? Cascata.DUAS : Cascata.MAO_D;
                prefs.edit().putInt("maos", maos).apply();
                jogar(false);
            }
        });
        barraJogo.addView(bMao);
        espaco(barraJogo);
        bModo = botao("", new View.OnClickListener() {
            public void onClick(View v) { desafio = !desafio; prefs.edit().putBoolean("desafio", desafio).apply(); jogar(false); }
        });
        barraJogo.addView(bModo);
        espaco(barraJogo);
        tVel = new TextView(this);
        tVel.setTextColor(Color.WHITE);
        tVel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tVel.setGravity(Gravity.CENTER);
        tVel.setMinWidth((int) (52 * dp));
        View.OnClickListener mudaVel = new View.OnClickListener() {
            public void onClick(View v) {
                vel = Math.max(30, Math.min(150, vel + ((Integer) v.getTag())));
                tVel.setText(vel + "%");
                cascata.setVelocidade(vel / 100f);
                prefs.edit().putInt("velPct", vel).apply();
            }
        };
        TextView vMenos = botao("−", mudaVel); vMenos.setTag(-10);
        TextView vMais = botao("+", mudaVel); vMais.setTag(10);
        barraJogo.addView(vMenos); barraJogo.addView(tVel); barraJogo.addView(vMais);
        espaco(barraJogo);
        bTrecho = botao("Trecho", new View.OnClickListener() { public void onClick(View v) { escolherTrecho(); } });
        barraJogo.addView(bTrecho);
        espaco(barraJogo);
        bOuvir = botao("Autoplay", new View.OnClickListener() { public void onClick(View v) { jogar(!cascata.demo()); } });
        barraJogo.addView(bOuvir);
        espaco(barraJogo);
        barraJogo.addView(botao("↺", new View.OnClickListener() { public void onClick(View v) { jogar(false); } }));
        raiz.addView(barraJogo, new LinearLayout.LayoutParams(-1, (int) (46 * dp)));

        cascata = new Cascata(this, teclado, synth);
        cascata.setFim(this);
        cascata.setVisibility(View.GONE);
        raiz.addView(cascata, new LinearLayout.LayoutParams(-1, 0, 1.15f));
        raiz.addView(teclado, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(raiz);

        // tela cheia de verdade; respeita o entalhe da câmera
        raiz.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = in.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout());
                    v.setPadding(i.left, 0, i.right, 0);
                }
                return in;
            }
        });
        imersivo();
        aplicar();
    }

    private void imersivo() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) imersivo(); }

    private LinearLayout linha() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (8 * dp);
        l.setPadding(pad, pad / 2, pad, pad / 2);
        return l;
    }

    private void espaco(LinearLayout l) { l.addView(new View(this), new LinearLayout.LayoutParams((int) (5 * dp), 1)); }

    private TextView botao(String txt, View.OnClickListener ouvinte) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, txt.length() == 1 ? 20 : 13);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth((int) (40 * dp));
        t.setPadding((int) (10 * dp), 0, (int) (10 * dp), 0);
        t.setBackground(fundo(0xFF2A2A30));
        t.setOnClickListener(ouvinte);
        t.setLayoutParams(new LinearLayout.LayoutParams(-2, (int) (36 * dp)));
        return t;
    }

    private GradientDrawable fundo(int cor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(18 * dp);
        g.setColor(cor);
        return g;
    }

    private void aplicar() {
        if (oitava * 7 + brancas - 1 > Teclado.MAX_G) oitava = (Teclado.MAX_G - brancas + 1) / 7;
        teclado.configurar(oitava, brancas, nomes);
        tFaixa.setText(teclado.faixa());
        bTam.setText(brancas == 8 ? "1 oitava" : brancas == 15 ? "2 oitavas" : "3 oitavas");
        bNomes.setText(nomes == Teclado.NOMES_DO_RE_MI ? "Dó Ré Mi" : nomes == Teclado.NOMES_CDE ? "C D E" : "Sem nomes");
        bSom.setText(timbre == Sintetizador.ACUSTICO ? "Acústico" : "Elétrico");
        bSust.setBackground(fundo(sustentar ? LARANJA : 0xFF2A2A30));
        prefs.edit().putInt("oitava", oitava).putInt("nomes", nomes).putInt("brancas", brancas).putInt("timbre", timbre).apply();
    }

    private void ajustes() {
        final String[] itens = {"Eco (reverb)", "Força pelo toque (ponta da tecla = forte)", "Vibrar ao acertar/errar"};
        final String[] chaves = {"reverb", "forca", "vibrar"};
        boolean[] marc = new boolean[3];
        for (int i = 0; i < 3; i++) marc[i] = prefs.getBoolean(chaves[i], true);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Ajustes")
            .setMultiChoiceItems(itens, marc, new DialogInterface.OnMultiChoiceClickListener() {
                public void onClick(DialogInterface d, int i, boolean on) {
                    prefs.edit().putBoolean(chaves[i], on).apply();
                    if (i == 0) synth.setReverb(on);
                    if (i == 1) teclado.setForcaFixa(!on);
                    if (i == 2) teclado.setVibrar(on);
                }
            })
            .setPositiveButton("OK", null)
            .show();
    }

    // ---------- progresso do jogador ----------
    private int xp() { return prefs.getInt("xp", 0); }
    private static int nivelDe(int xp) { return (int) Math.floor(Math.sqrt(xp / 120.0)) + 1; }
    private static int xpDoNivel(int n) { return 120 * (n - 1) * (n - 1); }
    private int sequencia() {
        long hoje = hoje();
        long ult = prefs.getLong("ultimoDia", -10);
        return hoje - ult <= 1 ? prefs.getInt("sequencia", 0) : 0;
    }
    private static long hoje() { long a = System.currentTimeMillis(); return (a + java.util.TimeZone.getDefault().getOffset(a)) / 86400000L; }
    private void registrarDia() {
        long hoje = hoje();
        long ult = prefs.getLong("ultimoDia", -10);
        int seq = prefs.getInt("sequencia", 0);
        if (hoje == ult) return;
        seq = hoje - ult == 1 ? seq + 1 : 1;
        prefs.edit().putLong("ultimoDia", hoje).putInt("sequencia", seq).apply();
    }

    private String cabecalhoJogador() {
        int x = xp(), n = nivelDe(x);
        int seq = sequencia();
        return "Nível " + n + "  ·  " + (x - xpDoNivel(n)) + "/" + (xpDoNivel(n + 1) - xpDoNivel(n)) + " XP"
            + (seq > 0 ? "  ·  🔥 " + seq + (seq == 1 ? " dia" : " dias seguidos") : "");
    }

    // ---------- escolha de música ----------
    private static final int[] CORES_CAT = {0xFF66BB6A, 0xFFAB8CFF, 0xFFFFC107, 0xFFFF6FA8, 0xFF4FC3F7, 0xFFFF7043, 0xFF26C6DA, 0xFFEF5350, 0xFF9CCC65};

    private TextView rotulo(String txt, int cor, float sp, boolean negrito) {
        TextView t = new TextView(this);
        t.setText(txt); t.setTextColor(cor);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        if (negrito) t.getPaint().setFakeBoldText(true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return t;
    }

    private ArrayList<Musicas.Musica> daCategoria(String cat) {
        ArrayList<Musicas.Musica> l = new ArrayList<Musicas.Musica>();
        for (Musicas.Musica m : Musicas.todas(getAssets())) if (m.categoria.equals(cat)) l.add(m);
        return l;
    }

    private View tituloDialogo(String principal, int cor, String sub) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding((int) (20 * dp), (int) (14 * dp), (int) (20 * dp), (int) (8 * dp));
        l.addView(rotulo(principal, cor, 20, true));
        if (sub != null) l.addView(rotulo(sub, OURO, 13, false));
        return l;
    }

    private void escolherMusica() {
        final ArrayList<String> cats = Musicas.categorias(getAssets());
        if (cats.isEmpty()) { Toast.makeText(this, "Acervo vazio", Toast.LENGTH_SHORT).show(); return; }
        android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            public int getCount() { return cats.size(); }
            public Object getItem(int i) { return cats.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, android.view.ViewGroup pai) {
                ArrayList<Musicas.Musica> ms = daCategoria(cats.get(i));
                int est = 0, coroas = 0;
                StringBuilder ex = new StringBuilder();
                for (Musicas.Musica m : ms) {
                    est += prefs.getInt("est_" + m.id, 0);
                    if (prefs.getBoolean("coroa_" + m.id, false)) coroas++;
                    if (ex.length() < 70) ex.append(ex.length() > 0 ? " · " : "").append(m.titulo.replaceAll(" \\(.*", ""));
                }
                int cor = CORES_CAT[i % CORES_CAT.length];
                LinearLayout l = new LinearLayout(Piano.this);
                l.setGravity(Gravity.CENTER_VERTICAL);
                l.setPadding(0, (int) (6 * dp), (int) (16 * dp), (int) (6 * dp));
                View barraCor = new View(Piano.this);
                barraCor.setBackgroundColor(cor);
                l.addView(barraCor, new LinearLayout.LayoutParams((int) (6 * dp), (int) (48 * dp)));
                LinearLayout txt = new LinearLayout(Piano.this);
                txt.setOrientation(LinearLayout.VERTICAL);
                txt.setPadding((int) (14 * dp), 0, 0, 0);
                txt.addView(rotulo(cats.get(i), Color.WHITE, 18, true));
                txt.addView(rotulo(ex.toString(), 0xFF9A9AA4, 12, false));
                l.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                LinearLayout dir = new LinearLayout(Piano.this);
                dir.setOrientation(LinearLayout.VERTICAL);
                dir.setGravity(Gravity.RIGHT);
                dir.addView(rotulo(ms.size() + " músicas", cor, 14, true));
                dir.addView(rotulo("★ " + est + "/" + (ms.size() * 3) + (coroas > 0 ? "   👑 " + coroas : ""), 0xFFFFB300, 13, false));
                l.addView(dir);
                return l;
            }
        };
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setCustomTitle(tituloDialogo("Músicas", LARANJA, cabecalhoJogador()))
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { escolherDaCategoria(cats.get(i), CORES_CAT[i % CORES_CAT.length]); }
            })
            .show();
    }

    private static String mmss(int s) { return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60); }

    private void escolherDaCategoria(String cat, final int cor) {
        final ArrayList<Musicas.Musica> ms = daCategoria(cat);
        android.widget.BaseAdapter ad = new android.widget.BaseAdapter() {
            public int getCount() { return ms.size(); }
            public Object getItem(int i) { return ms.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, android.view.ViewGroup pai) {
                Musicas.Musica m = ms.get(i);
                LinearLayout l = new LinearLayout(Piano.this);
                l.setGravity(Gravity.CENTER_VERTICAL);
                l.setPadding((int) (18 * dp), (int) (7 * dp), (int) (16 * dp), (int) (7 * dp));
                LinearLayout txt = new LinearLayout(Piano.this);
                txt.setOrientation(LinearLayout.VERTICAL);
                boolean coroa = prefs.getBoolean("coroa_" + m.id, false);
                txt.addView(rotulo((coroa ? "👑 " : "") + m.titulo, Color.WHITE, 16, false));
                int rec = prefs.getInt("rec_" + m.id, 0);
                txt.addView(rotulo(m.nivel + "  ·  " + mmss(m.segundos()) + "  ·  " + (m.nD + m.nE) + " notas"
                        + (m.temE() ? "  ·  2 mãos" : "") + (rec > 0 ? "  ·  recorde " + rec : ""),
                    m.nivel.equals("Fácil") ? 0xFF66BB6A : m.nivel.equals("Médio") ? 0xFFFFB300 : 0xFFFF7043, 12, false));
                l.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
                Estrelas e = new Estrelas(Piano.this, 18);
                e.set(prefs.getInt("est_" + m.id, 0));
                l.addView(e);
                return l;
            }
        };
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setCustomTitle(tituloDialogo(cat + "  (" + ms.size() + ")", cor, null))
            .setAdapter(ad, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { abrirMusica(ms.get(i)); }
            })
            .setNegativeButton("Voltar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { escolherMusica(); }
            })
            .show();
    }

    private void abrirMusica(Musicas.Musica m) {
        Musicas.carregar(getAssets(), m);
        if (m.ini == null || m.ini.length == 0) { Toast.makeText(this, "Música vazia", Toast.LENGTH_SHORT).show(); return; }
        musica = m;
        trecho = -1;
        tMusica.setText(m.titulo);
        barra.setVisibility(View.GONE);
        barraJogo.setVisibility(View.VISIBLE);
        cascata.setVisibility(View.VISIBLE);
        cascata.setVelocidade(vel / 100f);
        tVel.setText(vel + "%");
        teclado.setNomes(nomes == Teclado.NOMES_NENHUM ? Teclado.NOMES_DO_RE_MI : nomes);
        jogar(false);
    }

    private int maosEfetivas() { return musica != null && musica.temE() ? maos : Cascata.MAO_D; }

    private void jogar(boolean demo) {
        if (musica == null) return;
        float a = 0, b = -1;
        if (trecho >= 0) {
            a = musica.secIni[trecho];
            b = trecho + 1 < musica.secIni.length ? musica.secIni[trecho + 1] : -1;
        }
        int mm = maosEfetivas();
        cascata.iniciar(musica, mm, desafio, demo, a, b);
        bOuvir.setText(demo ? "Parar" : "Autoplay");
        bOuvir.setBackground(fundo(demo ? LARANJA : 0xFF2A2A30));
        bMao.setText(mm == Cascata.MAO_D ? "Mão direita" : mm == Cascata.MAO_E ? "Mão esquerda" : "Duas mãos");
        bMao.setBackground(fundo(mm == Cascata.MAO_E ? Teclado.AZUL_ESC : mm == Cascata.DUAS ? 0xFF7E57C2 : 0xFF2A2A30));
        bModo.setText(desafio ? "Desafio" : "Aprender");
        bModo.setBackground(fundo(desafio ? 0xFFD84315 : 0xFF2A2A30));
        bTrecho.setText(trecho >= 0 ? "↻ " + curto(musica.secRot[trecho]) : "Trecho");
        bTrecho.setBackground(fundo(trecho >= 0 ? 0xFF00897B : 0xFF2A2A30));
    }

    private static String curto(String s) { return s.length() > 10 ? s.substring(0, 9) + "…" : s; }

    private void escolherTrecho() {
        if (musica == null) return;
        final int n = musica.secIni.length;
        String[] itens = new String[n + 1];
        itens[0] = "Música inteira";
        float batida = 60f / musica.bpm;
        for (int i = 0; i < n; i++) {
            float fimB = i + 1 < n ? musica.secIni[i + 1] : musica.total;
            boolean dom = prefs.getBoolean("dom_" + musica.id + "_" + i, false);
            itens[i + 1] = (dom ? "✓ " : "   ") + (i + 1) + ". " + musica.secRot[i] + "  (" + Math.round((fimB - musica.secIni[i]) * batida) + " s)";
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setCustomTitle(tituloDialogo("Praticar um trecho", LARANJA, "O trecho repete até você sair. ✓ = dominado (95%+)"))
            .setItems(itens, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int i) { trecho = i - 1; jogar(false); }
            })
            .show();
    }

    private void fecharMusica() {
        cascata.parar();
        musica = null;
        cascata.setVisibility(View.GONE);
        barraJogo.setVisibility(View.GONE);
        barra.setVisibility(View.VISIBLE);
        aplicar();
    }

    @Override public boolean tocou(int midi) { return musica == null || cascata.tocou(midi); }
    @Override public void soltou(int midi) { if (musica != null) cascata.soltou(midi); }

    @Override
    public void fim(Cascata.Resultado r) {
        if (musica == null) return;
        if (r.demo) {
            Toast.makeText(this, "Agora é sua vez!", Toast.LENGTH_SHORT).show();
            jogar(false);
            return;
        }
        float prec = r.precisao();
        int est = r.acertos == 0 ? 0 : prec >= 95 ? 3 : prec >= 85 ? 2 : 1;
        String k = musica.id;
        int xpGanho = Math.max(5, r.pontos / 20) * (r.desafio ? 2 : 1);
        int xpAntes = xp();
        SharedPreferences.Editor ed = prefs.edit();
        ed.putInt("xp", xpAntes + xpGanho);
        registrarDia();
        if (r.trecho) {
            if (prec >= 95) ed.putBoolean("dom_" + k + "_" + trecho, true);
            ed.apply();
            Toast.makeText(this, Math.round(prec) + "% · " + (prec >= 95 ? "trecho dominado! " : "") + "+" + xpGanho + " XP · repetindo",
                Toast.LENGTH_SHORT).show();
            jogar(false);
            return;
        }
        boolean recorde = r.pontos > prefs.getInt("rec_" + k, 0);
        if (recorde) ed.putInt("rec_" + k, r.pontos);
        if (est > prefs.getInt("est_" + k, 0)) ed.putInt("est_" + k, est);
        boolean coroaNova = r.desafio && est == 3 && !prefs.getBoolean("coroa_" + k, false);
        if (coroaNova) ed.putBoolean("coroa_" + k, true);
        ed.apply();
        boolean subiu = nivelDe(xpAntes + xpGanho) > nivelDe(xpAntes);

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setPadding((int) (16 * dp), (int) (14 * dp), (int) (16 * dp), (int) (6 * dp));
        Estrelas e = new Estrelas(this, 40); e.set(est);
        v.addView(e);
        TextView t1 = new TextView(this);
        t1.setText(coroaNova ? "👑 Coroa do Desafio!" : est == 3 ? "Incrível!" : est == 2 ? "Muito bem!" : "Boa! Tente de novo");
        t1.setTextColor(coroaNova ? OURO : LARANJA); t1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        t1.getPaint().setFakeBoldText(true); t1.setGravity(Gravity.CENTER);
        v.addView(t1);
        StringBuilder sb = new StringBuilder();
        sb.append(r.pontos).append(" pontos").append(recorde ? "  ·  NOVO RECORDE!" : "").append('\n');
        sb.append(Math.round(prec)).append("% de precisão  ·  ").append(r.perfeitos).append(" perfeitas  ·  maior combo ").append(r.maxCombo).append('\n');
        if (r.longas > 0) sb.append("notas longas seguradas: ").append(r.seguradas).append(" de ").append(r.longas).append('\n');
        sb.append("+").append(xpGanho).append(" XP").append(r.desafio ? " (×2 desafio)" : "");
        if (subiu) sb.append("  ·  SUBIU PARA O NÍVEL ").append(nivelDe(xpAntes + xpGanho)).append("!");
        // notas que mais erra
        int[] top = {-1, -1};
        for (int i = 0; i < 128; i++) if (r.errosPorNota[i] > 0) {
            if (top[0] < 0 || r.errosPorNota[i] > r.errosPorNota[top[0]]) { top[1] = top[0]; top[0] = i; }
            else if (top[1] < 0 || r.errosPorNota[i] > r.errosPorNota[top[1]]) top[1] = i;
        }
        if (top[0] >= 0 && r.errosPorNota[top[0]] >= 2) {
            sb.append("\nAtenção ao ").append(Teclado.nome(top[0], teclado.nomesCde())).append(top[0] / 12 - 1);
            if (top[1] >= 0 && r.errosPorNota[top[1]] >= 2) sb.append(" e ao ").append(Teclado.nome(top[1], teclado.nomesCde())).append(top[1] / 12 - 1);
            sb.append(" (onde mais errou). Dica: use Trecho.");
        }
        if (!r.desafio && est == 3) sb.append("\nPróximo passo: tente o modo Desafio pela coroa 👑");
        TextView t2 = new TextView(this);
        t2.setText(sb.toString());
        t2.setTextColor(Color.WHITE); t2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t2.setGravity(Gravity.CENTER);
        v.addView(t2);

        Musicas.Musica[] todas = Musicas.todas(getAssets());
        int idx = 0;
        for (int i = 0; i < todas.length; i++) if (todas[i] == musica) idx = i;
        final Musicas.Musica prox = todas[(idx + 1) % todas.length];
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setView(v)
            .setCancelable(false)
            .setPositiveButton("Próxima", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { abrirMusica(prox); } })
            .setNeutralButton("De novo", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { jogar(false); } })
            .setNegativeButton("Lista", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { escolherMusica(); } })
            .show();
    }

    @Override protected void onResume() { super.onResume(); synth.iniciar(); if (musica != null) cascata.retomar(); imersivo(); }
    @Override protected void onPause() { cascata.pausar(); teclado.soltarTudo(); synth.parar(); super.onPause(); }
    @Override protected void onDestroy() { synth.liberar(); super.onDestroy(); }
}
