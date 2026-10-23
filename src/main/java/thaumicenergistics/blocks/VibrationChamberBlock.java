package thaumicenergistics.blocks;

import appeng.api.orientation.*;
import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.items.VibrationChamberItem;
import thaumicenergistics.menu.VibrationChamberMenu;

public final class VibrationChamberBlock extends AEBaseEntityBlock<VibrationChamberBlockEntity>{
    public static final IntegerProperty BURNING=IntegerProperty.create("burning",0,2);
    public VibrationChamberBlock(){super(Properties.of().strength(1).sound(SoundType.METAL).lightLevel(state->state.getValue(BURNING)==0?0:12));}
    @Override public IOrientationStrategy getOrientationStrategy(){return OrientationStrategies.full();}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(BURNING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var player=context.getPlayer();var state=super.getStateForPlacement(context);if(player==null)return state;
        Direction front=player.getXRot()>50?Direction.UP:player.getXRot()<-50?Direction.DOWN:Direction.from2DDataValue(net.minecraft.util.Mth.floor(player.getYRot()*4F/360F+.5F)&3).getOpposite();
        return getOrientationStrategy().setOrientation(state,front,front.getAxis()==Direction.Axis.Y?Direction.NORTH:Direction.UP);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof VibrationChamberBlockEntity chamber)chamber.restoreItem(VibrationChamberItem.contents(stack));
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof VibrationChamberBlockEntity chamber)||!chamber.valid(player))return InteractionResult.FAIL;
        if(player instanceof ServerPlayer server)server.openMenu(new SimpleMenuProvider((id,inventory,owner)->new VibrationChamberMenu(id,inventory,chamber),chamber.getName()));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!(level.getBlockEntity(pos) instanceof VibrationChamberBlockEntity chamber)||!chamber.valid(player))return ItemInteractionResult.FAIL;
        if(InteractionUtil.canWrenchDisassemble(held)&&player.isShiftKeyDown()){
            if(!level.isClientSide){var result=new ItemStack(this);VibrationChamberItem.contents(result,chamber.stored());chamber.burn().resetForDismantle();level.removeBlock(pos,false);popResource(level,pos,result);}
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(InteractionUtil.canWrenchRotate(held)){
            if(!level.isClientSide)chamber.getOrientation().rotateClockwiseAround(hit.getDirection()).setOn(level,pos);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(held.getItem() instanceof appeng.api.implementations.items.IMemoryCard)return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    // Native AE2 otherwise exports settings to every ordinary block drop. The
    // original preserves stored essentia only for explicit wrench dismantling.
    @Override public List<ItemStack> getDrops(BlockState state,LootParams.Builder params){return List.of(new ItemStack(this));}
}
