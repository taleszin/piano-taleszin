#!/usr/bin/env python3
"""Gera os exemplos de áudio e o gráfico de harmônicos desta pasta.

Os sintetizadores dos dois apps foram reescritos aqui em numpy, com as mesmas fórmulas do Java.
Por isso o áudio é o que o app calcula, não uma gravação do alto-falante.
Uso: python3 exemplos/gerar.py   (precisa de numpy e ffmpeg; rode antes ./build.sh em celular/)
"""
import os, re, wave, subprocess
import numpy as np

SR = 48000
AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(AQUI)
hz = lambda m: 440 * 2 ** ((m - 69) / 12)
q60 = lambda seg: np.log(0.001) / (seg * SR)          # expoente por amostra para cair 60 dB


# ---------- tablet: FM 1:1 + sininho 14:1 (tablet/src/.../Sintetizador.java) ----------
def fm_tablet(m, dur, solta):
    n = np.arange(int((dur + 0.4) * SR)); f = hz(m)
    ind = max(0.5, 1.9 - (m - 60) * 0.03) * np.exp(q60(1.4) * n)
    sino = (0.16 if f * 14 < SR * 0.42 else 0) * np.exp(q60(0.22) * n)
    fase = 2 * np.pi * f * n / SR
    s = np.sin(fase + np.sin(fase) * ind) + sino * np.sin(14 * fase)
    env = np.exp(q60(max(1.0, 4.5 - (m - 48) * 0.07)) * n) * np.minimum(1, n / (0.004 * SR))
    k = int(solta * SR); env[k:] *= np.exp(q60(0.25) * np.arange(len(n) - k))
    return s * env


# ---------- celular: ressonadores modais (celular/src/.../Sintetizador.java) ----------
HM = [36, 48, 61, 69, 74, 84, 96]
HDB = [[0, 12, 10, 12, 10, 6, 8, 0], [0, 10, 4, -2, -10, -10, -6, -12], [0, 2, -14, -18, -12, -22, -20, -32],
       [0, -11, -16, -26, -30, -33, -36, -48], [0, -22, -34, -45, -50, -58, -60, -70],
       [0, -22, -30, -46, -60, -66, -68, -70], [0, -30, -45, -60, -60, -60, -60, -60]]

def amp(m, h, v, versao):
    if versao == 'antes':   # 2.0 original: mesmo perfil em todas as regiões
        return (1.0 if h == 1 else 0.75 / h ** 1.35) * np.exp(-(h - 1) * 0.45 * (1 - v))
    db = np.interp(m, HM, [t[h - 1] for t in HDB]) + (v - 0.55) * 10 * np.log2(h)
    return 10 ** (db / 20)

def modal(m, v, dur, solta, versao):
    n = np.arange(int((dur + 0.6) * SR)); f0 = hz(m)
    B = 1.2e-4 * np.exp(0.045 * (m - 60)); tau1 = max(1.6, min(12, 7.5 * 2 ** (-(m - 48) / 22)))
    partes, soma = [], 0
    for h in range(1, 9):
        fh = h * f0 * np.sqrt(1 + B * h * h)
        if fh > 6000: break
        a = amp(m, h, v, versao); tau = tau1 / (1 + 0.22 * (h - 1)) * (1 if h == 1 else 0.8)
        partes.append((fh, tau, a)); soma += a if versao == 'antes' else a * a
        if h <= 2: partes.append((fh * 2 ** (0.9 / 1200), tau * 0.35, a * 0.55))
    norm = 0.55 / max(0.6, soma if versao == 'antes' else 1.2 * np.sqrt(soma))
    s = sum(a * norm * np.exp(-n / (tau * SR)) * np.sin(2 * np.pi * fh * n / SR) for fh, tau, a in partes)
    k = int(solta * SR); s[k:] *= np.exp(q60(0.28) * np.arange(len(n) - k))
    return s * v ** 1.6


def mixar(notas, segundos, saida):
    out = np.zeros(int((segundos + 3) * SR))
    for ini, x in notas:
        i = int(ini * SR)
        if i < len(out): out[i:i + len(x)] += x[:len(out) - i]
    return saida(out)

def gravar(nome, x):
    x = x[:int(np.nonzero(np.abs(x) > 1e-3)[0][-1]) + SR // 2]
    x = x / np.abs(x).max() * 0.89          # mesmo volume de pico nos três, para comparar timbre
    tmp = os.path.join(AQUI, nome + '.wav')
    w = wave.open(tmp, 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((x * 32767).astype(np.int16).tobytes()); w.close()
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', tmp, '-b:a', '128k', os.path.join(AQUI, nome + '.mp3')], check=True)
    os.remove(tmp); print('ok', nome + '.mp3')


SEG = 22
# tablet: a linha da música no formato do tablet (só melodia)
linha = [l for l in open(os.path.join(RAIZ, 'tablet/assets/musicas/8-trilhas.txt'), encoding='utf-8') if l.startswith('Harry Potter')][0]
campos = [c.strip() for c in linha.split('|')]
bpm = float(campos[2]); t = 0; notas = []
NOME = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
for tok in ' '.join(campos[3:]).split():
    alt, d = tok.split(':'); d = float(d) * 60 / bpm
    for a in alt.split('+'):
        mm = re.match(r'([A-G])(#|b)?(\d)', a)
        if mm and t < SEG:
            m = 12 * (int(mm.group(3)) + 1) + NOME[mm.group(1)] + {'#': 1, 'b': -1, None: 0}[mm.group(2)]
            notas.append((t, fm_tablet(m, d, d * 0.93)))
    t += d
gravar('hedwig-tablet', mixar(notas, SEG, lambda o: (lambda x: x / (1 + np.abs(x) * 0.6))(o * 0.32)))

# celular: arquivo compilado pelo m7s.py (duas mãos, acompanhamento gerado pelas cifras)
txt = open(os.path.join(RAIZ, 'celular/build/assets/musicas/8-trilhas/harry-potter-hedwig.txt'), encoding='utf-8').read()
bpm = float(re.search(r'^bpm=(.*)$', txt, re.M).group(1))
ev = [l.split() for l in txt.split('---\n', 1)[1].splitlines() if l.strip()]
for versao in ('antes', 'depois'):
    notas = []
    for e in ev:
        ini, d = float(e[0]) * 60 / bpm, float(e[1]) * 60 / bpm
        if ini > SEG: break
        v = 0.65 if e[2] == 'D' else 0.5
        for m in map(int, e[3].split('+')): notas.append((ini, modal(m, v, d, d * 0.93, versao) * 0.707))
    if versao == 'antes': fim = lambda o: (lambda x: x / (1 + np.abs(x) * 0.5))(o * 0.55)
    else: fim = lambda o: (lambda x: np.sign(x) * np.where(np.abs(x) <= 0.8, np.abs(x), 0.8 + 0.2 * np.tanh((np.abs(x) - 0.8) / 0.2)))(o * 0.55)
    gravar('hedwig-celular-' + versao, mixar(notas, SEG, fim))


# ---------- gráfico: harmônicos medidos (piano real da Univ. de Iowa, mf) x app antes x app depois ----------
MEDIDO = {  # dB relativos à fundamental, harmônicos 1..8, janela 30–400 ms após o ataque, força 0,6
    'Dó3 (grave)': {'real': [0, 14, 6, -1, -13, -11, 2, -11], 'antes': [0, -12, -22, -27, -32, -35, -39, -42], 'depois': [0, 10, 1, -5, -13, -13, -9, -15]},
    'Ré5 (agudo)': {'real': [0, -20, -32, -40, -45, -52, -50, -67], 'antes': [0, -13, -22, -27, -32, -36, -39, -42], 'depois': [0, -22, -37, -48, -53, -61, -63, -73]},
}
COR = {'real': '#222222', 'antes': '#E4572E', 'depois': '#2E86DE'}
ROT = {'real': 'piano real', 'antes': 'app 2.0 (antes)', 'depois': 'app 2.0 (agora)'}
W, H, PW, Y0, Y1 = 880, 340, 360, 20, -75
svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="Helvetica,Arial,sans-serif">',
       f'<rect width="{W}" height="{H}" rx="12" fill="#ffffff"/>']
for p, (titulo, series) in enumerate(MEDIDO.items()):
    x0, top, alt = 60 + p * (PW + 50), 50, 220
    X = lambda h: x0 + (h - 1) * PW / 7
    Y = lambda d: top + (Y0 - d) / (Y0 - Y1) * alt
    svg.append(f'<text x="{x0 + PW / 2}" y="32" text-anchor="middle" font-size="16" font-weight="bold" fill="#222">{titulo}</text>')
    for d in range(20, -80, -20):
        svg.append(f'<line x1="{x0}" x2="{x0 + PW}" y1="{Y(d):.1f}" y2="{Y(d):.1f}" stroke="#e5e5e5"/>')
        svg.append(f'<text x="{x0 - 8}" y="{Y(d) + 4:.1f}" text-anchor="end" font-size="11" fill="#777">{d:+d}</text>')
    for h in range(1, 9):
        svg.append(f'<text x="{X(h):.1f}" y="{top + alt + 18}" text-anchor="middle" font-size="11" fill="#777">{h}</text>')
    svg.append(f'<text x="{x0 + PW / 2}" y="{top + alt + 36}" text-anchor="middle" font-size="11" fill="#777">harmônico (1 = a nota)</text>')
    for k, v in series.items():
        pts = ' '.join(f'{X(h + 1):.1f},{Y(d):.1f}' for h, d in enumerate(v))
        larg = 3.5 if k == 'real' else 2.5
        svg.append(f'<polyline points="{pts}" fill="none" stroke="{COR[k]}" stroke-width="{larg}"' + (' stroke-dasharray="6 4"' if k == 'antes' else '') + '/>')
svg.append(f'<text x="22" y="{50 + 110}" font-size="11" fill="#777" transform="rotate(-90 22 160)" text-anchor="middle">dB</text>')
lx = 170
for k in ('real', 'antes', 'depois'):
    svg.append(f'<line x1="{lx}" x2="{lx + 28}" y1="322" y2="322" stroke="{COR[k]}" stroke-width="3"' + (' stroke-dasharray="6 4"' if k == 'antes' else '') + '/>')
    svg.append(f'<text x="{lx + 36}" y="326" font-size="13" fill="#222">{ROT[k]}</text>'); lx += 190
svg.append('</svg>')
open(os.path.join(AQUI, 'harmonicos.svg'), 'w').write('\n'.join(svg)); print('ok harmonicos.svg')
