# Piano Taleszin

**Um piano que cabe num tablet de 2014 e duzentas músicas que cabem num APK menor que uma foto.**

São dois apps Android feitos à mão, sem Gradle, sem biblioteca e sem nenhum arquivo de áudio gravado. Todo o som é calculado na hora, nota por nota.

| | Tablet ([`tablet/`](tablet)) | Celular ([`celular/`](celular)) |
|---|---|---|
| Aparelho | Multilaser M7s, de 2014: Android 4.4, 2 núcleos a 1 GHz, 512 MB de RAM | Galaxy A07: Android 15 |
| APK | **79 KB** | **605 KB**, com 202 músicas dentro |
| Código | Java puro, ~1.300 linhas | Java puro, ~2.000 linhas, e um compilador de partituras em Python |
| Som | FM sintetizado, piano elétrico | Modelo físico de corda, 32 vozes, reverb |
| Em uso | ~5 MB de RAM, ~4% de CPU | ~7% de CPU na thread de áudio |

---

## Capítulo 1: o tablet que ia para o lixo

Tudo começou com um tablet Multilaser travado, considerado descartável. Recuperei o aparelho: gravei de novo o firmware, montei uma ROM própria e, como faltava algo para fazer nele, escrevi um piano.

As limitações decidiram cada escolha:

- **Sem amostras de áudio.** O `SoundPool` do Android 4.4 recusa sons com mais de 1 MB, e um piano gravado de verdade pesa centenas de MB. Então o som é **sintetizado**: um piano elétrico por FM, com um "sininho" de 14:1 no ataque, no estilo dos E.Pianos dos anos 80.
- **Sem Gradle.** O build é um script de shell que chama `aapt2`, `javac`, `d8` e `apksigner` direto. O APK sai com **79 KB**.
- **O teclado mostra só o que a música usa.** Numa tela de 800x480, cada tecla precisa ser grande o suficiente para o dedo.

Depois veio o modo **Músicas**, no estilo de Guitar Hero e Synthesia. As notas caem sobre a tecla certa e esperam você tocar. Tem combo, "Perfeito!", estrelas e recorde por música. São 161 músicas, todas como melodia de uma mão.

## Capítulo 2: o celular que aguentava mais

O tablet ficou ótimo para o que é, mas eu tinha um celular muito mais potente no bolso. A pergunta virou esta: **o que dá para fazer com o mesmo espírito leve quando o limite sobe?**

A resposta foi refazer as três partes que mais pesavam na experiência.

### 1. As músicas viraram partituras de verdade

No tablet, uma música era uma linha de texto com a melodia. No celular, criei um formato de partitura (`.m7s`) com:

- mão direita e mão esquerda;
- acordes;
- compassos conferidos;
- ligaduras e quiálteras;
- seções como intro, refrão e ponte, e a ordem em que elas se repetem;
- **cifras** que se transformam em acompanhamento: valsa, Alberti, bossa, balada e marcha.

```
@titulo Garota de Ipanema (Tom Jobim e Vinicius de Moraes)
@bpm 136
@padrao bossa
@forma I A A2 B A3 A A2 B A3

[A] Tema A
D: G4:1.5 E4:.5 E4:1 D4:.5 G4:.5~ | G4:.5 E4:1 E4:1 E4:.5 D4:.5 R:.5 | ...
C: Fmaj7 | Fmaj7 | G7 | G7 | Gm7 | Gb7 | Fmaj7 | Gb7
```

Um compilador em Python ([`celular/tools/m7s.py`](celular/tools/m7s.py)) valida cada compasso e gera eventos compactos para o app. Ele também exporta MIDI, para conferir de ouvido no computador. O build **recusa** música com compasso errado.

Assim saíram **202 músicas em 9 categorias**: infantis, clássicas, brasileiras, cantoras, pop, Beatles, rock, trilhas de filmes e jogos (tem Zelda) e Natal. Elas foram transcritas de MIDIs e partituras e conferidas com a ferramenta.

### 2. O som virou uma corda de piano

No lugar do FM entrou um **modelo físico simplificado**. Cada nota é um conjunto de ressonadores, um por harmônico da corda. Cada ressonador se resume a uma conta por amostra:

```
y[n] = c · y[n-1] − r² · y[n-2]
```

Em cima disso, o modelo tem:

- a **inarmonicidade** real das cordas de piano: os harmônicos agudos ficam levemente "esticados";
- **duas cordas** um pouco desafinadas entre si, que dão o batimento característico;
- ruído de martelo;
- **abafador** quando a tecla sobe, que falta nas notas mais agudas, como num piano de verdade;
- **força do toque**: quanto mais perto da base da tecla, mais forte e mais brilhante a nota.

Tudo isso roda em `float` estéreo, com o buffer de baixa latência do próprio aparelho e 32 vozes simultâneas, gastando cerca de **7% de CPU**. O piano elétrico do tablet continua lá como segundo timbre.

### 3. Tocar virou um jogo, sem perder o jeito do tablet

- **Escolha a mão:** direita, esquerda ou as duas. A mão que você não tocar, o app toca por você, em notas translúcidas.
- **Aprender ou Desafio:** no Aprender a música espera você. No Desafio ela segue o ritmo, e terminar com 3 estrelas vale uma 👑.
- **Trecho:** repete só uma parte, o refrão por exemplo, até você dominar (≥ 95%).
- **Notas longas** que precisam ser seguradas até o fim.
- **XP, nível e sequência de dias 🔥**, combos com faíscas e vibração no acerto.
- No fim, o app mostra **as notas que você mais erra**.
- **Teclado com zoom automático.** Ele mostra a extensão que a música pede e só desliza quando uma nota sai da tela.

## O que eu queria provar

O app não precisa ser pesado para ser bom. O APK do celular, com 202 músicas, um sintetizador físico e um jogo completo, ainda pesa **menos que uma foto tirada pelo próprio celular**. As partituras ocupam 2 MB em texto e encolhem para 470 KB compactadas. O código do app inteiro vira um `classes.dex` de **39 KB**.

## Como gerar

Você precisa do Android SDK com `build-tools` e `platforms`, de um JDK e de Python 3.

```bash
cd tablet  && ./build.sh            # build/piano.apk           (Android 4.4+)
cd celular && ./build.sh            # build/piano-celular.apk   (Android 8+)
python3 celular/tools/m7s.py validar celular/musicas/8-trilhas/*.m7s
python3 celular/tools/m7s.py midi celular/musicas/3-brasileiras/garota-de-ipanema.m7s ipanema.mid
```

Os APKs prontos ficam em [Releases](../../releases). Os dois apps instalam lado a lado.

---

*As músicas são transcrições só da melodia e da harmonia, sem letras, feitas para estudo pessoal. Os direitos das obras são dos seus autores. Código sob licença MIT.*
