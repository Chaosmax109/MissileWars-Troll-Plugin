package de.mwtroll;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lädt alle Trolls aus trolls.yml und registriert pro Troll eine Permission.
 */
final class TrollRegistry {

    /** Ein Troll aus der trolls.yml. */
    static final class TrollDef {
        final String id;
        final String name;
        final String[] icon;
        final List<String> description;
        final int duration;
        final List<String> actions;

        TrollDef(String id, String name, String[] icon, List<String> description, int duration, List<String> actions) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.duration = duration;
            this.actions = actions;
        }

        boolean timed() {
            return duration > 0;
        }

        String permission() {
            return "mwtroll.troll." + id;
        }
    }

    /** Anzeige-Infos für die Spezial-Trolls (Raketen geben / nächste Items). */
    static final class SpecialDef {
        final String id;
        final boolean enabled;
        final String name;
        final String[] icon;
        final List<String> description;

        SpecialDef(String id, boolean enabled, String name, String[] icon, List<String> description) {
            this.id = id;
            this.enabled = enabled;
            this.name = name;
            this.icon = icon;
            this.description = description;
        }

        String permission() {
            return "mwtroll.troll." + id;
        }
    }

    static final String SPECIAL_ROCKET = "rocket";
    static final String SPECIAL_NEXT = "nextitems";

    /** Unterbefehle, die nicht als Troll-ID benutzt werden dürfen. */
    static final List<String> RESERVED = Arrays.asList("menu", "gui", "help", "hilfe", "?", "list", "liste", "reload",
            "stop", "reset", "clear", "set", "info", "debug", SPECIAL_ROCKET, "rakete", "give", SPECIAL_NEXT, "next");

    static final List<String> ACTION_TYPES = Arrays.asList("effect", "sound", "particle", "potion", "attribute",
            "velocity", "nofall", "message", "sender-message", "title", "actionbar", "broadcast", "console", "command",
            "give", "rocket", "nextitems", "delay");

    private final MWTroll plugin;
    private final Map<String, TrollDef> trolls = new LinkedHashMap<>();
    private final Map<String, SpecialDef> specials = new LinkedHashMap<>();
    private final List<String> registeredPermissions = new ArrayList<>();

    TrollRegistry(MWTroll plugin) {
        this.plugin = plugin;
    }

    void load() {
        File file = new File(plugin.getDataFolder(), "trolls.yml");
        if (!file.exists()) plugin.saveResource("trolls.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        trolls.clear();
        specials.clear();

        ConfigurationSection section = yml.getConfigurationSection("trolls");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if (s == null) continue;
                String id = key.toLowerCase(Locale.ROOT);
                if (RESERVED.contains(id)) {
                    plugin.getLogger().warning("trolls.yml: '" + key + "' ist ein reservierter Name und wird übersprungen.");
                    continue;
                }
                if (!s.getBoolean("enabled", true)) continue;
                List<String> actions = s.getStringList("actions");
                if (actions.isEmpty()) {
                    plugin.getLogger().warning("trolls.yml: Troll '" + key + "' hat keine actions und wird übersprungen.");
                    continue;
                }
                validate(key, actions);
                trolls.put(id, new TrollDef(id,
                        s.getString("name", "&6" + key),
                        icons(s.getString("icon", "PAPER")),
                        lines(s),
                        Math.max(0, s.getInt("duration", 0)),
                        actions));
            }
        }

        ConfigurationSection sp = yml.getConfigurationSection("special");
        specials.put(SPECIAL_ROCKET, special(sp, SPECIAL_ROCKET, "&cRaketen geben", "CREEPER_SPAWN_EGG",
                "&7Gibt dem Ziel bestimmte Raketen,", "&7Schilde, Pfeile oder Feuerbälle."));
        specials.put(SPECIAL_NEXT, special(sp, SPECIAL_NEXT, "&dNächste Items bestimmen", "ENDER_EYE",
                "&7Legt fest, welche Items das Ziel", "&7von MissileWars als nächstes bekommt."));

        registerPermissions();
        plugin.getLogger().info(trolls.size() + " Trolls aus trolls.yml geladen.");
    }

    private SpecialDef special(ConfigurationSection sp, String id, String defName, String defIcon, String... defDesc) {
        ConfigurationSection s = sp == null ? null : sp.getConfigurationSection(id);
        if (s == null) return new SpecialDef(id, true, defName, icons(defIcon), Arrays.asList(defDesc));
        List<String> desc = lines(s);
        return new SpecialDef(id, s.getBoolean("enabled", true), s.getString("name", defName),
                icons(s.getString("icon", defIcon)), desc.isEmpty() ? Arrays.asList(defDesc) : desc);
    }

    private static String[] icons(String raw) {
        String[] parts = raw.split("[,|]");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        return parts;
    }

    private static List<String> lines(ConfigurationSection s) {
        if (s.isList("description")) return new ArrayList<>(s.getStringList("description"));
        String single = s.getString("description", null);
        List<String> out = new ArrayList<>();
        if (single != null) out.add(single);
        return out;
    }

    private void validate(String key, List<String> actions) {
        for (String a : actions) {
            int idx = a.indexOf(':');
            String type = (idx < 0 ? a : a.substring(0, idx)).trim().toLowerCase(Locale.ROOT);
            if (!ACTION_TYPES.contains(type)) {
                plugin.getLogger().warning("trolls.yml: Troll '" + key + "' hat eine unbekannte Aktion: '" + a + "'");
            } else if (type.equals("effect")) {
                String effect = idx < 0 ? "" : a.substring(idx + 1).trim().toLowerCase(Locale.ROOT);
                if (!TrollManager.EFFECTS.contains(effect)) {
                    plugin.getLogger().warning("trolls.yml: Troll '" + key + "' nutzt unbekannten Effekt: '" + effect + "'");
                }
            }
        }
    }

    private void registerPermissions() {
        try {
            PluginManager pm = Bukkit.getPluginManager();
            for (String name : registeredPermissions) {
                try {
                    pm.removePermission(name);
                } catch (Throwable ignored) {
                }
            }
            registeredPermissions.clear();
            List<String> names = new ArrayList<>();
            for (TrollDef d : trolls.values()) names.add(d.permission());
            for (SpecialDef d : specials.values()) names.add(d.permission());
            for (String name : names) {
                if (pm.getPermission(name) != null) continue;
                Permission perm = new Permission(name, "Troll ausführen: " + name.substring("mwtroll.troll.".length()), PermissionDefault.OP);
                pm.addPermission(perm);
                perm.addParent("mwtroll.troll.*", true);
                registeredPermissions.add(name);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Konnte Troll-Permissions nicht registrieren: " + t);
        }
    }

    Collection<TrollDef> all() {
        return trolls.values();
    }

    TrollDef get(String id) {
        return id == null ? null : trolls.get(id.toLowerCase(Locale.ROOT));
    }

    SpecialDef special(String id) {
        return specials.get(id);
    }

    static boolean canUse(org.bukkit.command.CommandSender sender, String permission) {
        return sender.hasPermission("mwtroll.troll.*") || sender.hasPermission(permission);
    }

    boolean canUse(org.bukkit.command.CommandSender sender, TrollDef def) {
        return canUse(sender, def.permission());
    }

    boolean canUseSpecial(org.bukkit.command.CommandSender sender, String id) {
        SpecialDef d = specials.get(id);
        return d != null && d.enabled && canUse(sender, d.permission());
    }
}
