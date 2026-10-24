package dev.fire;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.UUID;

final class PlayerData {

    final UUID id;
    final String name;
    final double[] vl;
    final double[] buf;
    final long[] lastAlert;
    final ArrayDeque<String> history = new ArrayDeque<>();
    double heat;
    boolean alerts;
    boolean watch;
    boolean bypass;
    boolean scaled;
    long exemptUntilMs;
    long frozenUntilMs;
    int bypassTick = -1000;
    int debugIndex = -1;

    boolean hasLast;
    double lastX, lastY, lastZ, lastDx, lastDy, lastDz, lastH;
    double lastSlip = 0.6;
    double carryFriction = 0.546;
    float lastYaw, lastPitch;
    double lastDyaw;
    boolean groundServer = true;
    int airTicks;
    int lastMoveTick;
    int joinUntil, teleportUntil, velocityUntil, specialUntil, bounceUntil;
    int glideUntil, boostUntil, vehicleUntil, riptideUntil, flyUntil;
    boolean moveExempt;

    boolean hasSafe;
    String safeWorld;
    double safeX, safeY, safeZ;
    float safeYaw, safePitch;

    long timerLast;
    double timerBalance;
    int spinCount, roboCount, sprintCount;
    double lastMoveAngle;

    int kbTick = -1000;
    double kbX, kbZ, kbMag, kbSum;

    final long[] clicks = new long[48];
    int clickIdx, clickCount;
    boolean mining;
    long miningSince;

    final int[] atkIds = new int[6];
    final int[] atkTicks = new int[6];
    int atkIdx;

    long breakStartNanos;
    int breakX, breakY, breakZ;
    boolean breakStarted;
    long breakWindowStart;
    int breakCount;
    long placeWindowStart;
    int placeCount;

    PlayerData(UUID id, String name, int checks) {
        this.id = id;
        this.name = name;
        this.vl = new double[checks];
        this.buf = new double[checks];
        this.lastAlert = new long[checks];
        java.util.Arrays.fill(atkTicks, -1000);
    }

    void addHistory(String line, int max) {
        history.addFirst(line);
        while (history.size() > max) {
            history.removeLast();
        }
    }

    boolean globalExempt(Player p, Settings s) {
        if (bypass || p.isDead()) {
            return true;
        }
        if (exemptUntilMs != 0 && System.currentTimeMillis() < exemptUntilMs) {
            return true;
        }
        if (s.exemptModes.contains(p.getGameMode())) {
            return true;
        }
        return !s.disabledWorlds.isEmpty() && s.disabledWorlds.contains(p.getWorld().getName());
    }

    void refresh(Player p, int tick) {
        if (tick - bypassTick >= 40) {
            bypassTick = tick;
            bypass = p.hasPermission("fire.bypass");
            scaled = Math.abs(Util.attr(p, Attribute.SCALE, 1.0) - 1.0) > 0.001;
        }
    }

    boolean computeMoveExempt(Player p, int tick) {
        if (scaled) {
            return true;
        }
        if (p.getAllowFlight() || p.isFlying()) {
            flyUntil = tick + 40;
            return true;
        }
        if (p.isInsideVehicle()) {
            vehicleUntil = tick + 12;
            return true;
        }
        if (p.isGliding()) {
            glideUntil = tick + 40;
            return true;
        }
        if (p.isRiptiding()) {
            riptideUntil = tick + 50;
            return true;
        }
        return p.isSleeping() || tick < flyUntil || tick < joinUntil || tick < teleportUntil
                || tick < glideUntil || tick < vehicleUntil || tick < riptideUntil;
    }

    void resetMotion(int tick, int grace) {
        teleportUntil = Math.max(teleportUntil, tick + grace);
        lastH = 0;
        lastDx = 0;
        lastDy = 0;
        lastDz = 0;
        carryFriction = 0.546;
        timerLast = 0;
        timerBalance = 0;
        hasLast = false;
        groundServer = true;
        airTicks = 0;
        kbTick = -1000;
    }

    void markSafe(String world, double x, double y, double z, float yaw, float pitch) {
        hasSafe = true;
        safeWorld = world;
        safeX = x;
        safeY = y;
        safeZ = z;
        safeYaw = yaw;
        safePitch = pitch;
    }
}
