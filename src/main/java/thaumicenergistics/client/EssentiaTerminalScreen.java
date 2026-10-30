package thaumicenergistics.client;

import appeng.api.config.ViewItems;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.Scrollbar;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.research.ResearchApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaTerminalMenu;
import thaumicenergistics.network.TerminalActionPayload;
import thaumicenergistics.storage.*;

/** Original 195x204 terminal, 9x4 aspect widgets and native AE2 mode controls. */
public final class EssentiaTerminalScreen extends AbstractContainerScreen<EssentiaTerminalMenu>{
    private static final ResourceLocation TEXTURE=ThaumicEnergistics.id("textures/gui/essentia.terminal.png");
    private final Scrollbar scrollbar=new Scrollbar().setHeight(70);
    private final EssentiaRendering renderer=new EssentiaRendering();
    private EditBox search;
    private List<TerminalEntry> visible=List.of();
    private int previousOutput;
    private long lastSound=System.currentTimeMillis();
    public EssentiaTerminalScreen(EssentiaTerminalMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=195;imageHeight=204;}
    @Override protected void init(){
        super.init();scrollbar.setPosition(new Point(leftPos+175,topPos+18));
        String old=search==null?"":search.getValue();search=new EditBox(font,leftPos+98,topPos+6,69,10,Component.literal("Search"));
        search.setBordered(false);search.setMaxLength(14);search.setValue(old);search.setResponder(value->refresh());addRenderableWidget(search);refresh();
    }
    private void refresh(){visible=TerminalEntries.visible(menu.entries(),menu.sort(),menu.view(),search==null?"":search.getValue());scrollbar.setRange(0,Math.max(0,(visible.size()-36+8)/9),1);}
    @Override protected void containerTick(){
        super.containerTick();refresh();scrollbar.tick();
        var output=menu.getSlot(1).getItem();int count=output.getCount();
        if(count!=previousOutput){
            previousOutput=count;
            if(count>0&&System.currentTimeMillis()-lastSound>900){
                boolean label=thaumcraft.api.aspects.EssentiaItemApi.configureLabel(output,thaumcraft.api.aspects.Aspects.AIR).isPresent();
                var sound=label?net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse("thaumcraft:page")).orElse(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN):net.minecraft.sounds.SoundEvents.PLAYER_SWIM;
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound,1));lastSound=System.currentTimeMillis();
            }
        }
    }
    private Component aspectName(TerminalEntry entry){return ResearchApi.clientKnowledge().knows(entry.key().aspect())?entry.key().getDisplayName():Component.translatable("tc.aspect.unknown");}
    private void action(int id,TerminalEntry entry){PacketDistributor.sendToServer(new TerminalActionPayload(menu.containerId,id,entry==null?"":entry.key().tag()));}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        GuiTextures.blit(graphics,TEXTURE,leftPos,topPos,0,0,195,204);
        for(int index=0;index<36;index++){
            int row=index+scrollbar.getCurrentScroll()*9;if(row>=visible.size())break;var entry=visible.get(row);
            int x=leftPos+7+index%9*18,y=topPos+17+index/9*18;
            if(entry.key().aspect()==menu.selected()){
                int phase=Math.abs(Math.abs((int)(System.currentTimeMillis()/45L)%30)-15);int alpha=112-80*phase/15;
                graphics.fill(x,y,x+18,y+18,(alpha<<24)|entry.key().aspect().getColor());
                graphics.fill(x,y,x+18,y+1,0xff00ffff);graphics.fill(x,y+17,x+18,y+18,0xff00ffff);
                graphics.fill(x,y,x+1,y+18,0xff00ffff);graphics.fill(x+17,y,x+18,y+18,0xff00ffff);
                for(int dx:new int[]{0,16})for(int dy:new int[]{0,16})graphics.fill(x+dx,y+dy,x+dx+2,y+dy+2,0xff00ffff);
            }
            if(ResearchApi.clientKnowledge().knows(entry.key().aspect()))renderer.drawInGui(minecraft,graphics,x+1,y+1,entry.key());
            else renderer.drawUnknownInGui(graphics,x+1,y+1,entry.key());
            if(entry.amount()>0&&menu.view()!=ViewItems.CRAFTABLE||entry.craftable()){
                boolean large=appeng.core.AEConfig.instance().isUseLargeFonts();float scale=large?.85f:.5f,offset=large?0:-1;
                String text=entry.amount()>0&&menu.view()!=ViewItems.CRAFTABLE?(large?MonitorNumbers.wide(entry.amount()):MonitorNumbers.slim(entry.amount())):Component.translatable(large?"thaumicenergistics.gui.craft.large":"thaumicenergistics.gui.craft.small").getString();
                graphics.pose().pushPose();graphics.pose().translate(x+offset+18,y+offset+17,150);graphics.pose().scale(scale,scale,1);
                graphics.drawString(font,text,-font.width(text),-font.lineHeight,0xffffffff,true);graphics.flush();graphics.pose().popPose();
            }
        }
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos-18,topPos+9).blit(graphics);
        (menu.sort()==0?Icon.SORT_BY_NAME:Icon.SORT_BY_AMOUNT).getBlitter().dest(leftPos-18,topPos+9).blit(graphics);
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos-18,topPos+29).blit(graphics);
        (menu.view()==ViewItems.ALL?Icon.VIEW_MODE_ALL:menu.view()==ViewItems.STORED?Icon.VIEW_MODE_STORED:Icon.VIEW_MODE_CRAFTING).getBlitter().dest(leftPos-18,topPos+29).blit(graphics);
        scrollbar.drawForegroundLayer(graphics,new Rect2i(leftPos,topPos,imageWidth,imageHeight),new Point(mouseX,mouseY));
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){
        graphics.drawString(font,title,7,6,0,false);
        menu.entries().stream().filter(entry->entry.key().aspect()==menu.selected()).findFirst().ifPresent(entry->{
            graphics.drawString(font,Component.translatable("thaumicenergistics.gui.selected.aspect").append(": ").append(aspectName(entry)),45,91,0,false);
            graphics.drawString(font,Component.translatable("thaumicenergistics.gui.selected.amount").append(": "+entry.amount()),45,101,0,false);
        });
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);
        var entry=underMouse(mouseX,mouseY);
        if(entry!=null){
            boolean known=ResearchApi.clientKnowledge().knows(entry.key().aspect());String namespace=ResourceLocation.parse(entry.key().aspect().texture()).getNamespace();
            String mod=known?AspectModNames.owner(namespace):"Unknown";
            String help="tc.aspect.help."+entry.key().tag();
            var description=known&&net.minecraft.client.resources.language.I18n.exists(help)?Component.translatable(help):aspectName(entry);
            graphics.renderComponentTooltip(font,List.of(aspectName(entry),description.copy().withStyle(net.minecraft.ChatFormatting.GRAY),Component.literal(mod).withStyle(net.minecraft.ChatFormatting.DARK_PURPLE,net.minecraft.ChatFormatting.ITALIC)),mouseX,mouseY);
        }
        if(isHovering(-18,9,16,16,mouseX,mouseY))graphics.renderComponentTooltip(font,List.of(Component.translatable("gui.tooltips.ae2.SortBy"),Component.translatable(menu.sort()==0?"gui.tooltips.ae2.ItemName":"gui.tooltips.ae2.NumberOfItems").withStyle(net.minecraft.ChatFormatting.GRAY)),mouseX,mouseY);
        if(isHovering(-18,29,16,16,mouseX,mouseY))graphics.renderComponentTooltip(font,List.of(Component.translatable("gui.tooltips.ae2.View"),Component.translatable(menu.view()==ViewItems.ALL?"gui.tooltips.ae2.StoredCraftable":menu.view()==ViewItems.STORED?"gui.tooltips.ae2.StoredItems":"gui.tooltips.ae2.Craftable").withStyle(net.minecraft.ChatFormatting.GRAY)),mouseX,mouseY);
    }
    private TerminalEntry underMouse(double mouseX,double mouseY){
        if(!isHovering(7,17,162,72,mouseX,mouseY))return null;
        int index=(int)(mouseX-leftPos-7)/18+(int)(mouseY-topPos-17)/18*9+scrollbar.getCurrentScroll()*9;
        return index<visible.size()?visible.get(index):null;
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        var entry=underMouse(x,y);
        if(entry!=null){
            if(!menu.getCarried().isEmpty())action(1,entry);
            else{
                if(button!=1&&entry.craftable()&&(menu.view()==ViewItems.CRAFTABLE||button==2||button==0&&entry.amount()==0))action(6,entry);
                PacketDistributor.sendToServer(new TerminalActionPayload(menu.containerId,0,menu.selected()==entry.key().aspect()?"":entry.key().tag()));
                minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1));
            }return true;
        }
        if(isHovering(98,6,69,10,x,y)&&button==1){search.setValue("");return true;}
        if(isHovering(-18,9,16,16,x,y)&&button<2){action(button==0?2:3,null);return true;}
        if(isHovering(-18,29,16,16,x,y)&&button<2){action(button==0?4:5,null);return true;}
        if(scrollbar.onMouseDown(new Point((int)x,(int)y),button))return true;
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){return scrollbar.onMouseUp(new Point((int)x,(int)y),button)||super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return scrollbar.onMouseDrag(new Point((int)x,(int)y),button)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x<=leftPos+195&&!hasShiftDown())return scrollbar.onMouseWheel(new Point((int)x,(int)y),vertical);
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(search.isFocused()&&key!=256){if(search.keyPressed(key,scan,modifiers))return true;if(minecraft.options.keyInventory.matches(key,scan))return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
