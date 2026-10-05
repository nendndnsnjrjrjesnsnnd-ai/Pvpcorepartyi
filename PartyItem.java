package dev.pvpcore.party;

import dev.pvpcore.party.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lobideki "Parti" esyasi. PvPCore lobi esyalari (kilic/kit editor/ayarlar...) verildikten
 * sonra bos slota eklenir. Mac sirasinda verilmez.
 */
public final class PartyItem implements Listener {
    private final PartyPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public PartyItem(PartyPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "party_item");
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("item.enabled", true)) return;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) ensure(p);
        }, 40L, 20L);
    }

    public ItemStack create() {
        Material mat = Material.matchMaterial(plugin.getConfig().getString("item.material", "LECTERN"));
        if (mat == null) mat = Material.LECTERN;
        ItemStack it = Items.of(mat, 1,
                plugin.getConfig().getString("item.name", "&d★ &5Parti"),
                plugin.getConfig().getStringList("item.lore"));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isPartyItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Oyuncu lobide ise (PvPCore lobi esyasi var, macta degil) parti esyasini verir. */
    private void ensure(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR) return;
        UUID id = p.getUniqueId();
        if (plugin.matches().inMatch(id)) return;
        if (plugin.core().duelManager().isInMatch(id)) return;

        PlayerInventory inv = p.getInventory();
        boolean inLobby = false;
        boolean has = false;
        for (int s = 0; s < 9; s++) {
            ItemStack it = inv.getItem(s);
            if (it == null) continue;
            if (isPartyItem(it)) has = true;
            else if (plugin.core().lobbyItems().isLobbyItem(it)) inLobby = true;
        }
        if (!inLobby || has) return;

        int slot = plugin.getConfig().getInt("item.slot", 1);
        if (slot < 0 || slot > 8) slot = 1;
        ItemStack existing = inv.getItem(slot);
        if (existing != null && existing.getType() != Material.AIR) return;
        inv.setItem(slot, create());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!isPartyItem(it)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < 400) return;
        cooldown.put(p.getUniqueId(), now);

        if (plugin.core().duelManager().isInMatch(p.getUniqueId())) return;
        plugin.menus().openMain(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (isPartyItem(e.getCurrentItem()) || isPartyItem(e.getCursor())) {
            e.setCancelled(true);
            return;
        }
        if (e.getClick().isKeyboardClick() && e.getHotbarButton() >= 0
                && e.getWhoClicked() instanceof Player p
                && isPartyItem(p.getInventory().getItem(e.getHotbarButton()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isPartyItem(e.getOldCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isPartyItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isPartyItem(e.getMainHandItem()) || isPartyItem(e.getOffHandItem())) e.setCancelled(true);
    }
}
