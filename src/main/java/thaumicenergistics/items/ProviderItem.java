package thaumicenergistics.items;

import net.minecraft.core.Direction;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.blocks.ProviderBlockEntity;

/** Placement attachment metadata replacement, independent of AE2 orientation. */
public final class ProviderItem extends BlockItem {
    public ProviderItem(Block block){super(block,new Item.Properties());}
    @Override protected boolean placeBlock(BlockPlaceContext context,BlockState state){
        if(!super.placeBlock(context,state))return false;
        if(!context.getLevel().isClientSide&&context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ProviderBlockEntity provider)
            provider.attachment(context.getClickedFace().getOpposite());
        return true;
    }
}
