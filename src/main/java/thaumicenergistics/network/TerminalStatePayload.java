package thaumicenergistics.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaTerminalMenu;
import thaumicenergistics.storage.*;

/** Full changed snapshots of the original monitor list, bound to one current menu. */
public record TerminalStatePayload(int containerId,int sort,int view,String selected,List<TerminalEntry> entries) implements CustomPacketPayload{
    public TerminalStatePayload{entries=List.copyOf(entries);}
    public static final Type<TerminalStatePayload> TYPE=new Type<>(ThaumicEnergistics.id("terminal_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TerminalStatePayload> CODEC=StreamCodec.of((buffer,value)->{
        buffer.writeVarInt(value.containerId);buffer.writeByte(value.sort);buffer.writeByte(value.view);buffer.writeUtf(value.selected,256);buffer.writeVarInt(value.entries.size());
        for(var entry:value.entries){buffer.writeUtf(entry.key().tag(),256);buffer.writeVarLong(entry.amount());buffer.writeBoolean(entry.craftable());}
    },buffer->{
        int id=buffer.readVarInt(),sort=buffer.readUnsignedByte(),view=buffer.readUnsignedByte();String selected=buffer.readUtf(256);int count=buffer.readVarInt();
        if(count<0||count>4096)throw new io.netty.handler.codec.DecoderException("Invalid terminal entry count");
        var entries=new ArrayList<TerminalEntry>(count);
        for(int i=0;i<count;i++)entries.add(new TerminalEntry(EssentiaKey.ofTag(buffer.readUtf(256)),buffer.readVarLong(),buffer.readBoolean()));
        return new TerminalStatePayload(id,sort,view,selected,entries);
    });
    @Override public Type<TerminalStatePayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
            event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
                if(context.player().containerMenu instanceof EssentiaTerminalMenu menu)menu.receive(payload);
            }));
        }
    }
}
