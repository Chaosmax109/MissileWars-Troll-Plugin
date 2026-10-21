# MissileWarsTroll

Ein Troll-Add-on für das Minecraft-Plugin **MissileWars**. Admins können Spieler über ein Menü oder per Befehl trollen. Das geht für einzelne Spieler, ganze Teams oder alle Spieler.

> Inoffizielles Add-on, nicht von den MissileWars-Entwicklern.

## Funktionen

- **Menü** mit `/mw troll`: Ziel wählen, Troll wählen, Dauer einstellen
- **29 Trolls** ab Werk, zum Beispiel Einfrieren, Raketenstart, Fake-Explosion, Drehwurm, Mini/Riese, Hühnerregen und Fake-OP
- **trolls.yml**: Alle Trolls lassen sich bearbeiten, abschalten oder selbst anlegen. Dafür gibt es Aktionen wie `effect`, `sound`, `particle`, `potion`, `title`, `velocity`, `console`, `rocket` und `delay`.
- **Raketen geben**: Gibt bestimmte MissileWars-Raketen, Schilde, Pfeile oder Feuerbälle als Item.
- **Nächste Items bestimmen**: Legt fest, welche Items ein Spieler von MissileWars als nächstes bekommt.
- **Dauerhafte Einstellungen**: Speed, Sprungkraft, Größe, Schwerkraft, Leuchten, Unsichtbar und Einfrieren
- **Eigene Permission für jeden Troll**
- Wird automatisch zurückgesetzt, wenn ein Spieler den Server verlässt, die Arena verlässt oder das Spiel endet

## Voraussetzungen

- Paper 1.21.x / 26.x
- MissileWars 5.4.0 oder neuer

## Installation

1. `MissileWarsTroll-x.y.z.jar` in den Ordner `plugins/` legen. MissileWars muss dort ebenfalls liegen.
2. Den Server neu starten.
3. Optional `plugins/MissileWarsTroll/config.yml` und `trolls.yml` anpassen und danach `/mw troll reload` ausführen.

## Befehle

Alle Befehle funktionieren auch als `/mwtroll` oder `/mwt`.

| Befehl | Beschreibung |
|---|---|
| `/mw troll` | Menü öffnen |
| `/mw troll <troll> <Ziel> [Sekunden]` | Troll ausführen |
| `/mw troll rocket <Ziel> <Rakete> [Anzahl]` | Raketen oder Items geben |
| `/mw troll nextitems <Ziel> <Item1,Item2,...\|list\|clear>` | Nächste Items festlegen |
| `/mw troll set <Ziel> <speed\|jump\|size\|gravity\|glow\|invisible\|freeze> <Wert\|reset>` | Dauerhaft einstellen |
| `/mw troll stop <Ziel>` | Alles zurücksetzen |
| `/mw troll info <Spieler>` | Aktuelle Einstellungen anzeigen |
| `/mw troll list` | Alle Trolls anzeigen |
| `/mw troll reload` | config.yml und trolls.yml neu laden |
| `/mw troll debug` | Diagnose bei Problemen |

**Ziele:** ein Spielername, mehrere Namen wie `Name1,Name2`, `@a` (alle), `@ingame`, `@game`, `@team`, `@enemy` oder `game:<Arena>`

## Permissions

| Permission | Beschreibung |
|---|---|
| `mwtroll.*` | Alle Rechte |
| `mwtroll.use` | Menü und Befehl benutzen |
| `mwtroll.troll.*` | Alle Trolls ausführen |
| `mwtroll.troll.<id>` | Einen bestimmten Troll ausführen, z. B. `mwtroll.troll.freeze` |
| `mwtroll.troll.rocket` | Raketen geben |
| `mwtroll.troll.nextitems` | Nächste Items festlegen |
| `mwtroll.group` | Gruppen trollen (`@a`, `@team`, ...) |
| `mwtroll.set` | Dauerhafte Einstellungen setzen |
| `mwtroll.stop` | Trolls zurücksetzen |
| `mwtroll.reload` | Konfiguration neu laden |
| `mwtroll.bypass` | Schutz vor Trolls (nur aktiv mit `bypass-enabled: true`) |

## Eigene Trolls (trolls.yml)

```yaml
trolls:
  alarm:
    name: '&cRaketen-Alarm'
    icon: BELL
    description: ['&7Fake-Warnung: Rakete im Anflug!']
    duration: 0          # 0 = sofort, sonst Standard-Dauer in Sekunden
    actions:
      - 'title: &c&lACHTUNG! | &eRakete im Anflug!'
      - 'sound: block.note_block.pling 1 2'
      - 'delay: 40'
      - 'effect: fake_explosion'
```

Alle verfügbaren Aktionen und Effekte sind oben in der `trolls.yml` beschrieben.

## Selbst bauen

1. Die MissileWars-JAR nach `libs/MissileWars-5.4.0-Dev_26.1.jar` kopieren. Bei einem anderen Dateinamen muss der Pfad in der `pom.xml` angepasst werden.
2. In der `pom.xml` die `paper-api`-Version auf die Version deines Servers setzen.
3. `mvn package` ausführen. Die fertige JAR liegt danach in `target/`.
