package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaLevelEmitterItem extends Item implements IPartItem<EssentiaLevelEmitterPart> {
    public EssentiaLevelEmitterItem() { super(new Item.Properties()); }
    @Override public Class<EssentiaLevelEmitterPart> getPartClass() { return EssentiaLevelEmitterPart.class; }
    @Override public EssentiaLevelEmitterPart createPart() { return new EssentiaLevelEmitterPart(this); }
    @Override public InteractionResult useOn(UseOnContext context) { return PartHelper.usePartItem(context); }
}
