# Świąteczny Bank (Fabric, Minecraft 26.1.2)

Mod ekonomii na serwer: konto, /wypłać, /wpłać, przelewy z tematem, historia, zarobki, kary AFK.
Mod musi być zainstalowany na serwerze ORAZ u wszystkich graczy (+ Fabric API).

## Jak zdobyć plik .jar (bez instalowania czegokolwiek)
1. Załóż darmowe konto na github.com i utwórz nowe repozytorium (np. `xmasbank`).
2. Wgraj do niego CAŁĄ zawartość tego folderu (przycisk "Add file" -> "Upload files"; folder `.github` też musi trafić do repo).
3. Wejdź w zakładkę **Actions**, poczekaj aż zielony "build" się skończy (kilka minut).
4. Otwórz ten przebieg i na dole w **Artifacts** pobierz `xmasbank-mod` -> w środku jest `xmasbank-1.0.0.jar`.
5. Wrzuć .jar do folderu `mods` serwera i graczy.
Jeśli build zrobi się czerwony, skopiuj log błędu i wyślij go do Claude'a.

## Sterowanie
- Domyślny klawisz banku: **B** (zmiana: Ustawienia -> Sterowanie -> Świąteczny Bank).
- TAB: saldo w prawym górnym rogu.
- /wypłać <kwota>, /wpłać <kwota|wszystko> (działają też /wyplac, /wplac). Pieniądze w ekwipunku to przedmiot "Moneta".

## Zarobki / kary (do zmiany w Bank.java)
+5 za godzinę gry, +10 za osiągnięcie, +15 za 250 bloków, +15 za 50 wrogich mobów.
-30 kopanie w miejscu 20 min, -50 zabijanie w miejscu 30 min, -5 brak ruchu przez godzinę.
Dane zapisują się w pliku `xmasbank.json` w folderze świata.
