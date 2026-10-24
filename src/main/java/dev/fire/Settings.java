package dev.fire;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class Settings {

    record Action(String target, double at, List<String> run) {
        boolean matches(Check c) {
            return target.equals("*") || target.equalsIgnoreCase(c.id) || target.equalsIgnoreCase(c.category);
        }
    }

    boolean enforce;
    double leniency = 1.0;
    double tpsGuard = 17.0;
    int pingGuard = 600;
    int joinGrace = 60;
    int teleportGrace = 12;
    int velocityGrace = 12;
    int explosionGrace = 25;
    int windChargeGrace = 80;
    int boostGrace = 40;
    int damageGrace = 4;
    boolean alertsDefaultOn = true;
    boolean alertConsole = true;
    boolean logFile = true;
    long alertCooldownMs = 1500;
    int historySize = 40;
    boolean heatEnabled = true;
    double heatMax = 100;
    double heatDecay = 0.35;
    double heatAlertAt = 60;
    final Set<String> disabledWorlds = new HashSet<>();
    final Set<GameMode> exemptModes = EnumSet.noneOf(GameMode.class);
    final List<Action> actions = new ArrayList<>();
    final List<Action> heatActions = new ArrayList<>();

    void load(FileConfiguration c) {
        enforce = c.getString("general.mode", "monitor").equalsIgnoreCase("enforce");
        leniency = c.getDouble("general.leniency", 1.0);
        joinGrace = c.getInt("general.join-grace-ticks", 60);
        teleportGrace = c.getInt("general.teleport-grace-ticks", 12);
        velocityGrace = c.getInt("general.velocity-grace-ticks", 12);
        explosionGrace = c.getInt("general.explosion-grace-ticks", 25);
        windChargeGrace = c.getInt("general.wind-charge-grace-ticks", 80);
        boostGrace = c.getInt("general.elytra-boost-grace-ticks", 40);
        damageGrace = c.getInt("general.damage-grace-ticks", 4);
        tpsGuard = c.getDouble("performance.tps-guard", 17.0);
        pingGuard = c.getInt("performance.ping-guard", 600);
        alertsDefaultOn = c.getBoolean("alerts.default-on", true);
        alertCooldownMs = c.getLong("alerts.cooldown-ms", 1500L);
        alertConsole = c.getBoolean("alerts.console", true);
        logFile = c.getBoolean("alerts.log-to-file", true);
        historySize = Math.max(5, c.getInt("alerts.history-size", 40));
        heatEnabled = c.getBoolean("heat.enabled", true);
        heatMax = c.getDouble("heat.max", 100.0);
        heatDecay = c.getDouble("heat.decay-per-second", 0.35);
        heatAlertAt = c.getDouble("heat.alert-at", 60.0);
        disabledWorlds.clear();
        disabledWorlds.addAll(c.getStringList("general.disabled-worlds"));
        exemptModes.clear();
        for (String s : c.getStringList("general.exempt-gamemodes")) {
            try {
                exemptModes.add(GameMode.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        parse(c.getMapList("actions"), actions, "check", "vl");
        parse(c.getMapList("heat.actions"), heatActions, "check", "at");
    }

    private static void parse(List<Map<?, ?>> src, List<Action> out, String targetKey, String atKey) {
        out.clear();
        for (Map<?, ?> m : src) {
            Object at = m.containsKey(atKey) ? m.get(atKey) : m.get("at");
            Object run = m.get("do");
            if (!(at instanceof Number n) || !(run instanceof List<?> l)) {
                continue;
            }
            List<String> cmds = new ArrayList<>();
            for (Object o : l) {
                cmds.add(String.valueOf(o));
            }
            Object target = m.get(targetKey);
            out.add(new Action(target == null ? "*" : String.valueOf(target), n.doubleValue(), cmds));
        }
    }
}
