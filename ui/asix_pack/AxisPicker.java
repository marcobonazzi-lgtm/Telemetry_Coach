package org.simulator.ui.asix_pack;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

public class AxisPicker {
    public static AxisChoice pick(Lap lap) {
        AxisChoice a = new AxisChoice();
        boolean hasDist = lap.samples.stream().anyMatch(s -> !Double.isNaN(s.distance()));
        boolean hasLapT = lap.samples.stream().anyMatch(s -> {
            Double v = s.values().getOrDefault(Channel.LAP_TIME, Double.NaN);
            return v != null && !v.isNaN();
        });
        boolean hasAbsT = lap.samples.stream().anyMatch(s -> {
            Double v = s.values().getOrDefault(Channel.TIME, Double.NaN);
            return v != null && !v.isNaN();
        });
        if (hasDist) { a.label = "Distance [m]"; a.useDist = true; }
        else if (hasLapT) { a.label = "Lap Time [s]"; a.useLapTime = true; }
        else if (hasAbsT) { a.label = "Time [s]"; a.useAbsTime = true; }
        else { a.label = "Sample #"; }
        return a;
    }
}
