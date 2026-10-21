package de.mwtroll;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.command.CommandSender;

/**
 * Hängt "troll" als echten Unterbefehl an den MissileWars-Befehl /mw (ACF).
 * Wird nur geladen, wenn MissileWars ACF mitliefert.
 */
@CommandAlias("mw|missilewars")
public class AcfTroll extends BaseCommand {

    private final MWTroll plugin;

    public AcfTroll(MWTroll plugin) {
        this.plugin = plugin;
    }

    @Subcommand("troll")
    @Description("Troll-Menü und Troll-Befehle (MissileWarsTroll)")
    public void troll(CommandSender sender, String[] args) {
        plugin.command().handle(sender, "/mw", args);
    }
}
