package dev.pvpcore.party.gui;

import dev.pvpcore.party.Party;
import dev.pvpcore.party.PartyManager;
import dev.pvpcore.party.PartyPlugin;
import dev.pvpcore.party.util.Items;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyMenus {
    private static final int[] RED_SLOTS = {1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] BLUE_SLOTS = {28, 29, 30, 31, 32, 33, 34, 35, 37, 38, 39, 40, 41, 42, 43, 44};

    private static final class TeamSetup {
        final Set<UUID> red = new LinkedHashSet<>();
        final Set<UUID> blue = new LinkedHashSet<>();
        int kitIndex = 0;
    }

    private final PartyPlugin plugin;
    private final Map<UUID, TeamSetup> setups = new HashMap<>();

    public PartyMenus(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    private static List<String> lore(String... lines) {
        return Arrays.asList(lines);
    }

    // ============================================================ ANA MENU

    public void openMain(Player p) {
        Party party = plugin.parties().getOrCreate(p);
        boolean leader = party.isLeader(p.getUniqueId());
        PartyGui g = new PartyGui("&5★ Parti Yönetimi", 36);

        g.set(11, Items.of(Material.IRON_CHESTPLATE, "&f⚔ &dParti FFA",
                        "&7Herkes herkese karşı!", "&7Son hayatta kalan kazanır.", "",
                        leader ? "&eTıkla: &fKit seç ve başlat" : "&cSadece parti lideri başlatabilir"),
                (pl, c) -> {
                    if (!party.isLeader(pl.getUniqueId())) {
                        plugin.send(pl, "not-leader");
                        return;
                    }
                    openFfa(pl);
                });

        g.set(15, Items.of(Material.DIAMOND_CHESTPLATE, "&9⚑ &dTakım Savaşı",
                        "&7Partiyi iki takıma böl:", "&cKırmızı &7vs &9Mavi", "",
                        leader ? "&eTıkla: &fTakımları ayarla" : "&cSadece parti lideri başlatabilir"),
                (pl, c) -> {
                    if (!party.isLeader(pl.getUniqueId())) {
                        plugin.send(pl, "not-leader");
                        return;
                    }
                    openTeam(pl);
                });

        if (leader) {
            g.set(4, Items.of(Material.TNT, "&c✖ Partiyi Dağıt", "&7Partiyi tamamen dağıtır."),
                    (pl, c) -> openDisbandConfirm(pl));
        }

        g.set(27, Items.of(Material.ENDER_PEARL, "&c↩ Partiden Ayrıl", "&7Partiden ayrılırsın."),
                (pl, c) -> {
                    pl.closeInventory();
                    plugin.parties().leave(pl.getUniqueId(), false);
                    plugin.send(pl, "you-left");
                });

        g.set(31, Items.head(p, "&d☻ Parti Üyeleri",
                        lore("&7Üyeler: &f" + party.size() + "&7/&f" + plugin.parties().maxSize(), "",
                                "&eTıkla: &fÜyeleri yönet")),
                (pl, c) -> openMembers(pl));

        boolean canInvite = leader || !plugin.getConfig().getBoolean("party.only-leader-invite", true);
        g.set(35, Items.of(Material.SPYGLASS, "&a+ Arkadaşlarını Davet Et", "&7ℹ &fPartiye arkadaşlarını davet et!"),
                (pl, c) -> {
                    if (!canInvite) {
                        plugin.send(pl, "not-leader");
                        return;
                    }
                    openInvite(pl);
                });
        g.open(p);
    }

    // =========================================================== DAGIT ONAY

    public void openDisbandConfirm(Player p) {
        Party party = plugin.parties().get(p);
        if (party == null || !party.isLeader(p.getUniqueId())) {
            plugin.send(p, "not-leader");
            return;
        }
        PartyGui g = new PartyGui("&c✖ &5Partiyi Dağıt?", 36);
        g.set(13, Items.of(Material.CAKE, "&c✖ Partiyi Dağıt", "&7Bu işlem geri alınamaz."),
                (pl, c) -> {
                    pl.closeInventory();
                    Party cur = plugin.parties().get(pl);
                    if (cur != null && cur.isLeader(pl.getUniqueId())) plugin.parties().disband(cur, true);
                });
        g.set(31, Items.of(Material.BARRIER, "&a◀ Vazgeç"), (pl, c) -> openMain(pl));
        g.open(p);
    }

    // ============================================================== UYELER

    public void openMembers(Player p) {
        Party party = plugin.parties().get(p);
        if (party == null) {
            plugin.send(p, "not-in-party");
            return;
        }
        boolean leader = party.isLeader(p.getUniqueId());
        PartyGui g = new PartyGui("&5☻ Parti Üyeleri", 36);
        int slot = 0;
        for (UUID m : party.members()) {
            if (slot > 26) break;
            boolean isLeader = party.isLeader(m);
            boolean online = Bukkit.getPlayer(m) != null;
            List<String> l = new ArrayList<>();
            l.add(isLeader ? "&6★ Parti Lideri" : "&7Üye");
            l.add(online ? "&aÇevrimiçi" : "&cÇevrimdışı");
            if (leader && !m.equals(p.getUniqueId())) {
                l.add("");
                l.add("&eSol tık: &cPartiden at");
                l.add("&eSağ tık: &6Liderliği devret");
            }
            final UUID target = m;
            g.set(slot++, Items.head(Bukkit.getOfflinePlayer(m), "&f" + PartyManager.name(m), l),
                    (pl, click) -> {
                        if (!leader || target.equals(pl.getUniqueId())) return;
                        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                            plugin.parties().transfer(pl, target);
                        } else {
                            plugin.parties().kick(pl, target);
                        }
                        if (plugin.parties().get(pl) != null) openMembers(pl);
                        else pl.closeInventory();
                    });
        }
        g.set(31, Items.of(Material.BARRIER, "&c◀ Geri"), (pl, c) -> openMain(pl));
        g.open(p);
    }

    // ============================================================== DAVET

    public void openInvite(Player p) {
        Party party = plugin.parties().getOrCreate(p);
        PartyGui g = new PartyGui("&2+ &aArkadaşlarını Davet Et", 54);
        int slot = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (slot > 44) break;
            if (o.equals(p)) continue;
            if (!plugin.core().playerData().areFriends(p.getUniqueId(), o.getUniqueId())) continue;
            if (plugin.core().playerData().isInvisible(o.getUniqueId())) continue;
            if (plugin.parties().get(o) != null) continue;
            boolean invited = party.hasValidInvite(o.getUniqueId());
            final Player target = o;
            g.set(slot++, Items.head(o, "&a" + o.getName(),
                            lore(invited ? "&eDavet gönderildi" : "&7Tıkla: &fparti daveti gönder")),
                    (pl, c) -> {
                        plugin.parties().invite(pl, target);
                        openInvite(pl);
                    });
        }
        if (slot == 0) {
            g.set(22, Items.of(Material.PAPER, "&cÇevrimiçi arkadaşın yok",
                    "&7Arkadaş olmayanları davet etmek için:", "&f/p davet <oyuncu>"));
        }
        g.set(49, Items.of(Material.BARRIER, "&c◀ Geri"), (pl, c) -> openMain(pl));
        g.open(p);
    }

    // ================================================================= FFA

    public void openFfa(Player p) {
        List<Kit> kits = plugin.core().kitManager().list();
        if (kits == null || kits.isEmpty()) {
            plugin.send(p, "no-kits");
            return;
        }
        PartyGui g = new PartyGui("&5🗡 &dParti FFA", 36);
        int slot = 0;
        for (Kit kit : kits) {
            if (slot > 26) break;
            final Kit k = kit;
            g.set(slot++, Items.kitIcon(kit, lore("&7Herkes herkese karşı", "", "&eTıkla: &fFFA başlat")),
                    (pl, c) -> {
                        Party party = plugin.parties().get(pl);
                        if (party == null || !party.isLeader(pl.getUniqueId())) {
                            plugin.send(pl, "not-leader");
                            return;
                        }
                        pl.closeInventory();
                        plugin.matches().startFfa(pl, party, k);
                    });
        }
        g.set(31, Items.of(Material.BARRIER, "&c◀ Geri"), (pl, c) -> openMain(pl));
        g.open(p);
    }

    // =============================================================== TAKIM

    public void openTeam(Player p) {
        Party party = plugin.parties().get(p);
        if (party == null || !party.isLeader(p.getUniqueId())) {
            plugin.send(p, "not-leader");
            return;
        }
        List<Kit> kits = plugin.core().kitManager().list();
        if (kits == null || kits.isEmpty()) {
            plugin.send(p, "no-kits");
            return;
        }
        TeamSetup s = setups.computeIfAbsent(party.id(), id -> new TeamSetup());
        syncSetup(party, s);
        PartyGui g = new PartyGui("&5⚑ &dTakım Savaşı", 45);
        renderTeam(g, party, s, kits);
        g.open(p);
    }

    private void syncSetup(Party party, TeamSetup s) {
        s.red.retainAll(party.members());
        s.blue.retainAll(party.members());
        for (UUID m : party.members()) {
            if (s.red.contains(m) || s.blue.contains(m)) continue;
            if (s.red.size() <= s.blue.size()) s.red.add(m);
            else s.blue.add(m);
        }
    }

    private void renderTeam(PartyGui g, Party party, TeamSetup s, List<Kit> kits) {
        g.clear();
        if (s.kitIndex >= kits.size() || s.kitIndex < 0) s.kitIndex = 0;
        String redName = plugin.lang().raw("team-red");
        String blueName = plugin.lang().raw("team-blue");

        ItemStack redPane = Items.of(Material.RED_STAINED_GLASS_PANE, redName + " &7Takım",
                "&7Oyuncu sayısı: &f" + s.red.size());
        ItemStack bluePane = Items.of(Material.BLUE_STAINED_GLASS_PANE, blueName + " &7Takım",
                "&7Oyuncu sayısı: &f" + s.blue.size());
        g.set(0, redPane);
        g.set(9, redPane);
        g.set(27, bluePane);
        g.set(36, bluePane);

        fillTeam(g, party, s, kits, new ArrayList<>(s.red), RED_SLOTS, true);
        fillTeam(g, party, s, kits, new ArrayList<>(s.blue), BLUE_SLOTS, false);

        Kit kit = kits.get(s.kitIndex);
        g.set(20, Items.kitIcon(kit, lore("&7Seçili kit", "", "&eSol tık: &fsonraki", "&eSağ tık: &fönceki")),
                (pl, click) -> {
                    int n = kits.size();
                    if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) s.kitIndex = (s.kitIndex - 1 + n) % n;
                    else s.kitIndex = (s.kitIndex + 1) % n;
                    renderTeam(g, party, s, kits);
                });

        g.set(22, Items.of(Material.LIME_CONCRETE, "&a✔ &fSavaşı Başlat",
                        "&7Kit: &f" + (kit.displayName() != null ? kit.displayName() : kit.id()),
                        "&cKırmızı&7: &f" + s.red.size() + " &7| &9Mavi&7: &f" + s.blue.size()),
                (pl, c) -> {
                    Party cur = plugin.parties().get(pl);
                    if (cur == null || !cur.isLeader(pl.getUniqueId())) {
                        plugin.send(pl, "not-leader");
                        return;
                    }
                    syncSetup(cur, s);
                    if (s.red.isEmpty() || s.blue.isEmpty()) {
                        plugin.send(pl, "need-teams");
                        return;
                    }
                    pl.closeInventory();
                    plugin.matches().startTeam(pl, cur, new LinkedHashSet<>(s.red), new LinkedHashSet<>(s.blue), kit);
                });

        g.set(24, Items.of(Material.HOPPER, "&e⟲ Takımları Karıştır", "&7Oyuncuları rastgele böler."),
                (pl, c) -> {
                    List<UUID> all = new ArrayList<>(party.members());
                    Collections.shuffle(all);
                    s.red.clear();
                    s.blue.clear();
                    for (int i = 0; i < all.size(); i++) {
                        if (i % 2 == 0) s.red.add(all.get(i));
                        else s.blue.add(all.get(i));
                    }
                    renderTeam(g, party, s, kits);
                });

        g.set(26, Items.of(Material.BARRIER, "&c◀ Geri"), (pl, c) -> openMain(pl));
    }

    private void fillTeam(PartyGui g, Party party, TeamSetup s, List<Kit> kits,
                          List<UUID> members, int[] slots, boolean red) {
        for (int i = 0; i < members.size() && i < slots.length; i++) {
            final UUID m = members.get(i);
            g.set(slots[i], Items.head(Bukkit.getOfflinePlayer(m), (red ? "&c" : "&9") + PartyManager.name(m),
                            lore("&eTıkla: &fdiğer takıma geçir")),
                    (pl, c) -> {
                        if (red) {
                            s.red.remove(m);
                            s.blue.add(m);
                        } else {
                            s.blue.remove(m);
                            s.red.add(m);
                        }
                        renderTeam(g, party, s, kits);
                    });
        }
    }
}
