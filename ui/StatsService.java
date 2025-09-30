package org.simulator.ui;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

public final class StatsService {
    private StatsService(){}

    public static String buildAdvancedStats(Lap lap) {
        StringBuilder sb = new StringBuilder();

        // ==== Baseline (velocità, cambi, % gas/freno) ====
        double vmax = Double.NEGATIVE_INFINITY, sumV=0; int cntV=0;
        int shifts=0; Integer prevG=null;
        int thrCnt=0, brkCnt=0, tot=lap.samples.size();

        for (Sample s : lap.samples) {
            Double v = s.values().getOrDefault(Channel.SPEED, Double.NaN);
            if (v!=null && !v.isNaN()) { vmax=Math.max(vmax,v); sumV+=v; cntV++; }

            Double th = s.values().getOrDefault(Channel.THROTTLE, Double.NaN);
            Double br = s.values().getOrDefault(Channel.BRAKE, Double.NaN);
            if (th!=null && !th.isNaN() && th>90) thrCnt++;
            if (br!=null && !br.isNaN() && br>20) brkCnt++;

            Double gd = s.values().getOrDefault(Channel.GEAR, Double.NaN);
            Integer g = (gd!=null && !gd.isNaN()) ? gd.intValue() : null;
            if (g!=null && prevG!=null && !g.equals(prevG)) shifts++;
            if (g!=null) prevG=g;
        }
        double vavg = cntV>0 ? sumV/cntV : Double.NaN;

        sb.append("Lap: ").append(lap.index).append('\n');
        if (!Double.isNaN(lap.lapTime)) sb.append(String.format("Lap time: %.3f s%n", lap.lapTime));
        if (vmax>0) sb.append(String.format("Vmax: %.1f km/h%n", vmax));
        if (!Double.isNaN(vavg)) sb.append(String.format("Vmedia: %.1f km/h%n", vavg));
        sb.append("Cambi marcia: ").append(shifts).append('\n');
        if (tot>0) {
            sb.append(String.format("Tempo pieno gas (>90%%): %.1f%%%n", 100.0*thrCnt/tot));
            sb.append(String.format("Tempo in frenata (>20%%): %.1f%%%n", 100.0*brkCnt/tot));
        }

        // ==== Fuel ====
        var fuel = avgMinMax(lap, Channel.FUEL_LEVEL);
        if (fuel.count>0) {
            double used = Math.max(0.0, fuel.first - fuel.last);
            sb.append(String.format("Fuel start: %.2f  end: %.2f  used: %.2f%n",
                    fuel.first, fuel.last, used));
        }

        // ==== TC / ABS (percentuale di attivazione) ====
        double tcPct  = fractionActive(lap, Channel.TC_ACTIVE);
        double absPct = fractionActive(lap, Channel.ABS_ACTIVE);
        if (tcPct>=0)  sb.append(String.format("TC attivo: %.1f%%%n", 100.0*tcPct));
        if (absPct>=0) sb.append(String.format("ABS attivo: %.1f%%%n", 100.0*absPct));

        // ==== Surface Grip (medio) ====
        var grip = avgMinMax(lap, Channel.SURFACE_GRIP);
        if (grip.count>0) {
            sb.append(String.format("Surface grip medio: %s%n", fmt(grip.avg)));
        }

        // ==== Brake temps (avg) ====
        var bFL = avgMinMax(lap, Channel.BRAKE_TEMP_FL);
        var bFR = avgMinMax(lap, Channel.BRAKE_TEMP_FR);
        var bRL = avgMinMax(lap, Channel.BRAKE_TEMP_RL);
        var bRR = avgMinMax(lap, Channel.BRAKE_TEMP_RR);
        if (bFL.count+bFR.count+bRL.count+bRR.count > 0) {
            sb.append("Brake temp avg [°C]  FL/FR/RL/RR: ")
                    .append(fmt(bFL.avg)).append(" / ").append(fmt(bFR.avg)).append(" / ")
                    .append(fmt(bRL.avg)).append(" / ").append(fmt(bRR.avg)).append('\n');
        }

        // ==== Tyre pressures (avg) ====
        var pFL = avgMinMax(lap, Channel.TIRE_PRESSURE_FL);
        var pFR = avgMinMax(lap, Channel.TIRE_PRESSURE_FR);
        var pRL = avgMinMax(lap, Channel.TIRE_PRESSURE_RL);
        var pRR = avgMinMax(lap, Channel.TIRE_PRESSURE_RR);
        if (pFL.count+pFR.count+pRL.count+pRR.count > 0) {
            sb.append("Tyre pressure avg  FL/FR/RL/RR: ")
                    .append(fmt(pFL.avg)).append(" / ").append(fmt(pFR.avg)).append(" / ")
                    .append(fmt(pRL.avg)).append(" / ").append(fmt(pRR.avg)).append('\n');
        }

        // ==== Tyre temps (Middle) ====
        var tMidFL = avgMinMax(lap, Channel.TIRE_TEMP_MIDDLE_FL);
        var tMidFR = avgMinMax(lap, Channel.TIRE_TEMP_MIDDLE_FR);
        var tMidRL = avgMinMax(lap, Channel.TIRE_TEMP_MIDDLE_RL);
        var tMidRR = avgMinMax(lap, Channel.TIRE_TEMP_MIDDLE_RR);
        if (tMidFL.count+tMidFR.count+tMidRL.count+tMidRR.count > 0) {
            sb.append("Tyre temp (Middle) avg [°C]  FL/FR/RL/RR: ")
                    .append(fmt(tMidFL.avg)).append(" / ").append(fmt(tMidFR.avg)).append(" / ")
                    .append(fmt(tMidRL.avg)).append(" / ").append(fmt(tMidRR.avg)).append('\n');
        }

        return sb.toString();
    }

    private static String fmt(double v){ return Double.isNaN(v)? "n/d" : String.format("%.1f", v); }

    private static final class Stats { double first=Double.NaN, last=Double.NaN, min=Double.NaN, max=Double.NaN, avg=Double.NaN; int count=0; }

    private static Stats avgMinMax(Lap lap, Channel ch){
        Stats st = new Stats();
        double sum=0; int n=0; boolean firstSet=false;
        for (var s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v==null || v.isNaN()) continue;
            if (!firstSet){ st.first=v; firstSet=true; }
            st.last=v;
            st.min = (n==0)? v : Math.min(st.min, v);
            st.max = (n==0)? v : Math.max(st.max, v);
            sum += v; n++;
        }
        if (n>0){ st.avg = sum/n; st.count=n; }
        return st;
    }

    /** percentuale di campioni attivi (0..1), -1 se il canale manca */
    private static double fractionActive(Lap lap, Channel flag){
        int n=0, on=0; boolean found=false;
        for (var s: lap.samples){
            Double v = s.values().get(flag);
            if (v == null || v.isNaN()) continue;
            found = true; n++;
            // gestisce 0/1 e percentuali
            if (v >= 0.5) on++; // per % >50 è "on"
        }
        return found && n>0 ? (double)on/n : -1.0;
    }
}
