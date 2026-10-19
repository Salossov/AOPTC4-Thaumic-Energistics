package thaumicenergistics.storage;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumicenergistics.ThaumicEnergistics;

public final class EssentiaCellItem extends Item {
    private final CellTier tier;
    public EssentiaCellItem(CellTier tier) {
        super(new Item.Properties().stacksTo(1).rarity(switch (tier) {
            case K1 -> Rarity.COMMON;
            case K4, K16 -> Rarity.UNCOMMON;
            case K64 -> Rarity.RARE;
            case CREATIVE -> Rarity.EPIC;
        }));
        this.tier = tier;
    }
    public CellTier tier() { return tier; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var ledger = new EssentiaCell(stack, null).ledger();
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.cell_bytes", tier.creative() ? 0 : ledger.usedBytes(), tier.bytes).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.cell_types", ledger.usedTypes(), tier.types).withStyle(ChatFormatting.GRAY));
        if (!ledger.contents().partitions().isEmpty()) tooltip.add(Component.translatable("tooltip.thaumicenergistics.partitioned"));
        ledger.contents().amounts().forEach((tag, amount) -> tooltip.add(Component.translatable("tooltip.thaumicenergistics.cell_contents", EssentiaKey.ofTag(tag).getDisplayName(), amount)));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || tier.creative()) return InteractionResultHolder.pass(stack);
        var contents = stack.getOrDefault(ThaumicEnergistics.CELL_CONTENTS, CellContents.EMPTY);
        if (!contents.amounts().isEmpty()) return InteractionResultHolder.pass(stack);
        // ItemEssentiaCell.onItemRightClick: disassemble only if the casing fits in inventory.
        if (player.getInventory().getSlotWithRemainingSpace(new ItemStack(ThaumicEnergistics.STORAGE_CASING.get())) < 0
                && player.getInventory().getFreeSlot() < 0) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            var casing = new ItemStack(ThaumicEnergistics.STORAGE_CASING.get());
            if (!player.getInventory().add(casing)) return InteractionResultHolder.pass(stack);
            player.setItemInHand(hand, new ItemStack(ThaumicEnergistics.COMPONENT_ITEMS.get(tier).get()));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
