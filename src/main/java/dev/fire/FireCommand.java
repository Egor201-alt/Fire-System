package dev.fire;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class FireCommand implements TabExecutor {

    private static final List<String> SUB = List.of(
            "help", "alerts", "status", "checks", "info", "history", "heat", "reset",
            "toggle", "watch", "exempt", "debug", "tp", "reload");

    private final FirePlugin plugin;

    FireCommand(FirePlugin plugin) {
        this.plugin = plugin;
    }

    private static String perm(String sub) {
        return switch (sub) {
            case "alerts" -> "fire.alerts";
            case "status", "checks", "info", "history", "heat" -> "fire.info";
            case "reset" -> "fire.reset";
            case "toggle" -> "fire.toggle";
            case "watch" -> "fire.watch";
            case "exempt" -> "fire.exempt";
            case "debug" -> "fire.debug";
            case "tp" -> "fire.tp";
            case "reload" -> "fire.reload";
            default -> null;
        };
    }

    private static boolean can(CommandSender s, String sub) {
        String p = perm(sub);
        return p == null ? hasAny(s) : s.hasPermission(p);
    }

    private static boolean hasAny(CommandSender s) {
        for (String sub : SUB) {
            String p = perm(sub);
            if (p != null && s.hasPermission(p)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(String sub) {
        return switch (sub) {
            case "help" -> "список команд";
            case "alerts" -> "включить или выключить оповещения";
            case "status" -> "состояние и производительность";
            case "checks" -> "список проверок";
            case "info" -> "нарушения игрока";
            case "history" -> "последние срабатывания игрока";
            case "heat" -> "уровень Heat игрока";
            case "reset" -> "сбросить нарушения игрока [проверка]";
            case "toggle" -> "включить или выключить проверку";
            case "watch" -> "усиленное наблюдение за игроком";
            case "exempt" -> "временно исключить игрока (минуты или off)";
            case "debug" -> "отладка проверки на себе";
            case "tp" -> "телепорт к игроку";
            case "reload" -> "перезагрузить конфиги";
            default -> "";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAny(sender)) {
            plugin.messages.send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (!SUB.contains(sub)) {
            plugin.messages.send(sender, "usage");
            return true;
        }
        if (!can(sender, sub)) {
            plugin.messages.send(sender, "no-permission");
            return true;
        }
        switch (sub) {
            case "help" -> help(sender);
            case "alerts" -> alerts(sender, args);
            case "status" -> status(sender);
            case "checks" -> checks(sender);
            case "info" -> info(sender, args);
            case "history" -> history(sender, args);
            case "heat" -> heat(sender, args);
            case "reset" -> reset(sender, args);
            case "toggle" -> toggle(sender, args);
            case "watch" -> watch(sender, args);
            case "exempt" -> exempt(sender, args);
            case "debug" -> debug(sender, args);
            case "tp" -> tp(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                plugin.messages.send(sender, "reloaded");
            }
            default -> plugin.messages.send(sender, "usage");
        }
        return true;
    }

    private Player target(CommandSender sender, String[] args, int idx) {
        if (args.length <= idx) {
            plugin.messages.send(sender, "usage");
            return null;
        }
        Player p = Bukkit.getPlayerExact(args[idx]);
        if (p == null) {
            plugin.messages.send(sender, "unknown-player");
        }
        return p;
    }

    private void help(CommandSender sender) {
        plugin.messages.send(sender, "help-header", "version", plugin.getPluginMeta().getVersion());
        for (String sub : SUB) {
            if (can(sender, sub)) {
                plugin.messages.send(sender, "help-line", "cmd", sub, "desc", describe(sub));
            }
        }
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages.send(sender, "player-only");
            return;
        }
        PlayerData d = plugin.data(p);
        boolean on = args.length > 1 ? args[1].equalsIgnoreCase("on") : !d.alerts;
        d.alerts = on;
        if (on) {
            plugin.alertSet.add(p.getUniqueId());
        } else {
            plugin.alertSet.remove(p.getUniqueId());
        }
        plugin.messages.send(sender, on ? "alerts-on" : "alerts-off");
    }

    private void status(CommandSender sender) {
        int enabled = 0;
        for (Check c : plugin.checks) {
            if (c.enabled) {
                enabled++;
            }
        }
        double avgMove = plugin.moveCount == 0 ? 0 : plugin.moveNanos / 1000.0 / plugin.moveCount;
        double avgAttack = plugin.attackCount == 0 ? 0 : plugin.attackNanos / 1000.0 / plugin.attackCount;
        plugin.messages.send(sender, "status",
                "mode", plugin.settings.enforce ? "enforce" : "monitor",
                "tps", Util.f(plugin.tps),
                "mspt", Util.f(plugin.mspt),
                "lagging", plugin.lagging ? "да" : "нет",
                "enabled", String.valueOf(enabled),
                "total", String.valueOf(plugin.checks.size()),
                "tracked", String.valueOf(plugin.players.size()),
                "move", Util.f(avgMove),
                "attack", Util.f(avgAttack),
                "native", Terrain.nativeCollisions() ? "да" : "нет",
                "crit", Compat.hasCritical() ? "да" : "нет",
                "breakspeed", Compat.hasBreakSpeed() ? "да" : "нет");
    }

    private void checks(CommandSender sender) {
        plugin.messages.send(sender, "checks-header");
        for (Check c : plugin.checks) {
            plugin.messages.send(sender, c.enabled ? "check-on" : "check-off",
                    "id", c.id, "name", c.name, "category", c.category, "buffer", Util.f(c.buffer));
        }
    }

    private void info(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        plugin.messages.send(sender, "info-header", "player", t.getName(), "heat", Util.f(d.heat),
                "ping", String.valueOf(t.getPing()), "watch", d.watch ? "да" : "нет");
        boolean any = false;
        for (Check c : plugin.checks) {
            double v = d.vl[c.index];
            if (v >= 0.5) {
                any = true;
                plugin.messages.send(sender, "info-line", "check", c.name, "vl", Util.f(v));
            }
        }
        if (!any) {
            plugin.messages.send(sender, "info-empty");
        }
    }

    private void history(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        if (d.history.isEmpty()) {
            plugin.messages.send(sender, "history-empty");
            return;
        }
        int limit = 15;
        if (args.length > 2) {
            try {
                limit = Math.max(1, Math.min(60, Integer.parseInt(args[2])));
            } catch (NumberFormatException ignored) {
            }
        }
        plugin.messages.send(sender, "history-header", "player", t.getName());
        int n = 0;
        for (String line : d.history) {
            if (n++ >= limit) {
                break;
            }
            plugin.messages.send(sender, "history-line", "line", line);
        }
    }

    private void heat(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        plugin.messages.send(sender, "heat-line", "player", t.getName(), "heat", Util.f(d.heat),
                "max", Util.f(plugin.settings.heatMax));
    }

    private void reset(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        if (args.length > 2) {
            Check c = plugin.checkById(args[2]);
            if (c == null) {
                plugin.messages.send(sender, "unknown-check");
                return;
            }
            d.vl[c.index] = 0;
            d.buf[c.index] = 0;
        } else {
            java.util.Arrays.fill(d.vl, 0.0);
            java.util.Arrays.fill(d.buf, 0.0);
            d.heat = 0;
            d.history.clear();
        }
        plugin.messages.send(sender, "reset-done", "player", t.getName());
    }

    private void toggle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages.send(sender, "usage");
            return;
        }
        Check c = plugin.checkById(args[1]);
        if (c == null) {
            plugin.messages.send(sender, "unknown-check");
            return;
        }
        c.enabled = !c.enabled;
        plugin.checksCfg.set(c.id + ".enabled", c.enabled);
        plugin.saveChecks();
        plugin.rebuildHooks();
        plugin.messages.send(sender, "toggled", "check", c.name, "state", c.enabled ? "включена" : "выключена");
    }

    private void watch(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        d.watch = !d.watch;
        plugin.messages.send(sender, d.watch ? "watch-on" : "watch-off", "player", t.getName());
    }

    private void exempt(CommandSender sender, String[] args) {
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        PlayerData d = plugin.data(t);
        if (args.length < 3 || args[2].equalsIgnoreCase("off")) {
            d.exemptUntilMs = 0;
            plugin.messages.send(sender, "exempt-off", "player", t.getName());
            return;
        }
        try {
            long minutes = Math.max(1, Long.parseLong(args[2]));
            d.exemptUntilMs = System.currentTimeMillis() + minutes * 60_000L;
            plugin.messages.send(sender, "exempt-on", "player", t.getName(), "minutes", String.valueOf(minutes));
        } catch (NumberFormatException e) {
            plugin.messages.send(sender, "usage");
        }
    }

    private void debug(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages.send(sender, "player-only");
            return;
        }
        PlayerData d = plugin.data(p);
        if (args.length < 2 || args[1].equalsIgnoreCase("off")) {
            d.debugIndex = -1;
            plugin.messages.send(sender, "debug-off");
            return;
        }
        Check c = plugin.checkById(args[1]);
        if (c == null) {
            plugin.messages.send(sender, "unknown-check");
            return;
        }
        d.debugIndex = c.index;
        plugin.messages.send(sender, "debug-on", "check", c.name);
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages.send(sender, "player-only");
            return;
        }
        Player t = target(sender, args, 1);
        if (t == null) {
            return;
        }
        p.teleport(t.getLocation());
        plugin.messages.send(sender, "tp-done", "player", t.getName());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!hasAny(sender)) {
            return out;
        }
        if (args.length == 1) {
            String pre = args[0].toLowerCase(Locale.ROOT);
            for (String s : SUB) {
                if (can(sender, s) && s.startsWith(pre)) {
                    out.add(s);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUB.contains(sub) || !can(sender, sub)) {
            return out;
        }
        String pre = args[args.length - 1].toLowerCase(Locale.ROOT);
        boolean playerArg = args.length == 2 && List.of("info", "history", "heat", "reset", "watch", "exempt", "tp").contains(sub);
        boolean checkArg = (args.length == 2 && List.of("toggle", "debug").contains(sub))
                || (args.length == 3 && sub.equals("reset"));
        if (playerArg) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pre)) {
                    out.add(p.getName());
                }
            }
        } else if (checkArg) {
            for (Check c : plugin.checks) {
                if (c.id.startsWith(pre)) {
                    out.add(c.id);
                }
            }
            if (sub.equals("debug") && "off".startsWith(pre)) {
                out.add("off");
            }
        } else if (sub.equals("alerts") && args.length == 2) {
            for (String s : List.of("on", "off")) {
                if (s.startsWith(pre)) {
                    out.add(s);
                }
            }
        } else if (sub.equals("exempt") && args.length == 3 && "off".startsWith(pre)) {
            out.add("off");
        }
        return out;
    }
}
