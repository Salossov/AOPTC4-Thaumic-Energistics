package thaumicenergistics.assembler;

import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;

/** The six source bars and progress are snapshots; all inventory stacks use normal menu sync. */
public record AssemblerStatePayload(int menuId,boolean active,boolean crafting,int ticks,int speed,List<Integer> vis,List<Float> modifiers) implements CustomPacketPayload {
    public AssemblerStatePayload{
        vis=List.copyOf(vis);modifiers=List.copyOf(modifiers);
        if(vis.size()!=6||modifiers.size()!=6||ticks<0||speed<0||speed>AssemblerRules.MAX_SPEED_UPGRADES
                ||vis.stream().anyMatch(n->n<0)||modifiers.stream().anyMatch(n->!Float.isFinite(n)))throw new IllegalArgumentException("Invalid assembler state");
    }
    public static AssemblerStatePayload empty(int id){return new AssemblerStatePayload(id,false,false,0,0,Collections.nCopies(6,0),Collections.nCopies(6,1F));}
    public static final Type<AssemblerStatePayload> TYPE=new Type<>(ThaumicEnergistics.id("arcane_assembler_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AssemblerStatePayload> CODEC=StreamCodec.of((buffer,state)->{
        buffer.writeVarInt(state.menuId);buffer.writeBoolean(state.active);buffer.writeBoolean(state.crafting);buffer.writeVarInt(state.ticks);buffer.writeVarInt(state.speed);
        for(int i=0;i<6;i++){buffer.writeVarInt(state.vis.get(i));buffer.writeFloat(state.modifiers.get(i));}
    },buffer->{
        int id=buffer.readVarInt();boolean active=buffer.readBoolean(),crafting=buffer.readBoolean();int ticks=buffer.readVarInt(),speed=buffer.readVarInt();
        var vis=new ArrayList<Integer>(6);var modifiers=new ArrayList<Float>(6);for(int i=0;i<6;i++){vis.add(buffer.readVarInt());modifiers.add(buffer.readFloat());}
        return new AssemblerStatePayload(id,active,crafting,ticks,speed,vis,modifiers);
    });
    @Override public Type<AssemblerStatePayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{
            if(context.player().containerMenu instanceof ArcaneAssemblerMenu menu)menu.receive(payload);
        }));}
    }
}
