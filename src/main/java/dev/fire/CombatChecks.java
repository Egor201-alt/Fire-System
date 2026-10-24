package dev.fire;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

final class ReachCheck extends Check {

    private double tolerance;
    private double pingCompensation;
    private int maxPing;

    ReachCheck(FirePlugin plugin) {
        super(plugin, "reach", "Reach", "combat", ATTACK);
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.35);
        pingCompensation = s.getDouble("ping-compensation", 0.002);
        maxPing = s.getInt("max-ping", 300);
    }

    @Override
    void attack(Player p, PlayerData d, Entity target, EntityDamageByEntityEvent e) {
        Location eye = p.getEyeLocation();
        double range = Util.attr(p, Attribute.ENTITY_INTERACTION_RANGE, 3.0);
        double dist = Util.distToBox(eye.getX(), eye.getY(), eye.getZ(), target.getBoundingBox());
        int ping = Math.min(Math.max(p.getPing(), 0), maxPing);
        double max = range + (tolerance + ping * pingCompensation) * len();
        if (debugging(d)) {
            p.sendActionBar(net.kyori.adventure.text.Component.text("reach " + Util.f(dist) + "/" + Util.f(max)));
        }
        if (dist > max) {
            if (raise(d, 1.0)) {
                flag(p, d, "dist=" + Util.f(dist) + " max=" + Util.f(max) + " ping=" + ping);
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class HitboxCheck extends Check {

    private double expand;
    private double pingCompensation;
    private int maxPing;

    HitboxCheck(FirePlugin plugin) {
        super(plugin, "hitbox", "Hitbox", "combat", ATTACK);
    }

    @Override
    void configure(ConfigurationSection s) {
        expand = s.getDouble("expand", 0.35);
        pingCompensation = s.getDouble("ping-compensation", 0.0015);
        maxPing = s.getInt("max-ping", 300);
    }

    @Override
    void attack(Player p, PlayerData d, Entity target, EntityDamageByEntityEvent e) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        BoundingBox b = target.getBoundingBox();
        double range = Util.attr(p, Attribute.ENTITY_INTERACTION_RANGE, 3.0) + 2.0;
        int ping = Math.min(Math.max(p.getPing(), 0), maxPing);
        double ex = (expand + ping * pingCompensation) * len();
        boolean hit = Util.rayHits(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(),
                b.getMinX() - ex, b.getMinY() - ex, b.getMinZ() - ex, b.getMaxX() + ex, b.getMaxY() + ex, b.getMaxZ() + ex, range);
        if (!hit) {
            if (raise(d, 1.0)) {
                flag(p, d, "not looking at target ex=" + Util.f(ex) + " ping=" + ping);
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class MultiAuraCheck extends Check {

    private int targets;
    private int window;

    MultiAuraCheck(FirePlugin plugin) {
        super(plugin, "multiaura", "MultiAura", "combat", ATTACK);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        targets = s.getInt("targets", 3);
        window = s.getInt("window-ticks", 4);
    }

    @Override
    void attack(Player p, PlayerData d, Entity target, EntityDamageByEntityEvent e) {
        int tick = Bukkit.getCurrentTick();
        d.atkIds[d.atkIdx] = target.getEntityId();
        d.atkTicks[d.atkIdx] = tick;
        d.atkIdx = (d.atkIdx + 1) % d.atkIds.length;
        int distinct = 0;
        for (int i = 0; i < d.atkIds.length; i++) {
            if (tick - d.atkTicks[i] > window) {
                continue;
            }
            boolean dup = false;
            for (int j = 0; j < i; j++) {
                if (tick - d.atkTicks[j] <= window && d.atkIds[j] == d.atkIds[i]) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                distinct++;
            }
        }
        if (distinct >= targets) {
            if (raise(d, 1.0)) {
                flag(p, d, distinct + " targets in " + window + " ticks");
            }
        } else {
            lower(d, 0.1);
        }
    }
}

final class ClickerCheck extends Check {

    private double maxCps;
    private double deviation;
    private int samples;
    private double maxInterval;

    ClickerCheck(FirePlugin plugin) {
        super(plugin, "clicker", "Clicker", "combat", SWING);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxCps = s.getDouble("max-cps", 22.0);
        deviation = s.getDouble("pattern-deviation-ms", 3.5);
        samples = Math.min(46, Math.max(10, s.getInt("pattern-samples", 40)));
        maxInterval = s.getDouble("pattern-max-interval-ms", 140.0);
    }

    @Override
    void swing(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        if (d.mining && now - d.miningSince < 15000L) {
            return;
        }
        int len = d.clicks.length;
        d.clicks[d.clickIdx] = now;
        d.clickIdx = (d.clickIdx + 1) % len;
        if (d.clickCount < len) {
            d.clickCount++;
        }
        int cps = 0;
        for (int i = 0; i < d.clickCount; i++) {
            if (now - d.clicks[i] <= 1000L) {
                cps++;
            }
        }
        if (cps > maxCps) {
            if (raise(d, 1.0)) {
                flag(p, d, "cps=" + cps);
            }
            return;
        }
        if (d.clickCount < samples) {
            return;
        }
        double sum = 0;
        double sumSq = 0;
        int n = samples - 1;
        int idx = (d.clickIdx - 1 + len) % len;
        long prev = d.clicks[idx];
        for (int k = 0; k < n; k++) {
            idx = (idx - 1 + len) % len;
            long cur = d.clicks[idx];
            double iv = prev - cur;
            if (iv > 1000 || iv < 0) {
                lower(d, 0.2);
                return;
            }
            sum += iv;
            sumSq += iv * iv;
            prev = cur;
        }
        double mean = sum / n;
        double var = Math.max(0.0, sumSq / n - mean * mean);
        double sd = Math.sqrt(var);
        if (mean < maxInterval && sd < deviation) {
            if (raise(d, 1.0)) {
                flag(p, d, "pattern sd=" + Util.f(sd) + "ms avg=" + Util.f(mean) + "ms");
            }
        } else {
            lower(d, 0.1);
        }
    }
}

final class CriticalsCheck extends Check {

    CriticalsCheck(FirePlugin plugin) {
        super(plugin, "criticals", "Criticals", "combat", ATTACK);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void attack(Player p, PlayerData d, Entity target, EntityDamageByEntityEvent e) {
        if (!Compat.critical(e)) {
            return;
        }
        if (d.groundServer && d.airTicks == 0 && !d.moveExempt && Bukkit.getCurrentTick() >= d.velocityUntil) {
            if (raise(d, 1.0)) {
                flag(p, d, "critical on ground fall=" + Util.f3(p.getFallDistance()));
            }
        } else {
            lower(d, 0.5);
        }
    }
}
