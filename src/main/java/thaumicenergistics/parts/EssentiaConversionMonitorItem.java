package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaConversionMonitorItem extends Item implements IPartItem<EssentiaConversionMonitorPart> {
    public EssentiaConversionMonitorItem(){super(new Properties());}
    @Override public Class<EssentiaConversionMonitorPart> getPartClass(){return EssentiaConversionMonitorPart.class;}
    @Override public EssentiaConversionMonitorPart createPart(){return new EssentiaConversionMonitorPart(this);}
    @Override public InteractionResult useOn(UseOnContext context){return PartHelper.usePartItem(context);}
}
