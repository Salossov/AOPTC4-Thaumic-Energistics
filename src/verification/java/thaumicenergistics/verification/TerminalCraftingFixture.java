package thaumicenergistics.verification;

import appeng.api.crafting.*;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.storage.EssentiaKey;

/** Verification-only advertised processing pattern; not a gameplay crafting provider. */
final class TerminalCraftingFixture {
    static ICraftingProvider provider(Level level){
        var encoded=PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.STONE),1)),List.of(new GenericStack(EssentiaKey.of(Aspects.FIRE),8)));
        var pattern=PatternDetailsHelper.decodePattern(encoded,level);
        if(pattern==null)throw new IllegalStateException("Native AE2 processing pattern rejected essentia key");
        return new ICraftingProvider(){
            @Override public List<IPatternDetails> getAvailablePatterns(){return List.of(pattern);}
            @Override public boolean isBusy(){return true;}
            @Override public boolean pushPattern(IPatternDetails details,KeyCounter[] inputs){return false;}
        };
    }
    private TerminalCraftingFixture(){}
}
