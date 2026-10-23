package thaumicenergistics.storage;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;

public final class EssentiaCellItem extends Item {
    private final CellTier tier;

    public EssentiaCellItem(CellTier tier) {
        super(new Item.Properties().stacksTo(1).rarity(Objects.requireNonNull(tier, "tier cannot be null").rarity));
        this.tier = tier;
    }

    public CellTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var ledger = new EssentiaCell(stack, null).ledger();

        // 1. Bytes line: "%s of %s bytes used." (Gray)
        tooltip.add(Component.translatable(
                "tooltip.thaumicenergistics.cell_bytes",
                tier.creative() ? 0L : ledger.usedBytes(),
                tier.bytes
        ).withStyle(ChatFormatting.GRAY));

        // 2. Types line: "%s of %s essentia types stored." (Gray)
        // Uses occupied types count (not partition whitelist count) for normal cells.
        tooltip.add(Component.translatable(
                "tooltip.thaumicenergistics.cell_types",
                ledger.usedTypes(),
                tier.types
        ).withStyle(ChatFormatting.GRAY));

        // 3. Partitioned line: shown if cell is partitioned
        if (ledger.isPartitioned()) {
            tooltip.add(Component.translatable("tooltip.thaumicenergistics.partitioned"));
        }

        // 4. Stored contents details:
        if (ledger.usedTypes() > 0) {
            if (hasShiftDown()) {
                // Shift held: list aspects sorted alphabetically by display name
                List<Map.Entry<String, Long>> entries;
                if (tier.creative()) {
                    entries = ledger.contents().partitions().stream()
                            .map(tag -> Map.entry(tag, 1L))
                            .sorted(Comparator.comparing(e -> getAspectDisplayName(e.getKey()).getString()))
                            .toList();
                } else {
                    entries = ledger.contents().amounts().entrySet().stream()
                            .sorted(Comparator.comparing(e -> getAspectDisplayName(e.getKey()).getString()))
                            .toList();
                }

                for (var entry : entries) {
                    var key = EssentiaKey.ofTag(entry.getKey());
                    var aspect = key.aspect();
                    ChatFormatting color = getAspectChatColor(aspect);
                    Component shownName = aspect != null && !thaumcraft.api.research.ResearchApi.clientKnowledge().knows(aspect)
                            ? Component.translatable("tc.aspect.unknown") : getAspectDisplayName(entry.getKey());
                    Component nameComp = shownName.copy().withStyle(color);
                    Component countComp = Component.literal(" x " + entry.getValue()).withStyle(ChatFormatting.WHITE);
                    tooltip.add(Component.empty().append(nameComp).append(countComp));
                }
            } else {
                // Shift not held: prompt player
                tooltip.add(Component.translatable("thaumicenergistics.tooltip.itemstack.details")
                        .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
            }
        }
    }

    /**
     * Resolves the display name component for an aspect tag.
     */
    public static Component getAspectDisplayName(String tag) {
        var key = EssentiaKey.ofTag(tag);
        return key.getDisplayName();
    }

    /**
     * Source parity for aspect chat colors:
     * Primal aspects receive distinct chat formatting colors (AIR=yellow, WATER=aqua,
     * FIRE=red, ORDER=gray, ENTROPY=dark_gray, EARTH=green), others default to white.
     */
    public static ChatFormatting getAspectChatColor(Aspect aspect) {
        if (aspect == null) {
            return ChatFormatting.WHITE;
        }
        String tag = aspect.getTag();
        if ("aer".equals(tag)) return ChatFormatting.YELLOW;
        if ("aqua".equals(tag)) return ChatFormatting.AQUA;
        if ("ignis".equals(tag)) return ChatFormatting.RED;
        if ("ordo".equals(tag)) return ChatFormatting.GRAY;
        if ("perditio".equals(tag)) return ChatFormatting.DARK_GRAY;
        if ("terra".equals(tag)) return ChatFormatting.GREEN;
        return ChatFormatting.WHITE;
    }

    private static boolean hasShiftDown() {
        try {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                return net.minecraft.client.gui.screens.Screen.hasShiftDown();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Disassembles cell into component + casing when sneaking and empty.
     * Preserves contents if cell is not empty.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || tier.creative()) {
            return InteractionResultHolder.pass(stack);
        }
        var ledger = new EssentiaCell(stack, null).ledger();
        if (ledger.usedBytes() != 0 || ledger.total() != 0) {
            return InteractionResultHolder.pass(stack);
        }
        var casingHolder = ThaumicEnergistics.STORAGE_CASING;
        var componentHolder = ThaumicEnergistics.COMPONENT_ITEMS.get(tier);
        if (casingHolder == null || componentHolder == null) {
            return InteractionResultHolder.pass(stack);
        }
        var casingItem = casingHolder.get();
        var componentItem = componentHolder.get();
        if (casingItem == null || componentItem == null) {
            return InteractionResultHolder.pass(stack);
        }
        var casing = new ItemStack(casingItem);
        var component = new ItemStack(componentItem);

        // Disassemble only if casing can fit in inventory
        if (player.getInventory().getSlotWithRemainingSpace(casing) < 0
                && player.getInventory().getFreeSlot() < 0) {
            return InteractionResultHolder.pass(stack);
        }

        if (!level.isClientSide) {
            if (!player.getInventory().add(casing)) {
                return InteractionResultHolder.pass(stack);
            }
            if (stack.getCount() > 1) {
                stack.shrink(1);
                if (!player.getInventory().add(component)) {
                    player.drop(component, false);
                }
                return InteractionResultHolder.sidedSuccess(stack, false);
            } else {
                return InteractionResultHolder.sidedSuccess(component, false);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            var res = use(context.getLevel(), context.getPlayer(), context.getHand());
            if (res.getResult().consumesAction()) {
                // Unlike air use, useOn has no engine-managed InteractionResultHolder writeback.
                context.getPlayer().setItemInHand(context.getHand(), res.getObject());
                return res.getResult();
            }
        }
        return super.useOn(context);
    }
}
