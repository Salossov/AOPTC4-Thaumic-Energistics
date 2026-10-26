package thaumicenergistics.golems;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Wireless Golem Backpack item giving Thaumcraft golems direct wireless access to the ME network.
 * Uses modern AE2 GridLinkables and AEComponents.WIRELESS_LINK_TARGET (GlobalPos target).
 */
public class WirelessGolemBackpackItem extends Item {
    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack != null && stack.getItem() instanceof WirelessGolemBackpackItem;
        }

        @Override
        public void link(ItemStack stack, GlobalPos pos) {
            stack.set(AEComponents.WIRELESS_LINK_TARGET, pos);
        }

        @Override
        public void unlink(ItemStack stack) {
            stack.remove(AEComponents.WIRELESS_LINK_TARGET);
        }
    };

    public WirelessGolemBackpackItem() {
        super(new Item.Properties().stacksTo(1));
    }

    public static boolean isLinked(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.has(AEComponents.WIRELESS_LINK_TARGET);
    }

    public static GlobalPos getLinkedPosition(ItemStack stack) {
        return stack != null ? stack.get(AEComponents.WIRELESS_LINK_TARGET) : null;
    }

    public static GolemBackpackSkin getSkin(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && data.contains("Skin")) {
            return GolemBackpackSkin.fromMaterialName(data.copyTag().getString("Skin"));
        }
        return null;
    }

    public static void setSkin(ItemStack stack, GolemBackpackSkin skin) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (skin != null) {
                tag.putString("Skin", skin.getKey());
            } else {
                tag.remove("Skin");
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        if (isLinked(stack)) {
            lines.add(Tooltips.of(GuiText.Linked, Tooltips.GREEN));
        } else {
            lines.add(Tooltips.of(GuiText.Unlinked, Tooltips.RED));
        }
    }

    /**
     * Resolves the linked AE2 grid using modern ServerLevel and GlobalPos target.
     */
    public static IGrid getLinkedGrid(ItemStack stack, Level level, Consumer<Component> errorConsumer) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        GlobalPos target = getLinkedPosition(stack);
        if (target == null) {
            if (errorConsumer != null) {
                errorConsumer.accept(PlayerMessages.DeviceNotLinked.text());
            }
            return null;
        }

        ServerLevel targetLevel = serverLevel.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())) {
            return null;
        }

        BlockEntity be = targetLevel.getBlockEntity(target.pos());
        if (be instanceof appeng.api.implementations.blockentities.IWirelessAccessPoint point) {
            return point.getGrid();
        }
        if (be instanceof IInWorldGridNodeHost host) {
            IGridNode node = host.getGridNode(null);
            if (node != null) {
                return node.getGrid();
            }
        }

        return null;
    }
}
