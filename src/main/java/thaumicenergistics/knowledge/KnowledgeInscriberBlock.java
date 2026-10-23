package thaumicenergistics.knowledge;

import appeng.api.util.DimensionalBlockPos;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Knowledge Inscriber block.
 * Rotatable along all 6 directions (BlockStateProperties.FACING), hardness 1.0F, metal sound.
 * Wrenchable (disassemble and rotate). Real KCore dropped on remove, ghost slots never dropped.
 */
public class KnowledgeInscriberBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public KnowledgeInscriberBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction direction = context.getNearestLookingDirection().getOpposite();
        return this.defaultBlockState().setValue(FACING, direction);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KnowledgeInscriberBlockEntity(pos, state);
    }

    private boolean mayUse(Level level, BlockPos pos, Player player) {
        return !(player instanceof FakePlayer) && Platform.hasPermissions(new DimensionalBlockPos(level, pos), player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof KnowledgeInscriberBlockEntity be) {
            serverPlayer.openMenu(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) {
            return ItemInteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && InteractionUtil.canWrenchDisassemble(held)) {
            if (!level.isClientSide) {
                level.removeBlock(pos, false);
                popResource(level, pos, new ItemStack(this));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (InteractionUtil.canWrenchRotate(held)) {
            if (!level.isClientSide) {
                Direction current = state.getValue(FACING);
                Direction next = Direction.values()[(current.ordinal() + 1) % 6];
                level.setBlock(pos, state.setValue(FACING, next), Block.UPDATE_ALL);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof KnowledgeInscriberBlockEntity be) {
                ItemStack core = be.removeItemNoUpdate(KnowledgeInscriberBlockEntity.KCORE_SLOT);
                if (!core.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, core);
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
