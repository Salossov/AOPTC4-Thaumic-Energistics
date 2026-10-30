package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaLevelEmitterMenu;

public record EmitterThresholdPayload(int containerId,long threshold) implements CustomPacketPayload {
    public static final Type<EmitterThresholdPayload> TYPE=new Type<>(ThaumicEnergistics.id("emitter_threshold"));
    public static final StreamCodec<RegistryFriendlyByteBuf,EmitterThresholdPayload> CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,EmitterThresholdPayload::containerId,ByteBufCodecs.VAR_LONG,EmitterThresholdPayload::threshold,EmitterThresholdPayload::new);
    @Override public Type<EmitterThresholdPayload> type() { return TYPE; }
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
                var player=context.player();
                if(player.containerMenu instanceof EssentiaLevelEmitterMenu menu)menu.setThreshold(player,payload.containerId,payload.threshold);
            }));
        }
    }
}
