package thaumicenergistics.golems;

import appeng.api.util.DimensionalBlockPos;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Base block for Iron and Thaumium gearboxes.
 * Unbreakable crank distributing power evenly across connected crankable devices.
 */
public class GearboxBlock extends Block implements EntityBlock {
    public static final VoxelShape SHAPE = Block.box(3.68, 3.68, 3.68, 12.32, 12.32, 12.32);

    private final boolean isThaumium;

    public GearboxBlock(boolean isThaumium) {
        super(BlockBehaviour.Properties.of()
                .strength(0.6f)
                .sound(SoundType.STONE)
                .noOcclusion());
        this.isThaumium = isThaumium;
    }

    public boolean isThaumium() {
        return isThaumium;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GearboxBlockEntity(pos, state, isThaumium);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (lvl, pos, st, be) -> {
            if (be instanceof GearboxBlockEntity gearbox) {
                gearbox.tick(lvl, pos, st);
            }
        };
    }

    private boolean canPlayerInteract(Player player) {
        if (player instanceof FakePlayer fakePlayer) {
            if (!this.isThaumium) {
                return false; // Iron gearbox cannot be turned by golems/fake players
            }
            // AbstractBlockGearBoxBase permits only Thaumcraft's named golem profile.
            return "FakeThaumcraftGolem".equalsIgnoreCase(fakePlayer.getGameProfile().getName());
        }
        return true;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, pos), player)) {
            return ItemInteractionResult.FAIL;
        }

        if (player.isShiftKeyDown() && InteractionUtil.canWrenchDisassemble(stack)) {
            if (!level.isClientSide) {
                level.removeBlock(pos, false);
                popResource(level, pos, new ItemStack(this));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (InteractionUtil.canWrenchRotate(stack)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!Platform.hasPermissions(new DimensionalBlockPos(level, pos), player)) {
            return InteractionResult.FAIL;
        }

        if (!canPlayerInteract(player)) {
            return InteractionResult.FAIL;
        }

        if (level.getBlockEntity(pos) instanceof GearboxBlockEntity gearbox) {
            boolean cranked = gearbox.crankGearbox();
            return cranked ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.getBlockEntity(pos) instanceof GearboxBlockEntity gearbox) {
            gearbox.updateCrankables();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }
}
