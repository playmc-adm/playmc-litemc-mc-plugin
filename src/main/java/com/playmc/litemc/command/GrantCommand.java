package com.playmc.litemc.command;

import com.playmc.litemc.Messages;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Promotes an online player to the configured LuckPerms group.
 *
 * <p>LuckPerms still does the work, but the command is dispatched from the
 * console rather than run by the sender. That is the whole point: the sender
 * needs litemc.grant and nothing else, where the old commands.yml alias
 * required luckperms.user.parent.set - a node broad enough to make anyone an
 * admin - because aliases run with the sender's own permissions.
 */
public final class GrantCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final Messages messages;

    public GrantCommand(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            messages.send(sender, "grant.usage", Map.of("label", label));
            return true;
        }

        String requested = sanitize(args[0]);
        Player target = Bukkit.getPlayerExact(requested);
        if (target == null) {
            messages.send(sender, "grant.not-online", Map.of("player", requested));
            return true;
        }

        if (sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId())
                && !sender.hasPermission("litemc.grant.self")) {
            messages.send(sender, "grant.not-yourself", Map.of());
            return true;
        }

        String group = plugin.getConfig().getString("grant.group", "player").toLowerCase(Locale.ROOT);
        String membership = "group." + group;

        if (target.hasPermission(membership)) {
            messages.send(sender, "grant.already-granted",
                    Map.of("player", target.getName(), "group", group));
            return true;
        }

        String luckPermsCommand = plugin.getConfig()
                .getString("grant.command", "lp user %uuid% parent set %group%")
                .replace("%uuid%", target.getUniqueId().toString())
                .replace("%player%", target.getName())
                .replace("%group%", group);

        plugin.getLogger().info(sender.getName() + " granted " + group + " to "
                + target.getName() + " (" + target.getUniqueId() + ")");

        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), luckPermsCommand)) {
            plugin.getLogger().warning("LuckPerms refused the command: " + luckPermsCommand);
            messages.send(sender, "grant.failed", Map.of("player", target.getName()));
            return true;
        }

        messages.send(sender, "grant.granted", Map.of("player", target.getName(), "group", group));
        messages.send(target, "grant.promoted", Map.of("sender", sender.getName(), "group", group));

        verify(sender, target, group, membership);
        return true;
    }

    /**
     * LuckPerms applies the change asynchronously, so dispatchCommand returning
     * true only means the command was accepted. Re-check membership shortly
     * afterwards and say so - in chat and in the log - if it did not land.
     */
    private void verify(CommandSender sender, Player target, String group, String membership) {
        long delay = plugin.getConfig().getLong("grant.verify-delay-ticks", 40L);
        if (delay <= 0L) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline() || target.hasPermission(membership)) {
                return;
            }
            plugin.getLogger().warning(target.getName() + " is still not in the " + group
                    + " group after the grant; check the LuckPerms storage.");
            messages.send(sender, "grant.failed", Map.of("player", target.getName()));
        }, delay);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String group = plugin.getConfig().getString("grant.group", "player").toLowerCase(Locale.ROOT);
        String membership = "group." + group;
        String prefix = args[0].toLowerCase(Locale.ROOT);

        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(membership)) {
                continue;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    /**
     * Minecraft names are letters, digits and underscores. Stripping anything
     * else keeps unknown input from reaching a MiniMessage template as a tag.
     */
    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "");
    }
}
