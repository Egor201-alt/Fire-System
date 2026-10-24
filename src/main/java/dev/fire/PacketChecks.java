package dev.fire;

import org.bukkit.configuration.ConfigurationSection;

final class NoSwingCheck extends Check {

    private double graceMs;

    NoSwingCheck(FirePlugin plugin) {
        super(plugin, "noswing", "NoSwing", "combat", 0);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        graceMs = s.getDouble("grace-ms", 120.0);
    }

    void attack(PacketData pd, long now) {
        pd.lastAttack = now;
        if ((now - pd.lastSwing) / 1_000_000.0 > 100.0) {
            pd.pendingSwing = true;
        }
    }

    void swing(PacketData pd, long now) {
        pd.lastSwing = now;
        pd.pendingSwing = false;
    }

    String poll(PacketData pd, long now) {
        if (pd.pendingSwing && (now - pd.lastAttack) / 1_000_000.0 > graceMs) {
            pd.pendingSwing = false;
            return raiseP(pd, 1.0) ? "attack without arm swing" : null;
        }
        if (!pd.pendingSwing) {
            lowerP(pd, 0.05);
        }
        return null;
    }
}

final class InvSpeedCheck extends Check {

    private int maxPerSecond;
    private double deviation;
    private double maxInterval;
    private double reactionMs;

    InvSpeedCheck(FirePlugin plugin) {
        super(plugin, "invspeed", "InvSpeed", "player", 0);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxPerSecond = s.getInt("max-clicks-per-second", 16);
        deviation = s.getDouble("pattern-deviation-ms", 3.0);
        maxInterval = s.getDouble("pattern-max-interval-ms", 110.0);
        reactionMs = s.getDouble("totem-reaction-ms", 100.0);
    }

    String click(PacketData pd, long now) {
        int len = pd.windowClicks.length;
        pd.windowClicks[pd.windowIdx] = now;
        pd.windowIdx = (pd.windowIdx + 1) % len;
        if (pd.windowCount < len) {
            pd.windowCount++;
        }
        int cps = 0;
        for (int i = 0; i < pd.windowCount; i++) {
            if (now - pd.windowClicks[i] <= 1_000_000_000L) {
                cps++;
            }
        }
        if (cps > maxPerSecond) {
            return raiseP(pd, 1.0) ? "clicks=" + cps + "/s" : null;
        }
        int samples = 14;
        if (pd.windowCount >= samples) {
            double sum = 0;
            double sumSq = 0;
            int cur = (pd.windowIdx - 1 + len) % len;
            long prev = pd.windowClicks[cur];
            for (int k = 0; k < samples - 1; k++) {
                cur = (cur - 1 + len) % len;
                double iv = (prev - pd.windowClicks[cur]) / 1_000_000.0;
                if (iv > 600 || iv < 0) {
                    lowerP(pd, 0.1);
                    return null;
                }
                sum += iv;
                sumSq += iv * iv;
                prev = pd.windowClicks[cur];
            }
            double mean = sum / (samples - 1);
            double sd = Math.sqrt(Math.max(0.0, sumSq / (samples - 1) - mean * mean));
            if (mean < maxInterval && sd < deviation) {
                return raiseP(pd, 1.0) ? "bot pattern sd=" + Util.f(sd) + "ms avg=" + Util.f(mean) + "ms" : null;
            }
        }
        lowerP(pd, 0.1);
        return null;
    }

    String totem(PacketData pd, long now) {
        long pop = pd.lastPop;
        if (pop == 0) {
            return null;
        }
        double dt = (now - pop) / 1_000_000.0;
        if (dt < 0 || dt > 1000.0) {
            return null;
        }
        pd.lastPop = 0;
        if (dt < reactionMs) {
            return raiseP(pd, 1.0) ? "totem swapped " + Util.f(dt) + "ms after pop" : null;
        }
        return null;
    }
}

final class BadPacketsCheck extends Check {

    record Hit(String detail, boolean cancel, boolean force) {
    }

    private double maxDelta;
    private double cursorTolerance;
    private int maxSprintToggles;

    BadPacketsCheck(FirePlugin plugin) {
        super(plugin, "badpackets", "BadPackets", "packet", 0);
    }

    @Override
    double defaultBuffer() {
        return 1.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxDelta = s.getDouble("max-position-delta", 8.0);
        cursorTolerance = s.getDouble("cursor-tolerance", 0.01);
        maxSprintToggles = s.getInt("max-sprint-toggles-per-second", 30);
    }

    Hit position(PacketData pd, double x, double y, double z, boolean grace) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || Double.isInfinite(x) || Double.isInfinite(y)
                || Double.isInfinite(z) || Math.abs(x) > 3.0E7 || Math.abs(z) > 3.0E7 || Math.abs(y) > 2.0E7) {
            return new Hit("invalid position", true, true);
        }
        if (pd.teleportPending) {
            return null;
        }
        if (!pd.hasPos) {
            accept(pd, x, y, z);
            return null;
        }
        double dx = x - pd.px;
        double dy = y - pd.py;
        double dz = z - pd.pz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!grace && dist > maxDelta) {
            return new Hit("moved " + Util.f(dist) + " blocks in one packet", true, false);
        }
        accept(pd, x, y, z);
        return null;
    }

    void accept(PacketData pd, double x, double y, double z) {
        pd.px = x;
        pd.py = y;
        pd.pz = z;
        pd.hasPos = true;
    }

    Hit abilities(boolean flying, boolean allowed) {
        if (flying && !allowed) {
            return new Hit("fly flag in abilities packet", true, true);
        }
        return null;
    }

    Hit cursor(float x, float y, float z) {
        double lo = -cursorTolerance;
        double hi = 1.0 + cursorTolerance;
        if (x < lo || x > hi || y < lo || y > hi || z < lo || z > hi) {
            return new Hit("invalid placement cursor", true, true);
        }
        return null;
    }

    Hit sprint(PacketData pd, long now) {
        if (now - pd.sprintWindow >= 1_000_000_000L) {
            pd.sprintWindow = now;
            pd.sprintToggles = 0;
        }
        if (++pd.sprintToggles > maxSprintToggles) {
            pd.sprintToggles = 0;
            return new Hit("sprint toggle spam", false, false);
        }
        return null;
    }

    boolean confirm(PacketData pd) {
        return raiseP(pd, 1.0);
    }
}
