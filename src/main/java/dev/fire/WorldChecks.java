package dev.fire;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class FastBreakCheck extends Check {

    private double ratio;
    private double slack;

    FastBreakCheck(FirePlugin plugin) {
        super(plugin, "fastbreak", "FastBreak", "world", DAMAGE | BREAK);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        ratio = s.getDouble("ratio", 0.6);
        slack = s.getDouble("slack-ticks", 1.5);
    }

    @Override
    void blockDamage(Player p, PlayerData d, Block b, BlockDamageEvent e) {
        d.breakStartNanos = System.nanoTime();
        d.breakX = b.getX();
        d.breakY = b.getY();
        d.breakZ = b.getZ();
        d.breakStarted = !e.getInstaBreak();
    }

    @Override
    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
        if (!Compat.hasBreakSpeed() || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        float speed = Compat.breakSpeed(b, p);
        boolean started = d.breakStarted && b.getX() == d.breakX && b.getY() == d.breakY && b.getZ() == d.breakZ;
        d.breakStarted = false;
        if (speed <= 0f || speed >= 1.0f) {
            return;
        }
        double needed = 1.0 / speed;
        if (!started) {
            if (needed > 3.0 && raise(d, 1.0)) {
                flag(p, d, "break without start needed=" + Util.f(needed) + "t");
            }
            return;
        }
        double elapsed = (System.nanoTime() - d.breakStartNanos) / 50_000_000.0;
        if (elapsed < needed * ratio - slack) {
            if (raise(d, 1.0)) {
                flag(p, d, "took=" + Util.f(elapsed) + "t needed=" + Util.f(needed) + "t");
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class NukerCheck extends Check {

    private int maxPerSecond;

    NukerCheck(FirePlugin plugin) {
        super(plugin, "nuker", "Nuker", "world", BREAK);
    }

    @Override
    double defaultBuffer() {
        return 2.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxPerSecond = s.getInt("max-per-second", 18);
    }

    @Override
    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
        long now = System.currentTimeMillis();
        if (now - d.breakWindowStart >= 1000L) {
            d.breakWindowStart = now;
            d.breakCount = 0;
        }
        if (++d.breakCount > maxPerSecond) {
            if (raise(d, 1.0)) {
                flag(p, d, d.breakCount + " blocks/s");
            }
        }
    }
}

final class BlockVisibleCheck extends Check {

    private static final double[][] POINTS = {
            {0.5, 0.5, 0.5}, {0.5, 0.02, 0.5}, {0.5, 0.98, 0.5},
            {0.02, 0.5, 0.5}, {0.98, 0.5, 0.5}, {0.5, 0.5, 0.02}, {0.5, 0.5, 0.98}
    };

    BlockVisibleCheck(FirePlugin plugin) {
        super(plugin, "blockvisible", "BlockVisible", "world", BREAK | PLACE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
        test(p, d, b);
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        test(p, d, e.getBlockAgainst());
    }

    private void test(Player p, PlayerData d, Block target) {
        if (visible(p, target)) {
            lower(d, 0.5);
        } else if (raise(d, 1.0)) {
            flag(p, d, "no line of sight to " + target.getType().name().toLowerCase());
        }
    }

    private static boolean visible(Player p, Block target) {
        Location eye = p.getEyeLocation();
        World w = eye.getWorld();
        if (w.getType(Util.floor(eye.getX()), Util.floor(eye.getY()), Util.floor(eye.getZ())).isOccluding()) {
            return true;
        }
        for (double[] o : POINTS) {
            double tx = target.getX() + o[0];
            double ty = target.getY() + o[1];
            double tz = target.getZ() + o[2];
            Vector dir = new Vector(tx - eye.getX(), ty - eye.getY(), tz - eye.getZ());
            double dist = dir.length();
            if (dist < 1.0E-6) {
                return true;
            }
            dir.multiply(1.0 / dist);
            RayTraceResult r = w.rayTraceBlocks(eye, dir, dist + 0.05, FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) {
                return true;
            }
            Block hit = r.getHitBlock();
            if (hit.getX() == target.getX() && hit.getY() == target.getY() && hit.getZ() == target.getZ()) {
                return true;
            }
        }
        return false;
    }
}

final class InteractReachCheck extends Check {

    private double tolerance;

    InteractReachCheck(FirePlugin plugin) {
        super(plugin, "interactreach", "InteractReach", "world", BREAK | PLACE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        tolerance = s.getDouble("tolerance", 1.0);
    }

    @Override
    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
        test(p, d, b);
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        test(p, d, e.getBlockAgainst());
    }

    private void test(Player p, PlayerData d, Block target) {
        Location eye = p.getEyeLocation();
        double range = Util.attr(p, Attribute.BLOCK_INTERACTION_RANGE, 4.5);
        double dist = Util.distToBox(eye.getX(), eye.getY(), eye.getZ(), target.getBoundingBox());
        double max = range + tolerance * len();
        if (dist > max) {
            if (raise(d, 1.0)) {
                flag(p, d, "dist=" + Util.f(dist) + " max=" + Util.f(max));
            }
        } else {
            lower(d, 0.25);
        }
    }
}

final class ScaffoldCheck extends Check {

    private double expand;

    ScaffoldCheck(FirePlugin plugin) {
        super(plugin, "scaffold", "Scaffold", "world", PLACE);
    }

    @Override
    double defaultBuffer() {
        return 5.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        expand = s.getDouble("expand", 0.3);
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        Block against = e.getBlockAgainst();
        BoundingBox b = against.getBoundingBox();
        if (b.getVolume() < 1.0E-6) {
            return;
        }
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = Util.attr(p, Attribute.BLOCK_INTERACTION_RANGE, 4.5) + 1.5;
        double ex = (expand + Math.min(p.getPing(), 300) * 0.001) * len();
        boolean hit = Util.rayHits(eye.getX(), eye.getY(), eye.getZ(), dir.getX(), dir.getY(), dir.getZ(),
                b.getMinX() - ex, b.getMinY() - ex, b.getMinZ() - ex, b.getMaxX() + ex, b.getMaxY() + ex, b.getMaxZ() + ex, range);
        if (!hit) {
            if (raise(d, 1.0)) {
                flag(p, d, "placed against block not in view");
            }
        } else {
            lower(d, 0.5);
        }
    }
}

final class FastPlaceCheck extends Check {

    private int maxPerSecond;

    FastPlaceCheck(FirePlugin plugin) {
        super(plugin, "fastplace", "FastPlace", "world", PLACE);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        maxPerSecond = s.getInt("max-per-second", 18);
    }

    @Override
    void place(Player p, PlayerData d, BlockPlaceEvent e) {
        long now = System.currentTimeMillis();
        if (now - d.placeWindowStart >= 1000L) {
            d.placeWindowStart = now;
            d.placeCount = 0;
        }
        if (++d.placeCount > maxPerSecond) {
            if (raise(d, 1.0)) {
                flag(p, d, d.placeCount + " blocks/s");
            }
        }
    }
}

final class InvMoveCheck extends Check {

    private double minSpeed;

    InvMoveCheck(FirePlugin plugin) {
        super(plugin, "invmove", "InvMove", "player", INV);
    }

    @Override
    double defaultBuffer() {
        return 3.0;
    }

    @Override
    void configure(ConfigurationSection s) {
        minSpeed = s.getDouble("min-speed", 0.18);
    }

    @Override
    void inventory(Player p, PlayerData d, InventoryClickEvent e) {
        int tick = Bukkit.getCurrentTick();
        if (d.computeMoveExempt(p, tick) || tick < d.velocityUntil || tick < d.specialUntil) {
            return;
        }
        if (d.groundServer && d.lastSlip < 0.61 && tick - d.lastMoveTick <= 2 && d.lastH >= minSpeed) {
            if (raise(d, 1.0)) {
                flag(p, d, "clicked while moving h=" + Util.f3(d.lastH));
            }
        } else {
            lower(d, 0.5);
        }
    }
}
