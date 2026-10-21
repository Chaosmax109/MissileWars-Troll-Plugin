package de.mwtroll;

import de.butzlabben.missilewars.events.GameStopEvent;
import de.butzlabben.missilewars.events.PlayerArenaLeaveEvent;
import de.butzlabben.missilewars.game.Game;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class Listeners implements Listener {

    private static final List<String> MW_LABELS = Arrays.asList(
            "mw", "missilewars", "missilewars:mw", "missilewars:missilewars");

    private final MWTroll plugin;

    Listeners(MWTroll plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // "/mw troll ..." abfangen, bevor MissileWars den Befehl bekommt
    // ------------------------------------------------------------------

    /** @return true, wenn der Befehl ein "/mw troll"-Befehl war und behandelt wurde */
    private boolean intercept(CommandSender sender, String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2) return false;
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (!MW_LABELS.contains(label)) return false;
        if (!parts[1].equalsIgnoreCase("troll")) return false;
        String[] args = Arrays.copyOfRange(parts, 2, parts.length);
        plugin.command().handle(sender, "/" + parts[0], args);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent e) {
        if (e.isCancelled()) return;
        if (intercept(e.getPlayer(), e.getMessage())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onConsoleCommand(ServerCommandEvent e) {
        if (e.isCancelled()) return;
        if (intercept(e.getSender(), e.getCommand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTab(TabCompleteEvent e) {
        String buffer = e.getBuffer();
        if (buffer == null) return;
        String b = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        String[] parts = b.split(" ", -1);
        if (parts.length < 2 || !MW_LABELS.contains(parts[0].toLowerCase(Locale.ROOT))) return;
        CommandSender sender = e.getSender();
        if (!sender.hasPermission("mwtroll.use")) return;
        if (parts.length == 2) {
            if ("troll".startsWith(parts[1].toLowerCase(Locale.ROOT))) {
                List<String> list = new ArrayList<>(e.getCompletions() == null ? new ArrayList<>() : e.getCompletions());
                if (!list.contains("troll")) list.add("troll");
                e.setCompletions(list);
            }
            return;
        }
        if (!parts[1].equalsIgnoreCase("troll")) return;
        e.setCompletions(plugin.command().tab(sender, Arrays.copyOfRange(parts, 2, parts.length)));
    }

    // ------------------------------------------------------------------
    // Effekte
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!plugin.trolls().isFrozen(p)) return;
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getZ() == to.getZ() && to.getY() <= from.getY()) return;
        Location fixed = from.clone();
        fixed.setYaw(to.getYaw());
        fixed.setPitch(to.getPitch());
        // nach unten fallen ist erlaubt, damit niemand in der Luft hängt
        if (to.getY() < from.getY()) fixed.setY(to.getY());
        e.setTo(fixed);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player)) return;
        if (plugin.trolls().hasNoFall((Player) e.getEntity())) {
            e.setCancelled(true);
            e.getEntity().setFallDistance(0f);
        }
    }

    // ------------------------------------------------------------------
    // Aufräumen
    // ------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.trolls().stopAll(e.getPlayer());
    }

    @EventHandler
    public void onArenaLeave(PlayerArenaLeaveEvent e) {
        if (plugin.resetOnLeave()) plugin.trolls().stopAll(e.getPlayer());
    }

    @EventHandler
    public void onGameStop(GameStopEvent e) {
        if (!plugin.resetOnGameEnd()) return;
        Game g = MWHook.gameFromEvent(e);
        if (g == null) return;
        for (Player p : MWHook.playersOf(g)) plugin.trolls().stopAll(p);
    }
}
