package dev.fire;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.player.DiggingAction;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerAbilities;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class FirePackets extends PacketListenerAbstract implements PacketBridge {

    private final FirePlugin plugin;
    private final Map<UUID, PacketData> data = new ConcurrentHashMap<>();
    private final TimerCheck timer;
    private final ClickerCheck clicker;
    private final NoSwingCheck noSwing;
    private final InvSpeedCheck invSpeed;
    private final BadPacketsCheck bad;

    FirePackets(FirePlugin plugin) {
        super(PacketListenerPriority.NORMAL);
        this.plugin = plugin;
        this.timer = (TimerCheck) plugin.checkById("timer");
        this.clicker = (ClickerCheck) plugin.checkById("clicker");
        this.noSwing = (NoSwingCheck) plugin.checkById("noswing");
        this.invSpeed = (InvSpeedCheck) plugin.checkById("invspeed");
        this.bad = (BadPacketsCheck) plugin.checkById("badpackets");
    }

    void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        data.clear();
    }

    @Override
    public void pop(UUID id) {
        data.computeIfAbsent(id, k -> new PacketData(k, plugin.checks.size())).lastPop = System.nanoTime();
    }

    @Override
    public void forget(UUID id) {
        data.remove(id);
    }

    private void post(UUID id, Check c, String detail) {
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                PlayerData d = plugin.players.get(id);
                if (p == null || d == null) {
                    return;
                }
                plugin.violations.flag(p, d, c, detail);
            });
        } catch (RuntimeException ignored) {
        }
    }

    private void hit(UUID id, PacketReceiveEvent event, BadPacketsCheck.Hit hit, PacketData pd) {
        if (hit == null) {
            return;
        }
        if (hit.cancel() && plugin.settings.enforce && bad.cancel) {
            event.setCancelled(true);
        }
        if (hit.force() || bad.confirm(pd)) {
            post(id, bad, hit.detail());
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) {
            return;
        }
        User user = event.getUser();
        UUID id = user == null ? null : user.getUUID();
        if (id == null) {
            return;
        }
        PlayerData d = plugin.players.get(id);
        if (d == null) {
            return;
        }
        PacketData pd = data.computeIfAbsent(id, k -> new PacketData(k, plugin.checks.size()));
        long now = System.nanoTime();
        PacketTypeCommon type = event.getPacketType();
        boolean exempt = d.pktExempt;

        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            if (exempt) {
                pd.timerLast = 0;
                pd.timerBalance = 0;
                return;
            }
            WrapperPlayClientPlayerFlying w = new WrapperPlayClientPlayerFlying(event);
            if (timer.enabled) {
                int tick = plugin.tickNow;
                String detail = timer.packet(pd, now, tick < d.teleportUntil || tick < d.joinUntil || pd.teleportPending);
                if (detail != null) {
                    post(id, timer, detail);
                }
            }
            if (bad.enabled && w.hasPositionChanged()) {
                double x = w.getLocation().getX();
                double y = w.getLocation().getY();
                double z = w.getLocation().getZ();
                BadPacketsCheck.Hit h = bad.position(pd, x, y, z, plugin.tickNow < d.graceUntil());
                if (h != null) {
                    hit(id, event, h, pd);
                    if (!event.isCancelled() && !h.force()) {
                        bad.accept(pd, x, y, z);
                    }
                }
            }
            if (noSwing.enabled) {
                String detail = noSwing.poll(pd, now);
                if (detail != null) {
                    post(id, noSwing, detail);
                }
            }
            return;
        }

        if (exempt) {
            return;
        }

        if (type == PacketType.Play.Client.ANIMATION) {
            if (noSwing.enabled) {
                noSwing.swing(pd, now);
            }
            if (clicker.enabled && !pd.digging && (now - pd.lastUse) / 1_000_000.0 > 25.0) {
                String detail = clicker.packet(pd, now);
                if (detail != null) {
                    post(id, clicker, detail);
                }
            }
        } else if (type == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity w = new WrapperPlayClientInteractEntity(event);
            if (w.getAction() == WrapperPlayClientInteractEntity.InteractAction.ATTACK && noSwing.enabled) {
                noSwing.attack(pd, now);
            }
        } else if (type == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging w = new WrapperPlayClientPlayerDigging(event);
            DiggingAction a = w.getAction();
            if (a == DiggingAction.START_DIGGING) {
                pd.digging = true;
            } else if (a == DiggingAction.CANCELLED_DIGGING || a == DiggingAction.FINISHED_DIGGING) {
                pd.digging = false;
            } else if (a == DiggingAction.SWAP_ITEM_WITH_OFFHAND && invSpeed.enabled) {
                String detail = invSpeed.totem(pd, now);
                if (detail != null) {
                    post(id, invSpeed, detail);
                }
            }
        } else if (type == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            pd.lastUse = now;
            if (bad.enabled) {
                Vector3f c = new WrapperPlayClientPlayerBlockPlacement(event).getCursorPosition();
                if (c != null) {
                    hit(id, event, bad.cursor(c.getX(), c.getY(), c.getZ()), pd);
                }
            }
        } else if (type == PacketType.Play.Client.USE_ITEM) {
            pd.lastUse = now;
        } else if (type == PacketType.Play.Client.CLICK_WINDOW) {
            if (invSpeed.enabled) {
                String detail = invSpeed.click(pd, now);
                if (detail == null) {
                    detail = invSpeed.totem(pd, now);
                }
                if (detail != null) {
                    post(id, invSpeed, detail);
                }
            }
        } else if (type == PacketType.Play.Client.PLAYER_ABILITIES) {
            if (bad.enabled) {
                boolean flying = new WrapperPlayClientPlayerAbilities(event).isFlying();
                hit(id, event, bad.abilities(flying, d.pktAllowFlight), pd);
            }
        } else if (type == PacketType.Play.Client.ENTITY_ACTION) {
            if (bad.enabled) {
                WrapperPlayClientEntityAction.Action a = new WrapperPlayClientEntityAction(event).getAction();
                if (a == WrapperPlayClientEntityAction.Action.START_SPRINTING || a == WrapperPlayClientEntityAction.Action.STOP_SPRINTING) {
                    hit(id, event, bad.sprint(pd, now), pd);
                }
            }
        } else if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            pd.teleportPending = false;
            pd.hasPos = false;
            pd.timerBalance = 0;
            pd.timerLast = 0;
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getConnectionState() != ConnectionState.PLAY) {
            return;
        }
        PacketTypeCommon type = event.getPacketType();
        if (type != PacketType.Play.Server.PLAYER_POSITION_AND_LOOK && type != PacketType.Play.Server.RESPAWN) {
            return;
        }
        User user = event.getUser();
        UUID id = user == null ? null : user.getUUID();
        if (id == null) {
            return;
        }
        PacketData pd = data.computeIfAbsent(id, k -> new PacketData(k, plugin.checks.size()));
        pd.teleportPending = true;
        pd.hasPos = false;
    }
}
