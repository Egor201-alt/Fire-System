package dev.fire;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Violations {

    private final FirePlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final DateTimeFormatter time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Fire-IO");
        t.setDaemon(true);
        return t;
    });

    Violations(FirePlugin plugin) {
        this.plugin = plugin;
    }

    void shutdown() {
        io.shutdown();
    }

    void flag(Player p, PlayerData d, Check c, String detail) {
        Settings s = plugin.settings;
        int i = c.index;
        double before = d.vl[i];
        double after = before + c.vl;
        d.vl[i] = after;
        double heatBefore = d.heat;
        if (s.heatEnabled) {
            d.heat = Math.min(s.heatMax, d.heat + c.heat * c.vl);
        }
        long now = System.currentTimeMillis();
        String stamp = time.format(java.time.Instant.ofEpochMilli(now));
        d.addHistory(stamp + " " + c.name + " vl=" + Util.f(after) + " " + detail, s.historySize);
        if (s.enforce && c.cancel) {
            plugin.cancelRequested = true;
        }
        if (c.alert && now - d.lastAlert[i] >= s.alertCooldownMs) {
            d.lastAlert[i] = now;
            alert(p, d, c, detail);
        }
        if (s.heatEnabled && heatBefore < s.heatAlertAt && d.heat >= s.heatAlertAt) {
            broadcast(plugin.messages.parse("heat-alert", "player", p.getName(), "heat", Util.f(d.heat)));
        }
        if (s.logFile) {
            log(stamp + " " + p.getName() + " " + c.name + " vl=" + Util.f(after) + " " + detail);
        }
        if (!s.enforce) {
            return;
        }
        for (Settings.Action a : s.actions) {
            if (a.matches(c) && before < a.at() && after >= a.at()) {
                run(a.run(), p, d, c);
            }
        }
        if (s.heatEnabled) {
            for (Settings.Action a : s.heatActions) {
                if (heatBefore < a.at() && d.heat >= a.at()) {
                    run(a.run(), p, d, c);
                }
            }
        }
    }

    private void alert(Player p, PlayerData d, Check c, String detail) {
        Component msg = plugin.messages.parse("alert",
                "player", p.getName(),
                "check", c.name,
                "category", c.category,
                "vl", Util.f(d.vl[c.index]),
                "detail", detail,
                "ping", String.valueOf(p.getPing()),
                "tps", Util.f(plugin.tps),
                "heat", Util.f(d.heat));
        broadcast(msg);
    }

    void broadcast(Component msg) {
        for (UUID id : plugin.alertSet) {
            Player st = Bukkit.getPlayer(id);
            if (st != null) {
                st.sendMessage(msg);
            }
        }
        if (plugin.settings.alertConsole) {
            plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(msg));
        }
    }

    private void log(String line) {
        Path file = plugin.getDataFolder().toPath().resolve("violations.log");
        io.execute(() -> {
            try {
                Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
        });
    }

    private void run(List<String> tokens, Player p, PlayerData d, Check c) {
        String check = c == null ? "heat" : c.name;
        String vl = c == null ? "0" : Util.f(d.vl[c.index]);
        for (String raw : tokens) {
            String t = raw.replace("{player}", p.getName()).replace("{check}", check)
                    .replace("{vl}", vl).replace("{heat}", Util.f(d.heat));
            String low = t.toLowerCase(Locale.ROOT);
            if (low.equals("setback")) {
                plugin.setback(p, d);
            } else if (low.equals("kick")) {
                p.kick(plugin.messages.parse("kick-default"));
            } else if (low.startsWith("kick:")) {
                p.kick(mm.deserialize(t.substring(5)));
            } else if (low.startsWith("cmd:")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), t.substring(4).trim());
            } else if (low.startsWith("freeze:")) {
                try {
                    long seconds = Long.parseLong(t.substring(7).trim());
                    d.frozenUntilMs = System.currentTimeMillis() + seconds * 1000L;
                    plugin.messages.send(p, "frozen", "seconds", String.valueOf(seconds));
                } catch (NumberFormatException ignored) {
                }
            } else if (low.startsWith("message:")) {
                p.sendMessage(mm.deserialize(t.substring(8)));
            } else if (low.startsWith("broadcast:")) {
                broadcast(mm.deserialize(t.substring(10)));
            } else if (low.equals("reset")) {
                java.util.Arrays.fill(d.vl, 0.0);
                d.heat = 0;
            }
        }
    }

    void decay() {
        double heatDecay = plugin.settings.heatDecay;
        List<Check> checks = plugin.checks;
        for (PlayerData d : plugin.players.values()) {
            for (int i = 0; i < d.vl.length; i++) {
                double v = d.vl[i];
                if (v > 0) {
                    d.vl[i] = Math.max(0.0, v - checks.get(i).decay / 60.0);
                }
            }
            if (d.heat > 0) {
                d.heat = Math.max(0.0, d.heat - heatDecay);
            }
        }
    }
}
