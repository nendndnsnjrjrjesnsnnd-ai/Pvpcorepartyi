package dev.pvpcore.party.match;

import dev.pvpcore.party.Party;
import dev.pvpcore.party.PartyManager;
import dev.pvpcore.party.PartyPlugin;
import dev.pvpcore.party.util.Text;
import dev.rean.pvpcore.model.Arena;
import dev.rean.pvpcore.model.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PartyMatch {
    public enum Type { FFA, TEAM }
    public enum Team { RED, BLUE }
    public enum State { COUNTDOWN, FIGHTING, ENDED }

    private final PartyPlugin plugin;
    private final MatchManager manager;
    private final Party party;
    private final Type type;
    private final Kit kit;
    private final Arena arena;

    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> red;
    private final Set<UUID> blue;
    private final Map<UUID, GameMode> previousMode = new HashMap<>();
    private final Map<UUID, UUID> lastDamager = new HashMap<>();
    private final Set<Block> placedBlocks = new HashSet<>();

    private State state = State.COUNTDOWN;
    private BukkitTask countdownTask;
    private boolean arenaReleased = false;
    private boolean cleaned = false;

    PartyMatch(PartyPlugin plugin, MatchManager manager, Party party, Type type, Kit kit, Arena arena,
               Set<UUID> participants, Set<UUID> red, Set<UUID> blue) {
        this.plugin = plugin;
        this.manager = manager;
        this.party = party;
        this.type = type;
        this.kit = kit;
        this.arena = arena;
        this.participants.addAll(participants);
        this.alive.addAll(participants);
        this.red = red;
        this.blue = blue;
    }

    // ---------------------------------------------------------- erisim

    public Party party() { return party; }
    public State state() { return state; }
    public Kit kit() { return kit; }
    public boolean isAlive(UUID id) { return alive.contains(id); }
    public boolean isParticipant(UUID id) { return participants.contains(id); }

    public boolean sameTeam(UUID a, UUID b) {
        if (type != Type.TEAM) return false;
        return (red.contains(a) && red.contains(b)) || (blue.contains(a) && blue.contains(b));
    }

    public void recordDamager(UUID victim, UUID attacker) { lastDamager.put(victim, attacker); }
    public UUID lastDamager(UUID victim) { return lastDamager.get(victim); }
    public void trackPlaced(Block b) { placedBlocks.add(b); }
    public boolean untrackPlaced(Block b) { return placedBlocks.remove(b); }

    // ---------------------------------------------------------- baslat

    void begin() {
        Location redSpawn = arena.redSpawn();
        Location blueSpawn = arena.blueSpawn();
        int i = 0;
        int redCount = 0;
        int blueCount = 0;
        String kitName = kit.displayName() != null ? kit.displayName() : kit.id();

        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            previousMode.put(id, p.getGameMode());

            boolean useRed;
            if (type == Type.TEAM) useRed = red.contains(id);
            else useRed = (i % 2 == 0);
            i++;

            Location base = useRed ? redSpawn : blueSpawn;
            int idx = useRed ? redCount++ : blueCount++;
            p.teleport(spread(base, idx));
            applyKit(p);
            plugin.send(p, "match-starting", "kit", kitName);
        }

        int seconds = plugin.getConfig().getInt("match.countdown-seconds", 0);
        if (seconds <= 0) seconds = plugin.core().settings().countdownSeconds();
        if (seconds <= 0) seconds = 5;

        final int[] remaining = {seconds};
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.COUNTDOWN) {
                countdownTask.cancel();
                return;
            }
            if (remaining[0] > 0) {
                String text = plugin.lang().get("match-countdown", "seconds", String.valueOf(remaining[0]));
                for (UUID id : participants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    p.sendActionBar(Text.c(text));
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                }
                remaining[0]--;
            } else {
                state = State.FIGHTING;
                countdownTask.cancel();
                String go = plugin.lang().get("match-go");
                for (UUID id : participants) {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null) continue;
                    p.showTitle(Title.title(Text.c(go), Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
                }
            }
        }, 0L, 20L);
    }

    private static Location spread(Location base, int idx) {
        Location l = base.clone();
        if (idx <= 0) return l;
        double angle = idx * (Math.PI / 2);
        double radius = 0.8 * ((idx + 3) / 4);
        l.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        return l;
    }

    private void applyKit(Player p) {
        Kit effective = plugin.core().kitManager().getEffectiveKit(p.getUniqueId(), kit);
        if (effective == null) effective = kit;

        PlayerInventory inv = p.getInventory();
        inv.clear();
        ItemStack[] contents = effective.contents();
        if (contents != null) {
            for (int s = 0; s < Math.min(contents.length, 36); s++) {
                ItemStack it = contents[s];
                if (it != null && it.getType() != Material.AIR) inv.setItem(s, it.clone());
            }
        }
        ItemStack[] armor = effective.armor();
        if (armor != null) {
            ItemStack[] copy = new ItemStack[4];
            for (int s = 0; s < Math.min(4, armor.length); s++) {
                copy[s] = armor[s] == null ? null : armor[s].clone();
            }
            inv.setArmorContents(copy);
        }
        ItemStack off = effective.offhand();
        if (off != null && off.getType() != Material.AIR) inv.setItemInOffHand(off.clone());

        GameMode gm = effective.gameMode();
        p.setGameMode(gm == null ? GameMode.SURVIVAL : gm);
        resetVitals(p);
    }

    private static void resetVitals(Player p) {
        for (PotionEffect pe : new HashSet<>(p.getActivePotionEffects())) {
            p.removePotionEffect(pe.getType());
        }
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    // --------------------------------------------------------- eleme

    public void eliminate(UUID id, UUID killer, boolean silent) {
        if (state == State.ENDED) return;
        if (!alive.remove(id)) return;
        lastDamager.remove(id);

        Player p = Bukkit.getPlayer(id);
        if (!silent) {
            String victim = PartyManager.name(id);
            if (killer != null && !killer.equals(id)) {
                broadcast("match-kill", "victim", victim, "killer", PartyManager.name(killer));
            } else {
                broadcast("match-death", "victim", victim);
            }
        }
        if (p != null) {
            p.getInventory().clear();
            resetVitals(p);
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(Text.c(plugin.lang().get("match-eliminated")));
        }
        checkEnd();
    }

    /** Oyuncu maci terk eder (komut, partiden ayrilma, cikis). */
    public void leave(UUID id) {
        if (!participants.contains(id)) return;
        boolean wasAlive = alive.remove(id);
        participants.remove(id);
        red.remove(id);
        blue.remove(id);
        manager.unbind(id);
        lastDamager.remove(id);

        Player p = Bukkit.getPlayer(id);
        if (p != null) restore(p);
        if (state != State.ENDED) {
            broadcast("match-left", "player", PartyManager.name(id));
            if (wasAlive) checkEnd();
        }
    }

    private void checkEnd() {
        if (state == State.ENDED) return;
        if (type == Type.FFA) {
            if (alive.size() <= 1) finish(alive.isEmpty() ? null : alive.iterator().next(), null);
            return;
        }
        boolean redAlive = false;
        boolean blueAlive = false;
        for (UUID id : alive) {
            if (red.contains(id)) redAlive = true;
            if (blue.contains(id)) blueAlive = true;
        }
        if (redAlive && blueAlive) return;
        if (redAlive) finish(null, Team.RED);
        else if (blueAlive) finish(null, Team.BLUE);
        else finish(null, null);
    }

    private void finish(UUID winner, Team winnerTeam) {
        state = State.ENDED;
        if (countdownTask != null) countdownTask.cancel();

        if (type == Type.FFA && winner != null) {
            broadcast("match-win-ffa", "winner", PartyManager.name(winner));
        } else if (type == Type.TEAM && winnerTeam != null) {
            String team = plugin.lang().raw(winnerTeam == Team.RED ? "team-red" : "team-blue");
            broadcast("match-win-team", "team", team);
        } else {
            broadcast("match-draw");
        }

        long delay = Math.max(20L, plugin.getConfig().getLong("match.end-delay-ticks", 100L));
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanup, delay);
    }

    /** Maci aninda iptal eder (parti dagildi / sunucu kapaniyor). */
    public void abort() {
        if (cleaned) return;
        if (state != State.ENDED) broadcast("match-aborted");
        state = State.ENDED;
        if (countdownTask != null) countdownTask.cancel();
        cleanup();
    }

    private void cleanup() {
        if (cleaned) return;
        cleaned = true;
        for (UUID id : new HashSet<>(participants)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restore(p);
            manager.unbind(id);
        }
        participants.clear();
        alive.clear();
        manager.unregister(this);
        releaseArena();
    }

    private void releaseArena() {
        Runnable release = () -> {
            if (arenaReleased) return;
            arenaReleased = true;
            arena.setInUse(false);
        };
        try {
            plugin.core().arenaManager().regenArenaAsync(arena, release);
        } catch (Throwable t) {
            release.run();
        }
        // regen geri cagrisi gelmezse arena sonsuza dek kilitli kalmasin
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, release, 20L * 30);
        else release.run();
    }

    /** Oyuncuyu PvPCore lobisine geri dondurur. */
    private void restore(Player p) {
        p.getInventory().clear();
        resetVitals(p);
        GameMode prev = previousMode.get(p.getUniqueId());
        p.setGameMode(prev == null ? GameMode.ADVENTURE : prev);

        Location spawn = plugin.core().settings().mainSpawn();
        if (spawn != null && spawn.getWorld() != null) p.teleport(spawn);

        plugin.core().lobbyItems().giveLobbyItems(p);
        if (plugin.core().settings().joinSpeedEnabled()) {
            int level = Math.max(1, plugin.core().settings().joinSpeedLevel());
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION,
                    level - 1, false, false, false));
        }
    }

    public void broadcast(String key, String... kv) {
        String msg = plugin.lang().get(key, kv);
        for (UUID id : participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(Text.c(msg));
        }
    }
}
