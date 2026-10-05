package dev.pvpcore.party;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PartyListener implements Listener {
    private final PartyPlugin plugin;

    public PartyListener(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.parties().leave(e.getPlayer().getUniqueId(), false);
    }

    /** Parti sohbeti acik olan oyuncunun mesaji sadece partiye gider. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!plugin.parties().isChatToggled(p.getUniqueId())) return;
        e.setCancelled(true);
        final String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.parties().partyChat(p, msg));
    }
}
