package dev.fire;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;

import java.util.Locale;

final class Util {

    private Util() {
    }

    static double attr(Player p, Attribute a, double def) {
        AttributeInstance i = p.getAttribute(a);
        return i == null ? def : i.getValue();
    }

    static double jumpPower(Player p) {
        double j = attr(p, Attribute.JUMP_STRENGTH, 0.42);
        PotionEffect boost = p.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (boost != null) {
            j += 0.1 * (boost.getAmplifier() + 1);
        }
        return j;
    }

    static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    static double distToBox(double x, double y, double z, BoundingBox b) {
        double dx = Math.max(Math.max(b.getMinX() - x, 0.0), x - b.getMaxX());
        double dy = Math.max(Math.max(b.getMinY() - y, 0.0), y - b.getMaxY());
        double dz = Math.max(Math.max(b.getMinZ() - z, 0.0), z - b.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static boolean rayHits(double ox, double oy, double oz, double dx, double dy, double dz,
                           double minX, double minY, double minZ, double maxX, double maxY, double maxZ, double maxDist) {
        double tmin = 0.0;
        double tmax = maxDist;
        if (Math.abs(dx) < 1.0E-9) {
            if (ox < minX || ox > maxX) {
                return false;
            }
        } else {
            double inv = 1.0 / dx;
            double t1 = (minX - ox) * inv;
            double t2 = (maxX - ox) * inv;
            if (t1 > t2) {
                double t = t1;
                t1 = t2;
                t2 = t;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return false;
            }
        }
        if (Math.abs(dy) < 1.0E-9) {
            if (oy < minY || oy > maxY) {
                return false;
            }
        } else {
            double inv = 1.0 / dy;
            double t1 = (minY - oy) * inv;
            double t2 = (maxY - oy) * inv;
            if (t1 > t2) {
                double t = t1;
                t1 = t2;
                t2 = t;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return false;
            }
        }
        if (Math.abs(dz) < 1.0E-9) {
            if (oz < minZ || oz > maxZ) {
                return false;
            }
        } else {
            double inv = 1.0 / dz;
            double t1 = (minZ - oz) * inv;
            double t2 = (maxZ - oz) * inv;
            if (t1 > t2) {
                double t = t1;
                t1 = t2;
                t2 = t;
            }
            tmin = Math.max(tmin, t1);
            tmax = Math.min(tmax, t2);
            if (tmin > tmax) {
                return false;
            }
        }
        return true;
    }

    static float wrapDegrees(float v) {
        v = v % 360.0f;
        if (v >= 180.0f) {
            v -= 360.0f;
        }
        if (v < -180.0f) {
            v += 360.0f;
        }
        return v;
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }
}
