package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.StoragePriorityMenu;

/** Absolute numeric input; the server derives the target solely from the player's current menu. */
public record PriorityPayload(int containerId, int value) implements CustomPacketPayload {
    public static final Type<PriorityPayload> TYPE = new Type<>(ThaumicEnergistics.id("priority"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PriorityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,PriorityPayload::containerId,ByteBufCodecs.INT,PriorityPayload::value,PriorityPayload::new);
    @Override public Type<PriorityPayload> type() { return TYPE; }
    @EventBusSubscriber(modid = ThaumicEnergistics.ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE,CODEC,(payload,context) -> context.enqueueWork(() -> {
                var player = context.player();
                if (player.containerMenu instanceof StoragePriorityMenu menu) menu.setPriority(player,payload.containerId,payload.value);
            }));
        }
    }
}
