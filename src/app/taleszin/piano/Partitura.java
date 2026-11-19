package app.taleszin.piano;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Partitura simplificada: uma bolha por nota, largura = duração; a atual em laranja. */
final class Partitura extends View {
    private static final int LARANJA = 0xFFFF7A1E;
    private Musicas.Musica musica;
    private int atual;
    private boolean cde;
    private final float dp;
    private final Paint pFeita = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pProx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAtual = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPreta = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLinha = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float rolagem;

    Partitura(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        pFeita.setColor(0xFF26262C);
        pProx.setColor(0xFFF2F2F0);
        pAtual.setColor(LARANJA);
        pPreta.setColor(0xFF3A3A44);
        pTexto.setTextAlign(Paint.Align.CENTER);
        pTexto.setFakeBoldText(true);
        pTexto.setTextSize(15 * dp);
        pLinha.setColor(0xFF55555F);
        pLinha.setStrokeWidth(2 * dp);
        setBackgroundColor(0xFF16161B);
    }

    void set(Musicas.Musica m, int i, boolean nomesCde) { musica = m; atual = i; cde = nomesCde; invalidate(); }

    private float largura(int i) { return 26 * dp + 18 * dp * Math.min(musica.duracoes[i], 3f); }
    private float gap(int i) { return musica.fimDeFrase[i] ? 18 * dp : 5 * dp; }

    @Override
    protected void onDraw(Canvas c) {
        if (musica == null) return;
        float h = getHeight(), y0 = 8 * dp, y1 = h - 8 * dp;
        // posição da nota atual, para mantê-la a ~1/3 da largura
        float xAtual = 0;
        for (int i = 0; i < atual && i < musica.notas.length; i++) xAtual += largura(i) + gap(i);
        float alvo = Math.max(0, xAtual - getWidth() / 3f);
        rolagem += (alvo - rolagem) * 0.5f;
        if (Math.abs(alvo - rolagem) > 1) postInvalidateOnAnimation(); else rolagem = alvo;
        float x = 10 * dp - rolagem;
        for (int i = 0; i < musica.notas.length; i++) {
            float w = largura(i);
            if (x + w > 0 && x < getWidth()) {
                int m = musica.notas[i];
                boolean preta = Teclado.nome(m, true).length() > 1;
                Paint fundo = i < atual ? pFeita : i == atual ? pAtual : (preta ? pPreta : pProx);
                r.set(x, y0, x + w, y1);
                c.drawRoundRect(r, 8 * dp, 8 * dp, fundo);
                pTexto.setColor(i < atual ? 0xFF6A6A72 : (i == atual || preta) ? 0xFFFFFFFF : 0xFF2A2A2A);
                c.drawText(Teclado.nome(m, cde), x + w / 2, (y0 + y1) / 2 + 5 * dp, pTexto);
            }
            x += w + gap(i);
            if (musica.fimDeFrase[i]) c.drawLine(x - 9 * dp - 1, y0 + 6 * dp, x - 9 * dp - 1, y1 - 6 * dp, pLinha);
        }
    }
}
