package de.mwtroll;

import de.butzlabben.missilewars.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Löst Ziel-Angaben auf: Spielername(n) oder Selektoren wie @a, @ingame, @game, @enemy, @team, game:Arena.
 */
final class Targets {

    static final List<String> SELECTORS = Arrays.asList("@a", "@ingame", "@game", "@team", "@enemy");

    static final class Result {
        final List<Player> players;
        final String label;
        final String error;

        Result(List<Player> players, String label, String error) {
            this.players = players;
            this.label = label;
            this.error = error;
        }
    }

    private Targets() {
    }

    static boolean isGroup(String arg) {
        String a = arg.toLowerCase(Locale.ROOT);
        return a.startsWith("@") || a.startsWith("game:") || a.equals("all") || a.equals("alle") || a.contains(",");
    }

    static Result resolve(CommandSender sender, String arg, boolean includeSelf) {
        String a = arg.toLowerCase(Locale.ROOT);
        Player self = sender instanceof Player ? (Player) sender : null;
        Set<Player> found = new LinkedHashSet<>();
        String label;
        boolean group = true;

        switch (a) {
            case "@a":
            case "all":
            case "alle":
                found.addAll(Bukkit.getOnlinePlayers());
                label = "alle Spieler";
                break;
            case "@ingame":
                found.addAll(MWHook.allIngame());
                label = "alle Spieler in MissileWars-Spielen";
                break;
            case "@game":
            case "@spiel": {
                if (self == null) return new Result(null, null, "@game geht nur als Spieler. Nutze game:<Arena>.");
                Game g = MWHook.gameOf(self);
                if (g == null) return new Result(null, null, "Du bist in keinem MissileWars-Spiel.");
                found.addAll(MWHook.playersOf(g));
                label = "alle Spieler in deinem Spiel";
                break;
            }
            case "@team": {
                if (self == null) return new Result(null, null, "@team geht nur als Spieler.");
                found.addAll(MWHook.teamOfPlayers(self));
                if (found.isEmpty()) return new Result(null, null, "Du bist in keinem Team.");
                label = "dein Team";
                break;
            }
            case "@enemy":
            case "@gegner":
            case "gegner": {
                if (self == null) return new Result(null, null, "@enemy geht nur als Spieler.");
                found.addAll(MWHook.enemiesOf(self));
                if (found.isEmpty()) return new Result(null, null, "Kein Gegnerteam gefunden (bist du in einem Spiel?).");
                label = "das Gegnerteam";
                break;
            }
            default:
                if (a.startsWith("game:")) {
                    String name = arg.substring(5);
                    Game g = MWHook.gameByName(name);
                    if (g == null) return new Result(null, null, "Spiel/Arena '" + name + "' nicht gefunden.");
                    found.addAll(MWHook.playersOf(g));
                    label = "alle Spieler in " + name;
                } else {
                    List<String> missing = new ArrayList<>();
                    for (String n : arg.split(",")) {
                        if (n.isEmpty()) continue;
                        Player p = Bukkit.getPlayerExact(n);
                        if (p == null) {
                            for (Player o : Bukkit.getOnlinePlayers()) {
                                if (o.getName().equalsIgnoreCase(n)) p = o;
                            }
                        }
                        if (p == null) missing.add(n);
                        else found.add(p);
                    }
                    if (!missing.isEmpty()) {
                        return new Result(null, null, "Spieler nicht online: " + String.join(", ", missing));
                    }
                    group = arg.contains(",");
                    label = found.size() == 1 ? found.iterator().next().getName() : found.size() + " Spieler";
                }
        }

        List<Player> out = new ArrayList<>();
        List<String> protectedNames = new ArrayList<>();
        for (Player p : found) {
            if (group && !includeSelf && p == self) continue;
            if (isProtected(p, self)) {
                protectedNames.add(p.getName());
                continue;
            }
            out.add(p);
        }
        if (!protectedNames.isEmpty() && !group) {
            return new Result(null, null, String.join(", ", protectedNames)
                    + " ist geschützt (Recht mwtroll.bypass). Schutz abschalten: bypass-enabled: false in der config.yml");
        }
        if (!protectedNames.isEmpty() && sender != null) {
            sender.sendMessage(Msg.c(Msg.prefix + "&8Übersprungen (geschützt): " + String.join(", ", protectedNames)));
        }
        if (out.isEmpty()) {
            if (found.size() == 1 && found.contains(self) && group) {
                return new Result(null, null, "Außer dir ist hier niemand. (include-self-in-groups in der config.yml)");
            }
            return new Result(null, null, "Keine passenden Spieler gefunden.");
        }
        return new Result(out, label, null);
    }

    /** Geschützt nur, wenn bypass-enabled in der config.yml an ist. */
    static boolean isProtected(Player p, Player self) {
        MWTroll plugin = MWTroll.instance;
        if (plugin == null || !plugin.bypassEnabled() || p == self) return false;
        return p.hasPermission("mwtroll.bypass");
    }

    static List<String> suggestions() {
        List<String> list = new ArrayList<>(SELECTORS);
        for (String g : MWHook.gameNames()) list.add("game:" + g);
        for (Player p : Bukkit.getOnlinePlayers()) list.add(p.getName());
        return list;
    }
}
