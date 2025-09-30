
package org.simulator.ui;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.ui.asix_pack.AxisChoice;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Cache leggera per dati XY dei grafici (per velocizzare cambio giro e ridurre ricomputi). */
final class ChartDataCache {
    private ChartDataCache(){}

    private static final Map<Lap, Map<Key, SoftReference<double[][]>>> CACHE = new WeakHashMap<>();

    static double[][] getOrCompute(Lap lap, AxisChoice axis, Channel ch, Supplier<double[][]> supplier){
        Map<Key, SoftReference<double[][]>> m = CACHE.computeIfAbsent(lap, k -> new HashMap<>());
        Key key = new Key(axis.useDist, axis.useLapTime, axis.useAbsTime, ch.ordinal());
        SoftReference<double[][]> ref = m.get(key);
        double[][] val = ref != null ? ref.get() : null;
        if (val != null) return val;
        val = supplier.get();
        m.put(key, new SoftReference<>(val));
        return val;
    }
    static void clear(Lap lap){ CACHE.remove(lap); }

    private static final class Key {
        final boolean d, lt, at; final int ch;
        Key(boolean d, boolean lt, boolean at, int ch){ this.d=d; this.lt=lt; this.at=at; this.ch=ch; }
        @Override public boolean equals(Object o){
            if (this == o) return true;
            if (o==null || o.getClass()!=Key.class) return false;
            Key k = (Key) o; return d==k.d && lt==k.lt && at==k.at && ch==k.ch;
        }
        @Override public int hashCode(){ int h=17; h = 31*h + (d?1:0); h = 31*h + (lt?1:0); h = 31*h + (at?1:0); h = 31*h + ch; return h; }
    }
}
