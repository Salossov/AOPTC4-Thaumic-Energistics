package thaumicenergistics.network;

import appeng.api.config.*;
import appeng.api.stacks.AEItemKey;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.crafting.*;
import thaumicenergistics.menu.ArcaneTerminalMenu;

/** Bounded batches replace one menu snapshot atomically, without narrowing long item amounts. */
public record ArcaneTerminalStatePayload(int menuId,int revision,int offset,boolean last,SortOrder sort,SortDir direction,ViewItems view,
                                         List<ArcaneCraftingLogic.Cost> costs,List<ItemTerminalEntry> entries) implements CustomPacketPayload {
    public ArcaneTerminalStatePayload{costs=List.copyOf(costs);entries=List.copyOf(entries);}
    public static final Type<ArcaneTerminalStatePayload> TYPE=new Type<>(ThaumicEnergistics.id("arcane_terminal_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArcaneTerminalStatePayload> CODEC=StreamCodec.of((buffer,value)->{
        buffer.writeVarInt(value.menuId);buffer.writeInt(value.revision);buffer.writeVarInt(value.offset);buffer.writeBoolean(value.last);buffer.writeEnum(value.sort);buffer.writeEnum(value.direction);buffer.writeEnum(value.view);
        buffer.writeVarInt(value.costs.size());for(var cost:value.costs){buffer.writeUtf(cost.aspect().getTag(),256);buffer.writeVarInt(cost.centivis());buffer.writeBoolean(cost.enough());}
        buffer.writeVarInt(value.entries.size());for(var entry:value.entries){ItemStack.STREAM_CODEC.encode(buffer,entry.key().toStack());buffer.writeVarLong(entry.amount());buffer.writeBoolean(entry.craftable());}
    },buffer->{
        int menu=buffer.readVarInt(),revision=buffer.readInt(),offset=buffer.readVarInt();boolean last=buffer.readBoolean();var sort=buffer.readEnum(SortOrder.class);var direction=buffer.readEnum(SortDir.class);var view=buffer.readEnum(ViewItems.class);
        int count=buffer.readVarInt();if(count<0||count>6)throw new io.netty.handler.codec.DecoderException("Invalid arcane cost count");var costs=new ArrayList<ArcaneCraftingLogic.Cost>();
        for(int i=0;i<count;i++){var aspect=Aspect.getAspect(buffer.readUtf(256));int amount=buffer.readVarInt();boolean enough=buffer.readBoolean();if(aspect==null||!aspect.isPrimal()||amount<0)throw new io.netty.handler.codec.DecoderException("Invalid arcane cost");costs.add(new ArcaneCraftingLogic.Cost(aspect,amount,enough));}
        count=buffer.readVarInt();if(count<0||count>64||offset<0)throw new io.netty.handler.codec.DecoderException("Invalid arcane terminal batch");var entries=new ArrayList<ItemTerminalEntry>();
        for(int i=0;i<count;i++)entries.add(new ItemTerminalEntry(AEItemKey.of(ItemStack.STREAM_CODEC.decode(buffer)),buffer.readVarLong(),buffer.readBoolean()));
        return new ArcaneTerminalStatePayload(menu,revision,offset,last,sort,direction,view,costs,entries);
    });
    @Override public Type<ArcaneTerminalStatePayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof ArcaneTerminalMenu menu)menu.receive(payload);}));}
    }
}
