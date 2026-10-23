package thaumicenergistics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Item-owned viewing modes. Earlier development cells without this component use original defaults. */
public record TerminalSettings(int sort,int view){
    public static final TerminalSettings DEFAULT=new TerminalSettings(0,0);
    public static final Codec<TerminalSettings> CODEC=RecordCodecBuilder.create(instance->instance.group(
            Codec.intRange(0,1).optionalFieldOf("SortMode",0).forGetter(TerminalSettings::sort),
            Codec.intRange(0,2).optionalFieldOf("ViewMode",0).forGetter(TerminalSettings::view)).apply(instance,TerminalSettings::new));
    public TerminalSettings{if(sort<0||sort>1||view<0||view>2)throw new IllegalArgumentException("Invalid terminal viewing mode");}
}
