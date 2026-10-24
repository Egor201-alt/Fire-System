package dev.fire;

final class PacketsLoader {

    private PacketsLoader() {
    }

    static PacketBridge create(FirePlugin plugin) {
        FirePackets packets = new FirePackets(plugin);
        packets.register();
        return packets;
    }
}
