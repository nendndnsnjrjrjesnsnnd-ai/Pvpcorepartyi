package dev.pvpcore.party.match;

import dev.pvpcore.party.Party;
import dev.pvpcore.party.PartyManager;
import dev.pvpcore.party.PartyPlugin;
import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Parti maclarini (FFA / Takim Savasi) yonetir. PvPCore'un arena ve kit sistemini kullanir. */
public final class MatchManager {
    private final PartyPlugin plugin;
    private final Map<UUID, PartyMatch> byPlayer = new HashMap<>();
    private final Set<PartyMatch> matches = new HashSet<>();

    public MatchManager(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    public PartyMatch of(UUID id) { return byPlayer.get(id); }
    public boolean inMatch(UUID id) { return byPlayer.containsKey(id); }

    void bind(UUID id, PartyMatch m) { byPlayer.put(id, m); }
    void unbind(UUID id) { byPlayer.remove(id); }
    void unregister(PartyMatch m) { matches.remove(m); }

    public boolean startFfa(Player leader, Party party, Kit kit) {
        Set<UUID> all = new LinkedHashSet<>(party.members());
        return launch(leader, party, PartyMatch.Type.FFA, all, new LinkedHashSet<>(), new LinkedHashSet<>(), kit);
    }

    public boolean startTeam(Player leader, Party party, Set<UUID> red, Set<UUID> blue, Kit kit) {
        Set<UUID> all = new LinkedHashSet<>();
        all.addAll(red);
        all.addAll(blue);
        return launch(leader, party, PartyMatch.Type.TEAM, all, red, blue, kit);
    }

    private boolean launch(Player leader, Party party, PartyMatch.Type type,
                           Set<UUID> participants, Set<UUID> red, Set<UUID> blue, Kit kit) {
        if (!party.isLeader(leader.getUniqueId())) {
            plugin.send(leader, "not-leader");
            return false;
        }
        for (UUID id : party.members()) {
            if (byPlayer.containsKey(id)) {
                plugin.send(leader, "party-in-match");
                return false;
            }
        }

        // Sadece cevrimici oyuncular katilir.
        Set<UUID> online = new LinkedHashSet<>();
        Set<UUID> onlineRed = new LinkedHashSet<>();
        Set<UUID> onlineBlue = new LinkedHashSet<>();
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            if (plugin.core().duelManager().isInMatch(id) || p.getGameMode() == GameMode.SPECTATOR) {
                plugin.send(leader, "member-busy", "player", p.getName());
                return false;
            }
            online.add(id);
            if (red.contains(id)) onlineRed.add(id);
            if (blue.contains(id)) onlineBlue.add(id);
        }
        if (online.size() < 2) {
            plugin.send(leader, "need-players");
            return false;
        }
        if (type == PartyMatch.Type.TEAM && (onlineRed.isEmpty() || onlineBlue.isEmpty())) {
            plugin.send(leader, "need-teams");
            return false;
        }

        Optional<Arena> found = plugin.core().arenaManager().findAvailable();
        if (found == null || found.isEmpty()) {
            plugin.send(leader, "no-arena");
            return false;
        }
        Arena arena = found.get();
        if (arena.redSpawn() == null || arena.blueSpawn() == null) {
            plugin.send(leader, "no-arena");
            return false;
        }

        // PvPCore siralarindan cikar (sessizce).
        for (UUID id : online) {
            plugin.core().queueManager().leaveAllQuiet(id);
        }

        arena.setInUse(true);
        plugin.core().arenaManager().markUsed(arena);

        PartyMatch match = new PartyMatch(plugin, this, party, type, kit, arena, online, onlineRed, onlineBlue);
        matches.add(match);
        for (UUID id : online) byPlayer.put(id, match);
        match.begin();
        return true;
    }

    /** Bir uye partiden ayrildi/atildi/cikti: maci da terk eder. */
    public void onMemberRemoved(Party party, UUID id) {
        PartyMatch m = byPlayer.get(id);
        if (m != null) m.leave(id);
    }

    public void onPartyDisband(Party party) {
        for (PartyMatch m : new ArrayList<>(matches)) {
            if (m.party() == party) m.abort();
        }
    }

    public void shutdown() {
        for (PartyMatch m : new ArrayList<>(matches)) m.abort();
    }

    public String partyName(UUID id) {
        return PartyManager.name(id);
    }
}
