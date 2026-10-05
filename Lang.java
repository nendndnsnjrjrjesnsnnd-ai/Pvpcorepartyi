package dev.pvpcore.party;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Lang {
    private final PartyPlugin plugin;
    private YamlConfiguration cfg;

    public Lang(PartyPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        InputStream in = plugin.getResource("messages.yml");
        if (in != null) {
            try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                y.setDefaults(YamlConfiguration.loadConfiguration(r));
            } catch (Exception ignored) {
            }
        }
        this.cfg = y;
    }

    public String raw(String key) {
        String s = cfg.getString(key);
        return s == null ? key : s;
    }

    public List<String> list(String key) {
        return cfg.getStringList(key);
    }

    /** kv = anahtar,deger,anahtar,deger... ; metindeki %anahtar% degistirilir. */
    public String get(String key, String... kv) {
        return apply(raw(key), kv);
    }

    public String apply(String text, String... kv) {
        String s = text.replace("%prefix%", cfg.getString("prefix", ""));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("%" + kv[i] + "%", kv[i + 1]);
        }
        return s;
    }
}
