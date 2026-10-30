package thaumicenergistics.extras;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Scoped port of Packet_R_ParticleFX mode WRENCH_FX: the server broadcasts the
 * Potentia activation beam to players within the original 20-block range and
 * each client renders it through the public WandBeamApi. Original FXBeam
 * parameters: source player position, target block center (+0.5), Potentia
 * color, width 0.2, reverse=false, texture type 0, six-tick lifetime (the core
 * renderer owns its own eight-tick fade - recorded adaptation), skipped when
 * the client particle setting is minimal.
 *
 * Client rendering is isolated in {@link thaumicenergistics.extras.client.WrenchFocusBeamClientHandler}
 * to prevent dedicated-server classloading issues.
 */
public record WrenchFocusBeamPayload(int sourceId, double targetX, double targetY, double targetZ)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WrenchFocusBeamPayload> TYPE =
            new CustomPacketPayload.Type<>(thaumicenergistics.ThaumicEnergistics.id("wrench_focus_beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WrenchFocusBeamPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.sourceId);
                buffer.writeDouble(value.targetX);
                buffer.writeDouble(value.targetY);
                buffer.writeDouble(value.targetZ);
            },
            buffer -> new WrenchFocusBeamPayload(buffer.readVarInt(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

    @Override
    public CustomPacketPayload.Type<WrenchFocusBeamPayload> type() {
        return TYPE;
    }

    /** Original NetworkHandler.sendAreaPacketToClients(packet, 20.0F) range. */
    public static void broadcast(ServerPlayer source, BlockPos target) {
        var level = source.serverLevel();
        var message = new WrenchFocusBeamPayload(source.getId(),
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        for (var viewer : level.players()) {
            if (viewer.distanceToSqr(source.getX(), source.getY(), source.getZ()) <= FocusAEWrenchInteraction.BEAM_RANGE * FocusAEWrenchInteraction.BEAM_RANGE) {
                PacketDistributor.sendToPlayer(viewer, message);
            }
        }
    }

    @EventBusSubscriber(modid = thaumicenergistics.ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, CODEC, (payload, context) ->
                    context.enqueueWork(() -> thaumicenergistics.extras.client.WrenchFocusBeamClientHandler.render(payload)));
        }

        private Registration() {}
    }
}
