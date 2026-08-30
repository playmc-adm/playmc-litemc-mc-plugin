package com.playmc.litemc.listener;

import com.playmc.litemc.Messages;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Greets players who are still Visitors, pointing them at /rules and telling
 * them how to get granted.
 *
 * <p>Who sees it is decided by group membership rather than by whether the
 * player has joined before: a Visitor stays a newcomer until somebody grants
 * them, so the message keeps appearing until it is no longer the right advice
 * and stops on its own afterwards. That also means the plugin stores nothing.
 */
public final class WelcomeListener implements Listener {

    private final JavaPlugin plugin;
    private final Messages messages;

    public WelcomeListener(JavaPlugin plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("welcome.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        long delay = Math.max(1L, plugin.getConfig().getLong("welcome.delay-ticks", 40L));

        // Delayed so the greeting lands after the join and MOTD noise rather
        // than being scrolled away by it.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            String group = plugin.getConfig()
                    .getString("role.group", "player")
                    .toLowerCase(Locale.ROOT);
            Map<String, String> placeholders = Map.of("player", player.getName());

            if (player.hasPermission("group." + group)) {
                // Optional: silent when welcome.granted is not configured.
                messages.sendListIfPresent(player, "welcome.granted", placeholders);
                return;
            }

            messages.sendList(player, "welcome.lines", placeholders);
        }, delay);
    }
}
