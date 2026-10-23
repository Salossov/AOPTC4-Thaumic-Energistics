package thaumicenergistics.blocks;

import appeng.api.util.DimensionalBlockPos;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

public final class CellWorkbenchBlock extends Block implements EntityBlock {
    public CellWorkbenchBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CellWorkbenchBlockEntity(pos, state);
    }

    private boolean mayUse(Level level, BlockPos pos, Player player) {
        return !(player instanceof FakePlayer) && Platform.hasPermissions(new DimensionalBlockPos(level, pos), player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) return InteractionResult.FAIL;
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof CellWorkbenchBlockEntity workbench) {
            server.openMenu(workbench);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) return ItemInteractionResult.FAIL;
        if (player.isShiftKeyDown() && InteractionUtil.canWrenchDisassemble(stack)) {
            if (!level.isClientSide) {
                // onRemove drops the cell; the dismantled workbench itself drops once.
                level.removeBlock(pos, false);
                popResource(level, pos, new ItemStack(this));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (InteractionUtil.canWrenchRotate(stack)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof CellWorkbenchBlockEntity workbench) {
            var cell = workbench.inventory().removeItemNoUpdate(0);
            if (!cell.isEmpty()) {
                level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.2, cell));
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
