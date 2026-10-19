package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaStorageBusItem extends Item implements IPartItem<EssentiaStorageBusPart> {
    public EssentiaStorageBusItem() { super(new Item.Properties()); }
    @Override public Class<EssentiaStorageBusPart> getPartClass() { return EssentiaStorageBusPart.class; }
    @Override public EssentiaStorageBusPart createPart() { return new EssentiaStorageBusPart(this); }
    @Override public InteractionResult useOn(UseOnContext context) { return PartHelper.usePartItem(context); }
}
