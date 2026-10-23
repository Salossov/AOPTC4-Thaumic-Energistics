package thaumicenergistics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import thaumcraft.api.aspects.Aspect;

/** Only ThE-owned item data. Active combustion deliberately does not enter a dismantled item. */
public record ChamberContents(String tag,int amount){
    public static final ChamberContents EMPTY=new ChamberContents("",0);
    public static final Codec<ChamberContents> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.STRING.optionalFieldOf("aspect","").forGetter(ChamberContents::tag),
            Codec.intRange(0,64).optionalFieldOf("amount",0).forGetter(ChamberContents::amount)).apply(instance,ChamberContents::new));
    public ChamberContents{if(amount<0||amount>64||amount>0&&tag.isEmpty())throw new IllegalArgumentException("Invalid chamber contents");if(amount==0)tag="";}
    public Aspect aspect(){return tag.isEmpty()?null:Aspect.getAspect(tag);}
}
