package com.playmc.litemc.command;

import com.playmc.litemc.Messages;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Prints the rules from config.yml to whoever ran the command.
 *
 * <p>Gated on litemc.rules, which plugin.yml defaults to true, so a Visitor
 * can read the rules without being handed minecraft.command.tellraw and the
 * chat-spoofing that comes with it.
 */
public final class RulesCommand implements CommandExecutor {

    private final Messages messages;

    public RulesCommand(Messages messages) {
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        messages.sendList(sender, "rules.lines", Map.of());
        return true;
    }
}
