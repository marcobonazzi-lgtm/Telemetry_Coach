package org.simulator.ui;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.ui.asix_pack.AxisChoice;

import java.util.ArrayList;
import java.util.List;

public final class SeriesBundle {
    public final List<Double> x = new ArrayList<>();
    public final List<Double> speed = new ArrayList<>();
    public final List<Double> throttle = new ArrayList<>();
    public final List<Double> clutch = new ArrayList<>();
    public final List<Double> brake = new ArrayList<>();
    public final List<Double> steering = new ArrayList<>();
    public final List<Double> rpm = new ArrayList<>();
    public final List<Double> ffb = new ArrayList<>();
    public final List<Double> pedalForce = new ArrayList<>();
    public final List<Double> seatForce  = new ArrayList<>();

    private static final int DOWNSAMPLE_THRESHOLD = 50_000;

    public static SeriesBundle extract(Lap lap, AxisChoice axis) {
        SeriesBundle sb = new SeriesBundle();
        double d0 = Double.NaN, lt0 = Double.NaN, t0 = Double.NaN;

        for (Sample s : lap.samples) {
            if (axis.useDist && Double.isNaN(d0) && !Double.isNaN(s.distance())) d0 = s.distance();
            if (axis.useLapTime) {
                Double v = s.values().getOrDefault(Channel.LAP_TIME, Double.NaN);
                if (Double.isNaN(lt0) && v != null && !v.isNaN()) lt0 = v;
            }
            if (axis.useAbsTime) {
                Double v = s.values().getOrDefault(Channel.TIME, Double.NaN);
                if (Double.isNaN(t0) && v != null && !v.isNaN()) t0 = v;
            }
        }

        int idx = 0;
        for (Sample s : lap.samples) {
            double xVal;
            if (axis.useDist && !Double.isNaN(s.distance())) {
                xVal = s.distance() - (Double.isNaN(d0) ? 0 : d0);
            } else if (axis.useLapTime) {
                Double v = s.values().getOrDefault(Channel.LAP_TIME, Double.NaN);
                xVal = (v != null && !v.isNaN()) ? (v - (Double.isNaN(lt0) ? 0 : lt0)) : idx;
            } else if (axis.useAbsTime) {
                Double v = s.values().getOrDefault(Channel.TIME, Double.NaN);
                xVal = (v != null && !v.isNaN()) ? (v - (Double.isNaN(t0) ? 0 : t0)) : idx;
            } else {
                xVal = idx;
            }

            sb.x.add(xVal);
            sb.speed.add(s.values().getOrDefault(Channel.SPEED, Double.NaN));
// mappa rapida
            var map = s.values();

// THROTTLE e BRAKE: posizione grezza (0..1 o 0..100)
            Double thRaw = map.getOrDefault(org.simulator.canale.Channel.THROTTLE, Double.NaN);
            Double brRaw = map.getOrDefault(org.simulator.canale.Channel.BRAKE,    Double.NaN);

// CLUTCH: preferisci posizione. NON normalizzare / NON invertire qui.
            Double clRaw = map.getOrDefault(org.simulator.canale.Channel.CLUTCH, Double.NaN);

// Scrivi GREZZO nella bundle
            sb.throttle.add(thRaw);
            sb.brake.add(brRaw);
            sb.clutch.add(clRaw);

            sb.steering.add(s.values().getOrDefault(Channel.STEER_ANGLE, Double.NaN));
            sb.rpm.add(s.values().getOrDefault(Channel.ENGINE_RPM, Double.NaN));
            sb.ffb.add(s.values().getOrDefault(Channel.FFB, Double.NaN));
            sb.pedalForce.add(s.values().getOrDefault(Channel.PEDAL_FORCE, Double.NaN));
            sb.seatForce.add(s.values().getOrDefault(Channel.SEAT_FORCE, Double.NaN));
            idx++;
        }

        if (sb.x.size() > DOWNSAMPLE_THRESHOLD) {
            int step = (int) Math.ceil(sb.x.size() / (double) DOWNSAMPLE_THRESHOLD);
            sb = downsample(sb, step);
        }
        return sb;
    }

    private static SeriesBundle downsample(SeriesBundle sb, int step) {
        SeriesBundle o = new SeriesBundle();
        for (int i = 0; i < sb.x.size(); i += step) {
            o.x.add(sb.x.get(i));
            o.speed.add(sb.speed.get(i));
            o.throttle.add(sb.throttle.get(i));
            o.brake.add(sb.brake.get(i));
            o.clutch.add(sb.clutch.get(i));
            o.steering.add(sb.steering.get(i));
            o.rpm.add(sb.rpm.get(i));
            o.ffb.add(sb.ffb.get(i));
            o.pedalForce.add(sb.pedalForce.get(i));
            o.seatForce.add(sb.seatForce.get(i));
        }
        return o;
    }
    // ---- helpers percentuali robusti 0..1 ----
    private static double norm01Percent(Double v){
        if (v == null || v.isNaN() || v.isInfinite()) return Double.NaN;
        if (v < 0) v = 0.0;
        // già 0..1
        if (v <= 1.0001) return v;
        // 0..100
        if (v <= 100.0001) return v / 100.0;
        // 0..10000 (alcuni log buggati)
        if (v <= 10000.0)   return v / 10000.0;
        // qualunque altro valore → prova a normalizzare su 100
        return Math.min(1.0, v / 100.0);
    }

    // Legge un canale per nome, senza rompere se l'enum non esiste
    private static Double readByName(java.util.Map<org.simulator.canale.Channel, Double> m, String name){
        try {
            var ch = org.simulator.canale.Channel.valueOf(name);
            return m.getOrDefault(ch, Double.NaN);
        } catch (IllegalArgumentException ex) {
            return Double.NaN;
        }
    }

    // Primo canale disponibile tra più nomi
    private static Double firstPresent(java.util.Map<org.simulator.canale.Channel, Double> m, String... names){
        for (String n : names) {
            Double v = readByName(m, n);
            if (v != null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }

}
