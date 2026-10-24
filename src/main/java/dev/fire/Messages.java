package dev.fire;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

final class Messages {

    private final FirePlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private YamlConfiguration yml = new YamlConfiguration();

    Messages(FirePlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        yml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
    }

    String raw(String key) {
        String s = yml.getString(key);
        return s == null ? key : s;
    }

    Component parse(String key, String... kv) {
        return mm.deserialize(fill(raw(key), kv));
    }

    String fill(String s, String... kv) {
        s = s.replace("{prefix}", raw("prefix"));
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", mm.escapeTags(kv[i + 1]));
        }
        return s;
    }

    Component mini(String s) {
        return mm.deserialize(s);
    }

    void send(CommandSender to, String key, String... kv) {
        to.sendMessage(parse(key, kv));
    }
}
