package thaumicenergistics.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumcraft.api.client.ParticleApi;
import thaumicenergistics.ThaumicEnergistics;

/** Ritual source deliberately advertises no inventory and accepts no insertion. */
public final class InfusionProviderBlockEntity extends ProviderBlockEntity implements IAspectSource {
    public InfusionProviderBlockEntity(BlockPos pos,BlockState state){super(ThaumicEnergistics.INFUSION_PROVIDER_ENTITY.get(),pos,state,5);}
    @Override public boolean takeFromContainer(Aspect aspect,int amount){
        if(aspect==null||amount<=0||extractEssentiaFromNetwork(aspect,amount,true)!=amount)return false;
        int color=aspect.getColor();float red=(color&0xff0000)/1.671168E7F,green=(color&0xff00)/65280F,blue=(color&255)/255F;
        // Original proxy-only runes, not an invented packet broadcast; the
        // public API retains the matching integrated-client behavior.
        for(int i=0;i<5;i++)ParticleApi.blockRunes(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),red,green,blue,15,-.1F);
        for(int i=0;i<5;i++)ParticleApi.blockRunes(level,worldPosition.getX(),worldPosition.getY(),worldPosition.getZ(),red,green,blue,15,.1F);
        return true;
    }
    @Override public AspectList getAspects(){return null;}
    @Override public void setAspects(AspectList aspects){}
    @Override public boolean doesContainerAccept(Aspect aspect){return false;}
    // Quirk kept: this source rejects insertion via doesContainerAccept yet
    // its unused add method returns zero. It is never an allowed bus target.
    @Override public int addToContainer(Aspect aspect,int amount){return 0;}
    @Override public int containerContains(Aspect aspect){return 0;}
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount){return false;}
    @Override @Deprecated public boolean doesContainerContain(AspectList aspects){return false;}
    @Override @Deprecated public boolean takeFromContainer(AspectList aspects){return false;}
}
