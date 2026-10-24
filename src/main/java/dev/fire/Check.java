package dev.fire;

import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;

abstract class Check {

    static final int MOVE = 1;
    static final int ATTACK = 2;
    static final int SWING = 4;
    static final int BREAK = 8;
    static final int DAMAGE = 16;
    static final int PLACE = 32;
    static final int INV = 64;
    static final int TICK = 128;

    final FirePlugin plugin;
    final String id;
    final String name;
    final String category;
    final int hooks;
    int index;
    boolean enabled = true;
    boolean alert = true;
    boolean cancel = true;
    double vl = 1.0;
    double decay = 1.0;
    double heat = 1.0;
    double buffer = 4.0;

    Check(FirePlugin plugin, String id, String name, String category, int hooks) {
        this.plugin = plugin;
        this.id = id;
        this.name = name;
        this.category = category;
        this.hooks = hooks;
    }

    final void load(ConfigurationSection s) {
        enabled = s.getBoolean("enabled", true);
        alert = s.getBoolean("alert", true);
        cancel = s.getBoolean("cancel", true);
        vl = s.getDouble("vl", 1.0);
        decay = s.getDouble("decay", 1.0);
        heat = s.getDouble("heat", 1.0);
        buffer = s.getDouble("buffer", defaultBuffer());
        configure(s);
    }

    double defaultBuffer() {
        return 4.0;
    }

    void configure(ConfigurationSection s) {
    }

    void move(Player p, PlayerData d, Move m) {
    }

    void attack(Player p, PlayerData d, Entity target, EntityDamageByEntityEvent e) {
    }

    void swing(Player p, PlayerData d) {
    }

    void blockBreak(Player p, PlayerData d, Block b, BlockBreakEvent e) {
    }

    void blockDamage(Player p, PlayerData d, Block b, BlockDamageEvent e) {
    }

    void place(Player p, PlayerData d, BlockPlaceEvent e) {
    }

    void inventory(Player p, PlayerData d, InventoryClickEvent e) {
    }

    void tick(Player p, PlayerData d) {
    }

    final boolean raise(PlayerData d, double amount) {
        double b = d.buf[index] + amount * (d.watch ? 1.5 : 1.0);
        if (b >= buffer) {
            d.buf[index] = buffer * 0.5;
            return true;
        }
        d.buf[index] = b;
        return false;
    }

    final void lower(PlayerData d, double amount) {
        double b = d.buf[index] - amount;
        d.buf[index] = b < 0 ? 0 : b;
    }

    final void flag(Player p, PlayerData d, String detail) {
        plugin.violations.flag(p, d, this, detail);
    }

    final double len() {
        return plugin.settings.leniency;
    }

    final boolean debugging(PlayerData d) {
        return d.debugIndex == index;
    }
}
