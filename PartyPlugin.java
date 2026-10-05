package dev.pvpcore.party;

import dev.pvpcore.party.gui.GuiListener;
import dev.pvpcore.party.gui.PartyMenus;
import dev.pvpcore.party.match.MatchListener;
import dev.pvpcore.party.match.MatchManager;
import dev.pvpcore.party.util.Text;
import dev.rean.pvpcore.PvPCorePlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PartyPlugin extends JavaPlugin {
    private PvPCorePlugin core;
    private Lang lang;
    private PartyManager parties;
    private MatchManager matches;
    private PartyMenus menus;
    private PartyItem partyItem;

    @Override
    public void onEnable() {
        Plugin pc = getServer().getPluginManager().getPlugin("PvPCore");
        if (!(pc instanceof PvPCorePlugin)) {
            getLogger().severe("PvPCore bulunamadi! Eklenti kapatiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.core = (PvPCorePlugin) pc;

        saveDefaultConfig();
        this.lang = new Lang(this);
        this.parties = new PartyManager(this);
        this.matches = new MatchManager(this);
        this.menus = new PartyMenus(this);
        this.partyItem = new PartyItem(this);

        getServer().getPluginManager().registerEvents(new GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PartyListener(this), this);
        getServer().getPluginManager().registerEvents(new MatchListener(this), this);
        getServer().getPluginManager().registerEvents(partyItem, this);
        partyItem.start();

        PluginCommand cmd = getCommand("p");
        if (cmd != null) {
            PartyCommand handler = new PartyCommand(this);
            cmd.setExecutor(handler);
            cmd.setTabCompleter(handler);
        }
        getLogger().info("PvPCoreParty aktif (PvPCore ile entegre).");
    }

    @Override
    public void onDisable() {
        if (matches != null) matches.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        lang.reload();
    }

    public PvPCorePlugin core() { return core; }
    public Lang lang() { return lang; }
    public PartyManager parties() { return parties; }
    public MatchManager matches() { return matches; }
    public PartyMenus menus() { return menus; }
    public PartyItem partyItem() { return partyItem; }

    public void send(CommandSender to, String key, String... kv) {
        to.sendMessage(Text.c(lang.get(key, kv)));
    }
}
