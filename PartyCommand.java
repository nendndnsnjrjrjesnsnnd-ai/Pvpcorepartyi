package dev.pvpcore.party;

import dev.pvpcore.party.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList(
            "davet", "kabul", "reddet", "at", "transfer", "dagit", "sohbet", "temizle", "ayril", "liste", "menu");

    private final PartyPlugin plugin;

    public PartyCommand(PartyPlugin plugin) {
        this.plugin = plugin;
    }

    /** Turkce karakterleri sadelestirir: "dağıt" -> "dagit". */
    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT)
                .replace('ı', 'i').replace('ğ', 'g').replace('ş', 's')
                .replace('ü', 'u').replace('ö', 'o').replace('ç', 'c');
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.send(sender, "only-players");
            return true;
        }
        if (args.length == 0) {
            help(p);
            return true;
        }
        String sub = norm(args[0]);
        PartyManager pm = plugin.parties();

        switch (sub) {
            case "davet", "invite", "ekle" -> {
                if (args.length < 2) { help(p); return true; }
                Player t = pm.findVisible(p, args[1]);
                if (t == null) { plugin.send(p, "player-offline"); return true; }
                pm.invite(p, t);
            }
            case "kabul", "accept" -> pm.accept(p, args.length > 1 ? args[1] : null);
            case "reddet", "deny" -> pm.deny(p, args.length > 1 ? args[1] : null);
            case "at", "kick" -> {
                if (args.length < 2) { help(p); return true; }
                UUID target = memberByName(p, args[1]);
                if (target == null) { plugin.send(p, "not-member", "player", args[1]); return true; }
                pm.kick(p, target);
            }
            case "transfer" -> {
                if (args.length < 2) { help(p); return true; }
                UUID target = memberByName(p, args[1]);
                if (target == null) { plugin.send(p, "not-member", "player", args[1]); return true; }
                pm.transfer(p, target);
            }
            case "dagit", "disband" -> {
                Party party = pm.get(p);
                if (party == null) { plugin.send(p, "not-in-party"); return true; }
                if (!party.isLeader(p.getUniqueId())) { plugin.send(p, "not-leader"); return true; }
                pm.disband(party, true);
            }
            case "sohbet", "chat", "c" -> {
                if (args.length > 1) {
                    pm.partyChat(p, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                } else {
                    pm.toggleChat(p);
                }
            }
            case "temizle", "clear" -> pm.clear(p);
            case "ayril", "leave" -> {
                if (pm.get(p) == null) { plugin.send(p, "not-in-party"); return true; }
                pm.leave(p.getUniqueId(), false);
                plugin.send(p, "you-left");
            }
            case "liste", "list", "bilgi" -> list(p);
            case "menu", "gui", "yonetim" -> plugin.menus().openMain(p);
            default -> help(p);
        }
        return true;
    }

    private UUID memberByName(Player p, String name) {
        Party party = plugin.parties().get(p);
        if (party == null) return null;
        for (UUID m : party.members()) {
            if (PartyManager.name(m).equalsIgnoreCase(name)) return m;
        }
        return null;
    }

    private void help(Player p) {
        for (String line : plugin.lang().list("help")) {
            p.sendMessage(Text.c(plugin.lang().apply(line)));
        }
    }

    private void list(Player p) {
        Party party = plugin.parties().get(p);
        if (party == null) {
            plugin.send(p, "not-in-party");
            return;
        }
        plugin.send(p, "list-header", "size", String.valueOf(party.size()),
                "max", String.valueOf(plugin.parties().maxSize()));
        for (UUID m : party.members()) {
            plugin.send(p, party.isLeader(m) ? "list-leader" : "list-member", "player", PartyManager.name(m));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player p)) return out;
        if (args.length == 1) {
            for (String s : SUBS) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            }
        } else if (args.length == 2) {
            String sub = norm(args[0]);
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if (sub.equals("davet") || sub.equals("invite")) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (o != p && o.getName().toLowerCase(Locale.ROOT).startsWith(prefix)
                            && plugin.parties().get(o) == null) out.add(o.getName());
                }
            } else if (sub.equals("at") || sub.equals("transfer") || sub.equals("kick")) {
                Party party = plugin.parties().get(p);
                if (party != null) {
                    for (UUID m : party.members()) {
                        String n = PartyManager.name(m);
                        if (!m.equals(p.getUniqueId()) && n.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(n);
                    }
                }
            }
        }
        return out;
    }
}
