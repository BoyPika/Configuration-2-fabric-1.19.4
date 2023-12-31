package dev.toma.configuration.network;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.network.api.IClientPacket;
import dev.toma.configuration.network.api.IPacket;
import dev.toma.configuration.network.api.IPacketDecoder;
import dev.toma.configuration.network.api.IPacketEncoder;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.lang.reflect.InvocationTargetException;
import java.util.function.BiConsumer;

public final class Networking {

    public static final Marker MARKER = MarkerManager.getMarker("Network");

    public static void sendClientPacket(ServerPlayer target, IClientPacket<?> packet) {
        dispatch(packet, (packetId, buffer) -> ServerPlayNetworking.send(target, packetId, buffer));
    }

    private static <T> void dispatch(IPacket<T> packet, BiConsumer<ResourceLocation, FriendlyByteBuf> dispatcher) {
        ResourceLocation packetId = packet.getPacketId();
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        IPacketEncoder<T> encoder = packet.getEncoder();
        T data = packet.getPacketData();
        encoder.encode(data, buffer);
        dispatcher.accept(packetId, buffer);
    }

    public static final class PacketRegistry {

        public static void registerClient() {
            registerServer2ClientReceiver(S2C_SendConfigData.class);
        }

        @Environment(EnvType.CLIENT)
        private static <T> void registerServer2ClientReceiver(Class<? extends IClientPacket<T>> clientPacketClass) {
            try {
                IClientPacket<T> packet = clientPacketClass.getDeclaredConstructor().newInstance();
                ResourceLocation packetId = packet.getPacketId();
                ClientPlayNetworking.registerGlobalReceiver(packetId, (client, handler, buffer, responseDispatcher) -> {
                    IPacketDecoder<T> decoder = packet.getDecoder();
                    T packetData = decoder.decode(buffer);
                    client.execute(() -> packet.handleClientsidePacket(client, handler, packetData, responseDispatcher));
                });
            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException exc) {
                Configuration.LOGGER.fatal(MARKER, "Couldn't instantiate new client packet from class {}, make sure it declares public default constructor", clientPacketClass.getSimpleName());
                throw new RuntimeException(exc);
            }
        }
    }
}
