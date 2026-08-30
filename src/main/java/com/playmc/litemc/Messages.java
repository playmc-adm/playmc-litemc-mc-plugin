package com.playmc.litemc;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reads message strings out of config.yml and renders them with MiniMessage,
 * the same tag syntax the SimpleChat formats already use.
 *
 * <p>%placeholders% are substituted before the tags are parsed, so callers
 * must only pass values they trust; {@link com.playmc.litemc.command.GrantCommand}
 * strips everything but name characters from player input first.
 */
public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sends a single message, or logs a warning if config.yml has no such key. */
    public void send(CommandSender to, String path, Map<String, String> placeholders) {
        String raw = plugin.getConfig().getString(path);
        if (raw == null) {
            plugin.getLogger().warning("config.yml has no message at " + path);
            return;
        }
        to.sendMessage(render(raw, placeholders));
    }

    /** Sends every line of a message list, in order. */
    public void sendList(CommandSender to, String path, Map<String, String> placeholders) {
        List<String> lines = plugin.getConfig().getStringList(path);
        if (lines.isEmpty()) {
            plugin.getLogger().warning("config.yml has no message list at " + path);
            return;
        }
        for (String line : lines) {
            to.sendMessage(render(line, placeholders));
        }
    }

    private Component render(String raw, Map<String, String> placeholders) {
        String text = raw;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            text = text.replace("%" + placeholder.getKey() + "%", placeholder.getValue());
        }
        return miniMessage.deserialize(text);
    }
}
