package thaumicenergistics.parts;

import java.util.Objects;
import java.util.UUID;

/** Per-monitor PartEssentiaConversionMonitor double-click state; never persisted.
 * A successful first deposit records the aspect. The second click consumes the
 * latch only for that player, at an inclusive distance of forty server ticks.
 */
final class ConversionClickTracker<T> {
    private UUID player;
    private int tick;
    private T aspect;
    void deposited(UUID owner,int serverTick,T depositedAspect){
        player=Objects.requireNonNull(owner);tick=serverTick;aspect=Objects.requireNonNull(depositedAspect);
    }
    T consume(UUID owner,int serverTick){
        // Keep int subtraction before comparison, as in the release: a server's
        // tick counter crossing Integer.MAX_VALUE does not break a recent click.
        if(player!=null&&player.equals(owner)&&serverTick-tick<=40){
            player=null;tick=0;return aspect;
        }
        return null;
    }
}
