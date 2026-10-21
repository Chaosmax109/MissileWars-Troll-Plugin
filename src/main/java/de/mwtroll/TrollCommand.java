package de.mwtroll;

import de.butzlabben.missilewars.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * /mw troll ...  (bzw. /mwtroll ...)
 */
final class TrollCommand implements TabExecutor {

    private final MWTroll plugin;

    TrollCommand(MWTroll plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        handle(sender, "/" + label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tab(sender, args);
    }

    /** Einstiegspunkt für /mwtroll, /mw troll (abgefangen) und /mw troll (über MissileWars/ACF). */
    void handle(CommandSender sender, String base, String[] args) {
        try {
            handle0(sender, base, args);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei /mw troll " + String.join(" ", args), t);
            Msg.send(sender, "&cInterner Fehler: &7" + t + " &8(Details in der Konsole)");
        }
    }

    private boolean noPerm(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return false;
        Msg.send(sender, "&cDazu hast du keine Rechte. &8(" + perm + ")");
        return true;
    }

    private void handle0(CommandSender sender, String base, String[] args) {
        if (noPerm(sender, "mwtroll.use")) return;
        String b = base + " troll";

        if (args.length == 0 || args[0].equalsIgnoreCase("menu") || args[0].equalsIgnoreCase("gui")) {
            if (!(sender instanceof Player)) {
                help(sender, b);
                return;
            }
            Player p = (Player) sender;
            if (args.length >= 2) {
                Targets.Result r = Targets.resolve(sender, args[1], plugin.includeSelf());
                if (r.error != null) {
                    Msg.send(sender, "&c" + r.error);
                    return;
                }
                plugin.menus().openTrolls(p, args[1], r.label, 0);
            } else {
                plugin.menus().openTargets(p, 0);
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
            case "hilfe":
            case "?":
                help(sender, b);
                return;
            case "list":
            case "liste":
                list(sender);
                return;
            case "debug":
                debug(sender);
                return;
            case "reload":
                if (noPerm(sender, "mwtroll.reload")) return;
                plugin.reloadAll();
                Msg.send(sender, "&aconfig.yml und trolls.yml neu geladen.");
                return;
            case "stop":
            case "reset":
            case "clear":
                if (noPerm(sender, "mwtroll.stop")) return;
                if (args.length < 2) {
                    Msg.send(sender, "&cNutzung: &e" + b + " stop <Ziel>");
                    return;
                }
                stop(sender, args[1]);
                return;
            case "set":
                if (noPerm(sender, "mwtroll.set")) return;
                if (args.length < 4) {
                    Msg.send(sender, "&cNutzung: &e" + b + " set <Ziel> <Einstellung> <Wert|reset>");
                    Msg.send(sender, "&7Einstellungen: &e" + String.join(", ", TrollManager.SETTINGS));
                    return;
                }
                set(sender, args[1], args[2].toLowerCase(Locale.ROOT), args[3]);
                return;
            case "info":
                if (args.length < 2) {
                    Msg.send(sender, "&cNutzung: &e" + b + " info <Spieler>");
                    return;
                }
                info(sender, args[1]);
                return;
            case TrollRegistry.SPECIAL_ROCKET:
            case "rakete":
            case "give": {
                if (!plugin.registry().canUseSpecial(sender, TrollRegistry.SPECIAL_ROCKET)) {
                    Msg.send(sender, "&cDazu hast du keine Rechte. &8(mwtroll.troll.rocket)");
                    return;
                }
                if (args.length < 3) {
                    Msg.send(sender, "&cNutzung: &e" + b + " rocket <Ziel> <Rakete|Schild|arrow|fireball> [Anzahl]");
                    Game g = Equipment.gameFor(null, sender instanceof Player ? (Player) sender : null);
                    if (g != null) Msg.send(sender, "&7Verfügbar: &e" + String.join(", ", Equipment.keys(g)));
                    return;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        Msg.send(sender, "&cUngültige Anzahl: &e" + args[3]);
                        return;
                    }
                }
                rocket(sender, args[1], args[2], amount);
                return;
            }
            case TrollRegistry.SPECIAL_NEXT:
            case "next": {
                if (!plugin.registry().canUseSpecial(sender, TrollRegistry.SPECIAL_NEXT)) {
                    Msg.send(sender, "&cDazu hast du keine Rechte. &8(mwtroll.troll.nextitems)");
                    return;
                }
                if (args.length < 3) {
                    Msg.send(sender, "&cNutzung: &e" + b + " nextitems <Ziel> <Item1,Item2,...|list|clear>");
                    return;
                }
                next(sender, args[1], String.join(",", Arrays.copyOfRange(args, 2, args.length)));
                return;
            }
            default:
                break;
        }

        TrollRegistry.TrollDef def = plugin.registry().get(sub);
        if (def == null) {
            Msg.send(sender, "&cUnbekannter Troll oder Befehl: &e" + args[0]);
            Msg.send(sender, "&7Alle Trolls: &e" + b + " list&7, Hilfe: &e" + b + " help");
            return;
        }
        if (args.length < 2) {
            Msg.send(sender, "&cNutzung: &e" + b + " " + def.id + " <Ziel> [Sekunden]");
            return;
        }
        Integer seconds = null;
        if (args.length >= 3) {
            try {
                seconds = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                Msg.send(sender, "&cUngültige Dauer: &e" + args[2]);
                return;
            }
        }
        troll(sender, def, args[1], seconds);
    }

    private Targets.Result targets(CommandSender sender, String targetArg) {
        if (Targets.isGroup(targetArg) && !sender.hasPermission("mwtroll.group")) {
            Msg.send(sender, "&cDu darfst nur einzelne Spieler trollen. &8(mwtroll.group)");
            return null;
        }
        Targets.Result r = Targets.resolve(sender, targetArg, plugin.includeSelf());
        if (r.error != null) {
            Msg.send(sender, "&c" + r.error);
            return null;
        }
        return r;
    }

    /** Wird auch vom Menü benutzt. seconds == null: Standard-Dauer des Trolls. */
    void troll(CommandSender sender, TrollRegistry.TrollDef def, String targetArg, Integer seconds) {
        if (!plugin.registry().canUse(sender, def)) {
            Msg.send(sender, "&cDazu hast du keine Rechte. &8(" + def.permission() + ")");
            return;
        }
        Targets.Result r = targets(sender, targetArg);
        if (r == null) return;
        int s = seconds != null ? seconds : (def.duration > 0 ? def.duration : plugin.defaultDuration());
        s = Math.max(1, Math.min(plugin.maxDuration(), s));
        for (Player p : r.players) {
            plugin.trolls().run(p, sender, def, s);
            if (plugin.notifyTarget()) Msg.send(p, "&eDu wurdest getrollt: " + def.name);
        }
        Msg.send(sender, def.name + " &7auf &e" + r.label + " &7angewendet"
                + (def.timed() || seconds != null ? " &8(" + s + "s)" : "") + "&7.");
    }

    void rocket(CommandSender sender, String targetArg, String item, int amount) {
        Targets.Result r = targets(sender, targetArg);
        if (r == null) return;
        Player self = sender instanceof Player ? (Player) sender : null;
        for (Player p : r.players) {
            String err = plugin.equipment().give(p, self, item, amount);
            if (err != null) {
                Msg.send(sender, "&c" + err);
                return;
            }
        }
        Msg.send(sender, "&e" + r.label + " &7hat &a" + amount + "x " + item + " &7bekommen.");
    }

    void next(CommandSender sender, String targetArg, String items) {
        Targets.Result r = targets(sender, targetArg);
        if (r == null) return;
        String lower = items.toLowerCase(Locale.ROOT);
        if (lower.equals("clear") || lower.equals("reset")) {
            for (Player p : r.players) plugin.equipment().clear(p);
            Msg.send(sender, "&7Festgelegte Items für &e" + r.label + " &7gelöscht.");
            return;
        }
        if (lower.equals("list")) {
            for (Player p : r.players) {
                List<String> q = plugin.equipment().queued(p);
                Msg.send(sender, "&e" + p.getName() + "&7: " + (q.isEmpty() ? "&8(nichts festgelegt)" : "&f" + String.join(" &8→ &f", q)));
            }
            return;
        }
        Player self = sender instanceof Player ? (Player) sender : null;
        List<String> names = Arrays.asList(items.split(","));
        for (Player p : r.players) {
            String err = plugin.equipment().queue(p, self, names);
            if (err != null) {
                Msg.send(sender, "&c" + err);
                return;
            }
        }
        Msg.send(sender, "&e" + r.label + " &7bekommt als nächstes: &f" + String.join(" &8→ &f", names));
    }

    void stop(CommandSender sender, String targetArg) {
        Targets.Result r = Targets.resolve(sender, targetArg, true);
        if (r.error != null) {
            Msg.send(sender, "&c" + r.error);
            return;
        }
        for (Player p : r.players) plugin.trolls().stopAll(p);
        Msg.send(sender, "&aAlle Trolls und Einstellungen für &e" + r.label + " &azurückgesetzt.");
    }

    void set(CommandSender sender, String targetArg, String key, String value) {
        if (!TrollManager.SETTINGS.contains(key)) {
            Msg.send(sender, "&cUnbekannte Einstellung: &e" + key + "&c. Möglich: &e" + String.join(", ", TrollManager.SETTINGS));
            return;
        }
        Targets.Result r = targets(sender, targetArg);
        if (r == null) return;
        String error = null;
        for (Player p : r.players) {
            String e = plugin.trolls().applySetting(p, key, value);
            if (e != null) error = e;
        }
        if (error != null) {
            Msg.send(sender, "&c" + error);
            return;
        }
        Msg.send(sender, "&7Einstellung &e" + key + " &7= &a" + value + " &7für &e" + r.label + "&7.");
    }

    private void info(CommandSender sender, String name) {
        Targets.Result r = Targets.resolve(sender, name, true);
        if (r.error != null) {
            Msg.send(sender, "&c" + r.error);
            return;
        }
        for (Player p : r.players) {
            Msg.raw(sender, "&8&m------&r &cTroll-Info: &e" + p.getName() + " &8&m------");
            String team = MWHook.teamName(p);
            if (team != null) Msg.raw(sender, "&7Team: " + team);
            for (String key : TrollManager.SETTINGS) {
                Msg.raw(sender, "&7" + key + ": &e" + plugin.trolls().settingValue(p, key));
            }
            List<String> q = plugin.equipment().queued(p);
            if (!q.isEmpty()) Msg.raw(sender, "&7Nächste Items: &f" + String.join(", ", q));
        }
    }

    private void list(CommandSender sender) {
        Msg.raw(sender, "&8&m--------&r &cTrolls &8&m--------");
        for (TrollRegistry.TrollDef d : plugin.registry().all()) {
            boolean ok = plugin.registry().canUse(sender, d);
            Msg.raw(sender, (ok ? "&e" : "&8") + d.id + " &8- &f" + d.name + (d.timed() ? " &8[" + d.duration + "s]" : "")
                    + (d.description.isEmpty() ? "" : " &7" + Compat.stripColors(d.description.get(0))));
        }
        Msg.raw(sender, "&e" + TrollRegistry.SPECIAL_ROCKET + " &8- &fRaketen/Items geben");
        Msg.raw(sender, "&e" + TrollRegistry.SPECIAL_NEXT + " &8- &fNächste MissileWars-Items festlegen");
    }

    private void debug(CommandSender sender) {
        Msg.raw(sender, "&8&m--------&r &cTroll-Debug &8&m--------");
        try {
            Msg.raw(sender, "&7Server: &f" + Bukkit.getVersion());
        } catch (Throwable ignored) {
        }
        Msg.raw(sender, "&7Java: &f" + System.getProperty("java.version"));
        Msg.raw(sender, "&7Trolls geladen: &f" + plugin.registry().all().size());
        Msg.raw(sender, "&7Item-Test: &f" + (Compat.item("PLAYER_HEAD") != null ? "ok" : "FEHLER")
                + " &8(" + Compat.lastItemMethod + ")");
        if (!"-".equals(Compat.lastItemError)) Msg.raw(sender, "&7Letzter Item-Fehler: &c" + Compat.lastItemError);
        Msg.raw(sender, "&7/mw troll über MissileWars registriert: &f" + plugin.acfStatus());
        Msg.raw(sender, "&7Nächste-Items-Hook: &f" + Equipment.hookStatus);
        Msg.raw(sender, "&7MissileWars-Arenen: &f" + String.join(", ", MWHook.gameNames()));
        if (sender instanceof Player) {
            Game g = Equipment.gameFor((Player) sender, null);
            if (g != null) Msg.raw(sender, "&7Ausrüstung: &f" + String.join(", ", Equipment.keys(g)));
        }
    }

    private void help(CommandSender sender, String b) {
        Msg.raw(sender, "&8&m--------&r &cMissileWars Troll &8&m--------");
        Msg.raw(sender, "&e" + b + " &7- Troll-Menü öffnen");
        Msg.raw(sender, "&e" + b + " menu <Ziel> &7- Menü direkt für ein Ziel");
        Msg.raw(sender, "&e" + b + " <troll> <Ziel> [Sekunden] &7- Troll ausführen");
        Msg.raw(sender, "&e" + b + " rocket <Ziel> <Rakete> [Anzahl] &7- Raketen/Items geben");
        Msg.raw(sender, "&e" + b + " nextitems <Ziel> <Item1,Item2,..|list|clear> &7- nächste Items festlegen");
        Msg.raw(sender, "&e" + b + " set <Ziel> <Einstellung> <Wert|reset> &7- dauerhaft einstellen");
        Msg.raw(sender, "&e" + b + " stop <Ziel> &7- alles zurücksetzen");
        Msg.raw(sender, "&e" + b + " info <Spieler> &7- aktuelle Einstellungen");
        Msg.raw(sender, "&e" + b + " list &7- alle Trolls");
        Msg.raw(sender, "&e" + b + " reload &7- config.yml + trolls.yml neu laden");
        Msg.raw(sender, "&e" + b + " debug &7- Diagnose bei Problemen");
        Msg.raw(sender, "&7Ziele: &fName&7, &fName1,Name2&7, &f@a&7, &f@ingame&7, &f@game&7, &f@team&7, &f@enemy&7, &fgame:<Arena>");
    }

    List<String> tab(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        try {
            if (!sender.hasPermission("mwtroll.use")) return options;
            String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if (args.length <= 1) {
                options.addAll(Arrays.asList("menu", "list", "help", "info", "debug"));
                if (sender.hasPermission("mwtroll.set")) options.add("set");
                if (sender.hasPermission("mwtroll.stop")) options.add("stop");
                if (sender.hasPermission("mwtroll.reload")) options.add("reload");
                if (plugin.registry().canUseSpecial(sender, TrollRegistry.SPECIAL_ROCKET)) options.add(TrollRegistry.SPECIAL_ROCKET);
                if (plugin.registry().canUseSpecial(sender, TrollRegistry.SPECIAL_NEXT)) options.add(TrollRegistry.SPECIAL_NEXT);
                for (TrollRegistry.TrollDef d : plugin.registry().all()) {
                    if (plugin.registry().canUse(sender, d)) options.add(d.id);
                }
            } else if (args.length == 2) {
                if (!Arrays.asList("list", "help", "reload", "debug").contains(sub)) options.addAll(Targets.suggestions());
            } else if (args.length == 3) {
                if (sub.equals("set")) {
                    options.addAll(TrollManager.SETTINGS);
                } else if (sub.equals(TrollRegistry.SPECIAL_ROCKET) || sub.equals("rakete") || sub.equals("give")) {
                    options.addAll(Equipment.allKeys());
                } else if (sub.equals(TrollRegistry.SPECIAL_NEXT) || sub.equals("next")) {
                    String cur = args[2];
                    int comma = cur.lastIndexOf(',');
                    String prefix = comma < 0 ? "" : cur.substring(0, comma + 1);
                    if (comma < 0) options.addAll(Arrays.asList("list", "clear"));
                    for (String k : Equipment.allKeys()) options.add(prefix + k);
                } else if (plugin.registry().get(sub) != null) {
                    options.addAll(Arrays.asList("5", "10", "30", "60", "300", "1800"));
                }
            } else if (args.length == 4) {
                if (sub.equals("set")) {
                    String key = args[2].toLowerCase(Locale.ROOT);
                    if (TrollManager.NUMERIC_SETTINGS.contains(key)) options.addAll(Arrays.asList("0", "0.5", "1", "2", "3", "reset"));
                    else options.addAll(Arrays.asList("on", "off"));
                } else if (sub.equals(TrollRegistry.SPECIAL_ROCKET) || sub.equals("rakete") || sub.equals("give")) {
                    options.addAll(Arrays.asList("1", "5", "10", "64"));
                }
            }
        } catch (Throwable t) {
            return options;
        }
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }
}
