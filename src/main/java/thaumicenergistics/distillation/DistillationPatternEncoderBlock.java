package thaumicenergistics.distillation;

import appeng.api.util.DimensionalBlockPos;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
 * Distillation Pattern Encoder Block.
 * Supports 6-directional rotation via wrench, AE2 wrench dismantling, and right-click menu opening.
 * Not ticking and not connected to ME grid.
 */
public final class DistillationPatternEncoderBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public DistillationPatternEncoderBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.METAL));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var direction = context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, direction);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillationPatternEncoderBlockEntity(pos, state);
    }

    private boolean mayUse(Level level, BlockPos pos, Player player) {
        return !(player instanceof FakePlayer) && Platform.hasPermissions(new DimensionalBlockPos(level, pos), player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) {
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof DistillationPatternEncoderBlockEntity encoder) {
            server.openMenu(encoder);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!mayUse(level, pos, player)) {
            return ItemInteractionResult.FAIL;
        }
        if (player.isShiftKeyDown() && InteractionUtil.canWrenchDisassemble(stack)) {
            if (!level.isClientSide) {
                // onRemove drops real patterns; the dismantled encoder itself drops once.
                level.removeBlock(pos, false);
                popResource(level, pos, new ItemStack(this));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (InteractionUtil.canWrenchRotate(stack)) {
            if (!level.isClientSide) {
                Direction current = state.getValue(FACING);
                Direction next = Direction.values()[(current.ordinal() + 1) % Direction.values().length];
                level.setBlock(pos, state.setValue(FACING, next), 3);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof DistillationPatternEncoderBlockEntity encoder) {
            encoder.dropInventory(level, pos);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public String getDescriptionId() {
        return "thaumicenergistics.block.distillation.encoder.name";
    }
}
