package dev.fire;

import org.bukkit.World;

final class Move {

    World world;
    double fx, fy, fz, tx, ty, tz, dx, dy, dz, h;
    float yaw, pitch;
    int tick;
    boolean moved, rotated, clientGround, ground, unloaded, cancel;
    boolean water, lava, bubble, web, honey, berry, powder, climb, slime, bed;
    double slip = 0.6;

    void clearTerrain() {
        unloaded = false;
        ground = false;
        water = false;
        lava = false;
        bubble = false;
        web = false;
        honey = false;
        berry = false;
        powder = false;
        climb = false;
        slime = false;
        bed = false;
        slip = 0.6;
    }

    boolean special() {
        return water || lava || bubble || web || honey || berry || powder || climb;
    }
}
