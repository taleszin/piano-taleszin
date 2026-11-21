# Brief: cada música no tom da gravação real

O dono do app (Tales) quer tocar cada música **no mesmo tom da versão real**, e numa altura que soe como a música e não "fina". Hoje muitas estão transpostas para Dó/Lá menor e com a melodia uma oitava acima.

Repositório: `~/dev/piano-taleszin/celular`. Fontes em `musicas/<n-cat>/*.m7s`. Você só mexe nas categorias que lhe foram dadas.

## O que fazer em cada arquivo

1. **Tom atual**:
   - Rode `python3 tools/m7s.py tom arq.m7s`. Ele mostra o tom estimado (já aplicado o `@transpor`, se houver) e a extensão das mãos em MIDI (60 = C4, dó central).
   - Confira com o `@fonte`, que às vezes diz "transposta de X para Y".
2. **Tom original**:
   - Use o tom da **gravação de estúdio mais conhecida**. Em trilha, use a trilha original. Em clássica, use o tom da obra (ex.: Für Elise em Lá menor, Canon em Ré maior).
   - Na dúvida, pesquise com WebSearch/WebFetch em hooktheory.com, tunebat.com, songbpm.com, musicnotes ou wikipedia.
   - Se a música modula, vale o tom do começo.
3. **Escrever no cabeçalho**, sem mexer em nenhuma nota:
   ```
   @tom Mi menor
   @transpor 2
   ```
   - `@transpor` é em semitons e soma em todas as notas e cifras. Escolha N de modo que o tom compilado = tom original.
   - **Oitava**: entre N, N−12 e N+12, escolha o que deixa a **mediana da mão direita entre 62 e 72**. Mais perto de 67 é melhor; é onde a melodia soa como a música e não como apito.
   - Só passe de 72 se a música real é aguda de verdade: Für Elise, caixinha de música, celesta do Harry Potter. Nesses casos, use a altura da gravação.
   - A mão esquerda não pode descer de 33 (A1) e nada pode passar de 96.
   - Se já estiver no tom certo e na altura boa, escreva só `@tom` e `@transpor 0`.
4. **Validar**:
   - Rode `python3 tools/m7s.py validar arq.m7s`; precisa dar `ok`.
   - Rode `python3 tools/m7s.py tom arq.m7s` de novo e confira que agora sai o tom original, maior ou menor relativo conforme a música.

## Regras

- Não altere notas, ritmos, cifras ou seções: só as linhas `@tom` e `@transpor`.
- Não faça commit e não rode `./build.sh`.
- Não mexa em arquivos fora das suas categorias.
- O Mac tem 16 GB: nada de processo pesado. Só Python e web.

## Entrega

Uma tabela com: arquivo | tom original | fonte do tom | tom antes | @transpor | mediana D antes→depois | confiança.

Liste à parte:
- as músicas em que o tom original é incerto;
- as músicas em que a regra da oitava brigou com a altura real.
