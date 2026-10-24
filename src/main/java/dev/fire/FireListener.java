package dev.fire;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.Vector;

import java.util.Collection;

final class FireListener implements Listener {

    private final FirePlugin plugin;

    FireListener(FirePlugin plugin) {
        this.plugin = plugin;
    }

    private PlayerData peek(Entity e) {
        return e instanceof Player p ? plugin.players.get(p.getUniqueId()) : null;
    }

    private void grace(Collection<Player> near, int ticks) {
        int until = Bukkit.getCurrentTick() + ticks;
        for (Player p : near) {
            PlayerData d = plugin.players.get(p.getUniqueId());
            if (d != null) {
                d.velocityUntil = Math.max(d.velocityUntil, until);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        int tick = Bukkit.getCurrentTick();
        d.resetMotion(tick, plugin.settings.joinGrace);
        d.joinUntil = tick + plugin.settings.joinGrace;
        Location l = p.getLocation();
        d.markSafe(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        if (plugin.settings.alertsDefaultOn && p.hasPermission("fire.alerts")) {
            d.alerts = true;
            plugin.alertSet.add(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        plugin.players.remove(e.getPlayer().getUniqueId());
        plugin.alertSet.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        long t0 = System.nanoTime();
        Player p = e.getPlayer();
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }
        PlayerData d = plugin.data(p);
        if (d.frozenUntilMs != 0 && System.currentTimeMillis() < d.frozenUntilMs) {
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                e.setCancelled(true);
            }
            return;
        }
        int tick = Bukkit.getCurrentTick();
        d.refresh(p, tick);
        Move m = plugin.move;
        m.world = to.getWorld();
        m.fx = from.getX();
        m.fy = from.getY();
        m.fz = from.getZ();
        m.tx = to.getX();
        m.ty = to.getY();
        m.tz = to.getZ();
        m.dx = m.tx - m.fx;
        m.dy = m.ty - m.fy;
        m.dz = m.tz - m.fz;
        m.h = Math.sqrt(m.dx * m.dx + m.dz * m.dz);
        m.yaw = to.getYaw();
        m.pitch = to.getPitch();
        m.tick = tick;
        m.cancel = false;
        m.moved = m.dx * m.dx + m.dy * m.dy + m.dz * m.dz > 1.0E-8;
        m.rotated = Math.abs(Util.wrapDegrees(m.yaw - from.getYaw())) > 0.0001f || Math.abs(m.pitch - from.getPitch()) > 0.0001f;
        if (!m.moved && !m.rotated) {
            return;
        }
        if (d.globalExempt(p, plugin.settings)) {
            d.hasLast = false;
            return;
        }
        m.clientGround = p.isOnGround();
        d.moveExempt = d.computeMoveExempt(p, tick);
        if (m.moved) {
            Terrain.classify(m);
        } else {
            m.clearTerrain();
            m.ground = d.groundServer;
            m.slip = d.lastSlip;
        }
        if (m.special()) {
            d.specialUntil = tick + 5;
        }
        if (m.slime || m.bed) {
            d.bounceUntil = tick + 60;
        }
        plugin.cancelRequested = false;
        for (Check c : plugin.moveChecks) {
            c.move(p, d, m);
        }
        boolean flagged = plugin.cancelRequested;
        if (flagged && plugin.settings.enforce && m.moved) {
            e.setCancelled(true);
            plugin.moveNanos += System.nanoTime() - t0;
            plugin.moveCount++;
            return;
        }
        if (m.moved) {
            d.carryFriction = (d.groundServer ? d.lastSlip : 1.0) * 0.91;
            d.lastDx = m.dx;
            d.lastDy = m.dy;
            d.lastDz = m.dz;
            d.lastH = m.h;
            d.groundServer = m.ground;
            d.lastSlip = m.slip;
            d.airTicks = m.ground ? 0 : d.airTicks + 1;
            d.lastMoveTick = tick;
            if (!flagged && m.ground) {
                d.markSafe(m.world.getName(), m.tx, m.ty, m.tz, m.yaw, m.pitch);
            }
        }
        d.lastYaw = m.yaw;
        d.lastPitch = m.pitch;
        d.hasLast = true;
        plugin.moveNanos += System.nanoTime() - t0;
        plugin.moveCount++;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.resetMotion(Bukkit.getCurrentTick(), plugin.settings.teleportGrace);
        Location t = e.getTo();
        if (t != null && t.getWorld() != null) {
            d.markSafe(t.getWorld().getName(), t.getX(), t.getY(), t.getZ(), t.getYaw(), t.getPitch());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.resetMotion(Bukkit.getCurrentTick(), plugin.settings.joinGrace);
        Location t = e.getRespawnLocation();
        if (t.getWorld() != null) {
            d.markSafe(t.getWorld().getName(), t.getX(), t.getY(), t.getZ(), t.getYaw(), t.getPitch());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        PlayerData d = plugin.data(e.getPlayer());
        d.resetMotion(Bukkit.getCurrentTick(), plugin.settings.teleportGrace);
        Location l = e.getPlayer().getLocation();
        d.markSafe(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.players.get(p.getUniqueId());
        if (d == null) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        d.velocityUntil = Math.max(d.velocityUntil, tick + plugin.settings.velocityGrace);
        if (plugin.velocityCheck.enabled && !d.globalExempt(p, plugin.settings)) {
            Vector v = e.getVelocity();
            plugin.velocityCheck.begin(p, d, v.getX(), v.getZ(), tick);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent e) {
        PlayerData d = peek(e.getEntity());
        if (d != null) {
            d.velocityUntil = Math.max(d.velocityUntil, Bukkit.getCurrentTick() + plugin.settings.damageGrace);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        grace(e.getLocation().getNearbyPlayers(14.0), plugin.settings.explosionGrace);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        grace(e.getBlock().getLocation().getNearbyPlayers(14.0), plugin.settings.explosionGrace);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (e.getEntity().getType().name().contains("WIND_CHARGE")) {
            grace(e.getEntity().getLocation().getNearbyPlayers(8.0), plugin.settings.windChargeGrace);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        grace(e.getBlock().getLocation().getNearbyPlayers(6.0), plugin.settings.explosionGrace);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        grace(e.getBlock().getLocation().getNearbyPlayers(6.0), plugin.settings.explosionGrace);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (e.getItem() == null) {
            return;
        }
        PlayerData d = plugin.players.get(e.getPlayer().getUniqueId());
        if (d == null) {
            return;
        }
        Material t = e.getItem().getType();
        int tick = Bukkit.getCurrentTick();
        if (t == Material.WIND_CHARGE) {
            d.velocityUntil = Math.max(d.velocityUntil, tick + plugin.settings.windChargeGrace);
        } else if (t == Material.FIREWORK_ROCKET && e.getPlayer().isGliding()) {
            d.boostUntil = tick + plugin.settings.boostGrace;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent e) {
        PlayerData d = peek(e.getEntity());
        if (d != null) {
            d.glideUntil = Bukkit.getCurrentTick() + 40;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRiptide(PlayerRiptideEvent e) {
        PlayerData d = plugin.players.get(e.getPlayer().getUniqueId());
        if (d != null) {
            d.riptideUntil = Bukkit.getCurrentTick() + 60;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlightToggle(PlayerToggleFlightEvent e) {
        PlayerData d = plugin.players.get(e.getPlayer().getUniqueId());
        if (d != null) {
            d.flyUntil = Bukkit.getCurrentTick() + 60;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        PlayerData d = plugin.players.get(e.getPlayer().getUniqueId());
        if (d != null) {
            d.flyUntil = Bukkit.getCurrentTick() + 60;
            d.resetMotion(Bukkit.getCurrentTick(), 20);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent e) {
        PlayerData d = peek(e.getExited());
        if (d != null) {
            d.vehicleUntil = Bukkit.getCurrentTick() + 15;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            return;
        }
        long t0 = System.nanoTime();
        PlayerData d = plugin.data(p);
        int tick = Bukkit.getCurrentTick();
        d.refresh(p, tick);
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        if (p.getInventory().getItemInMainHand().getType() == Material.MACE) {
            d.velocityUntil = Math.max(d.velocityUntil, tick + 30);
        }
        d.moveExempt = d.computeMoveExempt(p, tick);
        plugin.cancelRequested = false;
        for (Check c : plugin.attackChecks) {
            c.attack(p, d, e.getEntity(), e);
        }
        if (plugin.cancelRequested && plugin.settings.enforce) {
            e.setCancelled(true);
        }
        plugin.attackNanos += System.nanoTime() - t0;
        plugin.attackCount++;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnimation(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING || plugin.swingChecks.length == 0) {
            return;
        }
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        for (Check c : plugin.swingChecks) {
            c.swing(p, d);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageBlock(BlockDamageEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        d.mining = true;
        d.miningSince = System.currentTimeMillis();
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        for (Check c : plugin.damageChecks) {
            c.blockDamage(p, d, e.getBlock(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamageAbort(BlockDamageAbortEvent e) {
        PlayerData d = plugin.players.get(e.getPlayer().getUniqueId());
        if (d != null) {
            d.mining = false;
            d.breakStarted = false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        d.mining = false;
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        plugin.cancelRequested = false;
        for (Check c : plugin.breakChecks) {
            c.blockBreak(p, d, e.getBlock(), e);
        }
        if (plugin.cancelRequested && plugin.settings.enforce) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data(p);
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        plugin.cancelRequested = false;
        for (Check c : plugin.placeChecks) {
            c.place(p, d, e);
        }
        if (plugin.cancelRequested && plugin.settings.enforce) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || plugin.invChecks.length == 0) {
            return;
        }
        PlayerData d = plugin.data(p);
        if (d.globalExempt(p, plugin.settings)) {
            return;
        }
        plugin.cancelRequested = false;
        for (Check c : plugin.invChecks) {
            c.inventory(p, d, e);
        }
        if (plugin.cancelRequested && plugin.settings.enforce) {
            e.setCancelled(true);
        }
    }
}
