package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class EssentiaBusItem extends Item implements IPartItem<EssentiaIOBusPart> {
    private final boolean importing;
    public EssentiaBusItem(boolean importing) { super(new Item.Properties()); this.importing = importing; }
    @Override public Class<EssentiaIOBusPart> getPartClass() { return EssentiaIOBusPart.class; }
    @Override public EssentiaIOBusPart createPart() { return new EssentiaIOBusPart(this, importing); }
    @Override public InteractionResult useOn(UseOnContext context) { return PartHelper.usePartItem(context); }
}
