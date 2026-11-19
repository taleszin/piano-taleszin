package app.taleszin.piano;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Piano extends Activity {
    private static final int LARANJA = 0xFFFF7A1E;
    private Sintetizador synth;
    private Teclado teclado;
    private SharedPreferences prefs;
    private TextView tFaixa, bNomes, bTam, bSust;
    private int oitava, nomes, brancas;
    private boolean sustentar;
    private float dp;

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

        synth = new Sintetizador();
        teclado = new Teclado(this, synth);

        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(0xFF101014);

        LinearLayout barra = new LinearLayout(this);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (8 * dp);
        barra.setPadding(pad, pad / 2, pad, pad / 2);

        TextView titulo = new TextView(this);
        titulo.setText("Piano");
        titulo.setTextColor(LARANJA);
        titulo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titulo.getPaint().setFakeBoldText(true);
        barra.addView(titulo, new LinearLayout.LayoutParams(0, -2, 1f));

        barra.addView(botao("−", new View.OnClickListener() {
            public void onClick(View v) { if (oitava > 1) { oitava--; aplicar(); } }
        }));
        tFaixa = new TextView(this);
        tFaixa.setTextColor(Color.WHITE);
        tFaixa.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        tFaixa.setGravity(Gravity.CENTER);
        tFaixa.setMinWidth((int) (96 * dp));
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
        bSust = botao("Pedal", new View.OnClickListener() {
            public void onClick(View v) { sustentar = !sustentar; synth.setSustentar(sustentar); aplicar(); }
        });
        barra.addView(bSust);

        raiz.addView(barra, new LinearLayout.LayoutParams(-1, (int) (52 * dp)));
        raiz.addView(teclado, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(raiz);
        aplicar();
    }

    private void espaco(LinearLayout l) { l.addView(new View(this), new LinearLayout.LayoutParams((int) (8 * dp), 1)); }

    private TextView botao(String txt, View.OnClickListener ouvinte) {
        TextView t = new TextView(this);
        t.setText(txt);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, txt.length() == 1 ? 22 : 15);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth((int) (44 * dp));
        t.setPadding((int) (12 * dp), 0, (int) (12 * dp), 0);
        t.setBackground(fundo(false));
        t.setOnClickListener(ouvinte);
        t.setLayoutParams(new LinearLayout.LayoutParams(-2, (int) (40 * dp)));
        return t;
    }

    private GradientDrawable fundo(boolean ligado) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(20 * dp);
        g.setColor(ligado ? LARANJA : 0xFF2A2A30);
        return g;
    }

    private void aplicar() {
        teclado.configurar(oitava, brancas, nomes);
        tFaixa.setText(teclado.faixa());
        bTam.setText(brancas == 15 ? "2 oitavas" : "1 oitava");
        bNomes.setText(nomes == Teclado.NOMES_DO_RE_MI ? "Dó Ré Mi" : nomes == Teclado.NOMES_CDE ? "C D E" : "Sem nomes");
        bSust.setBackground(fundo(sustentar));
        prefs.edit().putInt("oitava", oitava).putInt("nomes", nomes).putInt("brancas", brancas).apply();
    }

    @Override protected void onResume() { super.onResume(); synth.iniciar(); }
    @Override protected void onPause() { teclado.soltarTudo(); synth.parar(); super.onPause(); }
    @Override protected void onDestroy() { synth.liberar(); super.onDestroy(); }
}
