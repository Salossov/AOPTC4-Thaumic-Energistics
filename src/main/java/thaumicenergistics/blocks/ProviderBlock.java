package thaumicenergistics.blocks;

import appeng.api.orientation.*;
import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

/** Providers are unoriented full cubes; clicked face selects the initial cable. */
public final class ProviderBlock<T extends ProviderBlockEntity> extends AEBaseEntityBlock<T> {
    public ProviderBlock(){super(Properties.of().strength(1).sound(SoundType.METAL));}
    @Override public IOrientationStrategy getOrientationStrategy(){return OrientationStrategies.none();}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!InteractionUtil.canWrenchDisassemble(stack))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!(level.getBlockEntity(pos) instanceof ProviderBlockEntity provider)||!provider.valid(player))return ItemInteractionResult.FAIL;
        if(player.isShiftKeyDown()&&!level.isClientSide){provider.onBreakBlock();level.removeBlock(pos,false);popResource(level,pos,new ItemStack(this));}
        // Original consumes an ordinary wrench click without rotating the cube.
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos neighborPos,boolean moved){
        super.neighborChanged(state,level,pos,neighbor,neighborPos,moved);
        if(level.getBlockEntity(pos) instanceof ProviderBlockEntity provider)provider.checkGridConnectionColor();
    }
    @Override public void onRemove(BlockState oldState,Level level,BlockPos pos,BlockState next,boolean moved){
        if(!oldState.is(next.getBlock())&&level.getBlockEntity(pos) instanceof ProviderBlockEntity provider)provider.onBreakBlock();
        super.onRemove(oldState,level,pos,next,moved);
    }
    // Color and forced-color settings are world data only in the release.
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(new ItemStack(this));}
}
