package dev.fire;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class FirePlugin extends JavaPlugin {

    final Move move = new Move();
    final Move scratch = new Move();
    final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    final Set<UUID> alertSet = new HashSet<>();
    final List<Check> checks = new ArrayList<>();
    final Settings settings = new Settings();
    Messages messages;
    Violations violations;
    YamlConfiguration checksCfg;
    VelocityCheck velocityCheck;
    PacketBridge packets;
    volatile int tickNow;
    boolean moveFlagged;
    Check[] moveChecks = new Check[0];
    Check[] attackChecks = new Check[0];
    Check[] swingChecks = new Check[0];
    Check[] breakChecks = new Check[0];
    Check[] damageChecks = new Check[0];
    Check[] placeChecks = new Check[0];
    Check[] invChecks = new Check[0];
    Check[] tickChecks = new Check[0];
    Check[] vehicleChecks = new Check[0];
    boolean cancelRequested;
    volatile boolean lagging;
    double tps = 20.0;
    double mspt;
    long moveNanos;
    long moveCount;
    long attackNanos;
    long attackCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("checks.yml");
        saveIfMissing("messages.yml");
        if (!versionOk()) {
            getLogger().severe("Fire! requires Minecraft 26.3 or newer. Current: " + Bukkit.getMinecraftVersion());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        messages = new Messages(this);
        violations = new Violations(this);
        add(new TimerCheck(this));
        add(new AimCheck(this));
        add(new SpeedCheck(this));
        add(new StrafeCheck(this));
        add(new FlightCheck(this));
        add(new AirJumpCheck(this));
        add(new HighJumpCheck(this));
        add(new StepCheck(this));
        add(new HoverCheck(this));
        add(new GroundCheck(this));
        add(new PhaseCheck(this));
        add(new SprintCheck(this));
        add(new NoSlowCheck(this));
        add(new LiquidCheck(this));
        add(new ClimbCheck(this));
        add(new VehicleCheck(this));
        add(new ElytraCheck(this));
        velocityCheck = new VelocityCheck(this);
        add(velocityCheck);
        add(new ReachCheck(this));
        add(new HitboxCheck(this));
        add(new MultiAuraCheck(this));
        add(new ClickerCheck(this));
        add(new CriticalsCheck(this));
        add(new FastBreakCheck(this));
        add(new NukerCheck(this));
        add(new BlockVisibleCheck(this));
        add(new InteractReachCheck(this));
        add(new ScaffoldCheck(this));
        add(new FastPlaceCheck(this));
        add(new AirPlaceCheck(this));
        add(new TowerCheck(this));
        add(new XRayCheck(this));
        add(new InvMoveCheck(this));
        add(new NoSwingCheck(this));
        add(new InvSpeedCheck(this));
        add(new BadPacketsCheck(this));
        reloadAll();
        if (Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            try {
                packets = PacketsLoader.create(this);
            } catch (Throwable t) {
                packets = null;
                getLogger().log(Level.WARNING, "PacketEvents found but Fire! could not hook into it", t);
            }
        } else {
            getLogger().warning("PacketEvents not found: packet checks (precise Timer, Clicker, NoSwing, InvSpeed, BadPackets) are disabled.");
        }
        getServer().getPluginManager().registerEvents(new FireListener(this), this);
        FireCommand cmd = new FireCommand(this);
        PluginCommand pc = getCommand("fire");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        Bukkit.getScheduler().runTaskTimer(this, this::secondTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> tickNow = Bukkit.getCurrentTick(), 1L, 1L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            int tick = Bukkit.getCurrentTick();
            d.resetMotion(tick, settings.joinGrace);
            d.joinUntil = tick + settings.joinGrace;
            d.syncPacket(p, settings);
            Location l = p.getLocation();
            d.markSafe(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
            if (settings.alertsDefaultOn && p.hasPermission("fire.alerts")) {
                d.alerts = true;
                alertSet.add(p.getUniqueId());
            }
        }
        getLogger().info("Fire! enabled: " + checks.size() + " checks, mode=" + (settings.enforce ? "enforce" : "monitor")
                + ", native collisions=" + Terrain.nativeCollisions());
    }

    @Override
    public void onDisable() {
        if (packets != null) {
            packets.shutdown();
            packets = null;
        }
        if (violations != null) {
            violations.shutdown();
        }
        players.clear();
        alertSet.clear();
    }

    private void add(Check c) {
        c.index = checks.size();
        checks.add(c);
    }

    private void saveIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private boolean versionOk() {
        if (!getConfig().getBoolean("general.require-version", true)) {
            return true;
        }
        String[] parts = Bukkit.getMinecraftVersion().split("[.\\-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 26 || (major == 26 && minor >= 3);
        } catch (NumberFormatException e) {
            return true;
        }
    }

    void reloadAll() {
        reloadConfig();
        settings.load(getConfig());
        messages.load();
        checksCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "checks.yml"));
        for (Check c : checks) {
            ConfigurationSection s = checksCfg.getConfigurationSection(c.id);
            if (s == null) {
                s = checksCfg.createSection(c.id);
            }
            c.load(s);
        }
        rebuildHooks();
    }

    void rebuildHooks() {
        moveChecks = pick(Check.MOVE);
        attackChecks = pick(Check.ATTACK);
        swingChecks = pick(Check.SWING);
        breakChecks = pick(Check.BREAK);
        damageChecks = pick(Check.DAMAGE);
        placeChecks = pick(Check.PLACE);
        invChecks = pick(Check.INV);
        tickChecks = pick(Check.TICK);
        vehicleChecks = pick(Check.VEHICLE);
    }

    private Check[] pick(int mask) {
        return checks.stream().filter(c -> c.enabled && (c.hooks & mask) != 0).toArray(Check[]::new);
    }

    void saveChecks() {
        try {
            checksCfg.save(new File(getDataFolder(), "checks.yml"));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not save checks.yml", e);
        }
    }

    Check checkById(String id) {
        for (Check c : checks) {
            if (c.id.equalsIgnoreCase(id)) {
                return c;
            }
        }
        return null;
    }

    PlayerData data(Player p) {
        PlayerData d = players.get(p.getUniqueId());
        if (d == null) {
            d = new PlayerData(p.getUniqueId(), p.getName(), checks.size());
            players.put(p.getUniqueId(), d);
        }
        return d;
    }

    void setback(Player p, PlayerData d) {
        if (!d.hasSafe) {
            return;
        }
        World w = Bukkit.getWorld(d.safeWorld);
        if (w == null) {
            return;
        }
        Location l = new Location(w, d.safeX, d.safeY, d.safeZ, d.safeYaw, d.safePitch);
        d.teleportUntil = Bukkit.getCurrentTick() + settings.teleportGrace;
        Bukkit.getScheduler().runTask(this, () -> {
            if (p.isOnline()) {
                p.teleport(l);
            }
        });
    }

    private void secondTick() {
        double[] t = Bukkit.getTPS();
        tps = Math.min(20.0, t.length > 0 ? t[0] : 20.0);
        mspt = Bukkit.getAverageTickTime();
        lagging = tps < settings.tpsGuard || mspt > 80.0;
        violations.decay();
        int tick = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = data(p);
            d.refresh(p, tick);
            d.syncPacket(p, settings);
            if (tickChecks.length == 0 || lagging || d.globalExempt(p, settings)) {
                continue;
            }
            d.moveExempt = d.computeMoveExempt(p, tick);
            for (Check c : tickChecks) {
                c.tick(p, d);
            }
        }
    }
}
