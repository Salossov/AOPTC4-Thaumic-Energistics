package thaumicenergistics.parts;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class VisInterfaceItem extends Item implements IPartItem<VisInterfacePart> {
    public VisInterfaceItem() { super(new Item.Properties()); }
    @Override public Class<VisInterfacePart> getPartClass() { return VisInterfacePart.class; }
    @Override public VisInterfacePart createPart() { return new VisInterfacePart(this); }
    @Override public InteractionResult useOn(UseOnContext context) { return PartHelper.usePartItem(context); }
}
