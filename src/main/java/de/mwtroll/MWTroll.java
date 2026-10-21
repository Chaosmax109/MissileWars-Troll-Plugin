package de.mwtroll;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * MissileWars-Troll – Add-on für MissileWars.
 * Befehl: /mw troll  (Alternativ: /mwtroll, /mwt)
 */
public final class MWTroll extends JavaPlugin {

    static MWTroll instance;

    private TrollManager trolls;
    private TrollCommand command;
    private Menus menus;
    private TrollRegistry registry;
    private Equipment equipment;

    private Object acfCommand;
    private String acfStatus = "nein";

    private int defaultDuration;
    private int maxDuration;
    private boolean notifyTarget;
    private boolean includeSelf;
    private boolean resetOnGameEnd;
    private boolean resetOnLeave;
    private boolean bypassEnabled;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();
        reloadSettings();

        registry = new TrollRegistry(this);
        registry.load();
        trolls = new TrollManager(this);
        equipment = new Equipment(this);
        equipment.start();
        command = new TrollCommand(this);
        menus = new Menus(this);

        PluginCommand pc = getCommand("mwtroll");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        register("Befehl-/Effekt-Listener", new Listeners(this));
        register("Menü-Listener", menus);
        try {
            register("Paper-Tab-Vervollständigung", new PaperTabListener(this));
        } catch (Throwable ignored) {
            // kein Paper → normale Tab-Vervollständigung über /mwtroll
        }
        registerAcf();

        getLogger().info("MissileWars-Troll aktiviert. Befehl: /mw troll (oder /mwtroll)");
    }

    private void register(String name, org.bukkit.event.Listener listener) {
        try {
            Bukkit.getPluginManager().registerEvents(listener, this);
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Konnte " + name + " nicht registrieren", t);
        }
    }

    /** Hängt "troll" direkt an den MissileWars-Befehl /mw an (zusätzlich zum Abfangen des Befehls). */
    private void registerAcf() {
        try {
            Class<?> mw = Class.forName("de.butzlabben.missilewars.MissileWars");
            Object mwInstance = mw.getMethod("getInstance").invoke(null);
            Object manager = mw.getMethod("getCommandManager").invoke(mwInstance);
            Class<?> base = Class.forName("co.aikar.commands.BaseCommand", true, manager.getClass().getClassLoader());
            Object cmd = new AcfTroll(this);
            Method reg = manager.getClass().getMethod("registerCommand", base);
            reg.invoke(manager, cmd);
            acfCommand = cmd;
            acfStatus = "ja";
        } catch (Throwable t) {
            acfStatus = "nein (" + t.getClass().getSimpleName() + ") – Befehl wird abgefangen";
            getLogger().info("/mw troll wird über das Abfangen des Befehls bereitgestellt (" + t + ")");
        }
    }

    private void unregisterAcf() {
        if (acfCommand == null) return;
        try {
            Class<?> mw = Class.forName("de.butzlabben.missilewars.MissileWars");
            Object mwInstance = mw.getMethod("getInstance").invoke(null);
            Object manager = mw.getMethod("getCommandManager").invoke(mwInstance);
            Class<?> base = Class.forName("co.aikar.commands.BaseCommand", true, manager.getClass().getClassLoader());
            manager.getClass().getMethod("unregisterCommand", base).invoke(manager, acfCommand);
        } catch (Throwable ignored) {
        }
        acfCommand = null;
    }

    @Override
    public void onDisable() {
        if (trolls != null) trolls.stopAllOnline();
        if (equipment != null) equipment.shutdown();
        unregisterAcf();
    }

    /**
     * Ergänzt fehlende Einträge in der config.yml (bei jedem Start) und hebt alte Standardwerte an.
     * Wichtig: isSet() statt contains(), weil contains() auch die Standardwerte aus der JAR mitzählt.
     */
    private void migrateConfig() {
        try {
            boolean changed = false;
            int version = getConfig().isSet("config-version") ? getConfig().getInt("config-version", 1) : 1;
            if (version < 2 && getConfig().isSet("max-duration") && getConfig().getInt("max-duration", 1800) == 300) {
                getConfig().set("max-duration", 1800);
                changed = true;
            }
            String[][] keys = {
                    {"prefix", "Präfix vor allen Nachrichten (& Farbcodes)"},
                    {"default-duration", "Standard-Dauer in Sekunden, wenn ein Troll keine eigene duration hat"},
                    {"max-duration", "Maximal erlaubte Dauer in Sekunden (1800 = 30 Minuten)"},
                    {"notify-target", "Soll das Ziel eine Nachricht \"Du wurdest getrollt\" bekommen?"},
                    {"include-self-in-groups", "Soll der Ausführende bei Gruppen (@a, @game, ...) selbst mitgetrollt werden?"},
                    {"reset-on-game-end", "Alle Trolls und Einstellungen zurücksetzen, wenn ein MissileWars-Spiel endet"},
                    {"reset-on-arena-leave", "Alle Trolls und Einstellungen zurücksetzen, wenn ein Spieler die Arena verlässt"},
                    {"bypass-enabled", "Spieler mit dem Recht mwtroll.bypass vor Trolls schützen?|"
                            + "ACHTUNG: Wer alle Rechte hat (z. B. * in LuckPerms oder Admin-Ränge),|"
                            + "hat automatisch auch mwtroll.bypass und wäre dann geschützt."}
            };
            for (String[] k : keys) {
                if (getConfig().isSet(k[0])) continue;
                Object def = getConfig().get(k[0]);
                if (def == null) continue;
                getConfig().set(k[0], def);
                try {
                    getConfig().setComments(k[0], java.util.Arrays.asList(k[1].split("\\|")));
                } catch (Throwable ignored) {
                    // ältere Server ohne Kommentar-Unterstützung
                }
                getLogger().info("config.yml: fehlenden Eintrag '" + k[0] + "' ergänzt.");
                changed = true;
            }
            if (version < 3) {
                getConfig().set("config-version", 3);
                changed = true;
            }
            if (changed) saveConfig();
        } catch (Throwable t) {
            getLogger().warning("config.yml konnte nicht aktualisiert werden: " + t);
        }
    }

    void reloadSettings() {
        reloadConfig();
        Msg.prefix = getConfig().getString("prefix", "&8[&cTroll&8] &7");
        defaultDuration = Math.max(1, getConfig().getInt("default-duration", 10));
        maxDuration = Math.max(defaultDuration, getConfig().getInt("max-duration", 1800));
        notifyTarget = getConfig().getBoolean("notify-target", false);
        includeSelf = getConfig().getBoolean("include-self-in-groups", false);
        resetOnGameEnd = getConfig().getBoolean("reset-on-game-end", true);
        resetOnLeave = getConfig().getBoolean("reset-on-arena-leave", true);
        bypassEnabled = getConfig().getBoolean("bypass-enabled", false);
    }

    void reloadAll() {
        reloadSettings();
        registry.load();
    }

    TrollManager trolls() {
        return trolls;
    }

    TrollCommand command() {
        return command;
    }

    Menus menus() {
        return menus;
    }

    TrollRegistry registry() {
        return registry;
    }

    Equipment equipment() {
        return equipment;
    }

    String acfStatus() {
        return acfStatus;
    }

    int defaultDuration() {
        return defaultDuration;
    }

    int maxDuration() {
        return maxDuration;
    }

    boolean notifyTarget() {
        return notifyTarget;
    }

    boolean includeSelf() {
        return includeSelf;
    }

    boolean resetOnGameEnd() {
        return resetOnGameEnd;
    }

    boolean bypassEnabled() {
        return bypassEnabled;
    }

    boolean resetOnLeave() {
        return resetOnLeave;
    }
}
