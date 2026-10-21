package de.mwtroll;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tab-Vervollständigung für "/mw troll ..." auf Paper (überschreibt die Vorschläge von MissileWars).
 */
final class PaperTabListener implements Listener {

    private static final List<String> MW_LABELS = Arrays.asList(
            "mw", "missilewars", "missilewars:mw", "missilewars:missilewars");

    private final MWTroll plugin;

    PaperTabListener(MWTroll plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTab(AsyncTabCompleteEvent e) {
        try {
            String buffer = e.getBuffer();
            if (buffer == null) return;
            String b = buffer.startsWith("/") ? buffer.substring(1) : buffer;
            String[] parts = b.split(" ", -1);
            if (parts.length < 3) return;
            if (!MW_LABELS.contains(parts[0].toLowerCase(Locale.ROOT))) return;
            if (!parts[1].equalsIgnoreCase("troll")) return;
            if (!e.getSender().hasPermission("mwtroll.use")) return;
            e.setCompletions(plugin.command().tab(e.getSender(), Arrays.copyOfRange(parts, 2, parts.length)));
            e.setHandled(true);
        } catch (Throwable ignored) {
        }
    }
}
