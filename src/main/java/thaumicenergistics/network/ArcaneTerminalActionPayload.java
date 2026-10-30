package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.ArcaneTerminalMenu;

/** Client intent; all ownership, amounts, research and ingredients are resolved on the server. */
public record ArcaneTerminalActionPayload(int menuId,int action,int index,int button,boolean shift,ItemStack stack,String recipe) implements CustomPacketPayload{
    public ArcaneTerminalActionPayload{stack=stack.copyWithCount(stack.isEmpty()?0:1);}
    @Override public ItemStack stack(){return stack.copy();}
    public static final Type<ArcaneTerminalActionPayload> TYPE=new Type<>(ThaumicEnergistics.id("arcane_terminal_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ArcaneTerminalActionPayload> CODEC=StreamCodec.of((buffer,value)->{
        buffer.writeVarInt(value.menuId);buffer.writeVarInt(value.action);buffer.writeVarInt(value.index);buffer.writeInt(value.button);buffer.writeBoolean(value.shift);ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,value.stack);buffer.writeUtf(value.recipe,256);
    },buffer->new ArcaneTerminalActionPayload(buffer.readVarInt(),buffer.readVarInt(),buffer.readVarInt(),buffer.readInt(),buffer.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),buffer.readUtf(256)));
    @Override public Type<ArcaneTerminalActionPayload> type(){return TYPE;}
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToServer(TYPE,CODEC,(payload,context)->context.enqueueWork(()->{if(context.player().containerMenu instanceof ArcaneTerminalMenu menu)menu.action(context.player(),payload);}));}
    }
}
