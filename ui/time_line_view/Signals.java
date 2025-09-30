package org.simulator.ui.time_line_view;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.ui.asix_pack.AxisChoice;

import java.util.*;

/** Costruisce serie allineate su asse X e fornisce API comode + accesso generico ai Channel. */
public final class Signals {
    final List<Double> x = new ArrayList<>();
    final List<Double> lapTime = new ArrayList<>();

    // API comode (come prima)
    final List<Double> throttle01 = new ArrayList<>();
    final List<Double> brake01    = new ArrayList<>();
    final List<Double> clutch01   = new ArrayList<>(); // posizione normalizzata (0..1) – AC: 1=rilsciata

    final List<Double> steeringDeg= new ArrayList<>();
    final List<Double> ffbRaw     = new ArrayList<>();
    double ffbAbsMax = 1.0;

    final List<Double> tyreFL = new ArrayList<>(), tyreFR = new ArrayList<>(),
            tyreRL = new ArrayList<>(), tyreRR = new ArrayList<>();

    final List<Double> brkFL = new ArrayList<>(), brkFR = new ArrayList<>(),
            brkRL = new ArrayList<>(), brkRR = new ArrayList<>();

    final List<Double> seatL = new ArrayList<>(), seatR = new ArrayList<>(), seatRear = new ArrayList<>();
    double seatAbsMax = 1.0;

    // ===== NUOVO: serie generiche per tutti i Channel presenti =====
    private final Map<Channel, List<Double>> series = new LinkedHashMap<>();

    Signals(Lap lap, AxisChoice axis){
        if (lap==null) return;

        double d0 = Double.NaN, lt0=Double.NaN, t0=Double.NaN;
        for (Sample s : lap.samples){
            if (axis.useDist && Double.isNaN(d0) && !Double.isNaN(s.distance())) d0 = s.distance();
            Double lt = s.values().get(Channel.LAP_TIME);
            if (axis.useLapTime   && Double.isNaN(lt0) && lt != null && !lt.isNaN()) lt0 = lt;
            Double ti = s.values().get(Channel.TIME);
            if (axis.useAbsTime   && Double.isNaN(t0)  && ti != null && !ti.isNaN())  t0 = ti;
        }

        for (Sample smp : lap.samples){
            double xVal;
            if (axis.useDist && !Double.isNaN(smp.distance())) xVal = smp.distance() - (Double.isNaN(d0)?0:d0);
            else if (axis.useLapTime){
                Double v = smp.values().get(Channel.LAP_TIME);
                xVal = (v!=null && !v.isNaN()) ? (v - (Double.isNaN(lt0)?0:lt0)) : x.size();
            } else if (axis.useAbsTime){
                Double v = smp.values().get(Channel.TIME);
                xVal = (v!=null && !v.isNaN()) ? (v - (Double.isNaN(t0)?0:t0)) : x.size();
            } else xVal = x.size();
            x.add(xVal);

            // Lap time allineato a X
            Double lt = smp.values().get(Channel.LAP_TIME);
            lapTime.add((lt!=null && !lt.isNaN()) ? (lt - (Double.isNaN(lt0)?0:lt0)) : Double.NaN);

            // API comode (come prima)
            throttle01.add(norm01Percent(smp.values().get(Channel.THROTTLE)));
            brake01.add(norm01Percent(smp.values().get(Channel.BRAKE)));

            Double cl = firstNonNaN(
                    smp.values().get(Channel.CLUTCH),
                    readByName(smp, "CLUTCH_POS", "CLUTCH_POSITION", "CLUTCH_INPUT"),
                    percentIfLooksLikePercent(smp.values().get(Channel.CLUTCH_FORCE))
            );
            clutch01.add(norm01Percent(cl)); // AC: 1 = rilasciata

            steeringDeg.add(smp.values().getOrDefault(Channel.STEER_ANGLE, Double.NaN));
            Double ffb = smp.values().getOrDefault(Channel.FFB, Double.NaN);
            ffbRaw.add(ffb);
            if (ffb!=null && !ffb.isNaN()) ffbAbsMax = Math.max(ffbAbsMax, Math.abs(ffb));

            tyreFL.add(readTemp(smp, "FL"));
            tyreFR.add(readTemp(smp, "FR"));
            tyreRL.add(readTemp(smp, "RL"));
            tyreRR.add(readTemp(smp, "RR"));

            brkFL.add(readBrakeTemp(smp, "FL"));
            brkFR.add(readBrakeTemp(smp, "FR"));
            brkRL.add(readBrakeTemp(smp, "RL"));
            brkRR.add(readBrakeTemp(smp, "RR"));

            Double sl = firstNonNaN(smp.values().get(Channel.SEAT_FORCE_LEFT),  readByName(smp, "SEAT", "LEFT", "FORCE"));
            Double sr = firstNonNaN(smp.values().get(Channel.SEAT_FORCE_RIGHT), readByName(smp, "SEAT", "RIGHT","FORCE"));
            Double sp = firstNonNaN(smp.values().get(Channel.SEAT_FORCE_REAR),  readByName(smp, "SEAT", "REAR","POST","FORCE"));
            seatL.add(sl); seatR.add(sr); seatRear.add(sp);
            if (sl!=null && !sl.isNaN()) seatAbsMax = Math.max(seatAbsMax, Math.abs(sl));
            if (sr!=null && !sr.isNaN()) seatAbsMax = Math.max(seatAbsMax, Math.abs(sr));
            if (sp!=null && !sp.isNaN()) seatAbsMax = Math.max(seatAbsMax, Math.abs(sp));

            // ===== NUOVO: popolamento serie generiche allineate a X =====
            Set<Channel> present = smp.values().keySet();
            // backfill NaN su serie già viste
            for (Map.Entry<Channel, List<Double>> e : series.entrySet()){
                if (!present.contains(e.getKey())) e.getValue().add(Double.NaN);
            }
            // append per i canali presenti (creando la lista se nuova e backfill iniziale)
            for (Map.Entry<Channel, Double> e : smp.values().entrySet()){
                Channel ch = e.getKey();
                Double v   = e.getValue();
                List<Double> ys = series.get(ch);
                if (ys == null){
                    ys = new ArrayList<>();
                    for (int k=0; k<x.size()-1; k++) ys.add(Double.NaN);
                    series.put(ch, ys);
                }
                ys.add(v);
            }
        }

        if (ffbAbsMax<=0) ffbAbsMax = 1.0;
        if (seatAbsMax<=0) seatAbsMax = 1.0;
    }

    // ===== API comode (invariato) ===========================================
    public double throttle01(double xq){ return clamp01(interp(x, throttle01, xq)); }
    public double brake01(double xq){    return clamp01(interp(x, brake01, xq)); }
    public double clutch01Pedal(double xq){
        return clamp01(1.0 - clamp01(interp(x, clutch01, xq)));
    }
    public double ffb01(double xq){
        double v = Math.abs(interp(x, ffbRaw, xq));
        return clamp01(v / ffbAbsMax);
    }
    Double lapTimeSec(double xq){
        return interp(x, lapTime, xq);
    }

    public Double tyreTemp(String corner, double xq){
        return switch (corner){
            case "FL" -> interp(x, tyreFL, xq);
            case "FR" -> interp(x, tyreFR, xq);
            case "RL" -> interp(x, tyreRL, xq);
            case "RR" -> interp(x, tyreRR, xq);
            default -> Double.NaN;
        };
    }
    public Double brakeTemp(String corner, double xq){
        return switch (corner){
            case "FL" -> interp(x, brkFL, xq);
            case "FR" -> interp(x, brkFR, xq);
            case "RL" -> interp(x, brkRL, xq);
            case "RR" -> interp(x, brkRR, xq);
            default -> Double.NaN;
        };
    }
    public double seatForce01(String which, double xq){
        double v = switch (which){
            case "LEFT"  -> Math.abs(interp(x, seatL, xq));
            case "RIGHT" -> Math.abs(interp(x, seatR, xq));
            case "REAR"  -> Math.abs(interp(x, seatRear, xq));
            default -> 0.0;
        };
        return clamp01(v / seatAbsMax);
    }

    // ===== NUOVO: accesso generico ai Channel ===============================
    /** Ritorna il valore del canale interpolato sull'asse X, o NaN se assente. */
    public Double value(Channel ch, double xq){
        List<Double> ys = series.get(ch);
        if (ys == null) return Double.NaN;
        return interp(x, ys, xq);
    }
    /** true se il canale esiste ed ha almeno un valore finito. */
    public boolean has(Channel ch){
        List<Double> ys = series.get(ch);
        return ys != null && hasAnyFinite(ys);
    }

    // ===== helper interni (come prima) ======================================
    private static Double readByName(Sample s, String... tokens){
        for (Map.Entry<Channel, Double> e : s.values().entrySet()){
            String nm = e.getKey().name().toUpperCase(Locale.ROOT);
            boolean ok = true;
            for (String t : tokens) if (!nm.contains(t.toUpperCase(Locale.ROOT))) { ok=false; break; }
            if (ok) return e.getValue();
        }
        return Double.NaN;
    }
    private static Double firstNonNaN(Double... vv){
        for (Double d : vv) if (d!=null && !d.isNaN() && !d.isInfinite()) return d;
        return Double.NaN;
    }
    private static Double percentIfLooksLikePercent(Double v){
        if (v==null || v.isNaN()) return Double.NaN;
        if (v>=0 && v<=10000) return v;
        return Double.NaN;
    }
    private static double norm01Percent(Double v){
        if (v==null || v.isNaN()) return 0.0;
        if (v<=1.0001) return clamp01(v);
        if (v<=100.0+1e-6) return clamp01(v/100.0);
        if (v>100.0 && v<=10000.0) return clamp01(v/100.0/100.0);
        return 0.0;
    }
    private static double clamp01(double v){ return (Double.isNaN(v)?0.0: Math.max(0.0, Math.min(1.0, v))); }

    private static Double readTemp(Sample s, String corner){
        return readByTokens(s,
                new String[]{"TYRE","TIRE"}, new String[]{"TEMP"}, new String[]{corner});
    }
    private static Double readBrakeTemp(Sample s, String corner){
        return readByTokens(s,
                new String[]{"BRAKE"}, new String[]{"TEMP"}, new String[]{corner});
    }
    private static Double readByTokens(Sample s, String[] group1, String[] group2, String[] group3){
        for (Map.Entry<Channel, Double> e : s.values().entrySet()){
            String nm = e.getKey().name().toUpperCase(Locale.ROOT);
            if (containsAny(nm, group1) && containsAny(nm, group2) && containsAny(nm, group3)) {
                return e.getValue();
            }
        }
        return Double.NaN;
    }
    private static boolean containsAny(String name, String[] tokens){
        for (String t : tokens) if (name.contains(t)) return true;
        return false;
    }

    boolean hasAnyBrakeTemp() {
        return hasAnyFinite(brkFL) || hasAnyFinite(brkFR) || hasAnyFinite(brkRL) || hasAnyFinite(brkRR);
    }
    private static boolean hasAnyFinite(java.util.List<Double> list) {
        if (list == null || list.isEmpty()) return false;
        for (Double d : list) {
            if (d != null && !d.isNaN() && !d.isInfinite()) return true;
        }
        return false;
    }

    // ---- interp locale (identico) ----
    private static double interp(java.util.List<Double> xs, java.util.List<Double> ys, double xq){
        int n = Math.min(xs.size(), ys.size());
        if (n==0) return Double.NaN;
        if (xq <= xs.get(0)) return ys.get(0);
        if (xq >= xs.get(n-1)) return ys.get(n-1);
        int lo=0, hi=n-1;
        while (hi - lo > 1){
            int mid = (lo+hi)/2;
            double xm = xs.get(mid);
            if (xm <= xq) lo = mid; else hi = mid;
        }
        double x1 = xs.get(lo), x2 = xs.get(hi);
        double y1 = ys.get(lo), y2 = ys.get(hi);
        if (x2==x1) return y1;
        double t = (xq - x1)/(x2 - x1);
        return y1 + t*(y2 - y1);
    }
}
