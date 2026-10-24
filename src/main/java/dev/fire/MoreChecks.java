package dev.fire;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumSet;
import java.util.Set;

final class HighJumpCheck extends Check {

    private double tolerance;

    HighJumpCheck(FirePlugin plugin) {
        super(plugin, "highjump", "HighJump", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 1.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.02);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special() || !d.groundServer) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil || m.tick < d.bounceUntil || m.dy <= 0.001) {
            return;
        }
        double max = Util.jumpPower(p) + tolerance * len();
        if (!m.ground && m.dy > max) {
            if (raise(d, 1.0)) {
                flag(p, d, "dy=" + Util.f3(m.dy) + " max=" + Util.f3(max));
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class AirJumpCheck extends Check {

    static final double WINDOW = 0.12;

    AirJumpCheck(FirePlugin plugin) {
        super(plugin, "airjump", "AirJump", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 1.5;
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
        double predicted = (d.lastDy - gravity) * 0.98;
        if (m.dy > predicted + 0.1 && Math.abs(m.dy - Util.jumpPower(p)) < WINDOW) {
            if (raise(d, 1.0)) {
                flag(p, d, "jumped in air dy=" + Util.f3(m.dy) + " expected=" + Util.f3(predicted));
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class StepCheck extends Check {

    private double tolerance;

    StepCheck(FirePlugin plugin) {
        super(plugin, "step", "Step", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 1.5;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.02);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special() || !d.groundServer || !m.ground) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil || m.tick < d.bounceUntil) {
            return;
        }
        double step = Util.attr(p, Attribute.STEP_HEIGHT, 0.6);
        if (m.dy > step + tolerance * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, "step=" + Util.f3(m.dy) + " max=" + Util.f3(step));
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class StrafeCheck extends Check {

    private double tolerance;

    StrafeCheck(FirePlugin plugin) {
        super(plugin, "strafe", "Strafe", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 4.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.03);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special() || plugin.moveFlagged) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.specialUntil || m.h < 0.08 || d.lastH < 0.05) {
            return;
        }
        if (Terrain.collides(m.world, m.tx - 0.37, m.ty + 0.1, m.tz - 0.37, m.tx + 0.37, m.ty + 1.7, m.tz + 0.37)) {
            return;
        }
        double px = d.lastDx * d.carryFriction;
        double pz = d.lastDz * d.carryFriction;
        double diff = Math.sqrt((m.dx - px) * (m.dx - px) + (m.dz - pz) * (m.dz - pz));
        double accel;
        if (d.groundServer) {
            double g = d.lastSlip * 0.91;
            accel = Util.attr(p, Attribute.MOVEMENT_SPEED, 0.1) * 0.16277136 / (g * g * g);
            if (m.dy > 0.001) {
                accel += 0.2;
            }
        } else {
            accel = p.isSprinting() ? 0.026 : 0.02;
        }
        double limit = accel + tolerance * len();
        if (diff > limit) {
            if (raise(d, 1.0)) {
                flag(p, d, "turn=" + Util.f3(diff) + " max=" + Util.f3(limit));
            }
        } else {
            lower(d, 0.35);
        }
    }
}

final class NoSlowCheck extends Check {

    private double tolerance;
    private int minUseTicks;

    NoSlowCheck(FirePlugin plugin) {
        super(plugin, "noslow", "NoSlow", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 0.02);
        minUseTicks = s.getInt("min-use-ticks", 3);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.special()) {
            return;
        }
        if (!p.isHandRaised() || p.getActiveItemUsedTime() < minUseTicks || m.tick < d.velocityUntil || m.tick < d.specialUntil) {
            lower(d, 0.5);
            return;
        }
        double attr = Util.attr(p, Attribute.MOVEMENT_SPEED, 0.1);
        double accel;
        if (d.groundServer) {
            double g = d.lastSlip * 0.91;
            accel = attr * 0.16277136 / (g * g * g) * 0.2;
        } else {
            accel = 0.02 * 0.2;
        }
        double limit = d.lastH * d.carryFriction + accel + tolerance * len();
        if (m.h > limit && m.h > 0.06) {
            if (raise(d, 1.0)) {
                flag(p, d, "moving while using item h=" + Util.f3(m.h) + " max=" + Util.f3(limit));
            }
        } else {
            lower(d, 0.35);
        }
    }
}

final class LiquidCheck extends Check {

    private double maxSpeed;
    private int hoverTicks;

    LiquidCheck(FirePlugin plugin) {
        super(plugin, "liquid", "Jesus", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxSpeed = s.getDouble("max-speed", 0.45);
        hoverTicks = s.getInt("hover-ticks", 8);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || m.lava || !m.water || m.climb) {
            d.liquidCount = 0;
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.bounceUntil) {
            return;
        }
        double limit = maxSpeed * (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE) ? 2.0 : 1.0) * len();
        if (m.h > limit) {
            if (raise(d, 1.0)) {
                flag(p, d, "water speed h=" + Util.f3(m.h) + " max=" + Util.f3(limit));
            }
            return;
        }
        if (!m.ground && !p.isSwimming() && Math.abs(m.dy) < 0.0001 && m.h > 0.12) {
            if (++d.liquidCount >= hoverTicks) {
                d.liquidCount = 0;
                if (raise(d, 1.0)) {
                    flag(p, d, "walking on water h=" + Util.f3(m.h));
                }
            }
        } else {
            d.liquidCount = Math.max(0, d.liquidCount - 1);
            lower(d, 0.25);
        }
    }
}

final class ClimbCheck extends Check {

    private double maxUp;

    ClimbCheck(FirePlugin plugin) {
        super(plugin, "climb", "Climb", "movement", MOVE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxUp = s.getDouble("max-up", 0.215);
    }

    @Override
    void move(Player p, PlayerData d, Move m) {
        if (!m.moved || !d.hasLast || d.moveExempt || m.unloaded || !m.climb || m.water || m.lava) {
            return;
        }
        if (m.tick < d.velocityUntil || m.tick < d.bounceUntil || d.groundServer || d.airTicks < 3) {
            return;
        }
        if (m.dy > maxUp * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, "climb dy=" + Util.f3(m.dy) + " max=" + Util.f3(maxUp));
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class VehicleCheck extends Check {

    private double boatMax;
    private double minecartMax;
    private double otherMax;
    private int airTicks;

    VehicleCheck(FirePlugin plugin) {
        super(plugin, "vehicle", "Vehicle", "movement", VEHICLE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        boatMax = s.getDouble("boat-max-speed", 4.0);
        minecartMax = s.getDouble("minecart-max-speed", 0.7);
        otherMax = s.getDouble("mount-max-speed", 1.5);
        airTicks = s.getInt("boat-air-ticks", 8);
    }

    @Override
    void vehicle(Player p, PlayerData d, Entity v, Location from, Location to) {
        if (from.getWorld() != to.getWorld() || Bukkit.getCurrentTick() < d.teleportUntil || Bukkit.getCurrentTick() < d.velocityUntil) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        double limit = v instanceof Boat ? boatMax : v instanceof Minecart ? minecartMax : otherMax;
        if (h > limit * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, "vehicle speed h=" + Util.f3(h) + " max=" + Util.f(limit));
            }
            return;
        }
        if (v instanceof Boat) {
            if (!v.isOnGround() && !v.isInWater() && !v.isInLava() && dy > -0.02) {
                if (++d.vehicleAir >= airTicks) {
                    d.vehicleAir = 0;
                    if (raise(d, 1.0)) {
                        flag(p, d, "boat flying dy=" + Util.f3(dy));
                    }
                }
            } else {
                d.vehicleAir = 0;
            }
        }
        lower(d, 0.1);
    }
}

final class AirPlaceCheck extends Check {

    AirPlaceCheck(FirePlugin plugin) {
        super(plugin, "airplace", "AirPlace", "world", PLACE);
    }

    @Override
    double defaultBuffer() {
        return 1.5;
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        Material t = e.getBlockAgainst().getType();
        boolean liquid = t == Material.WATER || t == Material.LAVA;
        if (liquid) {
            Material item = e.getItemInHand().getType();
            if (item == Material.LILY_PAD || item == Material.FROGSPAWN) {
                return;
            }
        }
        if (t.isAir() || liquid) {
            if (raise(d, 1.0)) {
                flag(p, d, "placed against " + t.name().toLowerCase());
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class TowerCheck extends Check {

    private double maxPerSecond;
    private int minBlocks;

    TowerCheck(FirePlugin plugin) {
        super(plugin, "tower", "Tower", "world", PLACE);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxPerSecond = s.getDouble("max-blocks-per-second", 2.6);
        minBlocks = s.getInt("min-blocks", 5);
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        Location l = p.getLocation();
        Block placed = e.getBlockPlaced();
        if (placed.getX() != Util.floor(l.getX()) || placed.getZ() != Util.floor(l.getZ()) || placed.getY() != Util.floor(l.getY()) - 1) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        if (tick - d.towerStart > 40 || d.towerCount == 0) {
            d.towerStart = tick;
            d.towerCount = 1;
            d.towerY0 = l.getY();
            return;
        }
        d.towerCount++;
        double seconds = Math.max(0.05, (tick - d.towerStart) / 20.0);
        if (d.towerCount >= minBlocks && l.getY() > d.towerY0 + 1.5 && d.towerCount / seconds > maxPerSecond * len()) {
            if (raise(d, 1.0)) {
                flag(p, d, d.towerCount + " blocks in " + Util.f(seconds) + "s");
            }
            d.towerCount = 0;
        }
    }
}

final class XRayCheck extends Check {

    private static final Set<Material> ORES = EnumSet.of(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE);

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private int minStone;
    private int minHidden;
    private double maxRatio;

    XRayCheck(FirePlugin plugin) {
        super(plugin, "xray", "XRay", "world", BREAK);
    }

    @Override
    double defaultBuffer() {
        return 1.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        minStone = s.getInt("min-stone", 120);
        minHidden = s.getInt("min-hidden-ores", 4);
        maxRatio = s.getDouble("max-ratio", 0.03);
    }

    @Override
    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
        Material t = b.getType();
        if (Tag.BASE_STONE_OVERWORLD.isTagged(t) || t == Material.NETHERRACK) {
            d.xrayStone++;
        } else if (ORES.contains(t) && hidden(b)) {
            d.xrayHidden++;
        } else {
            return;
        }
        if (d.xrayStone >= minStone) {
            if (d.xrayHidden >= minHidden && d.xrayHidden / (double) d.xrayStone > maxRatio) {
                if (raise(d, 1.0)) {
                    flag(p, d, d.xrayHidden + " hidden ores in " + d.xrayStone + " blocks");
                }
            }
            if (d.xrayStone >= minStone * 3) {
                d.xrayStone = 0;
                d.xrayHidden = 0;
            }
        }
    }

    private static boolean hidden(Block b) {
        for (BlockFace f : FACES) {
            Material n = b.getRelative(f).getType();
            if (n.isAir() || n == Material.WATER || n == Material.LAVA) {
                return false;
            }
        }
        return true;
    }
}
