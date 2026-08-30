package com.playmc.litemc;

import com.playmc.litemc.command.RoleCommand;
import com.playmc.litemc.command.RulesCommand;
import com.playmc.litemc.listener.WelcomeListener;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server-specific commands for the PlayMC server.
 *
 * <p>Every command declares its own permission node in plugin.yml, which is
 * what a commands.yml alias cannot do: an alias carries no node of its own
 * and runs with the sender's permissions, so it can only be opened up by
 * granting the underlying command to everyone.
 */
public final class LiteMcPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();

        Messages messages = new Messages(this);

        RoleCommand grant = new RoleCommand(this, messages, RoleCommand.Action.GRANT);
        RoleCommand ungrant = new RoleCommand(this, messages, RoleCommand.Action.UNGRANT);

        register("rules", new RulesCommand(messages), null);
        register("grant", grant, grant);
        register("ungrant", ungrant, ungrant);

        getServer().getPluginManager().registerEvents(new WelcomeListener(this, messages), this);

        getLogger().info("Enabled LiteMC.");
    }

    private void register(String name, CommandExecutor executor, TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("/" + name + " is missing from plugin.yml");
        }
        command.setExecutor(executor);
        if (completer != null) {
            command.setTabCompleter(completer);
        }
    }
}
