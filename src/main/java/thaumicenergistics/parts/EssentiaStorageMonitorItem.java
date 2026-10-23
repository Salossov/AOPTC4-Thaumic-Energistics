package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaStorageMonitorItem extends Item implements IPartItem<EssentiaStorageMonitorPart> {
    public EssentiaStorageMonitorItem(){super(new Properties());}
    @Override public Class<EssentiaStorageMonitorPart> getPartClass(){return EssentiaStorageMonitorPart.class;}
    @Override public EssentiaStorageMonitorPart createPart(){return new EssentiaStorageMonitorPart(this);}
    @Override public InteractionResult useOn(UseOnContext context){return PartHelper.usePartItem(context);}
}
