package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Custom Item Renderer for Gearbox BlockItems, rendering the 3D model in inventory and hands.
 */
public class GearboxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean isThaumium;
    private GearboxBlockEntity dummyEntity;

    public GearboxItemRenderer(boolean isThaumium) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.isThaumium = isThaumium;
    }

    private GearboxBlockEntity getDummy() {
        if (dummyEntity == null) {
            BlockState state = isThaumium ? GolemsRegistrar.THAUMIUM_GEARBOX_BLOCK.get().defaultBlockState() : GolemsRegistrar.IRON_GEARBOX_BLOCK.get().defaultBlockState();
            dummyEntity = new GearboxBlockEntity(BlockPos.ZERO, state, isThaumium);
            dummyEntity.setThaumiumGearbox(isThaumium);
        }
        return dummyEntity;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        GearboxBlockEntity entity = getDummy();
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(entity, poseStack, buffer, packedLight, packedOverlay);
    }
}
