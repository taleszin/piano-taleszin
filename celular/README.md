# Piano Taleszin: celular

Versão para celular Android 8+ (feita para o Galaxy A07) do [piano do tablet](../tablet). A história completa está no [README principal](../README.md).

- **Build sem Gradle:** `./build.sh` → `build/piano-celular.apk` (applicationId `app.taleszin.piano.celular`, instala ao lado do app do tablet).
- **Acervo:** fontes em `musicas/<n-categoria>/*.m7s`. O `tools/m7s.py` valida, compila para `assets/musicas` e gera `indice.tsv`, e também exporta MIDI. `tools/midi2m7s.py` converte um MIDI num rascunho de `.m7s`.
- **Código:**
  - `Sintetizador.java`: ressonadores modais com inarmonicidade, 2 cordas, martelo, abafador e FM, com Freeverb reduzido.
  - `Cascata.java`: o jogo (grupos/acordes, mãos, Aprender/Desafio, notas longas, janela do teclado).
  - `Teclado.java`: teclado com janela fracionária e força pelo toque.
  - `Piano.java`: telas, XP, sequência de dias e coroas.
  - `Musicas.java`: leitura do acervo.

Formato `.m7s`:
- **Cabeçalhos** `@titulo @nivel @bpm @compasso @anacruse @padrao @forma @fonte`.
- **Seções** `[ID] rótulo`.
- **Linhas** `D:` (mão direita), `E:` (mão esquerda) e `C:` (cifras, que viram mão esquerda pelo padrão).
- **Notação**: `|` separa compassos (o compilador confere cada um), `+` junta notas de um acorde, `R` é pausa, `~` é ligadura, e a duração vem depois de `:`, em semínimas (`1/3` para quiálteras).
