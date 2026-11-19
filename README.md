# Piano (Taleszin OS)

Piano leve para o tablet M7s (Android 4.4, RK3026). Java puro, ~42 KB, ~5 MB de RAM, ~4 % de CPU tocando.

- Som sintetizado (sem amostras: o SoundPool do 4.4 recusa PCM > 1 MB, o que quebrava o org.esteban.piano).
- Multitoque e deslizar o dedo troca de nota; botões de oitava, 1/2 oitavas, nomes Dó Ré Mi / C D E / sem, pedal.
- Build sem Gradle: `./build.sh` → `build/piano.apk`. Ícone: `python3 tools/icone.py`.
