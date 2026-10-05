package dev.pvpcore.party;

import dev.pvpcore.party.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyManager {
    private final PartyPlugin plugin;
    private final Map<UUID, Party> byPlayer = new HashMap<>();
    private final Set<Party> parties = new LinkedHashSet<>();
    private final Set<UUID> chatToggle = new HashSet<>();

    public PartyManager(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    public Party get(UUID u) { return byPlayer.get(u); }
    public Party get(Player p) { return byPlayer.get(p.getUniqueId()); }

    public int maxSize() { return Math.max(2, plugin.getConfig().getInt("party.max-size", 10)); }
    public int inviteSeconds() { return Math.max(5, plugin.getConfig().getInt("party.invite-seconds", 60)); }

    public Party getOrCreate(Player p) {
        Party party = get(p);
        if (party != null) return party;
        party = new Party(p.getUniqueId());
        parties.add(party);
        byPlayer.put(p.getUniqueId(), party);
        plugin.send(p, "party-created");
        return party;
    }

    public static String name(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? "?" : n;
    }

    /** Cevrimici ve gorunur (invisible degil) oyuncuyu isimden bulur. */
    public Player findVisible(Player asker, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) return null;
        boolean invisible = plugin.core().playerData().isInvisible(target.getUniqueId());
        if (invisible && !asker.hasPermission("pvpcoreparty.bypass")
                && !plugin.core().playerData().areFriends(asker.getUniqueId(), target.getUniqueId())) {
            return null;
        }
        return target;
    }

    // ---------------------------------------------------------------- invite

    public void invite(Player inviter, Player target) {
        if (inviter.getUniqueId().equals(target.getUniqueId())) {
            plugin.send(inviter, "invite-self");
            return;
        }
        Party party = getOrCreate(inviter);
        boolean onlyLeader = plugin.getConfig().getBoolean("party.only-leader-invite", true);
        if (onlyLeader && !party.isLeader(inviter.getUniqueId())) {
            plugin.send(inviter, "not-leader");
            return;
        }
        if (party.size() >= maxSize()) {
            plugin.send(inviter, "party-full", "max", String.valueOf(maxSize()));
            return;
        }
        Party other = get(target);
        if (other != null) {
            plugin.send(inviter, other == party ? "already-member" : "target-in-party", "player", target.getName());
            return;
        }
        if (plugin.core().playerData().isDoNotDisturb(target.getUniqueId())
                && !inviter.hasPermission("pvpcoreparty.bypass")) {
            plugin.send(inviter, "target-dnd", "player", target.getName());
            return;
        }
        if (party.hasValidInvite(target.getUniqueId())) {
            plugin.send(inviter, "already-invited", "player", target.getName());
            return;
        }

        int seconds = inviteSeconds();
        party.invites().put(target.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        plugin.send(inviter, "invite-sent", "player", target.getName(), "seconds", String.valueOf(seconds));

        Component line = Text.c(plugin.lang().get("invite-received", "player", inviter.getName()))
                .append(Component.text(" "))
                .append(Text.c(plugin.lang().get("invite-click"))
                        .clickEvent(ClickEvent.runCommand("/p kabul " + inviter.getName()))
                        .hoverEvent(HoverEvent.showText(
                                Text.c(plugin.lang().get("invite-hover", "player", inviter.getName())))));
        target.sendMessage(line);

        final UUID targetId = target.getUniqueId();
        final UUID inviterId = inviter.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (party.invites().remove(targetId) != null && parties.contains(party)) {
                Player inv = Bukkit.getPlayer(inviterId);
                if (inv != null && get(targetId) == null) {
                    plugin.send(inv, "invite-expired", "player", name(targetId));
                }
            }
        }, seconds * 20L + 5L);
    }

    public void accept(Player p, String fromName) {
        if (get(p) != null) {
            plugin.send(p, "already-in-party");
            return;
        }
        if (plugin.core().duelManager().isInMatch(p.getUniqueId())) {
            plugin.send(p, "in-duel");
            return;
        }
        Party found = null;
        for (Party party : parties) {
            if (!party.hasValidInvite(p.getUniqueId())) continue;
            if (fromName == null) {
                found = party;
                break;
            }
            for (UUID m : party.members()) {
                if (name(m).equalsIgnoreCase(fromName)) {
                    found = party;
                    break;
                }
            }
            if (found != null) break;
        }
        if (found == null) {
            plugin.send(p, "no-invite");
            return;
        }
        if (found.size() >= maxSize()) {
            plugin.send(p, "party-full", "max", String.valueOf(maxSize()));
            return;
        }
        join(found, p);
    }

    public void deny(Player p, String fromName) {
        boolean removed = false;
        for (Party party : parties) {
            if (!party.hasValidInvite(p.getUniqueId())) continue;
            if (fromName != null) {
                boolean match = false;
                for (UUID m : party.members()) {
                    if (name(m).equalsIgnoreCase(fromName)) match = true;
                }
                if (!match) continue;
            }
            party.invites().remove(p.getUniqueId());
            removed = true;
        }
        plugin.send(p, removed ? "invite-denied" : "no-invite");
    }

    private void join(Party party, Player p) {
        party.invites().remove(p.getUniqueId());
        party.members().add(p.getUniqueId());
        byPlayer.put(p.getUniqueId(), party);
        broadcast(party, "joined", "player", p.getName());
        plugin.send(p, "you-joined", "leader", name(party.leader()));
    }

    // ----------------------------------------------------------- leave / kick

    /** Oyuncu ayrildi (komut, atilma veya cikis). */
    public void leave(UUID id, boolean silent) {
        Party party = byPlayer.get(id);
        if (party == null) return;

        plugin.matches().onMemberRemoved(party, id);

        party.members().remove(id);
        byPlayer.remove(id);
        chatToggle.remove(id);

        if (party.members().isEmpty()) {
            parties.remove(party);
            return;
        }
        if (!silent) broadcast(party, "left", "player", name(id));
        if (party.isLeader(id)) {
            UUID next = null;
            for (UUID m : party.members()) {
                if (Bukkit.getPlayer(m) != null) {
                    next = m;
                    break;
                }
            }
            if (next == null) {
                disband(party, false);
                return;
            }
            party.setLeader(next);
            broadcast(party, "leader-changed", "player", name(next));
        }
    }

    public void kick(Player leader, UUID target) {
        Party party = get(leader);
        if (party == null) {
            plugin.send(leader, "not-in-party");
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            plugin.send(leader, "not-leader");
            return;
        }
        if (target.equals(leader.getUniqueId())) {
            plugin.send(leader, "kick-self");
            return;
        }
        if (!party.isMember(target)) {
            plugin.send(leader, "not-member", "player", name(target));
            return;
        }
        Player t = Bukkit.getPlayer(target);
        String n = name(target);
        leave(target, true);
        broadcast(party, "kicked", "player", n);
        if (t != null) plugin.send(t, "you-kicked");
    }

    public void transfer(Player leader, UUID target) {
        Party party = get(leader);
        if (party == null) {
            plugin.send(leader, "not-in-party");
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            plugin.send(leader, "not-leader");
            return;
        }
        if (target.equals(leader.getUniqueId())) {
            plugin.send(leader, "transfer-self");
            return;
        }
        if (!party.isMember(target)) {
            plugin.send(leader, "not-member", "player", name(target));
            return;
        }
        party.setLeader(target);
        broadcast(party, "leader-changed", "player", name(target));
    }

    public void disband(Party party, boolean announce) {
        if (!parties.contains(party)) return;
        plugin.matches().onPartyDisband(party);
        broadcast(party, "disbanded");
        for (UUID m : new ArrayList<>(party.members())) {
            byPlayer.remove(m);
            chatToggle.remove(m);
        }
        party.members().clear();
        party.invites().clear();
        parties.remove(party);
    }

    /** Cevrimdisi uyeleri ve bekleyen davetleri temizler. */
    public void clear(Player leader) {
        Party party = get(leader);
        if (party == null) {
            plugin.send(leader, "not-in-party");
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            plugin.send(leader, "not-leader");
            return;
        }
        int count = party.invites().size();
        party.invites().clear();
        for (UUID m : new ArrayList<>(party.members())) {
            if (Bukkit.getPlayer(m) == null) {
                leave(m, true);
                count++;
            }
        }
        plugin.send(leader, "cleared", "count", String.valueOf(count));
    }

    // -------------------------------------------------------------- chat

    public boolean isChatToggled(UUID id) { return chatToggle.contains(id); }

    public void toggleChat(Player p) {
        if (get(p) == null) {
            plugin.send(p, "not-in-party");
            return;
        }
        if (chatToggle.remove(p.getUniqueId())) {
            plugin.send(p, "chat-off");
        } else {
            chatToggle.add(p.getUniqueId());
            plugin.send(p, "chat-on");
        }
    }

    public void partyChat(Player from, String message) {
        Party party = get(from);
        if (party == null) {
            plugin.send(from, "not-in-party");
            return;
        }
        broadcast(party, "chat-format", "player", from.getName(), "message", message);
    }

    // ------------------------------------------------------------ helpers

    public void broadcast(Party party, String key, String... kv) {
        for (UUID m : new ArrayList<>(party.members())) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) plugin.send(p, key, kv);
        }
    }

    public List<Player> onlineMembers(Party party) {
        List<Player> out = new ArrayList<>();
        for (UUID m : party.members()) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) out.add(p);
        }
        return out;
    }
}
