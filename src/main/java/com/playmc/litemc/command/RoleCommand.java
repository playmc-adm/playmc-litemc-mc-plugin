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
 * Moves an online player into or out of the configured LuckPerms group.
 *
 * <p>/grant and /ungrant are one operation in two directions, so they are one
 * class parameterised by {@link Action}. The single fact that differs is
 * whether the target should be in the group when it is over, and that drives
 * all three places the direction matters: the precondition, the verification,
 * and which names tab-complete. Holding it once is what keeps those three
 * from disagreeing with each other.
 *
 * <p>LuckPerms does the work in both directions, but the command is
 * dispatched from the console rather than run by the sender, so the sender
 * needs only litemc.grant or litemc.ungrant and never a LuckPerms node.
 */
public final class RoleCommand implements CommandExecutor, TabCompleter {

    /** The two directions, and everything that differs between them. */
    public enum Action {
        GRANT("grant", true, "lp user %uuid% parent set %group%"),
        UNGRANT("ungrant", false, "lp user %uuid% parent remove %group%");

        private final String section;
        private final boolean memberAfterwards;
        private final String fallbackCommand;

        Action(String section, boolean memberAfterwards, String fallbackCommand) {
            this.section = section;
            this.memberAfterwards = memberAfterwards;
            this.fallbackCommand = fallbackCommand;
        }

        private String message(String key) {
            return section + "." + key;
        }

        private String commandKey() {
            return "role." + section + "-command";
        }

        private String selfPermission() {
            return "litemc." + section + ".self";
        }
    }

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Action action;

    public RoleCommand(JavaPlugin plugin, Messages messages, Action action) {
        this.plugin = plugin;
        this.messages = messages;
        this.action = action;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            messages.send(sender, action.message("usage"), Map.of("label", label));
            return true;
        }

        String requested = sanitize(args[0]);
        Player target = Bukkit.getPlayerExact(requested);
        if (target == null) {
            messages.send(sender, action.message("not-online"), Map.of("player", requested));
            return true;
        }

        if (sender instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId())
                && !sender.hasPermission(action.selfPermission())) {
            messages.send(sender, action.message("not-yourself"), Map.of());
            return true;
        }

        String group = group();
        String membership = "group." + group;

        // Already on the side we would move them to, so there is nothing to do.
        if (target.hasPermission(membership) == action.memberAfterwards) {
            messages.send(sender, action.message("no-change"),
                    Map.of("player", target.getName(), "group", group));
            return true;
        }

        String luckPermsCommand = plugin.getConfig()
                .getString(action.commandKey(), action.fallbackCommand)
                .replace("%uuid%", target.getUniqueId().toString())
                .replace("%player%", target.getName())
                .replace("%group%", group);

        plugin.getLogger().info(sender.getName() + " ran /" + action.section + " on "
                + target.getName() + " (" + target.getUniqueId() + ") for group " + group);

        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), luckPermsCommand)) {
            plugin.getLogger().warning("LuckPerms refused the command: " + luckPermsCommand);
            messages.send(sender, action.message("failed"), Map.of("player", target.getName()));
            return true;
        }

        messages.send(sender, action.message("done"),
                Map.of("player", target.getName(), "group", group));
        messages.send(target, action.message("notified"),
                Map.of("sender", sender.getName(), "group", group));

        verify(sender, target, group, membership);
        return true;
    }

    /**
     * LuckPerms applies the change asynchronously, so dispatchCommand returning
     * true only means the command was accepted - a command naming a group that
     * does not exist is accepted and then fails. Re-check membership shortly
     * afterwards and say so, in chat and in the log, if it did not land.
     */
    private void verify(CommandSender sender, Player target, String group, String membership) {
        long delay = plugin.getConfig().getLong("role.verify-delay-ticks", 40L);
        if (delay <= 0L) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isOnline() || target.hasPermission(membership) == action.memberAfterwards) {
                return;
            }
            plugin.getLogger().warning(target.getName() + " is still "
                    + (action.memberAfterwards ? "outside" : "inside") + " the " + group
                    + " group after /" + action.section + "; check the LuckPerms storage.");
            messages.send(sender, action.message("failed"), Map.of("player", target.getName()));
        }, delay);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String membership = "group." + group();
        String prefix = args[0].toLowerCase(Locale.ROOT);

        List<String> names = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            // Only offer the players this direction would actually change.
            if (online.hasPermission(membership) == action.memberAfterwards) {
                continue;
            }
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(online.getName());
            }
        }
        return names;
    }

    /** The one group both directions pivot on. */
    private String group() {
        return plugin.getConfig().getString("role.group", "player").toLowerCase(Locale.ROOT);
    }

    /**
     * Minecraft names are letters, digits and underscores. Stripping anything
     * else keeps unknown input from reaching a MiniMessage template as a tag.
     */
    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_]", "");
    }
}
