package thaumicenergistics.distillation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Action payload for Distillation Pattern Encoder GUI interactions.
 * Server validates container ID, distance, permissions, and index bounds.
 */
public record DistillationPatternEncoderActionPayload(int containerId, BlockPos pos, int action, int index) implements CustomPacketPayload {
    public static final int ACTION_ENCODE = 0;
    public static final int ACTION_SELECT_ASPECT = 1;

    public static final Type<DistillationPatternEncoderActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "distillation_encoder_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DistillationPatternEncoderActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DistillationPatternEncoderActionPayload::containerId,
            BlockPos.STREAM_CODEC, DistillationPatternEncoderActionPayload::pos,
            ByteBufCodecs.VAR_INT, DistillationPatternEncoderActionPayload::action,
            ByteBufCodecs.VAR_INT, DistillationPatternEncoderActionPayload::index,
            DistillationPatternEncoderActionPayload::new
    );

    @Override
    public Type<DistillationPatternEncoderActionPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> {
                var player = context.player();
                if (player.containerMenu != null
                        && player.containerMenu.containerId == payload.containerId()
                        && player.containerMenu instanceof DistillationPatternEncoderMenu menu
                        && menu.stillValid(player)) {
                    if (payload.action() == ACTION_ENCODE) {
                        menu.onEncodePattern();
                    } else if (payload.action() == ACTION_SELECT_ASPECT) {
                        menu.onSelectAspectAction(payload.index());
                    }
                }
            }));
        }
    }
}
