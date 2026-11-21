#!/usr/bin/env python3
"""Converte um arquivo MIDI em .m7s (formato v2) já simplificado para tocar no celular.

Uso:
  midi2m7s.py entrada.mid --listar
      mostra trilhas/canais com nº de notas, altura média e extensão (para escolher as mãos)
  midi2m7s.py entrada.mid -o saida.m7s --titulo "Für Elise (Beethoven)" --nivel Médio [opções]

Opções:
  --trilhaD 1,2   trilhas (índices do --listar) da mão direita   } sem isso: divide por altura
  --trilhaE 3     trilhas da mão esquerda                        }   (--divisao 60 = C4 vai pra D)
  --canal         usar índices de canal em vez de trilha nos --trilhaD/--trilhaE
  --divisao 60    altura que separa as mãos quando não há trilhas definidas
  --grade .25     grade de quantização em semínimas (.25 semicolcheia, .5 colcheia, 1/3 tercina)
  --maxD 2        máx. notas simultâneas na mão direita (1 = só melodia, a mais aguda)
  --maxE 3        máx. notas simultâneas na mão esquerda (mantém a mais grave + as de cima)
  --semE          descarta a mão esquerda (para depois escrever C: com cifras)
  --de 1 --ate 64 trecho em compassos (1 = primeiro compasso do MIDI)
  --transpor -12  semitons
  --bpm 90        força o andamento (padrão: o primeiro tempo do MIDI)
  --secao 8       compassos por seção ([P1], [P2]...; renomeie depois)
  --durmin .25    descarta notas mais curtas que isso (ornamentos)
"""
import sys, argparse
from fractions import Fraction as F

NOMES = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']


def nome(m):
    return f'{NOMES[m % 12]}{m // 12 - 1}'


def ler_midi(path):
    d = open(path, 'rb').read()
    if d[:4] != b'MThd':
        raise SystemExit('não é MIDI')
    hl = int.from_bytes(d[4:8], 'big')
    tpq = int.from_bytes(d[12:14], 'big')
    if tpq & 0x8000:
        raise SystemExit('MIDI SMPTE não suportado')
    pos = 8 + hl
    notas, tempos, compassos = [], [], []
    trilha = 0
    while pos + 8 <= len(d):
        tipo, tam = d[pos:pos + 4], int.from_bytes(d[pos + 4:pos + 8], 'big')
        pos += 8
        if tipo != b'MTrk':
            pos += tam; continue
        fim = pos + tam
        t, status, abertas = 0, 0, {}
        while pos < fim:
            v = 0
            while True:
                b = d[pos]; pos += 1
                v = (v << 7) | (b & 0x7F)
                if not b & 0x80: break
            t += v
            b = d[pos]
            if b & 0x80:
                status = b; pos += 1
            if status == 0xFF:
                mt = d[pos]; pos += 1
                ln = 0
                while True:
                    b = d[pos]; pos += 1
                    ln = (ln << 7) | (b & 0x7F)
                    if not b & 0x80: break
                dado = d[pos:pos + ln]; pos += ln
                if mt == 0x51: tempos.append((t, int.from_bytes(dado, 'big')))
                if mt == 0x58: compassos.append((t, dado[0], 2 ** dado[1]))
                if mt == 0x2F: break
            elif status in (0xF0, 0xF7):
                ln = 0
                while True:
                    b = d[pos]; pos += 1
                    ln = (ln << 7) | (b & 0x7F)
                    if not b & 0x80: break
                pos += ln
            else:
                hi, ch = status & 0xF0, status & 0x0F
                n = 1 if hi in (0xC0, 0xD0) else 2
                a = d[pos]; b2 = d[pos + 1] if n == 2 else 0
                pos += n
                if hi == 0x90 and b2 > 0:
                    if (ch, a) in abertas:
                        t0, v0 = abertas.pop((ch, a)); notas.append([t0, t, a, ch, trilha, v0])
                    abertas[(ch, a)] = (t, b2)
                elif hi == 0x80 or (hi == 0x90 and b2 == 0):
                    if (ch, a) in abertas:
                        t0, v0 = abertas.pop((ch, a)); notas.append([t0, t, a, ch, trilha, v0])
        pos = fim
        trilha += 1
    notas = [n for n in notas if n[3] != 9 and n[1] > n[0]]  # sem bateria
    return tpq, notas, tempos, compassos


def q(x, g):
    return F(round(F(x) / g)) * g


def fmtd(d):
    d = F(d)
    if d == 1: return ''
    if d.denominator in (1, 2, 4, 8):
        s = f'{float(d):g}'
        return ':' + (s[1:] if s.startswith('0.') else s)
    return f':{d.numerator}/{d.denominator}'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('midi'); ap.add_argument('-o'); ap.add_argument('--listar', action='store_true')
    ap.add_argument('--titulo', default='Sem título'); ap.add_argument('--nivel', default='Médio')
    ap.add_argument('--trilhaD'); ap.add_argument('--trilhaE'); ap.add_argument('--canal', action='store_true')
    ap.add_argument('--divisao', type=int, default=60); ap.add_argument('--grade', default='.25')
    ap.add_argument('--maxD', type=int, default=2); ap.add_argument('--maxE', type=int, default=3)
    ap.add_argument('--semE', action='store_true'); ap.add_argument('--de', type=int, default=1)
    ap.add_argument('--ate', type=int, default=9999); ap.add_argument('--transpor', type=int, default=0)
    ap.add_argument('--bpm', type=int); ap.add_argument('--secao', type=int, default=8)
    ap.add_argument('--durmin', default='0'); ap.add_argument('--fonte', default='')
    a = ap.parse_args()
    tpq, notas, tempos, comps = ler_midi(a.midi)
    if a.listar:
        grupos = {}
        for n in notas:
            grupos.setdefault((n[4], n[3]), []).append(n[2])
        print(f'tpq={tpq} tempos={[(t, round(60e6 / u)) for t, u in tempos[:6]]} compassos={comps[:4]}')
        for (tr, ch), ms in sorted(grupos.items()):
            print(f'trilha {tr} canal {ch}: {len(ms)} notas, média {nome(round(sum(ms) / len(ms)))}, {nome(min(ms))}..{nome(max(ms))}')
        return
    g = F(a.grade) if '/' not in a.grade else F(*map(int, a.grade.split('/')))
    num, den = (comps[0][1], comps[0][2]) if comps else (4, 4)
    tam = F(num * 4, den)
    bpm = a.bpm or (round(60e6 / tempos[0][1]) if tempos else 120)
    durmin = F(a.durmin)

    def chave(n):
        return n[3] if a.canal else n[4]
    sd = set(map(int, a.trilhaD.split(','))) if a.trilhaD else None
    se = set(map(int, a.trilhaE.split(','))) if a.trilhaE else set()
    maos = {'D': [], 'E': []}
    for n in notas:
        ini, fim = q(F(n[0], tpq), g), q(F(n[1], tpq), g)
        if fim - ini < max(g, durmin) and F(n[1] - n[0], tpq) < max(g, durmin) / 2:
            continue
        if fim <= ini: fim = ini + g
        m = n[2] + a.transpor
        if sd is not None:
            mao = 'D' if chave(n) in sd else 'E' if chave(n) in se else None
        else:
            mao = 'D' if n[2] >= a.divisao else 'E'
        if mao is None or (mao == 'E' and a.semE): continue
        maos[mao].append((ini, fim, m))

    t0 = F(a.de - 1) * tam
    t1 = F(a.ate) * tam
    tudo = [x for v in maos.values() for x in v if t0 <= x[0] < t1]
    if not tudo: raise SystemExit('nenhuma nota no trecho')
    primeira = min(x[0] for x in tudo)
    # começa no compasso da 1ª nota; se ela não está no tempo 1, vira anacruse
    comp_ini = (primeira // tam) * tam
    desloc = primeira - comp_ini
    anac = F(0)
    if desloc > 0 and desloc >= tam / 2:
        anac = tam - desloc  # anacruse: começa direto na 1ª nota
        base = primeira
    else:
        base = comp_ini

    def seq(lista, maxn, aguda):
        lista = [x for x in lista if t0 <= x[0] < t1]
        por = {}
        for ini, fim, m in lista:
            por.setdefault(ini, {})
            por[ini][m] = max(por[ini].get(m, 0), fim)
        onsets = sorted(por)
        evs = []
        for i, o in enumerate(onsets):
            ms = sorted(por[o])
            if len(ms) > maxn:
                ms = ms[-maxn:] if aguda else [ms[0]] + ms[-(maxn - 1):] if maxn > 1 else [ms[0]]
            fim = max(por[o][m] for m in ms)
            prox = onsets[i + 1] if i + 1 < len(onsets) else fim
            fim = min(fim, prox)
            if fim <= o: fim = o + g
            evs.append((o - base, fim - base, ms))
        return evs

    seqs = {'D': seq(maos['D'], a.maxD, True)}
    if maos['E'] and not a.semE:
        seqs['E'] = seq(maos['E'], a.maxE, False)
    fim_total = max(e[1] for v in seqs.values() for e in v)

    def barras(evs):
        """-> lista de compassos (strings de tokens) com pausas e ligaduras nas barras"""
        out, t = [], F(0)
        limites = []
        b = anac if anac else tam
        while b < fim_total + tam:
            limites.append(b); b += tam
        cur, ci = [], 0
        def emit(ms, d, lig):
            nonlocal t, ci, cur
            while d > 0:
                lim = limites[ci]
                p = min(d, lim - t)
                tok = ('+'.join(nome(m) for m in ms) if ms else 'R') + fmtd(p)
                if ms and (lig or p < d): tok += '~'
                cur.append(tok); t += p; d -= p
                if t == lim:
                    out.append(cur); cur = []; ci += 1
        for ini, fim, ms in evs:
            if ini > t: emit([], ini - t, False)
            emit(ms, fim - ini, False)
        while ci < len(limites) and t < fim_total and t < limites[-1]:
            emit([], min(limites[ci], fim_total) - t, False)
            if t >= fim_total: break
        if cur: out.append(cur)
        return out
    bD = barras(seqs['D'])
    bE = barras(seqs['E']) if 'E' in seqs else None
    n = max(len(bD), len(bE) if bE else 0)
    def completar(bs, n):
        # compassos finais vazios viram pausas
        while len(bs) < n:
            bs.append(['R' + fmtd(tam)])
        return bs
    bD = completar(bD, n)
    if bE: bE = completar(bE, n)
    # o último compasso pode ficar curto: completa com pausa para casar as mãos
    def soma(c):
        s = F(0)
        for tok in c:
            p = tok.rstrip('~').split(':')
            s += 1 if len(p) == 1 else (F(p[1]) if '/' not in p[1] else F(*map(int, p[1].split('/'))))
        return s
    if bE:
        sd_, se_ = soma(bD[-1]), soma(bE[-1])
        if sd_ < se_: bD[-1].append('R' + fmtd(se_ - sd_))
        if se_ < sd_: bE[-1].append('R' + fmtd(sd_ - se_))

    L = [f'@titulo {a.titulo}', f'@nivel {a.nivel}', f'@bpm {bpm}', f'@compasso {num}/{den}']
    if anac: L.append(f'@anacruse {float(anac):g}')
    L.append(f'@fonte {a.fonte or "midi"}; grade {a.grade}; maxD {a.maxD}; maxE {a.maxE}')
    L.append('')
    for i in range(0, n, a.secao):
        L.append(f'[P{i // a.secao + 1}] Parte {i // a.secao + 1}')
        for j in range(i, min(n, i + a.secao), 4):
            L.append('D: ' + ' | '.join(' '.join(c) for c in bD[j:min(j + 4, i + a.secao, n)]))
        if bE:
            for j in range(i, min(n, i + a.secao), 4):
                L.append('E: ' + ' | '.join(' '.join(c) for c in bE[j:min(j + 4, i + a.secao, n)]))
        L.append('')
    txt = '\n'.join(L)
    if a.o:
        open(a.o, 'w', encoding='utf-8').write(txt)
        print(f'{a.o}: {n} compassos, D {len(seqs["D"])} eventos' + (f', E {len(seqs["E"])} eventos' if bE else ''))
    else:
        print(txt)


if __name__ == '__main__':
    main()
