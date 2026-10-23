package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Release TileAsItemRenderer: use the same frame/glass mesh in-world and as an item. */
public final class AssemblerItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static int modelGeneration;
    private int bakedGeneration = -1;
    private AssemblerModel model;

    public AssemblerItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    static void invalidateModels() { modelGeneration++; }

    @Override public void onResourceManagerReload(ResourceManager resources) {
        super.onResourceManagerReload(resources);
        model = null;
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poses,
                                       MultiBufferSource buffers, int light, int overlay) {
        if (model == null || bakedGeneration != modelGeneration) {
            model = new AssemblerModel(Minecraft.getInstance().getEntityModels().bakeLayer(AssemblerModel.LAYER));
            bakedGeneration = modelGeneration;
        }
        model.render(poses, buffers, light, overlay);
    }
}
