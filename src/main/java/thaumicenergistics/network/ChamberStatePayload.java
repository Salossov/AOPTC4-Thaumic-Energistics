package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.VibrationChamberMenu;

public record ChamberStatePayload(int containerId,String stored,int amount,String processing,float power,float maximumPower,int remaining,int total) implements CustomPacketPayload {
    public static final Type<ChamberStatePayload> TYPE=new Type<>(ThaumicEnergistics.id("chamber_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ChamberStatePayload> CODEC=StreamCodec.of((buffer,state)->{
        buffer.writeVarInt(state.containerId);buffer.writeUtf(state.stored,256);buffer.writeVarInt(state.amount);buffer.writeUtf(state.processing,256);buffer.writeFloat(state.power);buffer.writeFloat(state.maximumPower);buffer.writeInt(state.remaining);buffer.writeInt(state.total);
    },buffer->new ChamberStatePayload(buffer.readVarInt(),buffer.readUtf(256),buffer.readVarInt(),buffer.readUtf(256),buffer.readFloat(),buffer.readFloat(),buffer.readInt(),buffer.readInt()));
    @Override public Type<ChamberStatePayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof VibrationChamberMenu menu)menu.receive(payload);}));}
    }
}
