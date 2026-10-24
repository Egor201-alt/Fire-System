package dev.fire;

import java.util.UUID;

final class PacketData {

    final UUID id;
    final double[] buf;
    long timerLast;
    double timerBalance;
    final long[] clicks = new long[48];
    int clickIdx;
    int clickCount;
    boolean digging;
    long lastUse;
    long lastAttack;
    long lastSwing;
    boolean pendingSwing;
    final long[] windowClicks = new long[40];
    int windowIdx;
    int windowCount;
    volatile long lastPop;
    volatile boolean hasPos;
    volatile boolean teleportPending;
    double px;
    double py;
    double pz;
    long sprintWindow;
    int sprintToggles;

    PacketData(UUID id, int checks) {
        this.id = id;
        this.buf = new double[checks];
    }
}
