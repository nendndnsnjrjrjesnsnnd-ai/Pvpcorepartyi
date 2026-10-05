package dev.pvpcore.party.util;

import dev.rean.pvpcore.model.Kit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public final class Items {
    private Items() {}

    public static ItemStack of(Material material, String name, String... lore) {
        return of(material, 1, name, Arrays.asList(lore));
    }

    public static ItemStack of(Material material, int amount, String name, List<String> lore) {
        ItemStack it = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.item(name));
            meta.lore(Text.items(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static ItemStack head(OfflinePlayer player, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.displayName(Text.item(name));
            skull.lore(Text.items(lore));
            it.setItemMeta(skull);
        }
        return it;
    }

    /** PvPCore kit ikonunu kullanarak GUI esyasi uretir. */
    public static ItemStack kitIcon(Kit kit, List<String> lore) {
        ItemStack base = kit.icon() != null ? kit.icon().clone() : new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            String display = kit.displayName() != null ? kit.displayName() : kit.id();
            meta.displayName(Text.item("&f" + display));
            meta.lore(Text.items(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            base.setItemMeta(meta);
        }
        base.setAmount(1);
        return base;
    }
}
