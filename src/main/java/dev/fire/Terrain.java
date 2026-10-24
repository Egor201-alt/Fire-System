package dev.fire;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;

final class Terrain {

    private Terrain() {
    }

    static boolean nativeCollisions() {
        return true;
    }

    static boolean collides(World w, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return w.hasCollisionsIn(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
    }

    static double slip(Material t) {
        if (t == Material.BLUE_ICE) {
            return 0.989;
        }
        if (t == Material.ICE || t == Material.PACKED_ICE || t == Material.FROSTED_ICE) {
            return 0.98;
        }
        if (t == Material.SLIME_BLOCK) {
            return 0.8;
        }
        return 0.6;
    }

    static void classify(Move m) {
        m.clearTerrain();
        World w = m.world;
        double x = m.tx;
        double y = m.ty;
        double z = m.tz;
        int x0 = Util.floor(x - 0.31);
        int x1 = Util.floor(x + 0.31);
        int z0 = Util.floor(z - 0.31);
        int z1 = Util.floor(z + 0.31);
        if (!w.isChunkLoaded(x0 >> 4, z0 >> 4) || !w.isChunkLoaded(x1 >> 4, z1 >> 4)
                || !w.isChunkLoaded(x0 >> 4, z1 >> 4) || !w.isChunkLoaded(x1 >> 4, z0 >> 4)) {
            m.unloaded = true;
            return;
        }
        int fy = Util.floor(y);
        int y0 = Util.floor(y - 0.2);
        int y1 = Util.floor(y + 1.8);
        for (int yy = y0; yy <= y1; yy++) {
            boolean body = yy >= fy;
            for (int xx = x0; xx <= x1; xx++) {
                for (int zz = z0; zz <= z1; zz++) {
                    Material t = w.getType(xx, yy, zz);
                    if (t.isAir()) {
                        continue;
                    }
                    if (body) {
                        if (t == Material.WATER) {
                            m.water = true;
                        } else if (t == Material.BUBBLE_COLUMN) {
                            m.bubble = true;
                        } else if (t == Material.LAVA) {
                            m.lava = true;
                        } else if (t == Material.COBWEB) {
                            m.web = true;
                        } else if (t == Material.HONEY_BLOCK) {
                            m.honey = true;
                        } else if (t == Material.SWEET_BERRY_BUSH) {
                            m.berry = true;
                        } else if (t == Material.POWDER_SNOW) {
                            m.powder = true;
                        } else if (Tag.CLIMBABLE.isTagged(t)) {
                            m.climb = true;
                        }
                    } else {
                        if (t == Material.SLIME_BLOCK) {
                            m.slime = true;
                        } else if (Tag.BEDS.isTagged(t)) {
                            m.bed = true;
                        }
                    }
                }
            }
        }
        m.ground = collides(w, x - 0.3, y - 0.03, z - 0.3, x + 0.3, y, z + 0.3);
        m.slip = slip(w.getType(Util.floor(x), Util.floor(y - 0.5000001), Util.floor(z)));
    }
}
