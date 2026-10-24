package dev.fire;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class SpeedCheck extends Check {

    private double tolerance;
    private double jumpBoost;

    SpeedCheck(FirePlugin plugin) {
        super(plugin, "speed", "Speed", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 5.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.04);
        jumpBoost = s.getDouble("jump-boost", 0.2);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || m.h < 0.005 || d.moveExempt || m.unloaded || m.special()) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil) {
            return;
        }
        double attr = Util.attr(p, Attribute.MOVEMENT_SPEED, 0.1);
        boolean prevGround = d.groundServer;
        double accel;
        if (prevGround) {
            double g = d.lastSlip * 0.91;
            accel = attr * 0.16277136 / (g * g * g);
        } else {
            accel = p.isSprinting() ? 0.026 : 0.02;
        }
        double limit = d.lastH * d.carryFriction + accel;
        if (prevGround && m.dy > 0.001) {
            limit += jumpBoost;
        }
        limit += tolerance * len();
        if (debugging(d)) {
            p.sendActionBar(Component.text("speed h=" + Util.f3(m.h) + " max=" + Util.f3(limit)));
        }
        if (m.h > limit) {
            if (raise(d, 1.0)) {
                flag(p, d, "h=" + Util.f3(m.h) + " max=" + Util.f3(limit));
            }
        } else {
            lower(d, 0.35);
        }
    }
}

final class FlightCheck extends Check {

    private double tolerance;

    FlightCheck(FirePlugin plugin) {
        super(plugin, "flight", "Flight", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 2.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.02);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special() || d.groundServer) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil || m.tick < d.bounceUntil) {
            return;
        }
        if (p.hasPotionEffect(PotionEffectType.LEVITATION)) {
            return;
        }
        double gravity = Util.attr(p, Attribute.GRAVITY, 0.08);
        double max = (d.lastDy - gravity) * 0.98 + tolerance * len();
        if (debugging(d)) {
            p.sendActionBar(Component.text("flight dy=" + Util.f3(m.dy) + " max=" + Util.f3(max)));
        }
        if (m.dy > max) {
            if (Math.abs(m.dy - Util.jumpPower(p)) < AirJumpCheck.WINDOW) {
                return;
            }
            if (raise(d, 1.0)) {
                flag(p, d, "dy=" + Util.f3(m.dy) + " max=" + Util.f3(max));
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class HoverCheck extends Check {

    private int ticks;
    private double distance;

    HoverCheck(FirePlugin plugin) {
        super(plugin, "hover", "Hover", "movement", TICK);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        ticks = s.getInt("idle-ticks", 30);
        distance = s.getDouble("ground-distance", 0.5);
    }

    @Override
    void tick(Player p, PlayerData d) {
        int now = Bukkit.getCurrentTick();
        if (d.moveExempt || now < d.velocityUntil || now < d.specialUntil || now < d.bounceUntil) {
            return;
        }
        if (now - d.lastMoveTick < ticks || p.getPing() > plugin.settings.pingGuard) {
            lower(d, 1.0);
            return;
        }
        Location l = p.getLocation();
        Move s = plugin.scratch;
        s.world = l.getWorld();
        s.tx = l.getX();
        s.ty = l.getY();
        s.tz = l.getZ();
        Terrain.classify(s);
        if (s.unloaded || s.special()) {
            return;
        }
        boolean near = Terrain.collides(s.world, s.tx - 0.3, s.ty - distance, s.tz - 0.3, s.tx + 0.3, s.ty, s.tz + 0.3);
        if (!near) {
            if (raise(d, 1.0)) {
                flag(p, d, "idle=" + (now - d.lastMoveTick) + "t y=" + Util.f(s.ty));
            }
        } else {
            lower(d, 1.0);
        }
    }
}

final class GroundCheck extends Check {

    private double distance;
    private int antiHungerTicks;

    GroundCheck(FirePlugin plugin) {
        super(plugin, "ground", "Ground", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 2.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        distance = s.getDouble("distance", 0.4);
        antiHungerTicks = s.getInt("antihunger-ticks", 10);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special()) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil || m.tick < d.bounceUntil) {
            return;
        }
        if (m.clientGround && !m.ground) {
            boolean wide = Terrain.collides(m.world, m.tx - 0.3, m.ty - distance, m.tz - 0.3, m.tx + 0.3, m.ty, m.tz + 0.3);
            if (!wide) {
                if (raise(d, 1.0)) {
                    flag(p, d, "client=ground server=air dy=" + Util.f3(m.dy));
                }
                return;
            }
        }
        if (!m.clientGround && m.ground && d.groundServer && Math.abs(m.dy) < 1.0E-9 && m.h > 0.05) {
            if (++d.antiHunger >= antiHungerTicks) {
                d.antiHunger = 0;
                if (raise(d, 1.0)) {
                    flag(p, d, "client=air on flat ground (AntiHunger)");
                }
            }
            return;
        }
        d.antiHunger = Math.max(0, d.antiHunger - 1);
        lower(d, 0.5);
    }
}

final class TimerCheck extends Check {

    private double threshold;
    private double maxBank;

    TimerCheck(FirePlugin plugin) {
        super(plugin, "timer", "Timer", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 2.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        threshold = s.getDouble("threshold-ms", 60.0);
        maxBank = s.getDouble("max-bank-ms", 350.0);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (plugin.packets != null) {
            return;
        }
        long now = System.nanoTime();
        long last = d.timerLast;
        d.timerLast = now;
        if (last == 0 || plugin.lagging || m.tick < d.teleportUntil || m.tick < d.joinUntil) {
            d.timerBalance = 0;
            return;
        }
        double bal = d.timerBalance + 50.0 - (now - last) / 1_000_000.0;
        if (bal < -maxBank) {
            bal = -maxBank;
        }
        if (bal > threshold * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, "ahead=" + Util.f(bal) + "ms");
            }
            bal = 0;
        } else {
            lower(d, 0.02);
        }
        d.timerBalance = bal;
    }

    String packet(PacketData pd, long now, boolean skip) {
        long last = pd.timerLast;
        pd.timerLast = now;
        if (last == 0 || skip) {
            pd.timerBalance = 0;
            return null;
        }
        double bal = pd.timerBalance + 50.0 - (now - last) / 1_000_000.0;
        if (bal < -maxBank) {
            bal = -maxBank;
        }
        String out = null;
        if (bal > threshold * len()) {
            if (raiseP(pd, 1.0)) {
                out = "ahead=" + Util.f(bal) + "ms";
            }
            bal = 0;
        } else {
            lowerP(pd, 0.02);
        }
        pd.timerBalance = bal;
        return out;
    }
}

final class PhaseCheck extends Check {

    private double minDistance;

    PhaseCheck(FirePlugin plugin) {
        super(plugin, "phase", "Phase", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        minDistance = s.getDouble("min-distance", 0.6);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded) {
            return;
        }
        if (m.tick < d.velocityUntil) {
            return;
        }
        double dist = Math.sqrt(m.dx * m.dx + m.dy * m.dy + m.dz * m.dz);
        if (dist < minDistance) {
            return;
        }
        World w = m.world;
        if (w.getType(Util.floor(m.fx), Util.floor(m.fy + 0.5), Util.floor(m.fz)).isOccluding()) {
            return;
        }
        Location start = new Location(w, m.fx, m.fy + 0.5, m.fz);
        Vector dir = new Vector(m.dx, m.dy, m.dz).multiply(1.0 / dist);
        RayTraceResult r = w.rayTraceBlocks(start, dir, dist, FluidCollisionMode.NEVER, true);
        if (r != null && r.getHitBlock() != null) {
            if (raise(d, 1.0)) {
                flag(p, d, "through=" + r.getHitBlock().getType().name().toLowerCase() + " dist=" + Util.f(dist));
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class SprintCheck extends Check {

    private double minSpeed;
    private double angle;
    private int noKeyTicks;

    SprintCheck(FirePlugin plugin) {
        super(plugin, "sprint", "Sprint", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 4.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        minSpeed = s.getDouble("min-speed", 0.2);
        angle = s.getDouble("max-angle", 100.0);
        noKeyTicks = s.getInt("no-key-ticks", 4);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special()) {
            return;
        }
        if (!p.isSprinting() || m.h < minSpeed || d.lastSlip > 0.61 || m.tick < d.velocityUntil || m.tick < d.specialUntil) {
            lower(d, 0.5);
            return;
        }
        boolean bad;
        String detail;
        if (d.inputKnown) {
            bad = !d.inForward && m.tick - d.inputTick >= noKeyTicks;
            detail = "sprint without forward key h=" + Util.f3(m.h);
        } else {
            double moveYaw = Math.toDegrees(Math.atan2(-m.dx, m.dz));
            double diff = Math.abs(Util.wrapDegrees((float) (moveYaw - m.yaw)));
            bad = m.ground && d.groundServer && diff > angle && m.h >= d.lastH * 0.9;
            detail = "angle=" + Util.f(diff) + " h=" + Util.f3(m.h);
        }
        if (bad) {
            if (raise(d, 1.0)) {
                flag(p, d, detail);
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class ElytraCheck extends Check {

    private double maxSpeed;
    private int hoverTicks;

    ElytraCheck(FirePlugin plugin) {
        super(plugin, "elytra", "Elytra", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 2.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxSpeed = s.getDouble("max-speed", 3.8);
        hoverTicks = s.getInt("hover-ticks", 10);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !p.isGliding() || m.tick < d.boostUntil || m.tick < d.velocityUntil || m.tick < d.teleportUntil) {
            d.elytraHover = 0;
            return;
        }
        double speed = Math.sqrt(m.dx * m.dx + m.dy * m.dy + m.dz * m.dz);
        if (speed > maxSpeed * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, "speed=" + Util.f(speed) + " max=" + Util.f(maxSpeed));
            }
            return;
        }
        if (speed < 0.12 && Math.abs(m.dy) < 0.06) {
            if (++d.elytraHover >= hoverTicks) {
                d.elytraHover = 0;
                if (raise(d, 1.0)) {
                    flag(p, d, "hovering while gliding");
                }
            }
        } else {
            d.elytraHover = Math.max(0, d.elytraHover - 1);
            lower(d, 0.25);
        }
    }
}

final class VelocityCheck extends Check {

    private double ratio;
    private int window;
    private double minVelocity;

    VelocityCheck(FirePlugin plugin) {
        super(plugin, "velocity", "Velocity", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        ratio = s.getDouble("ratio", 0.15);
        window = s.getInt("window-ticks", 5);
        minVelocity = s.getDouble("min-velocity", 0.25);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (m.moved && m.tick - d.kbTick <= window && m.tick >= d.kbTick) {
            d.kbSum += m.dx * d.kbX + m.dz * d.kbZ;
        }
    }

    void begin(Player p, PlayerData d, double vx, double vz, int tick) {
        double mag = Math.sqrt(vx * vx + vz * vz);
        if (mag < minVelocity) {
            return;
        }
        d.kbTick = tick;
        d.kbX = vx / mag;
        d.kbZ = vz / mag;
        d.kbMag = mag;
        d.kbSum = 0;
        Bukkit.getScheduler().runTaskLater(plugin, () -> evaluate(p, d, tick), window + 2L);
    }

    private void evaluate(Player p, PlayerData d, int start) {
        if (!enabled || !p.isOnline() || d.kbTick != start) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        d.refresh(p, now);
        if (d.globalExempt(p, plugin.settings) || d.computeMoveExempt(p, now) || p.isBlocking() || plugin.lagging) {
            return;
        }
        Location l = p.getLocation();
        Move s = plugin.scratch;
        s.world = l.getWorld();
        s.tx = l.getX();
        s.ty = l.getY();
        s.tz = l.getZ();
        Terrain.classify(s);
        if (s.unloaded || s.special()) {
            return;
        }
        if (Terrain.collides(s.world, s.tx - 0.65, s.ty + 0.1, s.tz - 0.65, s.tx + 0.65, s.ty + 1.8, s.tz + 0.65)) {
            return;
        }
        if (d.kbSum < d.kbMag * ratio) {
            if (raise(d, 1.0)) {
                flag(p, d, "moved=" + Util.f3(d.kbSum) + " expected=" + Util.f3(d.kbMag));
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class AimCheck extends Check {

    private double spinDegrees;
    private int spinTicks;
    private int roboTicks;

    AimCheck(FirePlugin plugin) {
        super(plugin, "aim", "Aim", "movement", MOVE);
    }

    @Override
    void configure(ConfigurationSection s) {
        spinDegrees = s.getDouble("spin-degrees", 60.0);
        spinTicks = s.getInt("spin-ticks", 8);
        roboTicks = s.getInt("robotic-ticks", 6);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.rotated || !d.hasLast || p.isInsideVehicle() || m.tick < d.teleportUntil) {
            return;
        }
        float dyaw = Util.wrapDegrees(m.yaw - d.lastYaw);
        double abs = Math.abs(dyaw);
        if (abs >= spinDegrees) {
            if (++d.spinCount >= spinTicks) {
                if (raise(d, 1.0)) {
                    flag(p, d, "spin=" + Util.f(abs) + "/tick");
                }
                d.spinCount = 0;
            }
        } else {
            d.spinCount = Math.max(0, d.spinCount - 1);
        }
        if (abs > 1.0 && Math.abs(dyaw - d.lastDyaw) < 0.0005) {
            if (++d.roboCount >= roboTicks) {
                if (raise(d, 1.0)) {
                    flag(p, d, "robotic=" + Util.f3(abs));
                }
                d.roboCount = 0;
            }
        } else {
            d.roboCount = 0;
        }
        d.lastDyaw = dyaw;
    }
}
