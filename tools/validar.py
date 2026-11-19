#!/usr/bin/env python3
"""Valida arquivos de músicas do Piano Taleszin.
Linha: Título | Nível | bpm | notas   (linhas vazias e começando com # são ignoradas)
Nível: Fácil, Médio ou Difícil. bpm: inteiro 40..220 (batida = semínima).
Notas: tokens separados por espaço. Nota = letra A-G, '#' opcional (só sustenidos, nada de bemol),
oitava 2..6 (C4 = dó central), ':duração' opcional em semínimas (padrão 1; ex. :.5 colcheia, :2 mínima, :1.5 pontuada).
Pausa = R:duração (ex. R:.5). '|' separa frases (opcional, só visual).
Uso: python3 validar.py arquivo.txt"""
import sys, re
TOK = re.compile(r'^([A-G])(#?)([2-6])(?::(\d*\.?\d+))?$')
REST = re.compile(r'^R:(\d*\.?\d+)$')
def midi(l, s, o):
    return 12 * (int(o) + 1) + {'C':0,'D':2,'E':4,'F':5,'G':7,'A':9,'B':11}[l] + (1 if s else 0)
ok = True
for n, linha in enumerate(open(sys.argv[1], encoding='utf-8'), 1):
    linha = linha.strip()
    if not linha or linha.startswith('#'): continue
    partes = [p.strip() for p in linha.split('|', 3)]
    if len(partes) != 4: print(f'L{n}: precisa de 4 campos separados por " | "'); ok = False; continue
    tit, niv, bpm, notas = partes
    erros = []
    if niv not in ('Fácil','Médio','Difícil'): erros.append('nível inválido')
    if not bpm.isdigit() or not 40 <= int(bpm) <= 220: erros.append('bpm inválido')
    ms = []; 
    for t in notas.split():
        if t == '|' or REST.match(t): continue
        m = TOK.match(t)
        if not m: erros.append(f'token inválido "{t}"'); continue
        if m.group(1) in 'EB' and m.group(2): erros.append(f'"{t}": E#/B# não existem, use F/C')
        ms.append(midi(*m.group(1,2,3)))
    if len(ms) < 8: erros.append('menos de 8 notas')
    if ms:
        lo, hi = min(ms), max(ms)
        brancas = sum(1 for x in range(12*(lo//12), hi+1) if x % 12 in (0,2,4,5,7,9,11))
        if brancas > 22: erros.append(f'extensão grande demais ({brancas} teclas brancas; máx 22, ideal ≤ 15)')
    if erros: ok = False; print(f'L{n} {tit}: ' + '; '.join(erros))
    else: print(f'ok  {tit}: {len(ms)} notas, {niv}, {bpm} bpm')
sys.exit(0 if ok else 1)
