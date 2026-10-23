package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaTerminalItem extends Item implements IPartItem<EssentiaTerminalPart>{
    public EssentiaTerminalItem(){super(new Properties());}
    @Override public Class<EssentiaTerminalPart> getPartClass(){return EssentiaTerminalPart.class;}
    @Override public EssentiaTerminalPart createPart(){return new EssentiaTerminalPart(this);}
    @Override public InteractionResult useOn(UseOnContext context){return PartHelper.usePartItem(context);}
}
