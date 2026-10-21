package de.mwtroll;

import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.GameManager;
import de.butzlabben.missilewars.game.equipment.EquipmentManager;
import de.butzlabben.missilewars.game.equipment.PlayerEquipmentRandomizer;
import de.butzlabben.missilewars.game.equipment.SpecialGameEquipment;
import de.butzlabben.missilewars.game.schematics.objects.SchematicObject;
import de.butzlabben.missilewars.player.MWPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Zugriff auf die MissileWars-Ausrüstung (Raketen, Schilde, Pfeile, Feuerbälle):
 * - Items direkt geben
 * - festlegen, welche Items ein Spieler als nächstes von MissileWars bekommt
 */
final class Equipment {

    enum Kind { MISSILE, SHIELD, SPECIAL }

    static final class Entry {
        final String key;
        final String display;
        final Kind kind;
        final int index;
        final ItemStack item;

        Entry(String key, String display, Kind kind, int index, ItemStack item) {
            this.key = key;
            this.display = display;
            this.kind = kind;
            this.index = index;
            this.item = item;
        }
    }

    private final MWTroll plugin;
    private final Map<UUID, Deque<String>> queues = new HashMap<>();
    private BukkitTask task;
    static String hookStatus = "noch nicht benutzt";

    Equipment(MWTroll plugin) {
        this.plugin = plugin;
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    void shutdown() {
        if (task != null) task.cancel();
        queues.clear();
    }

    // ------------------------------------------------------------------
    // Ausrüstungs-Liste einer Arena
    // ------------------------------------------------------------------

    static String normalize(String s) {
        return Compat.stripColors(s).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    static List<Entry> entries(Game game) {
        List<Entry> out = new ArrayList<>();
        if (game == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        try {
            EquipmentManager em = game.getEquipmentManager();
            if (em == null) return out;
            addSchematics(out, seen, em.getMissileEquipment() == null ? null : em.getMissileEquipment().getSchematicEquipmentList(), Kind.MISSILE);
            addSchematics(out, seen, em.getShieldEquipment() == null ? null : em.getShieldEquipment().getSchematicEquipmentList(), Kind.SHIELD);
            SpecialGameEquipment special = em.getSpecialEquipment();
            if (special != null && special.getSpecialEquipmentList() != null) {
                List<ItemStack> list = special.getSpecialEquipmentList();
                for (int i = 0; i < list.size(); i++) {
                    ItemStack it = list.get(i);
                    String key;
                    String display;
                    if (it == special.getArrow()) {
                        key = "arrow";
                        display = "Pfeile";
                    } else if (it == special.getFireball()) {
                        key = "fireball";
                        display = "Feuerball";
                    } else {
                        key = "special" + i;
                        display = "Spezial " + i;
                    }
                    if (seen.add(key)) out.add(new Entry(key, display, Kind.SPECIAL, i, it));
                }
            }
        } catch (Throwable t) {
            plugin().getLogger().warning("Konnte MissileWars-Ausrüstung nicht lesen: " + t);
        }
        return out;
    }

    private static void addSchematics(List<Entry> out, Set<String> seen, List<SchematicObject> list, Kind kind) {
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            SchematicObject obj = list.get(i);
            String name;
            try {
                name = obj.getSchematicName(true);
            } catch (Throwable t) {
                name = obj.getDisplayName();
            }
            if (name == null) continue;
            name = name.replace(".schematic", "").replace(".schem", "");
            String key = normalize(name);
            if (kind == Kind.SHIELD && seen.contains(key)) key = "shield_" + key;
            if (!seen.add(key)) continue;
            String display;
            try {
                display = obj.getDisplayName();
                if (display == null || display.isEmpty() || display.contains("%")) display = name;
            } catch (Throwable t) {
                display = name;
            }
            ItemStack item = null;
            try {
                item = obj.getItem();
            } catch (Throwable ignored) {
            }
            out.add(new Entry(key, display, kind, i, item));
        }
    }

    static Entry find(Game game, String name) {
        String n = normalize(name);
        for (Entry e : entries(game)) {
            if (e.key.equals(n) || normalize(e.display).equals(n)) return e;
        }
        if (n.equals("pfeil") || n.equals("pfeile") || n.equals("arrows")) return find(game, "arrow");
        if (n.equals("feuerball") || n.equals("fire_charge")) return find(game, "fireball");
        return null;
    }

    /** Arena für ein Ziel: die Arena des Ziels, sonst die des Ausführenden, sonst die erste. */
    static Game gameFor(Player target, Player fallback) {
        Game g = target == null ? null : MWHook.gameOf(target);
        if (g == null && fallback != null) g = MWHook.gameOf(fallback);
        if (g == null) {
            try {
                for (Game any : GameManager.getInstance().getGames().values()) return any;
            } catch (Throwable ignored) {
            }
        }
        return g;
    }

    /** Namen für die Tab-Vervollständigung (alle Arenen). */
    static List<String> allKeys() {
        Set<String> keys = new LinkedHashSet<>();
        try {
            for (Game g : GameManager.getInstance().getGames().values()) {
                for (Entry e : entries(g)) keys.add(e.key);
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(keys);
    }

    // ------------------------------------------------------------------
    // Items geben
    // ------------------------------------------------------------------

    /** @return Fehlermeldung oder null */
    String give(Player target, Player sender, String name, int amount) {
        Game game = gameFor(target, sender);
        if (game == null) return "Keine MissileWars-Arena gefunden.";
        Entry e = find(game, name);
        if (e == null) return "Unbekanntes Item '" + name + "'. Möglich: " + String.join(", ", keys(game));
        if (e.item == null) return "MissileWars liefert für '" + name + "' kein Item.";
        int left = Math.max(1, Math.min(64 * 36, amount));
        while (left > 0) {
            ItemStack stack = e.item.clone();
            int n = Math.min(left, 64);
            stack.setAmount(n);
            target.getInventory().addItem(stack);
            left -= n;
        }
        return null;
    }

    static List<String> keys(Game game) {
        List<String> out = new ArrayList<>();
        for (Entry e : entries(game)) out.add(e.key);
        return out;
    }

    // ------------------------------------------------------------------
    // Nächste Items festlegen
    // ------------------------------------------------------------------

    /** @return Fehlermeldung oder null */
    String queue(Player target, Player sender, List<String> names) {
        Game game = MWHook.gameOf(target);
        if (game == null) return target.getName() + " ist in keinem MissileWars-Spiel.";
        List<String> keys = new ArrayList<>();
        for (String n : names) {
            if (n.trim().isEmpty()) continue;
            Entry e = find(game, n);
            if (e == null) return "Unbekanntes Item '" + n + "'. Möglich: " + String.join(", ", keys(game));
            keys.add(e.key);
        }
        String hook = install(target);
        if (hook != null) return hook;
        queues.computeIfAbsent(target.getUniqueId(), k -> new ArrayDeque<>()).addAll(keys);
        return null;
    }

    List<String> queued(Player p) {
        Deque<String> q = queues.get(p.getUniqueId());
        return q == null ? new ArrayList<>() : new ArrayList<>(q);
    }

    void clear(Player p) {
        queues.remove(p.getUniqueId());
    }

    void clear(UUID id) {
        queues.remove(id);
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = PlayerEquipmentRandomizer.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static PlayerEquipmentRandomizer randomizerOf(Player p) {
        Game g = MWHook.gameOf(p);
        if (g == null) return null;
        MWPlayer mw = g.getPlayer(p);
        return mw == null ? null : mw.getPlayerEquipmentRandomizer();
    }

    /** Hängt unseren "gezinkten" Zufallsgenerator in den MissileWars-Item-Verteiler des Spielers. */
    private String install(Player p) {
        try {
            PlayerEquipmentRandomizer r = randomizerOf(p);
            if (r == null) return p.getName() + " bekommt gerade keine MissileWars-Items (Spiel noch nicht gestartet?).";
            Field f = field("randomizer");
            Object current = f.get(r);
            if (current instanceof ForcedRandom && ((ForcedRandom) current).owner == r) return null;
            Random original = current instanceof ForcedRandom ? ((ForcedRandom) current).delegate : (Random) current;
            f.set(r, new ForcedRandom(this, p.getUniqueId(), r, original == null ? new Random() : original));
            hookStatus = "ok";
            return null;
        } catch (Throwable t) {
            hookStatus = "Fehler: " + t;
            plugin.getLogger().warning("Nächste-Items-Hook fehlgeschlagen: " + t);
            return "Diese MissileWars-/Java-Version erlaubt das Festlegen der nächsten Items nicht (siehe Konsole).";
        }
    }

    /** Läuft alle 5 Ticks: stellt sicher, dass MissileWars als nächstes die richtige Item-Art auswählt. */
    private void tick() {
        if (queues.isEmpty()) return;
        for (Map.Entry<UUID, Deque<String>> en : new ArrayList<>(queues.entrySet())) {
            if (en.getValue().isEmpty()) {
                queues.remove(en.getKey());
                continue;
            }
            Player p = null;
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getUniqueId().equals(en.getKey())) p = o;
            if (p == null) {
                queues.remove(en.getKey());
                continue;
            }
            try {
                Game g = MWHook.gameOf(p);
                if (g == null) continue;
                if (install(p) != null) continue;
                Entry next = find(g, en.getValue().peek());
                if (next == null) {
                    en.getValue().poll();
                    continue;
                }
                PlayerEquipmentRandomizer r = randomizerOf(p);
                Field counter = field("sendEquipmentCounter");
                int c = counter.getInt(r);
                if (next.kind == Kind.MISSILE && c >= 2) counter.setInt(r, 0);
                if (next.kind != Kind.MISSILE && c < 2) counter.setInt(r, 2);
            } catch (Throwable t) {
                hookStatus = "Fehler: " + t;
            }
        }
    }

    /** Wird von ForcedRandom aufgerufen, wenn MissileWars ein Item auswürfelt. */
    Integer forcedValue(UUID id, PlayerEquipmentRandomizer r, int origin, int bound) {
        Deque<String> q = queues.get(id);
        if (q == null || q.isEmpty()) return null;
        try {
            Player p = null;
            for (Player o : Bukkit.getOnlinePlayers()) if (o.getUniqueId().equals(id)) p = o;
            if (p == null) return null;
            Game g = MWHook.gameOf(p);
            Entry e = find(g, q.peek());
            if (e == null) {
                q.poll();
                return null;
            }
            int counter = field("sendEquipmentCounter").getInt(r);
            boolean missileBranch = counter < 2;
            int value;
            if (e.kind == Kind.MISSILE) {
                if (!missileBranch) return null;
                value = e.index + 1;
            } else {
                if (missileBranch) return null;
                int specialSize = g.getEquipmentManager().getSpecialEquipment().getSpecialEquipmentList().size();
                value = e.kind == Kind.SPECIAL ? e.index + 1 : specialSize + e.index + 1;
            }
            if (value < origin || value >= bound) return null;
            q.poll();
            if (q.isEmpty()) queues.remove(id);
            return value;
        } catch (Throwable t) {
            hookStatus = "Fehler: " + t;
            return null;
        }
    }

    /** Zufallsgenerator, der bei Bedarf einen festgelegten Wert liefert. */
    static final class ForcedRandom extends Random {
        private static final long serialVersionUID = 1L;
        final transient Equipment equipment;
        final UUID player;
        final transient PlayerEquipmentRandomizer owner;
        final Random delegate;

        ForcedRandom(Equipment equipment, UUID player, PlayerEquipmentRandomizer owner, Random delegate) {
            this.equipment = equipment;
            this.player = player;
            this.owner = owner;
            this.delegate = delegate;
        }

        @Override
        public int nextInt(int origin, int bound) {
            Integer forced = equipment == null ? null : equipment.forcedValue(player, owner, origin, bound);
            if (forced != null) return forced;
            return delegate.nextInt(origin, bound);
        }

        @Override
        public int nextInt(int bound) {
            return delegate.nextInt(bound);
        }

        @Override
        public int nextInt() {
            return delegate == null ? super.nextInt() : delegate.nextInt();
        }

        @Override
        public double nextDouble() {
            return delegate == null ? super.nextDouble() : delegate.nextDouble();
        }
    }

    private static MWTroll plugin() {
        return MWTroll.instance;
    }
}
