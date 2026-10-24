package dev.fire;

import java.util.UUID;

interface PacketBridge {

    void pop(UUID id);

    void forget(UUID id);

    void shutdown();
}
