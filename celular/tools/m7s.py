#!/usr/bin/env python3
"""Formato v2 de músicas do Piano Taleszin (celular) — leitor, validador e compilador.

Arquivo-fonte: musicas/<n-categoria>/<slug>.m7s   (texto UTF-8)

  # comentário
  @titulo Ode à Alegria (Beethoven)
  @nivel Fácil | Médio | Difícil
  @bpm 100                    (semínimas por minuto)
  @compasso 4/4               (padrão 4/4; 3/4, 6/8, 2/4, 12/8...)
  @anacruse 1                 (tempos do compasso incompleto inicial; padrão 0)
  @padrao baixo-acorde        (padrão da mão esquerda gerada a partir de C:)
  @forma A B A B*2 C          (ordem das seções; sem @forma = ordem do arquivo)
  @fonte texto livre          (de onde veio: mutopia, bitmidi, memória...)
  @tom Mi menor               (tom da gravação original, só informativo)
  @transpor -2                (semitons aplicados a todas as notas e cifras ao compilar; ±12 = oitava)

  [A] Verso                   (id da seção + rótulo opcional)
  D: E4 E4 F4 G4 | G4 F4 E4 D4 | C4:2 R:2      mão direita
  E: C3+G3:4 | G2+D3:4 | C3:2 G2:2             mão esquerda (opcional)
  C: C | G | C G                               cifras por compasso (geram E: se E: faltar)
  @padrao valsa               (troca o padrão só nesta seção)

Evento: alturas[:dur][~]
  alturas = nota{+nota} ou R (pausa).   nota = A-G, # ou b, oitava 1-7 (C4 = dó central)
  dur em semínimas: 1, .5, 1.5, 2, .25, 1/3 (tercina)… padrão 1. "~" liga à próxima (mesmas alturas).
'|' = barra de compasso (conferida). Várias linhas D: na mesma seção são emendadas.
Cifra: Raiz[#|b][qualidade][/Baixo][:dur]; qualidades: (maior) m 7 7M maj7 m7 m7b5 ø ° dim aug + sus2 sus4 6 m6 9 add9 7sus4
  "%" repete a cifra anterior; "-" = sem acorde. Sem duração, o compasso é dividido igualmente.

Padrões (mão esquerda gerada): bloco, baixo, baixo-acorde, valsa, alberti, arpejo, balada, oitavas, marcha, bossa

Uso:
  m7s.py validar arq.m7s [...]     valida (erros = código 1) e mostra resumo
  m7s.py compilar <dir-fonte> <dir-assets>   compila tudo (para no primeiro erro)
  m7s.py midi arq.m7s saida.mid    exporta MIDI para ouvir no Mac
  m7s.py tom arq.m7s [...]         tom estimado (já com @transpor) e extensão das mãos (MIDI, 60 = C4)
"""
import sys, os, re, math
from fractions import Fraction as F

NOTA = re.compile(r'^([A-G])([#b]?)([1-7])$')
BASE = {'C': 0, 'D': 2, 'E': 4, 'F': 5, 'G': 7, 'A': 9, 'B': 11}
NIVEIS = ('Fácil', 'Médio', 'Difícil')
PADROES = ('bloco', 'baixo', 'baixo-acorde', 'valsa', 'alberti', 'arpejo', 'balada', 'oitavas', 'marcha', 'bossa')
QUAL = {
    '': [0, 4, 7], 'm': [0, 3, 7], '7': [0, 4, 7, 10], '7M': [0, 4, 7, 11], 'maj7': [0, 4, 7, 11],
    'm7': [0, 3, 7, 10], 'm7b5': [0, 3, 6, 10], 'ø': [0, 3, 6, 10], '°': [0, 3, 6, 9], 'dim': [0, 3, 6],
    'aug': [0, 4, 8], '+': [0, 4, 8], 'sus2': [0, 2, 7], 'sus4': [0, 5, 7], '6': [0, 4, 7, 9],
    'm6': [0, 3, 7, 9], '9': [0, 4, 7, 10, 14], 'add9': [0, 4, 7, 14], '7sus4': [0, 5, 7, 10],
    'm9': [0, 3, 7, 10, 14], '7M9': [0, 4, 7, 11, 14], 'mM7': [0, 3, 7, 11], '5': [0, 7],
}
CIFRA = re.compile(r'^([A-G])([#b]?)(' + '|'.join(sorted((re.escape(q) for q in QUAL if q), key=len, reverse=True)) + r')?(?:/([A-G])([#b]?))?$')


class Erro(Exception):
    pass


TRANSP = 0   # @transpor da música sendo montada


def midi_de(t):
    m = NOTA.match(t)
    if not m:
        raise Erro(f'nota inválida "{t}"')
    l, a, o = m.groups()
    return 12 * (int(o) + 1) + BASE[l] + (1 if a == '#' else -1 if a == 'b' else 0)


def dur_de(s):
    s = s.strip()
    if not s:
        raise Erro('duração vazia')
    try:
        if '/' in s:
            a, b = s.split('/')
            v = F(int(a), int(b))
        else:
            v = F(s if not s.startswith('.') else '0' + s)
    except (ValueError, ZeroDivisionError):
        raise Erro(f'duração inválida "{s}"')
    if v <= 0 or v > 16:
        raise Erro(f'duração fora da faixa "{s}"')
    return v


def evento(tok):
    """-> (lista_midi ou [] para pausa, dur, ligada)"""
    lig = tok.endswith('~')
    if lig:
        tok = tok[:-1]
    if ':' in tok:
        alt, d = tok.split(':', 1)
        d = dur_de(d)
    else:
        alt, d = tok, F(1)
    if alt == 'R':
        return [], d, False
    ms = [midi_de(x) + TRANSP for x in alt.split('+')]
    if len(set(ms)) != len(ms):
        raise Erro(f'nota repetida no acorde "{tok}"')
    return sorted(ms), d, lig


def cifra(tok):
    m = CIFRA.match(tok)
    if not m:
        raise Erro(f'cifra inválida "{tok}"')
    r, a, q, b, ba = m.groups()
    raiz = (BASE[r] + (1 if a == '#' else -1 if a == 'b' else 0) + TRANSP) % 12
    baixo = raiz if not b else (BASE[b] + (1 if ba == '#' else -1 if ba == 'b' else 0) + TRANSP) % 12
    return raiz, [(raiz + i) for i in QUAL[q or '']], baixo


def ler(caminho):
    txt = open(caminho, encoding='utf-8').read()
    cab = {'compasso': '4/4', 'anacruse': '0', 'padrao': 'baixo-acorde'}
    secoes = []  # dicts: id, rotulo, D:[str], E:[str], C:[str], padrao
    atual = None
    for n, linha in enumerate(txt.splitlines(), 1):
        s = linha.split('#', 1)[0].strip() if not re.match(r'^\s*[DEC]:', linha) else linha.strip()
        # '#' em nota (C#4) não é comentário: só tira comentário de linhas que não são de notas
        if re.match(r'^\s*[DEC]:', linha) and ' #' in s:
            s = s.split(' #', 1)[0].strip()
        if not s:
            continue
        try:
            if s.startswith('@'):
                k, _, v = s[1:].partition(' ')
                k, v = k.strip(), v.strip()
                if atual is not None and k == 'padrao':
                    atual['padrao'] = v
                else:
                    cab[k] = v
            elif s.startswith('['):
                m = re.match(r'^\[([^\]]+)\]\s*(.*)$', s)
                if not m:
                    raise Erro('seção inválida')
                atual = {'id': m.group(1).strip(), 'rotulo': m.group(2).strip() or m.group(1).strip(),
                         'D': [], 'E': [], 'C': [], 'padrao': None, 'linha': n}
                if any(x['id'] == atual['id'] for x in secoes):
                    raise Erro(f'seção [{atual["id"]}] repetida')
                secoes.append(atual)
            elif s[:2] in ('D:', 'E:', 'C:'):
                if atual is None:
                    atual = {'id': 'A', 'rotulo': 'Música', 'D': [], 'E': [], 'C': [], 'padrao': None, 'linha': n}
                    secoes.append(atual)
                atual[s[0]].append(s[2:].strip())
            else:
                raise Erro(f'linha não reconhecida: {s[:40]}')
        except Erro as e:
            raise Erro(f'{os.path.basename(caminho)}:{n}: {e}')
    return cab, secoes


def compassos(linhas):
    juntas = ' | '.join(l.strip().strip('|').strip() for l in linhas if l.strip().strip('|').strip())
    return [c.split() for c in juntas.split('|')] if juntas else []


def notas_cifra(raiz, tons, baixo, ant):
    """voicing: baixo entre E2(40) e D#3(51); acorde fechado entre ~E3 e ~E4, perto do anterior."""
    b = 40 + (baixo - 40) % 12
    pcs = sorted(set(t % 12 for t in tons))
    if len(pcs) > 4:  # 9ª: tira a 5ª
        q = (raiz + 7) % 12
        if q in pcs:
            pcs.remove(q)
    melhor = None
    for base in range(50, 62):
        v = sorted(base + (pc - base) % 12 for pc in pcs)
        custo = abs(sum(v) / len(v) - (sum(ant) / len(ant) if ant else 57))
        if v[-1] > 67:
            custo += 20
        if melhor is None or custo < melhor[0]:
            melhor = (custo, v)
    return b, melhor[1]


def gerar_esq(cifras_compasso, tam, padrao, ant):
    """cifras_compasso: [(raiz,tons,baixo,dur)] somando tam. -> eventos [(midis, dur)]"""
    out = []
    for raiz, tons, baixo, d in cifras_compasso:
        if raiz is None:
            out.append(([], d)); continue
        b, ac = notas_cifra(raiz, tons, baixo, ant[0])
        ant[0] = ac
        quinta = b + 7
        def picar(seq, passo):
            ev, t, i = [], F(0), 0
            while t < d:
                p = min(passo, d - t)
                ev.append((seq[i % len(seq)], p)); t += p; i += 1
            return ev
        if padrao == 'bloco':
            out.append(([b] + [x for x in ac if x > b], d))
        elif padrao == 'baixo':
            out += picar([[b], [quinta]], F(2)) if d >= 4 else [([b], d)]
        elif padrao == 'oitavas':
            out += picar([[b, b + 12]], F(2) if d >= 2 else d)
        elif padrao == 'baixo-acorde':
            out += picar([[b]] + [ac] * 16, F(1)) if d >= 2 else [([b] + ac, d)]
        elif padrao == 'marcha':
            out += picar([[b], ac, [quinta - 12 if quinta - 12 >= 36 else quinta], ac], F(1)) if d >= 2 else [([b] + ac, d)]
        elif padrao == 'valsa':
            if d >= 3:
                out.append(([b], F(1))); out.append((ac, F(1))); out.append((ac, d - 2))
            else:
                out.append(([b] + ac, d))
        elif padrao == 'alberti':
            lo, mid, hi = ac[0], ac[1], ac[-1]
            out += picar([[lo], [hi], [mid], [hi]], F(1, 2))
        elif padrao == 'arpejo':
            out += picar([[b], [quinta], [b + 12], [ac[1] + 12 if ac[1] + 12 < 72 else ac[1]], [b + 12], [quinta]], F(1, 2))
        elif padrao == 'balada':
            if d >= 4:
                out.append(([b, quinta], F(2))); out.append((ac, d - 2))
            else:
                out.append(([b, quinta] if d >= 2 else [b], d))
        elif padrao == 'bossa':
            seq = [([b], F(3, 2)), (ac, F(1, 2)), ([quinta - 12 if quinta - 12 >= 36 else quinta], F(1)), (ac, F(1))]
            t = F(0)
            for m, p in seq:
                if t >= d: break
                p = min(p, d - t); out.append((m, p)); t += p
            if t < d: out.append((ac, d - t))
        else:
            raise Erro(f'padrão desconhecido "{padrao}"')
    return out


def montar(caminho, avisos=None):
    """-> dict com cabeçalho e eventos [(inicio,dur,mao,midis)], secoes [(inicio,rotulo)]"""
    if avisos is None:
        avisos = []
    global TRANSP
    cab, secoes = ler(caminho)
    nome = os.path.basename(caminho)
    try:
        TRANSP = int(cab.get('transpor', '0'))
    except ValueError:
        raise Erro(f'{nome}: @transpor deve ser inteiro (semitons)')
    if abs(TRANSP) > 24:
        raise Erro(f'{nome}: @transpor fora de -24..24')
    for k in ('titulo', 'nivel', 'bpm'):
        if k not in cab:
            raise Erro(f'{nome}: falta @{k}')
    if cab['nivel'] not in NIVEIS:
        raise Erro(f'{nome}: @nivel deve ser Fácil, Médio ou Difícil')
    bpm = int(cab['bpm'])
    if not 30 <= bpm <= 240:
        raise Erro(f'{nome}: @bpm fora de 30..240')
    num, den = (int(x) for x in cab['compasso'].split('/'))
    tam = F(num * 4, den)
    anac = dur_de(cab['anacruse']) if cab['anacruse'] != '0' else F(0)
    if cab['padrao'] not in PADROES:
        raise Erro(f'{nome}: @padrao "{cab["padrao"]}" desconhecido ({", ".join(PADROES)})')
    if not secoes:
        raise Erro(f'{nome}: sem notas')
    porid = {s['id']: s for s in secoes}
    forma = []
    for f in (cab.get('forma') or ' '.join(s['id'] for s in secoes)).split():
        k, _, r = f.partition('*')
        if k not in porid:
            raise Erro(f'{nome}: @forma cita seção [{k}] inexistente')
        forma += [k] * (int(r) if r else 1)

    # monta cada seção uma vez (lista de compassos por mão, em eventos)
    cache = {}
    ant = [None]
    primeira = forma[0]
    for s in secoes:
        tag = f'{nome} [{s["id"]}]'
        cd, ce, cc = compassos(s['D']), compassos(s['E']), compassos(s['C'])
        if not cd:
            raise Erro(f'{tag}: seção sem D:')
        if ce and len(ce) != len(cd):
            raise Erro(f'{tag}: D: tem {len(cd)} compassos e E: tem {len(ce)}')
        if cc and len(cc) != len(cd):
            raise Erro(f'{tag}: D: tem {len(cd)} compassos e C: tem {len(cc)}')
        maos = {}
        for mao, comp in (('D', cd), ('E', ce)):
            if not comp:
                continue
            res = []
            for i, c in enumerate(comp):
                evs = []
                try:
                    for tok in c:
                        evs.append(evento(tok))
                except Erro as e:
                    raise Erro(f'{tag} {mao}: compasso {i+1}: {e}')
                soma = sum(e[1] for e in evs)
                esperado = tam
                if i == 0 and s['id'] == primeira and anac:
                    esperado = anac
                ultimo = (i == len(comp) - 1)
                if soma != esperado and not (ultimo and soma < tam):
                    raise Erro(f'{tag} {mao}: compasso {i+1} soma {float(soma):g} tempos (esperado {float(esperado):g}): {" ".join(c)}')
                res.append(evs)
            maos[mao] = res
        if cc and 'E' not in maos:
            pad = s['padrao'] or cab['padrao']
            if pad not in PADROES:
                raise Erro(f'{tag}: padrão "{pad}" desconhecido')
            res = []
            for i, c in enumerate(cc):
                dur_d = sum(e[1] for e in maos['D'][i])
                itens, prev = [], None
                for tok in c:
                    t, _, d = tok.partition(':')
                    if t == '%':
                        if prev is None: raise Erro(f'{tag} C: "%" sem cifra anterior')
                        itens.append([prev, dur_de(d) if d else None])
                    elif t == '-':
                        itens.append([(None, None, None), dur_de(d) if d else None])
                    else:
                        try:
                            prev = cifra(t)
                        except Erro as e:
                            raise Erro(f'{tag} C: compasso {i+1}: {e}')
                        itens.append([prev, dur_de(d) if d else None])
                livres = [x for x in itens if x[1] is None]
                fixo = sum(x[1] for x in itens if x[1] is not None)
                if livres:
                    p = (dur_d - fixo) / len(livres)
                    if p <= 0: raise Erro(f'{tag} C: compasso {i+1}: durações não cabem')
                    for x in livres: x[1] = p
                if sum(x[1] for x in itens) != dur_d:
                    raise Erro(f'{tag} C: compasso {i+1}: cifras somam {float(sum(x[1] for x in itens)):g}, compasso tem {float(dur_d):g}')
                res.append([(m, d, False) for m, d in gerar_esq([(r, t, b, d) for (r, t, b), d in itens], tam, pad, ant)])
            maos['E'] = res
        cache[s['id']] = (maos, len(cd))

    eventos, marcas = [], []
    t = F(0)
    for sid in forma:
        maos, n = cache[sid]
        marcas.append((t, porid[sid]['rotulo']))
        fim = t
        for mao, comp in maos.items():
            tt = t
            pend = None  # ligadura
            for c in comp:
                for ms, d, lig in c:
                    if pend is not None and ms == pend[3]:
                        pend[1] += d
                        if not lig:
                            eventos.append(tuple(pend)); pend = None
                    else:
                        if pend is not None:
                            eventos.append(tuple(pend)); pend = None
                        if ms:
                            if lig:
                                pend = [tt, d, mao, ms]
                            else:
                                eventos.append((tt, d, mao, ms))
                    tt += d
            if pend is not None:
                eventos.append(tuple(pend))
            fim = max(fim, tt)
        t = fim
    # @transpor pode jogar a mão esquerda para baixo do teclado útil: dobra essas notas uma oitava acima
    if TRANSP:
        eventos = [(t0, d, mao, sorted(set(x + 12 if mao == 'E' and x < 33 else x for x in ms)))
                   for t0, d, mao, ms in eventos]
    eventos.sort(key=lambda e: (e[0], e[2] != 'D'))

    # checagens musicais (avisos)
    todos = [m for e in eventos for m in e[3]]
    if not todos:
        raise Erro(f'{nome}: sem notas')
    lo, hi = min(todos), max(todos)
    if lo < 28 or hi > 100:
        raise Erro(f'{nome}: extensão {lo}..{hi} fora do teclado útil (E1..E7)')
    for e in eventos:
        if len(e[3]) > 5:
            raise Erro(f'{nome}: acorde com mais de 5 notas em t={float(e[0]):g}')
        if e[3][-1] - e[3][0] > 12 and e[2] == 'D':
            avisos.append(f'acorde da mão direita abre mais de uma oitava em t={float(e[0]):g}')
    dmel = [e for e in eventos if e[2] == 'D']
    for a, b in zip(dmel, dmel[1:]):
        if abs(b[3][-1] - a[3][-1]) > 12:
            avisos.append(f'salto de {abs(b[3][-1]-a[3][-1])} semitons na melodia em t={float(b[0]):g}')
    segundos = float(t) * 60 / bpm
    if segundos < 45:
        avisos.append(f'música curta ({segundos:.0f} s)')
    cab['_tam'] = tam
    return {'cab': cab, 'eventos': eventos, 'secoes': marcas, 'total': t, 'segundos': segundos,
            'bpm': bpm, 'tam': tam, 'anac': anac}


def fmt(x):
    x = float(x)
    s = f'{x:.4f}'.rstrip('0').rstrip('.')
    return s


def compilar_um(m, categoria):
    c = m['cab']
    L = [f'titulo={c["titulo"]}', f'nivel={c["nivel"]}', f'bpm={m["bpm"]}', f'compasso={fmt(m["tam"])}',
         f'anacruse={fmt(m["anac"])}', f'categoria={categoria}', f'tom={c.get("tom", "")}']
    for t, r in m['secoes']:
        L.append(f'secao={fmt(t)} {r}')
    L.append('---')
    for t, d, mao, ms in m['eventos']:
        L.append(f'{fmt(t)} {fmt(d)} {mao} {"+".join(map(str, ms))}')
    return '\n'.join(L) + '\n'


def exportar_midi(m, saida):
    tpq = 480
    evs = []
    for t, d, mao, ms in m['eventos']:
        for x in ms:
            evs.append((int(t * tpq), 1, x, 90 if mao == 'D' else 64))
            evs.append((int((t + d) * tpq) - 10, 0, x, 0))
    evs.sort()
    def vlq(v):
        b = [v & 0x7F]; v >>= 7
        while v: b.insert(0, (v & 0x7F) | 0x80); v >>= 7
        return bytes(b)
    tr = bytearray(b'\x00\xff\x51\x03' + (60000000 // m['bpm']).to_bytes(3, 'big'))
    last = 0
    for tk, on, x, v in evs:
        tr += vlq(tk - last) + bytes([0x90 if on else 0x80, x, v]); last = tk
    tr += b'\x00\xff\x2f\x00'
    with open(saida, 'wb') as f:
        f.write(b'MThd' + (6).to_bytes(4, 'big') + (0).to_bytes(2, 'big') + (1).to_bytes(2, 'big') + tpq.to_bytes(2, 'big'))
        f.write(b'MTrk' + len(tr).to_bytes(4, 'big') + bytes(tr))


def resumo(m):
    ev = m['eventos']
    nd = sum(len(e[3]) for e in ev if e[2] == 'D')
    ne = sum(len(e[3]) for e in ev if e[2] == 'E')
    todos = [x for e in ev for x in e[3]]
    return (f'{m["cab"]["titulo"]} | {m["cab"]["nivel"]} | {m["bpm"]} bpm | {m["segundos"]:.0f}s | '
            f'{len(m["secoes"])} seções | D {nd} notas, E {ne} notas | {min(todos)}..{max(todos)}')


NOMES_PC = ['Dó', 'Dó#', 'Ré', 'Mib', 'Mi', 'Fá', 'Fá#', 'Sol', 'Láb', 'Lá', 'Sib', 'Si']
_MAIOR = [6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88]
_MENOR = [6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17]


def tom_estimado(m):
    """Krumhansl-Schmuckler sobre as notas já transpostas (peso = duração); + extensão da mão direita."""
    h = [0.0] * 12
    for t, d, mao, ms in m['eventos']:
        for x in ms:
            h[x % 12] += float(d)
    def corr(a, b):
        ma, mb = sum(a) / 12, sum(b) / 12
        num = sum((x - ma) * (y - mb) for x, y in zip(a, b))
        den = math.sqrt(sum((x - ma) ** 2 for x in a) * sum((y - mb) ** 2 for y in b)) or 1
        return num / den
    cand = []
    for r in range(12):
        rot = h[r:] + h[:r]
        cand.append((corr(rot, _MAIOR), NOMES_PC[r] + ' maior'))
        cand.append((corr(rot, _MENOR), NOMES_PC[r] + ' menor'))
    cand.sort(reverse=True)
    d = sorted(x for e in m['eventos'] if e[2] == 'D' for x in e[3])
    e = [x for ev in m['eventos'] if ev[2] == 'E' for x in ev[3]]
    ext = f'D {d[0]}..{d[-1]} mediana {d[len(d)//2]}' + (f' | E {min(e)}..{max(e)}' if e else '')
    return f'{cand[0][1]} ({cand[0][0]:.2f}; 2º {cand[1][1]} {cand[1][0]:.2f}) | {ext}'


def main():
    if len(sys.argv) < 3:
        print(__doc__); sys.exit(2)
    cmd = sys.argv[1]
    if cmd == 'validar':
        ok = True
        for a in sys.argv[2:]:
            av = []
            try:
                m = montar(a, av)
                print('ok  ' + resumo(m))
                for x in sorted(set(av))[:8]:
                    print('    aviso: ' + x)
            except Erro as e:
                ok = False; print('ERRO ' + str(e))
            except Exception as e:
                ok = False; print(f'ERRO {a}: {type(e).__name__}: {e}')
        sys.exit(0 if ok else 1)
    elif cmd == 'compilar':
        fonte, destino = sys.argv[2], sys.argv[3]
        n = 0; falhas = 0
        indice = []
        for cat in sorted(os.listdir(fonte)):
            dc = os.path.join(fonte, cat)
            if not os.path.isdir(dc): continue
            os.makedirs(os.path.join(destino, cat), exist_ok=True)
            for arq in sorted(os.listdir(dc)):
                if not arq.endswith('.m7s'): continue
                try:
                    m = montar(os.path.join(dc, arq))
                except Erro as e:
                    print('ERRO ' + str(e)); falhas += 1; continue
                with open(os.path.join(destino, cat, arq[:-4] + '.txt'), 'w', encoding='utf-8') as f:
                    f.write(compilar_um(m, cat))
                ev = m['eventos']
                nd = sum(len(e[3]) for e in ev if e[2] == 'D'); ne = sum(len(e[3]) for e in ev if e[2] == 'E')
                indice.append('\t'.join([f'{cat}/{arq[:-4]}', m['cab']['titulo'], m['cab']['nivel'], str(m['bpm']),
                                          str(nd), str(ne), fmt(m['total']), m['cab'].get('tom', '')]))
                n += 1
        with open(os.path.join(destino, 'indice.tsv'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(indice) + '\n')
        print(f'{n} músicas compiladas, {falhas} com erro')
        sys.exit(1 if falhas else 0)
    elif cmd == 'tom':
        for a in sys.argv[2:]:
            try:
                print(tom_estimado(montar(a)), ' ', os.path.basename(a))
            except Erro as e:
                print('ERRO ' + str(e))
    elif cmd == 'midi':
        exportar_midi(montar(sys.argv[2]), sys.argv[3])
    else:
        print(__doc__); sys.exit(2)


if __name__ == '__main__':
    main()
