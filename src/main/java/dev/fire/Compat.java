package dev.fire;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

final class Compat {

    private Compat() {
    }

    static boolean critical(EntityDamageByEntityEvent e) {
        return e.isCritical();
    }

    static float breakSpeed(Block b, Player p) {
        return b.getBreakSpeed(p);
    }
}
