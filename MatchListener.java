package dev.pvpcore.party.match;

import dev.pvpcore.party.PartyPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.UUID;

public final class MatchListener implements Listener {
    private final PartyPlugin plugin;

    public MatchListener(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    private PartyMatch matchOf(Entity e) {
        return e instanceof Player p ? plugin.matches().of(p.getUniqueId()) : null;
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private static boolean hasTotem(Player p) {
        PlayerInventory inv = p.getInventory();
        return inv.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
                || inv.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    // ------------------------------------------------------------- hasar / olum

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        PartyMatch m = plugin.matches().of(victim.getUniqueId());

        Player attacker = null;
        if (e instanceof EntityDamageByEntityEvent be) attacker = resolveAttacker(be.getDamager());

        if (m == null) {
            // Maci olan biri maç dışındakine vuramaz
            if (attacker != null && plugin.matches().inMatch(attacker.getUniqueId())) e.setCancelled(true);
            return;
        }
        if (m.state() != PartyMatch.State.FIGHTING || !m.isAlive(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (attacker != null) {
            PartyMatch am = plugin.matches().of(attacker.getUniqueId());
            if (am != m || !m.isAlive(attacker.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            if (!attacker.getUniqueId().equals(victim.getUniqueId())
                    && m.sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            m.recordDamager(victim.getUniqueId(), attacker.getUniqueId());
        }

        // Olumcul darbe: totem yoksa oyuncuyu olduruldugu gibi elemis say (olum ekrani yok)
        if (victim.getHealth() - e.getFinalDamage() > 0) return;
        if (hasTotem(victim)) return;
        e.setCancelled(true);
        m.eliminate(victim.getUniqueId(), m.lastDamager(victim.getUniqueId()), false);
    }

    /** Yedek: baska bir yolla olurse yine de temiz elensin. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        PartyMatch m = plugin.matches().of(p.getUniqueId());
        if (m == null) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        m.eliminate(p.getUniqueId(), m.lastDamager(p.getUniqueId()), false);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isDead()) p.spigot().respawn();
        });
    }

    // ------------------------------------------------------------ hareket / esya

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        PartyMatch m = plugin.matches().of(e.getPlayer().getUniqueId());
        if (m == null || m.state() != PartyMatch.State.COUNTDOWN) return;
        if (e.getTo() == null) return;
        if (e.getFrom().getX() != e.getTo().getX() || e.getFrom().getZ() != e.getTo().getZ()) {
            e.setTo(e.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        PartyMatch m = plugin.matches().of(e.getPlayer().getUniqueId());
        if (m != null && m.state() != PartyMatch.State.FIGHTING) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent e) {
        PartyMatch m = matchOf(e.getEntity());
        if (m == null) return;
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if ((r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN)
                && !m.kit().naturalRegen()) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        PartyMatch m = matchOf(e.getEntity());
        if (m == null) return;
        if (!m.kit().naturalSaturationDecrease() && e.getFoodLevel() < ((Player) e.getEntity()).getFoodLevel()) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ bloklar

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        PartyMatch m = plugin.matches().of(e.getPlayer().getUniqueId());
        if (m == null) return;
        if (m.state() != PartyMatch.State.FIGHTING || !m.isAlive(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        m.trackPlaced(e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        PartyMatch m = plugin.matches().of(e.getPlayer().getUniqueId());
        if (m == null) return;
        if (m.state() != PartyMatch.State.FIGHTING || !m.isAlive(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (m.untrackPlaced(e.getBlock())) return;
        if (!m.kit().canBreakBlock(e.getBlock().getType())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ komut / cikis

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        PartyMatch m = plugin.matches().of(p.getUniqueId());
        if (m == null) return;

        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (msg.startsWith("/")) msg = msg.substring(1);
        String cmd = msg.split("\\s+")[0];
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);

        if (cmd.equals("p") || cmd.equals("party") || cmd.equals("parti")) return;

        if (cmd.equals("leave")) {
            e.setCancelled(true);
            UUID id = p.getUniqueId();
            plugin.send(p, "match-forfeit");
            m.leave(id);
            return;
        }
        if (p.hasPermission("pvpcoreparty.bypass")) return;
        if (!plugin.core().settings().allowCommandsInMatch()) {
            e.setCancelled(true);
            plugin.send(p, "command-blocked");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        PartyMatch m = plugin.matches().of(e.getPlayer().getUniqueId());
        if (m != null) m.leave(e.getPlayer().getUniqueId());
    }
}
