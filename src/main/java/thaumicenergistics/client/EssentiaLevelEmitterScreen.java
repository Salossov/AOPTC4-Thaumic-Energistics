package thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaLevelEmitterMenu;
import thaumicenergistics.network.EmitterThresholdPayload;

public final class EssentiaLevelEmitterScreen extends AbstractContainerScreen<EssentiaLevelEmitterMenu> {
    private static final ResourceLocation TEXTURE=ThaumicEnergistics.id("textures/gui/essentia.level.emitter.png");
    private static final ResourceLocation FILTER=ThaumicEnergistics.id("textures/gui/essentia.io.bus.png");
    private final EssentiaRendering aspects=new EssentiaRendering();
    private final Button[] adjustments=new Button[6];
    private EditBox amount;
    private long serverValue;
    private boolean receiving;
    public EssentiaLevelEmitterScreen(EssentiaLevelEmitterMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=176;imageHeight=184;titleLabelX=8;titleLabelY=5;
    }
    private void button(int action) { if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action); }
    @Override protected void init() {
        super.init();
        int[] deltas={1,10,100,-1,-10,-100};
        for(int index=0;index<6;index++) {
            final int action=index;
            adjustments[index]=addRenderableWidget(Button.builder(Component.literal((deltas[index]>0?"+":"")+deltas[index]),pressed->button(action+(hasShiftDown()?7:0)))
                    .bounds(leftPos+15+index%3*50,topPos+17+index/3*42,42,20).build());
        }
        amount=new EditBox(font,leftPos+25,topPos+44,80,10,Component.translatable("gui.ae2.LevelEmitter"));
        amount.setBordered(false);amount.setTextColor(0xffffffff);amount.setMaxLength(10);amount.setFilter(value->value.matches("[0-9]*"));
        updateAmount();
        amount.setResponder(value->{if(!receiving)PacketDistributor.sendToServer(new EmitterThresholdPayload(menu.containerId,value.isEmpty()?0:Long.parseLong(value)));});
        addRenderableWidget(amount);setInitialFocus(amount);
    }
    private void updateAmount() {receiving=true;serverValue=menu.threshold();amount.setValue(Long.toString(serverValue));receiving=false;}
    @Override protected void containerTick() {
        super.containerTick();if(menu.threshold()!=serverValue)updateAmount();
        int[] deltas={1,10,100,-1,-10,-100};
        for(int index=0;index<6;index++){int value=deltas[index]*(hasShiftDown()?100:1);adjustments[index].setMessage(Component.literal((value>0?"+":"")+value));}
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY) {
        GuiTextures.blit(graphics,TEXTURE,leftPos,topPos,0,0,176,184);
        GuiTextures.blit(graphics,FILTER,leftPos+123,topPos+39,79,39,18,18);
        // Original GuiButtonRedstoneModes: AE2 state icon at (-18,2), emitter tooltip wording.
        appeng.client.gui.Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos-18,topPos+2).blit(graphics);
        (menu.lowSignal()?appeng.client.gui.Icon.REDSTONE_LOW:appeng.client.gui.Icon.REDSTONE_HIGH).getBlitter().dest(leftPos-18,topPos+2).blit(graphics);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY) {graphics.drawString(font,title,8,5,0,false);}
    @Override protected void renderSlot(GuiGraphics graphics,Slot slot) {
        if(slot.index==0){var key=menu.filter();if(key!=null)aspects.drawInGui(minecraft,graphics,slot.x,slot.y,key);}
        else super.renderSlot(graphics,slot);
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial) {
        super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);
        if(isHovering(123,39,18,18,mouseX,mouseY)&&menu.filter()!=null)graphics.renderTooltip(font,menu.filter().getDisplayName(),mouseX,mouseY);
        if(isHovering(-18,2,16,16,mouseX,mouseY))graphics.renderComponentTooltip(font,java.util.List.of(Component.translatable("gui.tooltips.ae2.RedstoneMode"),
                Component.translatable(menu.lowSignal()?"gui.tooltips.ae2.EmitLevelsBelow":"gui.tooltips.ae2.EmitLevelAbove").withStyle(net.minecraft.ChatFormatting.GRAY)),mouseX,mouseY);
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int mouseButton) {
        if(mouseButton==0&&isHovering(-18,2,16,16,mouseX,mouseY)){button(6);return true;}
        if(isHovering(123,39,18,18,mouseX,mouseY)&&minecraft.gameMode!=null&&(mouseButton==0||mouseButton==1)) {
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId,0,mouseButton,net.minecraft.world.inventory.ClickType.PICKUP,minecraft.player);return true;
        }
        return super.mouseClicked(mouseX,mouseY,mouseButton);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(amount.isFocused()&&key!=256){if(amount.keyPressed(key,scan,modifiers))return true;if(minecraft.options.keyInventory.matches(key,scan))return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
