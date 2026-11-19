# Piano (Taleszin OS)

Piano leve para o tablet M7s (Android 4.4, RK3026). Java puro, ~42 KB, ~5 MB de RAM, ~4 % de CPU tocando.

- Som de piano elétrico (FM 1:1 + sininho 14:1, estilo E.Piano) ou "Clássico"; sintetizado, sem amostras (o SoundPool do 4.4 recusa PCM > 1 MB).
- Multitoque e deslizar o dedo troca de nota; botões de oitava, 1/2 oitavas, nomes Dó Ré Mi / C D E / sem, pedal.
- Build sem Gradle: `./build.sh` → `build/piano.apk`. Ícone: `python3 tools/icone.py`.
- Músicas em estilo Guitar Hero/Synthesia (`Cascata.java`): as notas caem sobre a tecla certa e esperam na linha (modo fácil); "Perfeito!"/"Boa!", combo com multiplicador, tecla vermelha e silêncio no erro, contagem 3-2-1, estrelas e recorde por música, velocidade de 30 a 150 %, "Ouvir" toca sozinho. O teclado mostra só as teclas que a música usa. Repertório de domínio público em `Musicas.java`.
