package de.mwtroll;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Führt Trolls aus und merkt sich alles, was verändert wurde,
 * damit es sauber zurückgesetzt werden kann.
 */
final class TrollManager {

    /** Einstellungen, die per /mw troll set dauerhaft gesetzt werden können. */
    static final List<String> SETTINGS = Arrays.asList("speed", "jump", "size", "gravity", "glow", "invisible", "freeze");
    /** Alle eingebauten Effekte, nutzbar in trolls.yml mit "effect: <name>". */
    static final List<String> EFFECTS = Arrays.asList("fake_explosion", "creeper", "launch", "lightning", "turn",
            "shuffle", "drop", "jumpscare", "demo", "fake_op", "cave", "drunk", "blind", "levitate", "freeze", "spin",
            "tiny", "giant", "nojump", "snail", "turbo", "moon", "glow", "pumpkin", "chickens");
    static final List<String> NUMERIC_SETTINGS = Arrays.asList("speed", "jump", "size", "gravity");

    static final class PlayerState {
        final Map<String, Double> originalBase = new HashMap<>();
        final Map<String, Double> settingMultiplier = new HashMap<>();
        final List<BukkitTask> tasks = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final Set<PotionEffectType> potions = new HashSet<>();
        ItemStack savedHelmet;
        boolean helmetSaved;
        boolean freezeSetting;
        long freezeUntil;
        boolean glowSetting;
        int glowTimed;
        boolean invisibleSetting;
        long noFallUntil;

        boolean isEmpty() {
            return originalBase.isEmpty() && settingMultiplier.isEmpty() && entities.isEmpty() && potions.isEmpty()
                    && !helmetSaved && !freezeSetting && !glowSetting && !invisibleSetting && glowTimed <= 0
                    && freezeUntil < System.currentTimeMillis();
        }
    }

    private final MWTroll plugin;
    private final Map<UUID, PlayerState> states = new HashMap<>();
    private final Random random = new Random();

    TrollManager(MWTroll plugin) {
        this.plugin = plugin;
    }

    PlayerState state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), k -> new PlayerState());
    }

    PlayerState peek(Player p) {
        return states.get(p.getUniqueId());
    }

    // ------------------------------------------------------------------
    // Hilfsfunktionen
    // ------------------------------------------------------------------

    private BukkitTask later(Player p, long ticks, Runnable r) {
        PlayerState st = state(p);
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            st.tasks.remove(holder[0]);
            if (p.isOnline()) r.run();
        }, Math.max(1L, ticks));
        st.tasks.add(holder[0]);
        return holder[0];
    }

    private static Attribute attributeFor(String key) {
        switch (key) {
            case "speed":
                return Compat.attribute("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED");
            case "jump":
                return Compat.attribute("JUMP_STRENGTH", "GENERIC_JUMP_STRENGTH");
            case "size":
                return Compat.attribute("SCALE", "GENERIC_SCALE");
            case "gravity":
                return Compat.attribute("GRAVITY", "GENERIC_GRAVITY");
            default:
                return null;
        }
    }

    private static double clamp(String key, double mult) {
        switch (key) {
            case "speed":
            case "jump":
                return Math.max(0, Math.min(10, mult));
            case "size":
                return Math.max(0.07, Math.min(15, mult));
            case "gravity":
                return Math.max(-5, Math.min(12, mult));
            default:
                return mult;
        }
    }

    /** Setzt ein Attribut auf (Originalwert * Multiplikator). */
    private boolean setAttributeMultiplier(Player p, String key, double mult) {
        Attribute attr = attributeFor(key);
        if (attr == null) return false;
        AttributeInstance ai = p.getAttribute(attr);
        if (ai == null) return false;
        PlayerState st = state(p);
        st.originalBase.putIfAbsent(key, ai.getBaseValue());
        ai.setBaseValue(st.originalBase.get(key) * clamp(key, mult));
        return true;
    }

    /** Stellt ein Attribut auf den dauerhaften Einstellungswert oder den Originalwert zurück. */
    private void restoreAttribute(Player p, String key) {
        PlayerState st = peek(p);
        if (st == null) return;
        Double orig = st.originalBase.get(key);
        if (orig == null) return;
        Attribute attr = attributeFor(key);
        AttributeInstance ai = attr == null ? null : p.getAttribute(attr);
        if (ai == null) return;
        Double mult = st.settingMultiplier.get(key);
        if (mult != null) {
            ai.setBaseValue(orig * clamp(key, mult));
        } else {
            ai.setBaseValue(orig);
            st.originalBase.remove(key);
        }
    }

    private void timedAttribute(Player p, String key, double mult, int seconds) {
        if (setAttributeMultiplier(p, key, mult)) {
            later(p, seconds * 20L, () -> restoreAttribute(p, key));
        }
    }

    private void potion(Player p, int seconds, int amplifier, String... names) {
        PotionEffectType type = Compat.potion(names);
        if (type == null) return;
        p.addPotionEffect(new PotionEffect(type, seconds * 20, amplifier, false, false));
        state(p).potions.add(type);
    }

    private void particle(Player p, Location loc, int count, String... names) {
        Particle particle = Compat.particle(names);
        if (particle == null) return;
        try {
            p.spawnParticle(particle, loc, count, 0.3, 0.3, 0.3, 0.05);
        } catch (Throwable ignored) {
        }
    }

    private void sound(Player p, Location loc, String key, float volume, float pitch) {
        try {
            p.playSound(loc, key, volume, pitch);
        } catch (Throwable ignored) {
        }
    }

    boolean isFrozen(Player p) {
        PlayerState st = peek(p);
        return st != null && (st.freezeSetting || st.freezeUntil > System.currentTimeMillis());
    }

    boolean hasNoFall(Player p) {
        PlayerState st = peek(p);
        return st != null && st.noFallUntil > System.currentTimeMillis();
    }

    private void noFall(Player p, long millis) {
        PlayerState st = state(p);
        st.noFallUntil = Math.max(st.noFallUntil, System.currentTimeMillis() + millis);
    }

    // ------------------------------------------------------------------
    // Trolls
    // ------------------------------------------------------------------

    /** Führt einen eingebauten Effekt aus. @return false, wenn der Effekt unbekannt ist */
    boolean effect(Player p, String key, int seconds) {
        seconds = Math.max(1, seconds);
        Location loc = p.getLocation();
        PlayerState st = state(p);
        switch (key.toLowerCase(java.util.Locale.ROOT)) {
            case "fake_explosion":
                particle(p, loc, 1, "EXPLOSION_EMITTER", "EXPLOSION_HUGE");
                particle(p, loc, 10, "EXPLOSION", "EXPLOSION_LARGE");
                sound(p, loc, "minecraft:entity.generic.explode", 1f, 1f);
                break;
            case "creeper": {
                double rad = Math.toRadians(loc.getYaw());
                Location behind = loc.clone().add(Math.sin(rad) * 1.5, 0, -Math.cos(rad) * 1.5);
                sound(p, behind, "minecraft:entity.creeper.primed", 1f, 0.8f);
                break;
            }
            case "launch":
                noFall(p, 10_000);
                p.setVelocity(new Vector(0, 2.3, 0));
                particle(p, loc, 30, "CLOUD");
                sound(p, loc, "minecraft:entity.firework_rocket.launch", 1f, 1f);
                break;
            case "lightning":
                World w = p.getWorld();
                w.strikeLightningEffect(loc);
                break;
            case "turn": {
                Location l = p.getLocation();
                l.setYaw(l.getYaw() + 180f);
                p.teleport(l);
                break;
            }
            case "shuffle": {
                PlayerInventory inv = p.getInventory();
                List<ItemStack> items = new ArrayList<>(Arrays.asList(inv.getStorageContents()));
                Collections.shuffle(items, random);
                inv.setStorageContents(items.toArray(new ItemStack[0]));
                sound(p, loc, "minecraft:item.armor.equip_generic", 1f, 1.5f);
                break;
            }
            case "drop": {
                PlayerInventory inv = p.getInventory();
                ItemStack hand = inv.getItemInMainHand();
                if (!Compat.isAir(hand)) {
                    p.getWorld().dropItemNaturally(p.getLocation().add(0, 1.2, 0), hand.clone());
                    inv.setItemInMainHand(null);
                }
                break;
            }
            case "jumpscare":
                particle(p, loc, 1, "ELDER_GUARDIAN", "MOB_APPEARANCE");
                sound(p, loc, "minecraft:entity.elder_guardian.curse", 1f, 1f);
                break;
            case "demo":
                try {
                    p.showDemoScreen();
                } catch (Throwable ignored) {
                }
                break;
            case "fake_op":
                p.sendMessage("§7§o[Server: Made " + p.getName() + " a server operator]");
                break;
            case "cave":
                sound(p, loc, "minecraft:ambient.cave", 1f, 1f);
                break;

            case "drunk":
                potion(p, seconds, 0, "NAUSEA", "CONFUSION");
                potion(p, seconds, 1, "SLOWNESS", "SLOW");
                break;
            case "blind":
                potion(p, seconds, 0, "BLINDNESS");
                break;
            case "levitate":
                potion(p, seconds, 1, "LEVITATION");
                noFall(p, (seconds + 10) * 1000L);
                break;
            case "freeze":
                st.freezeUntil = Math.max(st.freezeUntil, System.currentTimeMillis() + seconds * 1000L);
                break;
            case "spin": {
                final int[] ticks = {seconds * 10};
                BukkitTask[] task = new BukkitTask[1];
                task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                    if (!p.isOnline() || ticks[0]-- <= 0) {
                        task[0].cancel();
                        st.tasks.remove(task[0]);
                        return;
                    }
                    Location l = p.getLocation();
                    l.setYaw(l.getYaw() + 36f);
                    p.teleport(l);
                }, 0L, 2L);
                st.tasks.add(task[0]);
                break;
            }
            case "tiny":
                timedAttribute(p, "size", 0.3, seconds);
                break;
            case "giant":
                timedAttribute(p, "size", 2.5, seconds);
                break;
            case "nojump":
                timedAttribute(p, "jump", 0, seconds);
                break;
            case "snail":
                timedAttribute(p, "speed", 0.25, seconds);
                break;
            case "turbo":
                timedAttribute(p, "speed", 3.0, seconds);
                break;
            case "moon":
                noFall(p, (seconds + 10) * 1000L);
                timedAttribute(p, "gravity", 0.15, seconds);
                break;
            case "glow":
                st.glowTimed++;
                p.setGlowing(true);
                later(p, seconds * 20L, () -> {
                    st.glowTimed = Math.max(0, st.glowTimed - 1);
                    if (st.glowTimed == 0) p.setGlowing(st.glowSetting);
                });
                break;
            case "pumpkin": {
                PlayerInventory inv = p.getInventory();
                if (!st.helmetSaved) {
                    ItemStack helmet = inv.getHelmet();
                    st.savedHelmet = helmet == null ? null : helmet.clone();
                    st.helmetSaved = true;
                }
                inv.setHelmet(Compat.item("CARVED_PUMPKIN", "PUMPKIN"));
                later(p, seconds * 20L, () -> restoreHelmet(p));
                break;
            }
            case "chickens": {
                int amount = 14;
                for (int i = 0; i < amount; i++) {
                    later(p, i * 3L, () -> {
                        Location base = p.getLocation();
                        Location spawn = base.clone().add(random.nextDouble() * 6 - 3, 4 + random.nextDouble() * 3, random.nextDouble() * 6 - 3);
                        try {
                            Chicken c = p.getWorld().spawn(spawn, Chicken.class);
                            st.entities.add(c);
                        } catch (Throwable ignored) {
                        }
                    });
                }
                later(p, seconds * 20L, () -> removeEntities(st));
                break;
            }
            default:
                return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Trolls aus trolls.yml ausführen
    // ------------------------------------------------------------------

    /** Führt alle Aktionen eines Trolls aus trolls.yml für ein Ziel aus. */
    void run(Player target, org.bukkit.command.CommandSender sender, TrollRegistry.TrollDef def, int seconds) {
        long offset = 0;
        for (String raw : def.actions) {
            int idx = raw.indexOf(':');
            String type = (idx < 0 ? raw : raw.substring(0, idx)).trim().toLowerCase(java.util.Locale.ROOT);
            String value = idx < 0 ? "" : raw.substring(idx + 1).trim();
            value = value.replace("%player%", target.getName())
                    .replace("%sender%", sender.getName())
                    .replace("%duration%", String.valueOf(seconds))
                    .replace("%troll%", Compat.stripColors(def.name));
            if (type.equals("delay")) {
                try {
                    offset += Long.parseLong(value);
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            final String t = type;
            final String v = value;
            Runnable r = () -> {
                try {
                    action(target, sender, t, v, seconds);
                } catch (Throwable ex) {
                    plugin.getLogger().warning("Troll '" + def.id + "', Aktion '" + raw + "' fehlgeschlagen: " + ex);
                }
            };
            if (offset <= 0) r.run();
            else later(target, offset, r);
        }
    }

    private static double num(String[] parts, int i, double def) {
        if (parts.length <= i) return def;
        try {
            return Double.parseDouble(parts[i].replace(',', '.'));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void action(Player p, org.bukkit.command.CommandSender sender, String type, String value, int seconds) {
        String[] parts = value.split("\\s+");
        Location loc = p.getLocation();
        switch (type) {
            case "effect":
                effect(p, value, seconds);
                break;
            case "sound": {
                String key = parts[0].contains(":") ? parts[0] : "minecraft:" + parts[0];
                sound(p, loc, key.toLowerCase(java.util.Locale.ROOT), (float) num(parts, 1, 1), (float) num(parts, 2, 1));
                break;
            }
            case "particle":
                particle(p, loc.clone().add(0, 1, 0), (int) num(parts, 1, 10), parts[0].toUpperCase(java.util.Locale.ROOT));
                break;
            case "potion":
                potion(p, (int) num(parts, 2, seconds), (int) num(parts, 1, 0), parts[0]);
                break;
            case "attribute": {
                String key = parts[0].toLowerCase(java.util.Locale.ROOT);
                timedAttribute(p, key, num(parts, 1, 1), (int) num(parts, 2, seconds));
                break;
            }
            case "velocity":
                noFall(p, 10_000);
                p.setVelocity(new Vector(num(parts, 0, 0), num(parts, 1, 1), num(parts, 2, 0)));
                break;
            case "nofall":
                noFall(p, (long) (num(parts, 0, 10) * 1000));
                break;
            case "message":
                p.sendMessage(Msg.c(value));
                break;
            case "sender-message":
                sender.sendMessage(Msg.c(value));
                break;
            case "title": {
                String[] tp = value.split("\\|", 2);
                p.sendTitle(Msg.c(tp[0].trim()), tp.length > 1 ? Msg.c(tp[1].trim()) : "", 10, 60, 20);
                break;
            }
            case "actionbar":
                Compat.actionBar(p, Msg.c(value));
                break;
            case "broadcast":
                for (Player o : Bukkit.getOnlinePlayers()) o.sendMessage(Msg.c(value));
                break;
            case "console":
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.startsWith("/") ? value.substring(1) : value);
                break;
            case "command":
                Bukkit.dispatchCommand(p, value.startsWith("/") ? value.substring(1) : value);
                break;
            case "give": {
                ItemStack item = Compat.item(parts[0]);
                if (item == null) break;
                item.setAmount((int) Math.max(1, Math.min(64, num(parts, 1, 1))));
                p.getInventory().addItem(item);
                break;
            }
            case "rocket": {
                String err = plugin.equipment().give(p, sender instanceof Player ? (Player) sender : null, parts[0], (int) num(parts, 1, 1));
                if (err != null) sender.sendMessage(Msg.c(Msg.prefix + "&c" + err));
                break;
            }
            case "nextitems": {
                String err = plugin.equipment().queue(p, sender instanceof Player ? (Player) sender : null,
                        Arrays.asList(value.replace(" ", "").split(",")));
                if (err != null) sender.sendMessage(Msg.c(Msg.prefix + "&c" + err));
                break;
            }
            default:
                break;
        }
    }

    private void restoreHelmet(Player p) {
        PlayerState st = peek(p);
        if (st == null || !st.helmetSaved) return;
        p.getInventory().setHelmet(st.savedHelmet);
        st.savedHelmet = null;
        st.helmetSaved = false;
    }

    private void removeEntities(PlayerState st) {
        for (Entity e : st.entities) {
            try {
                if (e.isValid()) e.remove();
            } catch (Throwable ignored) {
            }
        }
        st.entities.clear();
    }

    // ------------------------------------------------------------------
    // Dauerhafte Einstellungen
    // ------------------------------------------------------------------

    /**
     * @return Fehlermeldung oder null bei Erfolg
     */
    String applySetting(Player p, String key, String value) {
        PlayerState st = state(p);
        boolean reset = value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default") || value.equalsIgnoreCase("normal");
        if (NUMERIC_SETTINGS.contains(key)) {
            if (reset) {
                st.settingMultiplier.remove(key);
                restoreAttribute(p, key);
                return null;
            }
            double mult;
            try {
                mult = Double.parseDouble(value.replace(',', '.'));
            } catch (NumberFormatException e) {
                return "Ungültiger Wert: " + value + " (Zahl oder 'reset')";
            }
            mult = clamp(key, mult);
            st.settingMultiplier.put(key, mult);
            if (!setAttributeMultiplier(p, key, mult)) {
                st.settingMultiplier.remove(key);
                return "Diese Einstellung wird von deiner Serverversion nicht unterstützt.";
            }
            return null;
        }
        Boolean on = parseBool(value);
        if (reset) on = false;
        if (on == null) return "Ungültiger Wert: " + value + " (on/off)";
        switch (key) {
            case "glow":
                st.glowSetting = on;
                if (st.glowTimed == 0) p.setGlowing(on);
                return null;
            case "invisible": {
                st.invisibleSetting = on;
                PotionEffectType inv = Compat.potion("INVISIBILITY");
                if (inv == null) return "Unsichtbarkeit nicht verfügbar.";
                if (on) {
                    p.addPotionEffect(new PotionEffect(inv, -1, 0, false, false));
                    st.potions.add(inv);
                } else {
                    p.removePotionEffect(inv);
                }
                return null;
            }
            case "freeze":
                st.freezeSetting = on;
                return null;
            default:
                return "Unbekannte Einstellung: " + key;
        }
    }

    /** Aktueller Wert einer Einstellung als Text (für Menü und Info). */
    String settingValue(Player p, String key) {
        PlayerState st = peek(p);
        if (NUMERIC_SETTINGS.contains(key)) {
            Double m = st == null ? null : st.settingMultiplier.get(key);
            return m == null ? "1.0 (normal)" : trim(m);
        }
        boolean on = st != null && (key.equals("glow") ? st.glowSetting : key.equals("invisible") ? st.invisibleSetting : st.freezeSetting);
        return on ? "an" : "aus";
    }

    double numericSetting(Player p, String key) {
        PlayerState st = peek(p);
        Double m = st == null ? null : st.settingMultiplier.get(key);
        return m == null ? 1.0 : m;
    }

    boolean boolSetting(Player p, String key) {
        PlayerState st = peek(p);
        if (st == null) return false;
        switch (key) {
            case "glow":
                return st.glowSetting;
            case "invisible":
                return st.invisibleSetting;
            case "freeze":
                return st.freezeSetting;
            default:
                return false;
        }
    }

    static String trim(double d) {
        if (d == Math.rint(d)) return String.valueOf((long) d) + ".0";
        return String.valueOf(Math.round(d * 100.0) / 100.0);
    }

    static Boolean parseBool(String s) {
        switch (s.toLowerCase()) {
            case "on":
            case "an":
            case "true":
            case "ja":
            case "yes":
            case "1":
                return true;
            case "off":
            case "aus":
            case "false":
            case "nein":
            case "no":
            case "0":
                return false;
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // Zurücksetzen
    // ------------------------------------------------------------------

    /** Entfernt alle Trolls und Einstellungen vom Spieler. */
    void stopAll(Player p) {
        if (plugin.equipment() != null) plugin.equipment().clear(p);
        PlayerState st = states.remove(p.getUniqueId());
        if (st == null) return;
        for (BukkitTask t : new ArrayList<>(st.tasks)) {
            try {
                t.cancel();
            } catch (Throwable ignored) {
            }
        }
        st.tasks.clear();
        removeEntities(st);
        for (PotionEffectType type : st.potions) {
            try {
                p.removePotionEffect(type);
            } catch (Throwable ignored) {
            }
        }
        for (Map.Entry<String, Double> e : st.originalBase.entrySet()) {
            Attribute attr = attributeFor(e.getKey());
            AttributeInstance ai = attr == null ? null : p.getAttribute(attr);
            if (ai != null) ai.setBaseValue(e.getValue());
        }
        if (st.helmetSaved) {
            p.getInventory().setHelmet(st.savedHelmet);
        }
        if (st.glowSetting || st.glowTimed > 0) p.setGlowing(false);
        // Fallschutz noch kurz behalten, falls der Spieler gerade in der Luft ist
        if (st.noFallUntil > System.currentTimeMillis()) {
            PlayerState fresh = state(p);
            fresh.noFallUntil = st.noFallUntil;
        }
    }

    void stopAllOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            stopAll(p);
        }
        states.clear();
    }

    void cleanupIfEmpty(Player p) {
        PlayerState st = peek(p);
        if (st != null && st.tasks.isEmpty() && st.isEmpty() && st.noFallUntil < System.currentTimeMillis()) {
            states.remove(p.getUniqueId());
        }
    }
}
