package thaumicenergistics.extras;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.api.client.WandBeamApi;
import thaumcraft.api.aspects.Aspects;

/**
 * Scoped port of Packet_R_ParticleFX mode WRENCH_FX: the server broadcasts the
 * Potentia activation beam to players within the original 20-block range and
 * each client renders it through the public WandBeamApi. Original FXBeam
 * parameters: source player position, target block center (+0.5), Potentia
 * color, width 0.2, reverse=false, texture type 0, six-tick lifetime (the core
 * renderer owns its own eight-tick fade - recorded adaptation), skipped when
 * the client particle setting is minimal.
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

    @EventBusSubscriber(modid = thaumicenergistics.ThaumicEnergistics.ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> render(payload, context.player())));
        }

        private static void render(WrenchFocusBeamPayload payload, Player player) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var level = minecraft.level;
            if (level == null || !(level.getEntity(payload.sourceId()) instanceof Player source)) {
                return;
            }
            // Original client gate: gameSettings.particleSetting != 2 (minimal).
            if (minecraft.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) {
                return;
            }
            WandBeamApi.emit(source, new WandBeamApi.Beam(new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()),
                    0, Aspects.ENERGY.getColor(), false, 0.2f));
        }

        private Registration() {}
    }
}
