package de.mwtroll;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Kompatibilitätsschicht: alles, was sich zwischen Paper-Versionen (1.21.x bis 26.x)
 * geändert haben kann, wird hier per Reflection mit mehreren Fallbacks gelöst.
 */
final class Compat {

    /** Welche Methode zuletzt erfolgreich Items erzeugt hat (für /mw troll debug). */
    static String lastItemMethod = "-";
    static String lastItemError = "-";

    private Compat() {
    }

    static Object staticField(Class<?> owner, String... names) {
        for (String n : names) {
            try {
                return owner.getField(n).get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    static Particle particle(String... names) {
        try {
            Object o = staticField(Particle.class, names);
            return o instanceof Particle ? (Particle) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static PotionEffectType potion(String... names) {
        try {
            String[] upper = new String[names.length];
            for (int i = 0; i < names.length; i++) upper[i] = names[i].toUpperCase(Locale.ROOT);
            Object o = staticField(PotionEffectType.class, upper);
            return o instanceof PotionEffectType ? (PotionEffectType) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static Attribute attribute(String... names) {
        try {
            Object o = staticField(Attribute.class, names);
            return o instanceof Attribute ? (Attribute) o : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------

    /** Erzeugt ein Item aus dem ersten gültigen Material-Namen; nie null (Fallback: Stein/Papier). */
    static ItemStack item(String... names) {
        for (String n : names) {
            ItemStack s = single(n);
            if (s != null) return s;
        }
        for (String n : new String[]{"STONE", "PAPER", "DIRT"}) {
            ItemStack s = single(n);
            if (s != null) return s;
        }
        return null;
    }

    private static ItemStack single(String name) {
        if (name == null || name.isEmpty()) return null;
        String upper = name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
        // 1) Material.matchMaterial + ItemStack.of / Konstruktor
        try {
            Class<?> matClass = Class.forName("org.bukkit.Material");
            Object mat = matClass.getMethod("matchMaterial", String.class).invoke(null, upper);
            if (mat != null) {
                try {
                    Method of = ItemStack.class.getMethod("of", matClass, int.class);
                    ItemStack s = (ItemStack) of.invoke(null, mat, 1);
                    if (s != null) {
                        lastItemMethod = "ItemStack.of(Material)";
                        return s;
                    }
                } catch (Throwable t) {
                    lastItemError = t.toString();
                }
                try {
                    Constructor<ItemStack> c = ItemStack.class.getConstructor(matClass, int.class);
                    ItemStack s = c.newInstance(mat, 1);
                    lastItemMethod = "new ItemStack(Material)";
                    return s;
                } catch (Throwable t) {
                    lastItemError = t.toString();
                }
            }
        } catch (Throwable t) {
            lastItemError = t.toString();
        }
        // 2) ItemFactory#createItemStack("minecraft:xyz") – nutzt den Vanilla-Parser
        try {
            Object factory = Bukkit.class.getMethod("getItemFactory").invoke(null);
            Method create = factory.getClass().getMethod("createItemStack", String.class);
            ItemStack s = (ItemStack) create.invoke(factory, "minecraft:" + upper.toLowerCase(Locale.ROOT));
            if (s != null) {
                lastItemMethod = "ItemFactory.createItemStack";
                return s;
            }
        } catch (Throwable t) {
            lastItemError = t.toString();
        }
        return null;
    }

    static String typeName(ItemStack stack) {
        if (stack == null) return "AIR";
        try {
            Object type = ItemStack.class.getMethod("getType").invoke(stack);
            return String.valueOf(type).toUpperCase(Locale.ROOT);
        } catch (Throwable t) {
            return "?";
        }
    }

    static boolean isAir(ItemStack stack) {
        if (stack == null) return true;
        try {
            if (stack.getAmount() <= 0) return true;
        } catch (Throwable ignored) {
        }
        return typeName(stack).contains("AIR");
    }

    static Inventory createInventory(InventoryHolder holder, int size, String title) {
        try {
            return Bukkit.createInventory(holder, size, title);
        } catch (Throwable e) {
            return Bukkit.createInventory(holder, size);
        }
    }

    /** Schickt eine Actionbar-Nachricht, falls die Serverversion das per String kann. */
    static void actionBar(org.bukkit.entity.Player p, String text) {
        try {
            Method m = p.getClass().getMethod("sendActionBar", String.class);
            m.invoke(p, text);
        } catch (Throwable t) {
            p.sendMessage(text);
        }
    }

    static String stripColors(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[§&][0-9a-fk-orx]", "");
    }
}
