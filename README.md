# Craft Casino – Fabric-Mod für Minecraft 26.1.x

`/ccasino` öffnet einen komplett selbst gezeichneten Casino-Tisch mit vier Spielen:

| Spiel | Was passiert |
|---|---|
| **Slots** | 3 Walzen mit Minecraft-Items, Hebel zum Ziehen, Walzen stoppen versetzt mit Nachfedern, Lauflichter, Gewinnlinie |
| **Roulette** | Europäischer Kessel dreht sich, Kugel läuft gegenläufig, springt in die Fächer; beliebig viele Chips aufs Feld setzen |
| **Blackjack** | 6-Deck-Schuh, Karten fliegen vom Schlitten, drehen sich um; Karte / Halten / Doppeln |
| **Crash** | Rakete steigt, Kurve wächst live, rechtzeitig auszahlen bevor sie explodiert |

Dazu: Holztisch mit Filz und Lauflichtern, animierter Kontostand, Münz- und Konfetti-Partikel, Gewinnbanner, Sounds, Bildschirmwackeln bei Explosion.

**Befehle:** `/ccasino` · `/ccasino stats` · `/ccasino reset`
**Tasten:** Leertaste = Drehen/Austeilen/Starten/Auszahlen · H/S/D im Blackjack · ESC = zurück zur Lobby

Die Chips sind rein virtuell (Start 1.000, bei < 10 kann man +500 nachfüllen) und werden in
`config/ccasino.properties` gespeichert. Die Mod ist **nur clientseitig** – sie funktioniert in
Singleplayer und auf jedem Server, braucht aber dort nichts installiert.

## Bauen

Benötigt **JDK 25**.

```
./gradlew build          (Windows: gradlew.bat build)
```

Die fertige Jar liegt dann in `build/libs/ccasino-1.0.0.jar` → zusammen mit **Fabric API** in den
`mods`-Ordner einer Fabric-26.1.x-Installation.

**Ohne lokales Java:** Projekt in ein GitHub-Repo pushen – der Workflow unter `.github/workflows/build.yml`
baut automatisch; die Jar gibt es dann im Actions-Tab unter „Artifacts“.

## Versionen

`gradle.properties`: Minecraft 26.1.2, Fabric Loader 0.19.2, Fabric API 0.155.3+26.1.2, Loom 1.16-SNAPSHOT.
Die Mod lädt auf 26.1 – 26.1.2.

## Aufbau

```
net.ccasino.CasinoMod        Befehl + Screen öffnen
net.ccasino.Wallet           Chips speichern/laden
net.ccasino.ui.CasinoScreen  Tisch, Lobby, Einsatzleiste, Banner, Übergänge
net.ccasino.ui.Ui            Zeichen-Helfer (Kreise, Chips, Buttons, Easing …)
net.ccasino.ui.Particles     Münzen, Konfetti, Funken
net.ccasino.games.*          Slots, Roulette, Blackjack, Crash
```

Neues Spiel hinzufügen: Klasse von `Game` ableiten und in `CasinoScreen` ins `games`-Array eintragen.
