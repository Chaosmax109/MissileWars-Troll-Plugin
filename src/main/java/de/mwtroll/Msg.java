package de.mwtroll;

import org.bukkit.command.CommandSender;

final class Msg {

    static String prefix = "&8[&cTroll&8] &7";

    private Msg() {
    }

    static String c(String s) {
        if (s == null) return "";
        return s.replaceAll("&([0-9a-fk-orA-FK-OR])", "§$1");
    }

    static void send(CommandSender to, String message) {
        to.sendMessage(c(prefix + message));
    }

    static void raw(CommandSender to, String message) {
        to.sendMessage(c(message));
    }
}
