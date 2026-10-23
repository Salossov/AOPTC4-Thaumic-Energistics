package thaumicenergistics.assembler;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.util.InteractionUtil;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

public final class ArcaneAssemblerBlock extends AEBaseEntityBlock<ArcaneAssemblerBlockEntity> {
    public ArcaneAssemblerBlock() { super(Properties.of().strength(1).sound(SoundType.METAL).noOcclusion()); }
    @Override public IOrientationStrategy getOrientationStrategy() { return OrientationStrategies.none(); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }

    private InteractionResult open(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ArcaneAssemblerBlockEntity assembler) || !assembler.valid(player))
            return InteractionResult.FAIL;
        if (player instanceof ServerPlayer server) {
            int tool = ArcaneAssemblerMenu.findNetworkTool(player.getInventory());
            server.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new ArcaneAssemblerMenu(id, inventory, assembler, tool), assembler.getName()),
                    buffer -> buffer.writeVarInt(tool));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                          Player player, BlockHitResult hit) {
        return open(level, pos, player);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                                         Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ArcaneAssemblerBlockEntity assembler) || !assembler.valid(player))
            return ItemInteractionResult.FAIL;
        if (InteractionUtil.canWrenchDisassemble(held) && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack drop = assembler.dismantleItem();
                level.removeBlock(pos, false);
                popResource(level, pos, drop);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (InteractionUtil.canWrenchRotate(held)) return ItemInteractionResult.sidedSuccess(level.isClientSide);
        if (held.getItem() instanceof IMemoryCard) {
            assembler.memoryCard(player, held);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // Source opens the machine with ordinary held items as well as an empty hand.
        open(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ArcaneAssemblerBlockEntity assembler) {
            if (placer instanceof Player player) assembler.getMainNode().setOwningPlayer(player);
            assembler.restoreItem(stack);
        }
    }

    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!old.is(next.getBlock()) && level.getBlockEntity(pos) instanceof ArcaneAssemblerBlockEntity assembler)
            assembler.breakAndDrop();
        super.onRemove(old, level, pos, next, moved);
    }

    // Ordinary breaking does not retain Vis or the source's network link. Wrench retains only Vis.
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(this));
    }
}
