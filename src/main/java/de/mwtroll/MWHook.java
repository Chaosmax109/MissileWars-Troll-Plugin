package de.mwtroll;

import de.butzlabben.missilewars.events.GameEvent;
import de.butzlabben.missilewars.game.Game;
import de.butzlabben.missilewars.game.GameManager;
import de.butzlabben.missilewars.game.Team;
import de.butzlabben.missilewars.player.MWPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Zugriff auf MissileWars (Spiele, Teams). Alle Aufrufe sind abgesichert,
 * damit eine geänderte MissileWars-Version das Add-on nicht crasht.
 */
final class MWHook {

    private MWHook() {
    }

    static Game gameOf(Player p) {
        try {
            for (Game g : GameManager.getInstance().getGames().values()) {
                if (g.getPlayers().containsKey(p.getUniqueId())) return g;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static String gameName(Game g) {
        try {
            for (java.util.Map.Entry<String, Game> e : GameManager.getInstance().getGames().entrySet()) {
                if (e.getValue() == g) return e.getKey();
            }
        } catch (Throwable ignored) {
        }
        return "?";
    }

    static List<Player> playersOf(Game g) {
        List<Player> out = new ArrayList<>();
        if (g == null) return out;
        try {
            for (UUID id : g.getPlayers().keySet()) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getUniqueId().equals(id)) out.add(p);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static List<Player> allIngame() {
        List<Player> out = new ArrayList<>();
        try {
            for (Game g : GameManager.getInstance().getGames().values()) {
                out.addAll(playersOf(g));
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static Team teamOf(Player p) {
        try {
            Game g = gameOf(p);
            if (g == null) return null;
            MWPlayer mw = g.getPlayer(p);
            return mw == null ? null : mw.getTeam();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static String teamName(Player p) {
        try {
            Team t = teamOf(p);
            if (t == null) return null;
            String color = t.getColorCode();
            return (color == null ? "" : color) + t.getName();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static List<Player> enemiesOf(Player p) {
        List<Player> out = new ArrayList<>();
        try {
            Team t = teamOf(p);
            if (t == null) return out;
            Team enemy = t.getEnemyTeam();
            if (enemy == null) return out;
            for (MWPlayer mw : enemy.getMembers()) {
                Player pl = mw.getPlayer();
                if (pl != null && pl.isOnline()) out.add(pl);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static List<Player> teamOfPlayers(Player p) {
        List<Player> out = new ArrayList<>();
        try {
            Team t = teamOf(p);
            if (t == null) return out;
            for (MWPlayer mw : t.getMembers()) {
                Player pl = mw.getPlayer();
                if (pl != null && pl.isOnline()) out.add(pl);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Liest das (private) Game-Feld aus einem MissileWars-GameEvent. */
    static Game gameFromEvent(GameEvent event) {
        try {
            Field f = GameEvent.class.getDeclaredField("game");
            f.setAccessible(true);
            return (Game) f.get(event);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static List<String> gameNames() {
        try {
            return new ArrayList<>(GameManager.getInstance().getGames().keySet());
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    static Game gameByName(String name) {
        try {
            for (java.util.Map.Entry<String, Game> e : GameManager.getInstance().getGames().entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
