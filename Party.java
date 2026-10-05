package dev.pvpcore.party;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Party {
    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    /** davet edilen oyuncu -> bitis zamani (ms) */
    private final Map<UUID, Long> invites = new HashMap<>();

    public Party(UUID leader) {
        this.leader = leader;
        this.members.add(leader);
    }

    public UUID id() { return id; }
    public UUID leader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }
    public boolean isLeader(UUID u) { return leader.equals(u); }
    public Set<UUID> members() { return members; }
    public boolean isMember(UUID u) { return members.contains(u); }
    public int size() { return members.size(); }
    public Map<UUID, Long> invites() { return invites; }

    public boolean hasValidInvite(UUID u) {
        Long exp = invites.get(u);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            invites.remove(u);
            return false;
        }
        return true;
    }
}
