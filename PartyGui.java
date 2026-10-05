package dev.pvpcore.party.gui;

import dev.pvpcore.party.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class PartyGui implements InventoryHolder {
    @FunctionalInterface
    public interface Action {
        void run(Player player, ClickType click);
    }

    private final Inventory inventory;
    private final Map<Integer, Action> actions = new HashMap<>();

    public PartyGui(String title, int size) {
        this.inventory = Bukkit.createInventory(this, size, Text.c(title));
    }

    public void set(int slot, ItemStack item, Action action) {
        if (slot < 0 || slot >= inventory.getSize()) return;
        inventory.setItem(slot, item);
        if (action != null) actions.put(slot, action);
        else actions.remove(slot);
    }

    public void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    public void clear() {
        inventory.clear();
        actions.clear();
    }

    public void handle(int slot, Player player, ClickType click) {
        Action a = actions.get(slot);
        if (a != null) a.run(player, click);
    }

    public void open(Player p) {
        p.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
