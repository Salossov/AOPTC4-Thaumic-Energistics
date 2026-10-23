package thaumicenergistics.client;

import appeng.api.config.*;
import appeng.client.Point;
import appeng.client.gui.Icon;
import appeng.client.gui.widgets.Scrollbar;
import appeng.core.AEConfig;
import appeng.items.storage.ViewCellItem;
import appeng.api.stacks.AEItemKey;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.crafting.*;
import thaumicenergistics.menu.ArcaneTerminalMenu;
import thaumicenergistics.network.ArcaneTerminalActionPayload;
import thaumicenergistics.storage.*;

/** Release 230x243 layout, persistent view cells and small/tall terminal modes. */
public final class ArcaneTerminalScreen extends AbstractContainerScreen<ArcaneTerminalMenu> {
    private static final ResourceLocation TEXTURE=ThaumicEnergistics.id("textures/gui/arcane.crafting.png");
    private static final ResourceLocation STATES=ThaumicEnergistics.id("textures/gui/states.png");
    private final Scrollbar scrollbar=new Scrollbar();
    private final EssentiaRendering aspectRenderer=new EssentiaRendering();
    private List<Slot> originalSlots;
    private List<ItemTerminalEntry> visible=List.of();
    private EditBox search;
    private int rows=3,lowerOffset;
    private boolean spaceHeld;
    private final Map<AEItemKey,List<String>> tooltipSearchCache=new HashMap<>();
    private Object cachedKnowledge;
    private String cachedLanguage;
    private String lastExternalSearch;
    private String compiledQuery;
    private ArcaneTerminalViewRules.Search query;
    public ArcaneTerminalScreen(ArcaneTerminalMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=230;imageHeight=243;}
    @Override protected void init(){
        boolean tall=AEConfig.instance().getTerminalStyle()==TerminalStyle.TALL;
        int extra=tall?Math.max(0,(height-243)/18-3):0;rows=3+extra;lowerOffset=extra*18;imageHeight=243+lowerOffset;
        super.init();
        tooltipSearchCache.clear();cachedKnowledge=null;cachedLanguage=null;compiledQuery=null;
        if(originalSlots==null)originalSlots=List.copyOf(menu.slots);
        for(int index=0;index<originalSlots.size();index++){
            var source=originalSlots.get(index);boolean fixed=index>=ArcaneTerminalMenu.FIRST_VIEW&&index<=ArcaneTerminalMenu.LAST_VIEW;
            var slot=new LayoutSlot(source,fixed?0:lowerOffset);slot.index=index;menu.slots.set(index,slot);
        }
        scrollbar.setHeight(52+lowerOffset).setPosition(new Point(leftPos+175,topPos+18));
        String previous=search==null?"":search.getValue();
        search=new EditBox(font,leftPos+98,topPos+6,65,10,Component.literal("Search"));search.setBordered(false);search.setMaxLength(15);search.setValue(previous);
        search.setResponder(value->{refresh();syncExternalSearch();});configureSearchFocus();addRenderableWidget(search);refresh();syncExternalSearch();
        send(0,null,0,0,false,"");
    }
    private static final class LayoutSlot extends Slot {
        private final Slot delegate;
        LayoutSlot(Slot delegate,int offset){super(delegate.container,delegate.getContainerSlot(),delegate.x,delegate.y+offset);this.delegate=delegate;}
        @Override public boolean mayPlace(ItemStack stack){return delegate.mayPlace(stack);}
        @Override public boolean mayPickup(Player player){return delegate.mayPickup(player);}
        @Override public int getMaxStackSize(){return delegate.getMaxStackSize();}
        @Override public int getMaxStackSize(ItemStack stack){return delegate.getMaxStackSize(stack);}
        @Override public boolean isActive(){return delegate.isActive();}
    }
    private void refresh(){
        String text=search==null?"":search.getValue();
        if(!text.equals(compiledQuery)){compiledQuery=text;query=ArcaneTerminalViewRules.search(text);}
        var knowledge=thaumcraft.api.research.ResearchApi.clientKnowledge();String language=minecraft.getLanguageManager().getSelected();
        if(knowledge!=cachedKnowledge||!Objects.equals(language,cachedLanguage)){tooltipSearchCache.clear();cachedKnowledge=knowledge;cachedLanguage=language;}
        if(tooltipSearchCache.size()>menu.entries().size()*2+64)tooltipSearchCache.clear();
        var cells=new ArrayList<ItemStack>();for(int i=11;i<=15;i++)if(!menu.inventory().getItem(i).isEmpty())cells.add(menu.inventory().getItem(i));
        var partition=ViewCellItem.createItemFilter(cells);var values=new ArrayList<ItemTerminalEntry>();
        for(var entry:menu.entries()){
            if(menu.view()==ViewItems.CRAFTABLE&&!entry.craftable()||menu.view()==ViewItems.STORED&&entry.amount()==0)continue;
            if(!partition.isEmpty()&&!partition.isListed(entry.key()))continue;
            String name=entry.key().getDisplayName().getString(),mod=entry.key().getId().getNamespace();
            // RV3 enables SEARCH_TOOLTIPS by default, including @ searches.
            if(!query.matches(name,mod,List.of())){
                if(!thaumicenergistics.config.ThEClientConfig.SEARCH_TOOLTIPS.get())continue;
                var tooltip=tooltipSearchCache.computeIfAbsent(entry.key(),key->net.minecraft.client.gui.screens.Screen.getTooltipFromItem(minecraft,key.toStack()).stream().map(Component::getString).toList());
                if(!query.matches(name,mod,tooltip))continue;
            }
            values.add(entry);
        }
        values.sort((first,second)->ArcaneTerminalViewRules.compare(menu.sort(),menu.direction(),menu.view(),
                first.key().getDisplayName().getString(),second.key().getDisplayName().getString(),first.key().getId().getNamespace(),second.key().getId().getNamespace(),first.amount(),second.amount()));
        visible=List.copyOf(values);scrollbar.setRange(0,Math.max(0,(visible.size()+8)/9-rows),2);
    }
    private void configureSearchFocus(){boolean automatic=AEConfig.instance().isAutoFocusSearch();search.setCanLoseFocus(!automatic);search.setFocused(automatic);}
    private void syncExternalSearch(){
        // Original NEI modes publish the terminal query; they do not import the viewer's filter.
        if(search==null||!AEConfig.instance().isSyncWithExternalSearch()){lastExternalSearch=null;return;}
        String value=search.getValue().trim().toLowerCase(Locale.ROOT);
        if(!value.equals(lastExternalSearch))lastExternalSearch=ExternalRecipeSearch.publish(value)?value:null;
    }
    private void changeSearchMode(boolean backwards){
        var config=AEConfig.instance();int mode=ArcaneTerminalViewRules.rotateSearchMode(ArcaneTerminalViewRules.searchMode(config.isAutoFocusSearch(),config.isSyncWithExternalSearch()),backwards);
        config.setAutoFocusSearch(mode%2==0);config.setSyncWithExternalSearch(mode>=2);config.save();configureSearchFocus();syncExternalSearch();
    }
    @Override protected void containerTick(){super.containerTick();refresh();syncExternalSearch();scrollbar.tick();}
    private void send(int action,ItemTerminalEntry entry,int index,int button,boolean shift,String recipe){
        PacketDistributor.sendToServer(new ArcaneTerminalActionPayload(menu.containerId,action,index,button,shift,entry==null?ItemStack.EMPTY:entry.key().toStack(),recipe));
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        GuiTextures.blit(graphics,TEXTURE,leftPos,topPos,0,0,195,35);
        for(int extra=0;extra<rows-3;extra++)GuiTextures.blit(graphics,TEXTURE,leftPos,topPos+35+extra*18,0,35,195,18);
        GuiTextures.blit(graphics,TEXTURE,leftPos,topPos+35+lowerOffset,0,35,195,208);
        GuiTextures.blit(graphics,TEXTURE,leftPos+195,topPos,195,0,35,104);
        for(int slot=11;slot<=15;slot++)if(menu.inventory().getItem(slot).isEmpty())Icon.BACKGROUND_VIEW_CELL.getBlitter().dest(leftPos+206,topPos+8+(slot-11)*18).blit(graphics);
        for(int slot=0;slot<rows*9;slot++){
            int index=slot+scrollbar.getCurrentScroll()*9;if(index>=visible.size())break;var entry=visible.get(index);
            int x=leftPos+7+slot%9*18,y=topPos+17+slot/9*18;
            graphics.renderItem(entry.key().toStack(),x,y);graphics.renderItemDecorations(font,entry.key().toStack(),x,y,"");
            if(entry.amount()>0&&menu.view()!=ViewItems.CRAFTABLE||entry.craftable()){
                boolean large=AEConfig.instance().isUseLargeFonts();float scale=large?.85F:.5F,offset=large?0:-1;
                String amount=entry.amount()>0&&menu.view()!=ViewItems.CRAFTABLE?(large?MonitorNumbers.wide(entry.amount()):MonitorNumbers.slim(entry.amount())):
                        Component.translatable(large?"thaumicenergistics.gui.craft.large":"thaumicenergistics.gui.craft.small").getString();
                graphics.pose().pushPose();graphics.pose().translate(x+offset+18,y+offset+17,150);graphics.pose().scale(scale,scale,1);
                graphics.drawString(font,amount,-font.width(amount),-font.lineHeight,0xffffffff,true);graphics.flush();graphics.pose().popPose();
            }
            if(isHovering(7+slot%9*18,17+slot/9*18,16,16,mouseX,mouseY))graphics.fill(x,y,x+16,y+16,0x80ffffff);
        }
        int costIndex=0;for(var cost:menu.costs()){
            int x=leftPos+136+costIndex%2*18,y=topPos+90+lowerOffset+costIndex/2*18;
            aspectRenderer.drawInGui(minecraft,graphics,x,y,EssentiaKey.of(cost.aspect()),cost.enough()?1:ArcaneTerminalViewRules.costAlpha(System.currentTimeMillis()));
            graphics.pose().pushPose();graphics.pose().translate(x+16,y+11,150);graphics.pose().scale(.5F,.5F,1);
            String amount=new java.text.DecimalFormat("#####.#").format(cost.display());graphics.drawString(font,amount,-font.width(amount),0,0xffffffff,true);graphics.flush();graphics.pose().popPose();costIndex++;
        }
        toolbar(graphics,8,switch(menu.sort()){case AMOUNT->Icon.SORT_BY_AMOUNT;case MOD->Icon.SORT_BY_MOD;default->Icon.SORT_BY_NAME;});
        toolbar(graphics,28,menu.direction()==SortDir.ASCENDING?Icon.ARROW_UP:Icon.ARROW_DOWN);
        toolbar(graphics,48,menu.view()==ViewItems.ALL?Icon.VIEW_MODE_ALL:menu.view()==ViewItems.STORED?Icon.VIEW_MODE_STORED:Icon.VIEW_MODE_CRAFTING);
        toolbar(graphics,68,AEConfig.instance().isSyncWithExternalSearch()?Icon.SEARCH_JEI:AEConfig.instance().isAutoFocusSearch()?Icon.SEARCH_AUTO_FOCUS:Icon.SEARCH_DEFAULT);
        toolbar(graphics,88,AEConfig.instance().getTerminalStyle()==TerminalStyle.TALL?Icon.TERMINAL_STYLE_TALL:Icon.TERMINAL_STYLE_SMALL);
        graphics.pose().pushPose();graphics.pose().translate(leftPos+98,topPos+89+lowerOffset,0);graphics.pose().scale(.5F,.5F,1);Icon.CLEAR.getBlitter().dest(0,0).blit(graphics);graphics.pose().popPose();
        graphics.flush();com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.blit(STATES,leftPos+26,topPos+112+lowerOffset,8,8,0,0,16,16,256,256);
        scrollbar.drawForegroundLayer(graphics,new Rect2i(leftPos,topPos,imageWidth,imageHeight),new Point(mouseX,mouseY));
    }
    private void toolbar(GuiGraphics graphics,int y,Icon icon){Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos-18,topPos+y).blit(graphics);icon.getBlitter().dest(leftPos-18,topPos+y).blit(graphics);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){graphics.drawString(font,title,8,6,0,false);}
    private ItemTerminalEntry underMouse(double x,double y){
        if(!isHovering(7,17,162,rows*18,x,y))return null;
        int index=(int)(x-leftPos-7)/18+(int)(y-topPos-17)/18*9+scrollbar.getCurrentScroll()*9;
        return index<visible.size()?visible.get(index):null;
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partial){
        super.render(graphics,x,y,partial);renderTooltip(graphics,x,y);var entry=underMouse(x,y);
        if(entry!=null)graphics.renderTooltip(font,entry.key().toStack(),x,y);
        String[] help={"gui.tooltips.ae2.SortBy","gui.tooltips.ae2.SortOrder","gui.tooltips.ae2.View","thaumicenergistics.gui.search_mode","gui.tooltips.ae2.TerminalStyle"};
        for(int i=0;i<5;i++)if(isHovering(-18,8+i*20,16,16,x,y)){
            var label=Component.translatable(help[i]);
            if(i==3){int mode=ArcaneTerminalViewRules.searchMode(AEConfig.instance().isAutoFocusSearch(),AEConfig.instance().isSyncWithExternalSearch());
                label.append(": ").append(Component.translatable("thaumicenergistics.gui.search_mode."+mode));}
            graphics.renderTooltip(font,label,x,y);
        }
        if(isHovering(26,112+lowerOffset,8,8,x,y))graphics.renderTooltip(font,Component.translatable("thaumicenergistics.tooltip.button.swap.armor.title"),x,y);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button<0)return super.mouseClicked(x,y,button);
        if(isHovering(7,17,162,rows*18,x,y)){
            var entry=underMouse(x,y);boolean extract=menu.getCarried().isEmpty()||hasShiftDown()&&button==1;
            if(extract&&entry!=null){boolean craft=button!=1&&entry.craftable()&&(entry.amount()==0||button==2);send(craft?9:1,entry,0,button,hasShiftDown(),"");}
            else if(!extract)send(2,null,0,button,hasShiftDown(),"");return true;
        }
        if(spaceHeld){for(int i=0;i<36;i++){var slot=menu.getSlot(i);if(isHovering(slot.x,slot.y,16,16,x,y)&&slot.hasItem()){send(3,null,i,button,hasShiftDown(),"");return true;}}}
        if(button==1&&isHovering(98,6,65,10,x,y)){search.setValue("");return true;}
        if(button<2){
            for(int i=0;i<5;i++)if(isHovering(-18,8+i*20,16,16,x,y)){
                if(i<3)send(4+i,null,0,button,false,"");
                else if(i==3)changeSearchMode(button==1);
                else{AEConfig.instance().setTerminalStyle(AEConfig.instance().getTerminalStyle()==TerminalStyle.TALL?TerminalStyle.SMALL:TerminalStyle.TALL);AEConfig.instance().save();init();}
                return true;
            }
            if(isHovering(98,89+lowerOffset,8,8,x,y)){send(7,null,0,button,false,"");return true;}
            if(isHovering(26,112+lowerOffset,8,8,x,y)){send(8,null,0,button,false,"");return true;}
        }
        if(scrollbar.onMouseDown(new Point((int)x,(int)y),button))return true;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseReleased(double x,double y,int button){return scrollbar.onMouseUp(new Point((int)x,(int)y),button)||super.mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return scrollbar.onMouseDrag(new Point((int)x,(int)y),button)||super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(isHovering(7,17,162,rows*18,x,y)){
            if(hasShiftDown()){
                if(vertical>0&&!menu.getCarried().isEmpty())send(2,null,0,-2,true,"");
                else if(vertical<0){var entry=underMouse(x,y);if(entry!=null)send(1,entry,0,-2,true,"");}
                return true;
            }
            return scrollbar.onMouseWheel(new Point((int)x,(int)y),vertical);
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==32)spaceHeld=true;
        if(search.isFocused()&&key!=256){if(key==32&&search.getValue().isEmpty())return true;if(search.keyPressed(key,scan,modifiers))return true;if(minecraft.options.keyInventory.matches(key,scan))return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean keyReleased(int key,int scan,int modifiers){if(key==32)spaceHeld=false;return super.keyReleased(key,scan,modifiers);}
    @Override public boolean charTyped(char character,int modifiers){
        if(search.isFocused())return character==' '&&search.getValue().isEmpty()||search.charTyped(character,modifiers);
        return super.charTyped(character,modifiers);
    }
}
