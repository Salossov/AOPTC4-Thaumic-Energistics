package thaumicenergistics.parts;

import appeng.api.parts.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class ArcaneTerminalItem extends Item implements IPartItem<ArcaneTerminalPart>{
    public ArcaneTerminalItem(){super(new Properties());}
    @Override public Class<ArcaneTerminalPart> getPartClass(){return ArcaneTerminalPart.class;}
    @Override public ArcaneTerminalPart createPart(){return new ArcaneTerminalPart(this);}
    @Override public InteractionResult useOn(UseOnContext context){return PartHelper.usePartItem(context);}
}
