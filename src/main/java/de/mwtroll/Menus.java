package de.mwtroll;

import de.butzlabben.missilewars.game.Game;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Die Troll-GUIs: Ziel wählen → Troll wählen → (Einstellungen / Raketen geben / nächste Items).
 */
final class Menus implements Listener {

    enum Type { TARGETS, TROLLS, SETTINGS, GIVE, NEXT }

    static final class Holder implements InventoryHolder {
        final Type type;
        final String targetArg;
        final String targetLabel;
        final int page;
        final Map<Integer, Object> actions = new HashMap<>();
        Inventory inventory;

        Holder(Type type, String targetArg, String targetLabel, int page) {
            this.type = type;
            this.targetArg = targetArg;
            this.targetLabel = targetLabel;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int TARGETS_PER_PAGE = 45;
    private static final int TROLLS_PER_PAGE = 36;
    private static final double STEP = 0.25;

    private final MWTroll plugin;
    /** Im Menü gewählte Dauer; nicht vorhanden = Standard-Dauer des jeweiligen Trolls. */
    private final Map<UUID, Integer> durations = new HashMap<>();

    Menus(MWTroll plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Helfer
    // ------------------------------------------------------------------

    static ItemStack item(String[] materials, String name, List<String> lore) {
        ItemStack stack = Compat.item(materials);
        if (stack == null) return null;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                try {
                    meta.setDisplayName(Msg.c("&r" + name));
                } catch (Throwable ignored) {
                }
                try {
                    List<String> l = new ArrayList<>();
                    for (String s : lore) l.add(Msg.c(s));
                    meta.setLore(l);
                } catch (Throwable ignored) {
                }
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
        }
        return stack;
    }

    static ItemStack item(String material, String name, String... lore) {
        return item(new String[]{material}, name, java.util.Arrays.asList(lore));
    }

    private static ItemStack head(Player owner, String name, List<String> lore) {
        ItemStack stack = item(new String[]{"PLAYER_HEAD", "SKELETON_SKULL"}, name, lore);
        if (stack == null) return null;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta instanceof SkullMeta) {
                ((SkullMeta) meta).setOwningPlayer(owner);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
        }
        return stack;
    }

    /** Kopie eines MissileWars-Items mit zusätzlicher Lore. */
    private static ItemStack withLore(ItemStack base, String fallbackMaterial, String name, List<String> lore) {
        if (base == null) return item(new String[]{fallbackMaterial}, name, lore);
        ItemStack stack = base.clone();
        try {
            stack.setAmount(1);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                try {
                    if (!meta.hasDisplayName()) meta.setDisplayName(Msg.c("&r" + name));
                } catch (Throwable ignored) {
                }
                List<String> l = new ArrayList<>();
                for (String s : lore) l.add(Msg.c(s));
                meta.setLore(l);
                stack.setItemMeta(meta);
            }
        } catch (Throwable ignored) {
        }
        return stack;
    }

    private static void put(Holder h, int slot, ItemStack stack, Object action) {
        if (stack == null) return;
        h.inventory.setItem(slot, stack);
        if (action != null) h.actions.put(slot, action);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        s = Compat.stripColors(s);
        return s.length() > 22 ? s.substring(0, 22) + ".." : s;
    }

    private void safe(Player viewer, String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Fehler beim Öffnen von " + what, t);
            Msg.send(viewer, "&cMenü-Fehler: &7" + t + " &8(Details in der Konsole, /mw troll debug)");
        }
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private Holder create(Type type, String targetArg, String label, int page, String title) {
        Holder h = new Holder(type, targetArg, label, page);
        h.inventory = Compat.createInventory(h, 54, Msg.c(title));
        return h;
    }

    // ------------------------------------------------------------------
    // Menü 1: Ziel auswählen
    // ------------------------------------------------------------------

    void openTargets(Player viewer, int page) {
        safe(viewer, "Zielauswahl", () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
            int pages = Math.max(1, (players.size() + TARGETS_PER_PAGE - 1) / TARGETS_PER_PAGE);
            int pg = Math.max(0, Math.min(page, pages - 1));

            Holder h = create(Type.TARGETS, null, null, pg, "&c&lTroll &8» &7Ziel wählen");
            int slot = 0;
            for (int i = pg * TARGETS_PER_PAGE; i < Math.min(players.size(), (pg + 1) * TARGETS_PER_PAGE); i++) {
                Player p = players.get(i);
                List<String> lore = new ArrayList<>();
                String team = MWHook.teamName(p);
                if (team != null) lore.add("&7Team: " + team);
                if (Targets.isProtected(p, viewer)) lore.add("&cGeschützt (mwtroll.bypass)");
                lore.add("&eKlicken zum Auswählen");
                put(h, slot++, head(p, "&e" + p.getName(), lore), "player:" + p.getName());
            }

            if (viewer.hasPermission("mwtroll.group")) {
                put(h, 45, item("NETHER_STAR", "&b&lAlle Spieler", "&7Alle Spieler auf dem Server", "&8@a"), "target:@a");
                put(h, 46, item("TNT", "&c&lAlle in MissileWars", "&7Alle Spieler in allen Arenen", "&8@ingame"), "target:@ingame");
                put(h, 47, item("IRON_SWORD", "&6&lMein Spiel", "&7Alle Spieler in deiner Arena", "&8@game"), "target:@game");
                put(h, 51, item(new String[]{"RED_WOOL", "TNT"}, "&4&lGegnerteam", list("&7Alle Spieler im gegnerischen Team", "&8@enemy")), "target:@enemy");
                put(h, 52, item(new String[]{"LIME_WOOL", "EMERALD"}, "&a&lMein Team", list("&7Alle Spieler in deinem Team", "&8@team")), "target:@team");
                if (viewer.hasPermission("mwtroll.stop")) {
                    put(h, 53, item("MILK_BUCKET", "&f&lAlles zurücksetzen", "&7Entfernt alle Trolls und", "&7Einstellungen bei allen Spielern."), "stopall");
                }
            }
            if (pg > 0) put(h, 48, item("ARROW", "&7« Vorherige Seite"), "page:" + (pg - 1));
            put(h, 49, item("BARRIER", "&cSchließen"), "close");
            if (pg < pages - 1) put(h, 50, item("ARROW", "&7Nächste Seite »"), "page:" + (pg + 1));
            viewer.openInventory(h.inventory);
        });
    }

    // ------------------------------------------------------------------
    // Menü 2: Troll auswählen
    // ------------------------------------------------------------------

    void openTrolls(Player viewer, String targetArg, String label, int page) {
        safe(viewer, "Troll-Menü", () -> {
            Holder h = create(Type.TROLLS, targetArg, label, page, "&c&lTroll &8» &7" + shorten(label));
            fillTrolls(viewer, h);
            viewer.openInventory(h.inventory);
        });
    }

    private String durationText(Player viewer) {
        Integer d = durations.get(viewer.getUniqueId());
        return d == null ? "Standard" : d + "s";
    }

    private void fillTrolls(Player viewer, Holder h) {
        h.inventory.clear();
        h.actions.clear();
        Integer chosen = durations.get(viewer.getUniqueId());

        List<TrollRegistry.TrollDef> visible = new ArrayList<>();
        for (TrollRegistry.TrollDef d : plugin.registry().all()) {
            if (plugin.registry().canUse(viewer, d)) visible.add(d);
        }
        int pages = Math.max(1, (visible.size() + TROLLS_PER_PAGE - 1) / TROLLS_PER_PAGE);
        int pg = Math.max(0, Math.min(h.page, pages - 1));

        int slot = 0;
        for (int i = pg * TROLLS_PER_PAGE; i < Math.min(visible.size(), (pg + 1) * TROLLS_PER_PAGE); i++) {
            TrollRegistry.TrollDef d = visible.get(i);
            List<String> lore = new ArrayList<>(d.description);
            if (d.timed()) lore.add("&8Dauer: &e" + (chosen != null ? chosen : d.duration) + "s");
            else lore.add(chosen != null ? "&8Sofort &7(Dauer " + chosen + "s für Effekte)" : "&8Sofort");
            lore.add("&8Permission: mwtroll.troll." + d.id);
            lore.add("");
            lore.add("&eKlicken zum Ausführen");
            put(h, slot++, item(d.icon, d.name, lore), d);
        }

        TrollRegistry.SpecialDef rocket = plugin.registry().special(TrollRegistry.SPECIAL_ROCKET);
        if (plugin.registry().canUseSpecial(viewer, TrollRegistry.SPECIAL_ROCKET)) {
            List<String> l = new ArrayList<>(rocket.description);
            l.add("");
            l.add("&eKlicken zum Öffnen");
            put(h, 37, item(rocket.icon, rocket.name, l), "give");
        }
        TrollRegistry.SpecialDef next = plugin.registry().special(TrollRegistry.SPECIAL_NEXT);
        if (plugin.registry().canUseSpecial(viewer, TrollRegistry.SPECIAL_NEXT)) {
            List<String> l = new ArrayList<>(next.description);
            l.add("");
            l.add("&eKlicken zum Öffnen");
            put(h, 39, item(next.icon, next.name, l), "next");
        }
        put(h, 41, item("NAME_TAG", "&7Ziel: &e" + h.targetLabel, "&8" + h.targetArg), null);

        put(h, 45, item("ARROW", "&7« Zurück zur Zielauswahl"), "back");
        if (pg > 0) put(h, 46, item("ARROW", "&7« Vorherige Seite"), "tpage:" + (pg - 1));
        put(h, 47, item("CLOCK", "&eDauer: &f" + durationText(viewer),
                "&7Standard = Dauer aus der trolls.yml",
                "&aLinksklick &7+5s  &cRechtsklick &7-5s",
                "&aShift-Links &7+60s  &eShift-Rechts &7Standard"), "duration");
        if (viewer.hasPermission("mwtroll.set")) {
            put(h, 49, item("COMPARATOR", "&b&lEinstellungen",
                    "&7Speed, Sprungkraft, Größe, Schwerkraft,",
                    "&7Leuchten, Unsichtbar, Einfrieren", "&7dauerhaft für das Ziel setzen."), "settings");
        }
        if (viewer.hasPermission("mwtroll.stop")) {
            put(h, 51, item("MILK_BUCKET", "&f&lZurücksetzen", "&7Entfernt alle Trolls und", "&7Einstellungen beim Ziel."), "stop");
        }
        if (pg < pages - 1) put(h, 52, item("ARROW", "&7Nächste Seite »"), "tpage:" + (pg + 1));
        put(h, 53, item("BARRIER", "&cSchließen"), "close");
    }

    // ------------------------------------------------------------------
    // Menü 3: Einstellungen
    // ------------------------------------------------------------------

    void openSettings(Player viewer, String targetArg, String label) {
        safe(viewer, "Einstellungen", () -> {
            Holder h = create(Type.SETTINGS, targetArg, label, 0, "&b&lEinstellungen &8» &7" + shorten(label));
            fillSettings(viewer, h);
            viewer.openInventory(h.inventory);
        });
    }

    private void fillSettings(Player viewer, Holder h) {
        h.inventory.clear();
        h.actions.clear();
        Targets.Result r = Targets.resolve(viewer, h.targetArg, plugin.includeSelf());
        Player ref = r.players == null || r.players.isEmpty() ? null : r.players.get(0);

        String[][] numeric = {
                {"speed", "Geschwindigkeit", "SUGAR"},
                {"jump", "Sprungkraft", "RABBIT_FOOT"},
                {"size", "Größe", "PUFFERFISH"},
                {"gravity", "Schwerkraft", "ANVIL"}
        };
        int[] numSlots = {10, 12, 14, 16};
        for (int i = 0; i < numeric.length; i++) {
            String key = numeric[i][0];
            double v = ref == null ? 1.0 : plugin.trolls().numericSetting(ref, key);
            put(h, numSlots[i], item(numeric[i][2], "&b" + numeric[i][1] + " &8(" + key + ")",
                    "&7Aktuell: &e" + TrollManager.trim(v) + "x", "",
                    "&aLinksklick &7+" + STEP, "&cRechtsklick &7-" + STEP,
                    "&eShift-Klick &7zurücksetzen (1.0)"), "num:" + key);
        }
        String[][] toggles = {
                {"glow", "Leuchten", "GLOWSTONE_DUST"},
                {"invisible", "Unsichtbar", "GLASS"},
                {"freeze", "Einfrieren", "ICE"}
        };
        int[] togSlots = {29, 31, 33};
        for (int i = 0; i < toggles.length; i++) {
            String key = toggles[i][0];
            boolean on = ref != null && plugin.trolls().boolSetting(ref, key);
            put(h, togSlots[i], item(toggles[i][2], "&b" + toggles[i][1] + " &8(" + key + ")",
                    "&7Aktuell: " + (on ? "&aan" : "&caus"), "", "&eKlicken zum Umschalten"), "tog:" + key);
        }
        put(h, 45, item("ARROW", "&7« Zurück zu den Trolls"), "backtrolls");
        if (viewer.hasPermission("mwtroll.stop")) {
            put(h, 49, item("MILK_BUCKET", "&f&lAlles zurücksetzen", "&7Entfernt alle Trolls und", "&7Einstellungen beim Ziel."), "stop");
        }
        put(h, 53, item("BARRIER", "&cSchließen"), "close");
        put(h, 4, item("NAME_TAG", "&7Ziel: &e" + h.targetLabel, "&8" + h.targetArg,
                ref != null && r.players.size() > 1 ? "&7Angezeigt: Werte von " + ref.getName() : ""), null);
    }

    // ------------------------------------------------------------------
    // Menü 4/5: Raketen geben / nächste Items
    // ------------------------------------------------------------------

    void openEquipment(Player viewer, String targetArg, String label, Type type) {
        safe(viewer, "Ausrüstungs-Menü", () -> {
            String title = type == Type.GIVE ? "&c&lRaketen geben &8» &7" : "&d&lNächste Items &8» &7";
            Holder h = create(type, targetArg, label, 0, title + shorten(label));
            fillEquipment(viewer, h);
            viewer.openInventory(h.inventory);
        });
    }

    private Player firstTarget(Player viewer, String targetArg) {
        Targets.Result r = Targets.resolve(viewer, targetArg, plugin.includeSelf());
        return r.players == null || r.players.isEmpty() ? null : r.players.get(0);
    }

    private void fillEquipment(Player viewer, Holder h) {
        h.inventory.clear();
        h.actions.clear();
        Player ref = firstTarget(viewer, h.targetArg);
        Game game = Equipment.gameFor(ref, viewer);
        List<Equipment.Entry> entries = Equipment.entries(game);
        int slot = 0;
        for (Equipment.Entry e : entries) {
            if (slot >= 45) break;
            String kind = e.kind == Equipment.Kind.MISSILE ? "Rakete" : e.kind == Equipment.Kind.SHIELD ? "Schild" : "Spezial";
            List<String> lore = new ArrayList<>();
            lore.add("&7" + kind + " &8(" + e.key + ")");
            lore.add("");
            if (h.type == Type.GIVE) {
                lore.add("&aLinksklick &71x geben");
                lore.add("&aRechtsklick &75x geben");
                lore.add("&aShift-Klick &716x geben");
            } else {
                lore.add("&eKlicken &7= als nächstes Item anhängen");
            }
            String fallback = e.kind == Equipment.Kind.MISSILE ? "CREEPER_SPAWN_EGG" : e.kind == Equipment.Kind.SHIELD ? "SNOWBALL" : "ARROW";
            put(h, slot++, withLore(e.item, fallback, "&e" + Compat.stripColors(e.display), lore), "eq:" + e.key);
        }
        if (entries.isEmpty()) {
            put(h, 22, item("BARRIER", "&cKeine MissileWars-Ausrüstung gefunden",
                    "&7Ist eine Arena geladen?"), null);
        }

        if (h.type == Type.NEXT && ref != null) {
            List<String> lore = new ArrayList<>();
            List<String> q = plugin.equipment().queued(ref);
            if (q.isEmpty()) lore.add("&8(nichts festgelegt)");
            int n = 1;
            for (String k : q) lore.add("&7" + (n++) + ". &f" + k);
            if (Targets.isGroup(h.targetArg)) lore.add("&8Angezeigt: " + ref.getName());
            put(h, 49, item(new String[]{"BOOK"}, "&d&lWarteschlange", lore), null);
            put(h, 51, item("LAVA_BUCKET", "&cWarteschlange leeren"), "nextclear");
        }
        put(h, 45, item("ARROW", "&7« Zurück zu den Trolls"), "backtrolls");
        put(h, 47, item("NAME_TAG", "&7Ziel: &e" + h.targetLabel, "&8" + h.targetArg), null);
        put(h, 53, item("BARRIER", "&cSchließen"), "close");
    }

    // ------------------------------------------------------------------
    // Klicks
    // ------------------------------------------------------------------

    /**
     * MissileWars blockiert in Lobby und Arena jedes Inventar außer dem Spielerinventar.
     * Unser Troll-Menü wird hier wieder freigegeben (läuft nach MissileWars).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent e) {
        try {
            if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(false);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        try {
            if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
        } catch (Throwable ignored) {
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top;
        try {
            top = e.getInventory();
            if (!(top.getHolder() instanceof Holder)) return;
        } catch (Throwable t) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player viewer = (Player) e.getWhoClicked();
        try {
            click(e, viewer, (Holder) top.getHolder(), top);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Fehler bei Klick im Troll-Menü", t);
            Msg.send(viewer, "&cMenü-Fehler: &7" + t + " &8(Details in der Konsole)");
        }
    }

    private void click(InventoryClickEvent e, Player viewer, Holder h, Inventory top) {
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        Object action = h.actions.get(slot);
        if (action == null) return;

        if (action instanceof TrollRegistry.TrollDef) {
            plugin.command().troll(viewer, (TrollRegistry.TrollDef) action, h.targetArg, durations.get(viewer.getUniqueId()));
            return;
        }

        String a = (String) action;
        if (a.equals("close")) {
            later(viewer::closeInventory);
        } else if (a.startsWith("page:")) {
            int page = Integer.parseInt(a.substring(5));
            later(() -> openTargets(viewer, page));
        } else if (a.startsWith("tpage:")) {
            int page = Integer.parseInt(a.substring(6));
            later(() -> openTrolls(viewer, h.targetArg, h.targetLabel, page));
        } else if (a.startsWith("player:")) {
            String name = a.substring(7);
            later(() -> openTrolls(viewer, name, name, 0));
        } else if (a.startsWith("target:")) {
            String sel = a.substring(7);
            Targets.Result r = Targets.resolve(viewer, sel, plugin.includeSelf());
            if (r.error != null) {
                Msg.send(viewer, "&c" + r.error);
                return;
            }
            later(() -> openTrolls(viewer, sel, r.label, 0));
        } else if (a.equals("stopall")) {
            plugin.command().stop(viewer, "@a");
        } else if (a.equals("back")) {
            later(() -> openTargets(viewer, 0));
        } else if (a.equals("backtrolls")) {
            later(() -> openTrolls(viewer, h.targetArg, h.targetLabel, 0));
        } else if (a.equals("duration")) {
            UUID id = viewer.getUniqueId();
            if (e.isShiftClick() && e.isRightClick()) {
                durations.remove(id);
            } else {
                int cur = durations.getOrDefault(id, plugin.defaultDuration());
                int step = e.isShiftClick() ? 60 : 5;
                int d = cur + (e.isRightClick() ? -step : step);
                durations.put(id, Math.max(5, Math.min(plugin.maxDuration(), d)));
            }
            fillTrolls(viewer, h);
        } else if (a.equals("settings")) {
            later(() -> openSettings(viewer, h.targetArg, h.targetLabel));
        } else if (a.equals("give")) {
            later(() -> openEquipment(viewer, h.targetArg, h.targetLabel, Type.GIVE));
        } else if (a.equals("next")) {
            later(() -> openEquipment(viewer, h.targetArg, h.targetLabel, Type.NEXT));
        } else if (a.equals("nextclear")) {
            plugin.command().next(viewer, h.targetArg, "clear");
            fillEquipment(viewer, h);
        } else if (a.startsWith("eq:")) {
            String key = a.substring(3);
            if (h.type == Type.GIVE) {
                int amount = e.isShiftClick() ? 16 : e.isRightClick() ? 5 : 1;
                plugin.command().rocket(viewer, h.targetArg, key, amount);
            } else {
                plugin.command().next(viewer, h.targetArg, key);
                fillEquipment(viewer, h);
            }
        } else if (a.equals("stop")) {
            plugin.command().stop(viewer, h.targetArg);
            if (h.type == Type.SETTINGS) fillSettings(viewer, h);
        } else if (a.startsWith("num:")) {
            String key = a.substring(4);
            String value;
            if (e.isShiftClick()) {
                value = "reset";
            } else {
                Player ref = firstTarget(viewer, h.targetArg);
                if (ref == null) {
                    Msg.send(viewer, "&cZiel nicht mehr verfügbar.");
                    return;
                }
                double cur = plugin.trolls().numericSetting(ref, key);
                value = TrollManager.trim(cur + (e.isRightClick() ? -STEP : STEP));
            }
            plugin.command().set(viewer, h.targetArg, key, value);
            fillSettings(viewer, h);
        } else if (a.startsWith("tog:")) {
            String key = a.substring(4);
            Player ref = firstTarget(viewer, h.targetArg);
            if (ref == null) {
                Msg.send(viewer, "&cZiel nicht mehr verfügbar.");
                return;
            }
            boolean cur = plugin.trolls().boolSetting(ref, key);
            plugin.command().set(viewer, h.targetArg, key, cur ? "off" : "on");
            fillSettings(viewer, h);
        }
    }

    static List<String> list(String... s) {
        return java.util.Arrays.asList(s);
    }
}
