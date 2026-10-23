package thaumicenergistics.parts;

import appeng.api.parts.PartHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumicenergistics.ThaumicEnergistics;

/** Restore the original shift-right-click despite vanilla's held-item sneak bypass.
 * AE2's onShiftClicked is a left-click callback, not the old onShiftActivate.
 */
@EventBusSubscriber(modid=ThaumicEnergistics.ID)
public final class ConversionMonitorInteraction {
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void portableContainerUse(PlayerInteractEvent.RightClickBlock event){
        if(!event.getEntity().isShiftKeyDown() || event.getUseBlock()==TriState.FALSE
                || thaumicenergistics.storage.PortableAspectItems.inspect(event.getItemStack()).isEmpty())return;
        var host=PartHelper.getPartHost(event.getLevel(),event.getPos());
        if(host==null)return;
        var selected=host.selectPartWorld(event.getHitVec().getLocation());
        if(selected!=null&&selected.part instanceof EssentiaConversionMonitorPart)event.setUseBlock(TriState.TRUE);
    }
    private ConversionMonitorInteraction(){}
}
