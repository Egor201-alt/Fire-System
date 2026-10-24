package dev.fire;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.lang.reflect.Method;

final class Compat {

    private static final Method CRITICAL = find(EntityDamageByEntityEvent.class, "isCritical");
    private static final Method BREAK_SPEED = find(Block.class, "getBreakSpeed", Player.class);

    private Compat() {
    }

    private static Method find(Class<?> c, String name, Class<?>... args) {
        try {
            return c.getMethod(name, args);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static boolean hasCritical() {
        return CRITICAL != null;
    }

    static boolean hasBreakSpeed() {
        return BREAK_SPEED != null;
    }

    static boolean critical(EntityDamageByEntityEvent e) {
        if (CRITICAL == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(CRITICAL.invoke(e));
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    static float breakSpeed(Block b, Player p) {
        if (BREAK_SPEED == null) {
            return -1f;
        }
        try {
            Object o = BREAK_SPEED.invoke(b, p);
            return o instanceof Number n ? n.floatValue() : -1f;
        } catch (ReflectiveOperationException ex) {
            return -1f;
        }
    }
}
