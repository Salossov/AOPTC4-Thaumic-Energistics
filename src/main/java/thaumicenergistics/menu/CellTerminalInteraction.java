package thaumicenergistics.menu;

import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.util.InteractionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.EssentiaCellItem;

/** Modern AE2 removed ICellHandler.openChestGui. Intercept only its storage-display face;
 * the other faces still open native cell/configuration slots. Never override a protection denial.
 */
@EventBusSubscriber(modid=ThaumicEnergistics.ID)
public final class CellTerminalInteraction{
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void openCell(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity().isShiftKeyDown()||event.getUseBlock()==TriState.FALSE||InteractionUtil.canWrenchRotate(event.getItemStack())
                ||event.getItemStack().getItem() instanceof appeng.api.implementations.items.IMemoryCard)return;
        if(!(event.getLevel().getBlockEntity(event.getPos()) instanceof MEChestBlockEntity chest)||event.getHitVec().getDirection()!=chest.getTop()
                ||!(chest.getCellItem(0) instanceof EssentiaCellItem))return;
        if(!event.getLevel().isClientSide){
            var host=new CellTerminalHost(chest);
            if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)||!host.valid(player))return;
            player.openMenu(new SimpleMenuProvider((id,inv,owner)->new EssentiaTerminalMenu(id,inv,host),Component.translatable("thaumicenergistics.gui.essentia.cell.title")));
        }
        event.setCanceled(true);event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
    private CellTerminalInteraction(){}
}
