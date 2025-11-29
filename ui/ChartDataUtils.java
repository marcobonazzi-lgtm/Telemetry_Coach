package org.simulator.ui;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.ui.asix_pack.AxisChoice;

import java.util.List;

final class ChartDataUtils {

    private ChartDataUtils(){}

    static void extractXY(Lap lap, AxisChoice axis, Channel ch,
                          List<Double> outX, List<Double> outY) {
        double d0  = firstNonNaNDist(lap);
        double lt0 = firstNonNaNLapT(lap);
        double t0  = firstNonNaNTime(lap);

        for (Sample s : lap.samples) {
            double x;
            if (axis.useDist && !Double.isNaN(s.distance())) {
                x = s.distance() - d0;
            } else if (axis.useLapTime) {
                Double v = s.values().getOrDefault(Channel.LAP_TIME, Double.NaN);
                x = (v != null && !v.isNaN()) ? (v - lt0) : Double.NaN;
            } else if (axis.useAbsTime) {
                Double v = s.values().getOrDefault(Channel.TIME, Double.NaN);
                x = (v != null && !v.isNaN()) ? (v - t0) : Double.NaN;
            } else {
                x = Double.NaN;
            }

            Double y = s.values().getOrDefault(ch, Double.NaN);
            if (!Double.isNaN(x) && y != null && !y.isNaN()) {
                outX.add(x); outY.add(y);
            }
        }
    }

    static double firstNonNaNDist(Lap lap){
        for (var s: lap.samples) if (!Double.isNaN(s.distance())) return s.distance();
        return 0.0;
    }
    static double firstNonNaNLapT(Lap lap){
        for (var s: lap.samples){
            Double v = s.values().getOrDefault(Channel.LAP_TIME, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return 0.0;
    }
    static double firstNonNaNTime(Lap lap){
        for (var s: lap.samples){
            Double v = s.values().getOrDefault(Channel.TIME, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return 0.0;
    }


}
