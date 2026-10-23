package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaTerminalMenu;

/** No client position, stack or amount is authoritative. */
public record TerminalActionPayload(int containerId,int action,String aspect) implements CustomPacketPayload{
    public static final Type<TerminalActionPayload> TYPE=new Type<>(ThaumicEnergistics.id("terminal_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TerminalActionPayload> CODEC=StreamCodec.of((buffer,value)->{
        buffer.writeVarInt(value.containerId);buffer.writeByte(value.action);buffer.writeUtf(value.aspect,256);
    },buffer->new TerminalActionPayload(buffer.readVarInt(),buffer.readUnsignedByte(),buffer.readUtf(256)));
    @Override public Type<TerminalActionPayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
            event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
                if(context.player().containerMenu instanceof EssentiaTerminalMenu menu)menu.action(context.player(),payload.containerId,payload.action,payload.aspect);
            }));
        }
    }
}
