package org.simulator.coach;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.*;

/**
 * Core del "Coach": soglie, categorie/priorità, utilities e helpers condivisi.
 * Nota: le icone emoji sono disabilitate per evitare il "pallino grigio" in UI.
 */
public final class CoachCore {

    private CoachCore(){}

    // ====== soglie "default" (fallback) ======
    public static final double THR_FULL = 0.90;
    public static final double THR_STAB = 0.30;
    public static final double BRK_ON   = 0.20;
    public static final double BRK_TRAIL= 0.05;
    public static final double STEER_DEG= 25.0;
    public static final double LATG_HIGH= 0.9;
    public static final double COAST_THR= 0.05;
    public static final double COAST_BAD= 0.12;
    public static final double TRAIL_LOW= 0.05;
    public static final double TRAIL_HIGH=0.30;
    public static final double TC_MUCH  = 0.10;
    public static final double ABS_MUCH = 0.08;
    public static final double FFB_CLIP = 0.92;
    public static final double FFB_BADPCT=0.10;
    public static final double THR_OSC_DPS = 40.0;
    public static final double STEER_REV_RATE = 140.0;
    public static final double UPSHIFT_RPM_HIGH_Q = 0.95;
    public static final double LIFT_BEFORE_BRAKE_S = 0.25;
    public static final double EXIT_GAS_80_DELAY_S = 1.2;

    public static final double TYRE_OK_MIN = 80, TYRE_OK_MAX = 100; // °C
    public static final double PSI_GOOD_MIN = 26, PSI_GOOD_MAX = 28; // psi
    public static final double BRAKE_OK_MIN = 150, BRAKE_OK_MAX = 300; // °C

    // Nuove soglie (nuovi canali)
    public static final double OFFTRACK_BAD = 0.05;
    public static final double DRS_UNUSED_FACTOR = 0.5;
    public static final double ERS_RECOV_OVER_DEPLOY = 1.5;
    public static final double SLIPR_HIGH = 0.15;
    public static final double SLIPA_HIGH_DEG = 8.0;
    public static final double RIDE_LOW_WARN = 35.0;
    public static final double SUSP_SPIKE_N = 25.0;
    public static final double WIND_STRONG = 7.0;
    public static final double GRIP_LOW = 0.96;

    // ======= PRIORITÀ & CATEGORIE =======
    public enum Priority { HIGH, MEDIUM, LOW }
    public enum Category { GUIDA, GOMME, FRENI, FFB, TRASM, DANNI, SESSIONE }

    public static boolean INCLUDE_ICON = false;
    public static String icon(Priority p){ if(!INCLUDE_ICON) return ""; return switch(p){ case HIGH->"🔴 "; case MEDIUM->"🟠 "; default->"🟢 "; }; }
    public static String catLabel(Category c){ return switch(c){ case GUIDA->"[Guida] "; case GOMME->"[Gomme] "; case FRENI->"[Freni] "; case FFB->"[FFB] "; case TRASM->"[Trasmissione] "; case DANNI->"[Danni] "; case SESSIONE->"[Sessione] "; }; }

    public static final class Note {
        public final Priority priority; public final Category category; public final String text;
        public Note(Priority p, Category c, String t){ this.priority=p; this.category=c; this.text=t; }
        public String render(){ return icon(priority) + catLabel(category) + text; }
    }
    public static void add(List<Note> list, boolean cond, Priority p, Category c, String text){ if (cond) list.add(new Note(p,c,text)); }

    // =================== UTILS CANALI/STAT ===================
    public static double val(Sample s, Channel c) { return s.values().getOrDefault(c, Double.NaN); }
    public static boolean has(Lap lap, Channel c) {
        if (lap == null || lap.samples == null) return false;
        for (Sample s : lap.samples) {
            Double v = s.values().get(c);
            if (v != null && !v.isNaN()) return true;
        }
        return false;
    }
    public static boolean nearZero(double v, double thr){ return !Double.isNaN(v) && v < thr*100.0; }
    public static double fraction(Lap lap, java.util.function.Predicate<Sample> pred){
        if (lap == null || lap.samples == null) return 0.0;
        int n=0, m=0; for (Sample s: lap.samples){ if (pred.test(s)) m++; n++; }
        return n>0 ? (double)m/n : 0.0;
    }
    public static double fractionActive(Lap lap, Channel flag){
        return fraction(lap, s -> { double v = val(s, flag); return !Double.isNaN(v) && v >= 0.5; });
    }
    public static String pctFmt(double p){ return String.format("%.0f%%", p*100.0); }
    public static Integer toInt(double d){ if (Double.isNaN(d) || Double.isInfinite(d)) return null; return (int)Math.round(d); }
    public static double firstNonNaN(Lap lap, Channel ch){
        if (lap == null || lap.samples == null) return Double.NaN;
        for (Sample s: lap.samples){ Double v = s.values().getOrDefault(ch, Double.NaN); if (v!=null && !v.isNaN()) return v; }
        return Double.NaN;
    }
    public static String fmt1(double d){ return Double.isNaN(d) ? "--" : String.format("%.1f", d); }
    public static String fmt1s(double s){ return Double.isNaN(s) ? "--" : String.format("%.1fs", s); }
    public static double mean(double[] a){ double s=0; int n=0; for (double v: a){ if (!Double.isNaN(v)){ s+=v; n++; } } return n>0 ? s/n : Double.NaN; }
    public static double mean(List<Double> a){ double s=0; int n=0; for (double v: a){ if (!Double.isNaN(v)){ s+=v; n++; } } return n>0 ? s/n : Double.NaN; }
    public static boolean allNaN(double... v){ for (double x: v) if (!Double.isNaN(x)) return false; return true; }
    public static List<String> dedupByStem(List<String> in){
        List<String> out = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (String s: in){
            String stem = s.toLowerCase(Locale.ROOT).replaceAll("[^a-zàèéìòù0-9]+"," ").replaceAll("\\s+"," ").trim();
            stem = stem.replaceAll("( molto| spesso| in sessione| in [0-9]+/[0-9]+ giri| avg .*\\))$", "");
            if (seen.add(stem)) out.add(s);
        }
        return out;
    }
    public static String avgPct(String prefix, double sum, double n){ if (n<=0) return ""; return prefix + String.format("%.0f%%",(sum/n)*100.0) + ")"; }

    // ------- analisi riusabili -------
    public static boolean suspectedOversteer(Lap lap){
        int sus=0;
        for (int i = 1; i < lap.samples.size(); i++) {
            double steer = val(lap.samples.get(i), Channel.STEER_ANGLE);
            double latG  = val(lap.samples.get(i), Channel.CG_ACCEL_LATERAL);
            double vNow  = val(lap.samples.get(i), Channel.SPEED);
            double vPrev = val(lap.samples.get(i-1), Channel.SPEED);
            if (Double.isNaN(steer) || Double.isNaN(latG) || Double.isNaN(vNow) || Double.isNaN(vPrev)) continue;
            boolean steerBigDeg = Math.abs(steer) > STEER_DEG;
            if (steerBigDeg && Math.abs(latG) > LATG_HIGH && (vPrev - vNow) > 5) sus++;
        }
        return sus > 5;
    }
    public static double apexMinSpeed(Lap lap, List<Integer> apexes){
        if (apexes == null || apexes.isEmpty()) return Double.NaN;
        double vMin = Double.POSITIVE_INFINITY; boolean ok=false;
        for (int idx : apexes){
            if (idx<0 || idx>=lap.samples.size()) continue;
            double v = val(lap.samples.get(idx), Channel.SPEED);
            if (!Double.isNaN(v)) { vMin = Math.min(vMin, v); ok=true; }
        }
        return ok ? vMin : Double.NaN;
    }
    public static int downshiftsWithHighThrottle(Lap lap){
        int bad=0;
        for (int i=1;i<lap.samples.size();i++){
            Sample a=lap.samples.get(i-1), b=lap.samples.get(i);
            Integer gA=toInt(val(a, Channel.GEAR)), gB=toInt(val(b, Channel.GEAR));
            if (gA==null || gB==null) continue;
            if (gB < gA) {
                double thr=val(b, Channel.THROTTLE), brk=val(b, Channel.BRAKE), st=Math.abs(val(b, Channel.STEER_ANGLE));
                if (thr > 70 && brk < 5 && st < 5) bad++;
            }
        }
        return bad;
    }
    public static boolean lateLiftBeforeBrake(Lap lap, double liftWindowSec){
        if (!has(lap, Channel.THROTTLE) || !has(lap, Channel.BRAKE) || !has(lap, Channel.TIME)) return false;
        int cases=0, bad=0;
        for (int i=1;i<lap.samples.size();i++){
            Sample s = lap.samples.get(i);
            double br = val(s, Channel.BRAKE);
            if (Double.isNaN(br) || br < BRK_ON*100) continue;
            cases++;
            double tNow = val(s, Channel.TIME);
            boolean lifted = false;
            for (int j=i-1;j>=0;j--){
                Sample p = lap.samples.get(j);
                double tp = val(p, Channel.TIME);
                if (Double.isNaN(tp) || Double.isNaN(tNow)) break;
                if (tNow - tp > liftWindowSec) break;
                double th = val(p, Channel.THROTTLE);
                if (!Double.isNaN(th) && th < 10) { lifted = true; break; }
            }
            if (!lifted) bad++;
        }
        return cases >= 5 && bad > cases*0.5;
    }
    public static double throttleOscillationPct(Lap lap, double thrPercPerSec){
        int n=0, harsh=0;
        for (int i=1;i<lap.samples.size();i++){
            Sample a=lap.samples.get(i-1), b=lap.samples.get(i);
            double ta=val(a, Channel.TIME), tb=val(b, Channel.TIME);
            double pa=val(a, Channel.THROTTLE), pb=val(b, Channel.THROTTLE);
            if (Double.isNaN(ta)||Double.isNaN(tb)||Double.isNaN(pa)||Double.isNaN(pb)) continue;
            double dt = Math.max(1e-3, tb-ta);
            double rate = Math.abs((pb-pa)/dt);
            boolean inUse = Math.max(pa, pb) > THR_STAB*100;
            if (inUse && rate > thrPercPerSec) harsh++;
            n++;
        }
        return n>0 ? (double)harsh/n : 0.0;
    }
    public static double steeringHarshPct(Lap lap, double degPerSec){
        int n=0, harsh=0;
        for (int i=1;i<lap.samples.size();i++){
            Sample a=lap.samples.get(i-1), b=lap.samples.get(i);
            double ta=val(a, Channel.TIME), tb=val(b, Channel.TIME);
            double sa=val(a, Channel.STEER_ANGLE), sb=val(b, Channel.STEER_ANGLE);
            if (Double.isNaN(ta)||Double.isNaN(tb)||Double.isNaN(sa)||Double.isNaN(sb)) continue;
            double dt = Math.max(1e-3, tb-ta);
            double rate = Math.abs((sb-sa)/dt);
            if (rate > degPerSec) harsh++;
            n++;
        }
        return n>0 ? (double)harsh/n : 0.0;
    }
    public static double rpmQuantile(Lap lap, double q){
        List<Double> v = new ArrayList<>();
        for (Sample s: lap.samples){ double r = val(s, Channel.ENGINE_RPM); if (!Double.isNaN(r)) v.add(r); }
        if (v.isEmpty()) return Double.NaN;
        v.sort(Double::compareTo);
        int idx = Math.min(v.size()-1, Math.max(0, (int)Math.floor(q*(v.size()-1))));
        return v.get(idx);
    }
    public static final class DelayStats { public boolean valid=false; public double avg=Double.NaN, min=Double.NaN, max=Double.NaN; public final List<Double> all=new ArrayList<>(); }
    public static DelayStats apexToThrottleStats(Lap lap, List<Integer> apexIdx, double thrPct){
        DelayStats ds = new DelayStats();
        if (lap==null || lap.samples==null || lap.samples.isEmpty() || apexIdx==null || apexIdx.isEmpty() || !has(lap, Channel.TIME) || !has(lap, Channel.THROTTLE)) return ds;
        for (int idx: apexIdx){
            if (idx<0 || idx>=lap.samples.size()) continue;
            Sample a = lap.samples.get(idx);
            double tA = val(a, Channel.TIME);
            if (Double.isNaN(tA)) continue;
            double hit = Double.NaN;
            for (int k=idx; k<lap.samples.size(); k++){
                double thr = val(lap.samples.get(k), Channel.THROTTLE);
                double tk  = val(lap.samples.get(k), Channel.TIME);
                if (Double.isNaN(thr) || Double.isNaN(tk)) continue;
                double thrNorm = (thr > 1.001) ? thr/100.0 : thr;
                if (thrNorm >= thrPct) { hit = tk; break; }
            }
            if (!Double.isNaN(hit) && hit >= tA) ds.all.add(hit - tA);
        }
        if (!ds.all.isEmpty()){ ds.valid=true; ds.min=Collections.min(ds.all); ds.max=Collections.max(ds.all); ds.avg=mean(ds.all); }
        return ds;
    }

    // ----- aggregati -----
    public static double[] avgTyreTemps(Lap lap){ double fl=meanChannel(lap, Channel.TIRE_TEMP_MIDDLE_FL), fr=meanChannel(lap, Channel.TIRE_TEMP_MIDDLE_FR), rl=meanChannel(lap, Channel.TIRE_TEMP_MIDDLE_RL), rr=meanChannel(lap, Channel.TIRE_TEMP_MIDDLE_RR); if (allNaN(fl,fr,rl,rr)) return null; return new double[]{fl,fr,rl,rr}; }
    public static double[] avgTyrePsis(Lap lap){ double fl=meanChannel(lap, Channel.TIRE_PRESSURE_FL), fr=meanChannel(lap, Channel.TIRE_PRESSURE_FR), rl=meanChannel(lap, Channel.TIRE_PRESSURE_RL), rr=meanChannel(lap, Channel.TIRE_PRESSURE_RR); if (allNaN(fl,fr,rl,rr)) return null; return new double[]{fl,fr,rl,rr}; }
    public static double[] avgBrakeTemps(Lap lap){ double fl=meanChannel(lap, Channel.BRAKE_TEMP_FL), fr=meanChannel(lap, Channel.BRAKE_TEMP_FR), rl=meanChannel(lap, Channel.BRAKE_TEMP_RL), rr=meanChannel(lap, Channel.BRAKE_TEMP_RR); if (allNaN(fl,fr,rl,rr)) return null; return new double[]{fl,fr,rl,rr}; }
    public static double meanChannel(Lap lap, Channel ch){
        if (!has(lap, ch)) return Double.NaN;
        double sum=0; int n=0;
        for (Sample s: lap.samples){
            double v = val(s, ch);
            if (!Double.isNaN(v)) { sum+=v; n++; }
        }
        return n>0 ? (sum/n) : Double.NaN;
    }
    public static double[] meanOf(List<double[]> arr){
        if (arr.isEmpty()) return null;
        int m=-1; for (double[] a: arr){ if (a!=null){ m=a.length; break; } }
        if (m<=0) return null;
        double[] sum=new double[m]; int[] cnt=new int[m];
        for (double[] a: arr){
            if (a==null) continue;
            for (int i=0;i<m;i++){ double v=a[i]; if (!Double.isNaN(v)){ sum[i]+=v; cnt[i]++; } }
        }
        double[] out=new double[m];
        for (int i=0;i<m;i++) out[i]= cnt[i]>0? sum[i]/cnt[i] : Double.NaN;
        return out;
    }
    public static double ffbClipFraction(Lap lap){
        if (lap==null || lap.samples==null || lap.samples.isEmpty()) return 0.0;
        int n=0,m=0;
        for (var s: lap.samples){
            Double v = s.values().getOrDefault(Channel.FFB, Double.NaN);
            if (v==null || v.isNaN()) continue;
            n++; if (v >= FFB_CLIP) m++;
        }
        return n>0 ? (double)m/n : 0.0;
    }
    public static int pedalSpikeCount(Lap lap, double spikeN, double windowS){
        if (lap==null || lap.samples==null || lap.samples.size()<2) return 0;
        int spikes=0;
        for (int i=1;i<lap.samples.size();i++){
            var a=lap.samples.get(i-1); var b=lap.samples.get(i);
            double p0=val(a, Channel.PEDAL_FORCE), p1=val(b, Channel.PEDAL_FORCE);
            if (Double.isNaN(p0) || Double.isNaN(p1)) continue;
            double dt = Double.NaN;
            if (has(lap, Channel.TIME)){
                double t0=val(a, Channel.TIME), t1=val(b, Channel.TIME);
                if (!Double.isNaN(t0) && !Double.isNaN(t1)) dt=Math.max(1e-3, t1-t0);
            }
            if (p1 - p0 > spikeN && (Double.isNaN(dt) || dt <= windowS)) spikes++;
        }
        return spikes;
    }
    public static double seatKerbPct(Lap lap){
        if (lap==null || lap.samples==null || lap.samples.size()<2) return 0.0;
        int hit=0, base=0;
        for (int i=1;i<lap.samples.size();i++){
            var a=lap.samples.get(i-1); var b=lap.samples.get(i);
            double s0=val(a, Channel.SEAT_FORCE), s1=val(b, Channel.SEAT_FORCE);
            if (Double.isNaN(s0) || Double.isNaN(s1)) continue;
            double dt = Double.NaN;
            if (has(lap, Channel.TIME)){
                double t0=val(a, Channel.TIME), t1=val(b, Channel.TIME);
                if (!Double.isNaN(t0) && !Double.isNaN(t1)) dt=Math.max(1e-3, t1-t0);
            }
            boolean fast = (Double.isNaN(dt) || dt <= 0.04);
            if (fast) { base++; if (Math.abs(s1 - s0) > 120.0) hit++; }
        }
        return base>0 ? (double)hit/base : 0.0;
    }

    // --- slip ----
    public static double[] slipRatioFrontRearPct(Lap lap){
        if (lap==null || lap.samples==null) return new double[]{0,0};
        int n=0, f=0, r=0;
        for (var s: lap.samples){
            double fl = val(s, Channel.TIRE_SLIP_RATIO_FL);
            double fr = val(s, Channel.TIRE_SLIP_RATIO_FR);
            double rl = val(s, Channel.TIRE_SLIP_RATIO_RL);
            double rr = val(s, Channel.TIRE_SLIP_RATIO_RR);
            if (Double.isNaN(fl) && Double.isNaN(fr) && Double.isNaN(rl) && Double.isNaN(rr)) continue;
            boolean fh = (abs(fl)>SLIPR_HIGH) || (abs(fr)>SLIPR_HIGH);
            boolean rh = (abs(rl)>SLIPR_HIGH) || (abs(rr)>SLIPR_HIGH);
            if (fh) f++; if (rh) r++; n++;
        }
        return new double[]{ n>0?(double)f/n:0.0, n>0?(double)r/n:0.0 };
    }
    public static double[] slipAngleFrontRearPct(Lap lap){
        if (lap==null || lap.samples==null) return new double[]{0,0};
        int n=0, f=0, r=0;
        for (var s: lap.samples){
            double fl = val(s, Channel.TIRE_SLIP_ANGLE_FL);
            double fr = val(s, Channel.TIRE_SLIP_ANGLE_FR);
            double rl = val(s, Channel.TIRE_SLIP_ANGLE_RL);
            double rr = val(s, Channel.TIRE_SLIP_ANGLE_RR);
            if (Double.isNaN(fl) && Double.isNaN(fr) && Double.isNaN(rl) && Double.isNaN(rr)) continue;
            boolean fh = (abs(fl)>SLIPA_HIGH_DEG) || (abs(fr)>SLIPA_HIGH_DEG);
            boolean rh = (abs(rl)>SLIPA_HIGH_DEG) || (abs(rr)>SLIPA_HIGH_DEG);
            if (fh) f++; if (rh) r++; n++;
        }
        return new double[]{ n>0?(double)f/n:0.0, n>0?(double)r/n:0.0 };
    }
    public static double abs(double v){ return Double.isNaN(v)? Double.NaN : Math.abs(v); }

    // ======================= RICONOSCIMENTO MEZZO =======================
    public enum VehicleKind { FORMULA_HYBRID, FORMULA, LMP, GT3, GT4, DTM, TOURING, RALLY, KART, STREET, UNKNOWN }

    public static VehicleKind detectVehicleKind(Lap lap) {
        if (lap == null) return VehicleKind.UNKNOWN;
        int scoreHybrid=0, scoreFormula=0, scoreProto=0, scoreGT3=0, scoreGT4=0, scoreDTM=0, scoreTouring=0, scoreRally=0, scoreKart=0, scoreStreet=0;

        scoreHybrid += has(lap, Channel.DRS_AVAILABLE)?1:0;
        scoreHybrid += has(lap, Channel.DRS_ACTIVE)?1:0;
        scoreHybrid += has(lap, Channel.ERS_IS_CHARGING)?2:0;
        scoreHybrid += has(lap, Channel.KERS_DEPLOYED_ENERGY)?2:0;

        scoreFormula += (has(lap, Channel.DRS_AVAILABLE) || has(lap, Channel.DRS_ACTIVE)) ? 2 : 0;
        if (!has(lap, Channel.ERS_IS_CHARGING) && !has(lap, Channel.KERS_DEPLOYED_ENERGY) && (has(lap, Channel.DRS_AVAILABLE) || has(lap, Channel.DRS_ACTIVE))) scoreFormula += 1;

        if (has(lap, Channel.ABS_ACTIVE) && has(lap, Channel.TC_ACTIVE)) scoreProto += 1;

        if (has(lap, Channel.ABS_ACTIVE)) { scoreGT3++; scoreGT4++; scoreDTM++; scoreTouring++; }
        if (has(lap, Channel.TC_ACTIVE))  { scoreGT3++; scoreGT4++; scoreDTM++; scoreTouring++; }
        if ((has(lap, Channel.ABS_ACTIVE) || has(lap, Channel.TC_ACTIVE)) && !has(lap, Channel.DRS_AVAILABLE) && !has(lap, Channel.ERS_IS_CHARGING)) {
            scoreGT3+=2; scoreGT4+=1; scoreDTM+=1; scoreTouring+=1;
        }

        if (!has(lap, Channel.DRS_AVAILABLE) && !has(lap, Channel.ERS_IS_CHARGING)) { scoreRally++; scoreStreet++; }
        if (!has(lap, Channel.ABS_ACTIVE) && !has(lap, Channel.TC_ACTIVE) && !has(lap, Channel.DRS_AVAILABLE)) scoreKart++;

        Map<VehicleKind, Integer> scores = new EnumMap<>(VehicleKind.class);
        scores.put(VehicleKind.FORMULA_HYBRID, scoreHybrid);
        scores.put(VehicleKind.FORMULA,       scoreFormula);
        scores.put(VehicleKind.LMP,           scoreProto);
        scores.put(VehicleKind.GT3,           scoreGT3);
        scores.put(VehicleKind.GT4,           scoreGT4);
        scores.put(VehicleKind.DTM,           scoreDTM);
        scores.put(VehicleKind.TOURING,       scoreTouring);
        scores.put(VehicleKind.RALLY,         scoreRally);
        scores.put(VehicleKind.KART,          scoreKart);
        scores.put(VehicleKind.STREET,        scoreStreet);

        return scores.entrySet().stream().max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0).map(Map.Entry::getKey).orElse(VehicleKind.UNKNOWN);
    }
    public static VehicleKind detectVehicleKind(List<Lap> laps) {
        if (laps == null || laps.isEmpty()) return VehicleKind.UNKNOWN;
        Map<VehicleKind, Integer> tally = new EnumMap<>(VehicleKind.class);
        for (Lap l : laps) tally.merge(detectVehicleKind(l), 1, Integer::sum);
        return tally.entrySet().stream().max(Map.Entry.comparingByValue())
                .filter(e -> e.getValue() > 0).map(Map.Entry::getKey).orElse(VehicleKind.UNKNOWN);
    }

    // ======================= PROFILI MEZZO-SPECIFICI =======================
    private static final class Profile {
        final double thrOscDps, steerRevRate, absMuch, tcMuch, trailLow, trailHigh, coastBad, exitGasDelay;
        Profile(double thrOscDps, double steerRevRate, double absMuch, double tcMuch, double trailLow, double trailHigh, double coastBad, double exitGasDelay){
            this.thrOscDps=thrOscDps; this.steerRevRate=steerRevRate; this.absMuch=absMuch; this.tcMuch=tcMuch; this.trailLow=trailLow; this.trailHigh=trailHigh; this.coastBad=coastBad; this.exitGasDelay=exitGasDelay;
        }
    }
    private static final Map<VehicleKind, Profile> PROFILES = new EnumMap<>(VehicleKind.class);
    static {
        // Valori pragmatici: più sensibili su Formula, più permissivi su GT; LMP a metà.
        PROFILES.put(VehicleKind.FORMULA_HYBRID, new Profile(50, 180, 0.05, 0.06, 0.06, 0.28, 0.10, 1.0));
        PROFILES.put(VehicleKind.FORMULA,        new Profile(50, 180, 0.05, 0.06, 0.06, 0.28, 0.10, 1.0));
        PROFILES.put(VehicleKind.LMP,            new Profile(45, 170, 0.06, 0.08, 0.05, 0.30, 0.11, 1.1));
        PROFILES.put(VehicleKind.GT3,            new Profile(40, 140, 0.10, 0.12, 0.05, 0.32, 0.12, 1.2));
        PROFILES.put(VehicleKind.GT4,            new Profile(38, 135, 0.12, 0.14, 0.05, 0.32, 0.12, 1.25));
        PROFILES.put(VehicleKind.DTM,            new Profile(42, 150, 0.09, 0.10, 0.05, 0.30, 0.12, 1.15));
        PROFILES.put(VehicleKind.TOURING,        new Profile(36, 125, 0.12, 0.16, 0.05, 0.34, 0.13, 1.30));
        PROFILES.put(VehicleKind.RALLY,          new Profile(34, 120, 0.15, 0.18, 0.04, 0.36, 0.14, 1.35));
        PROFILES.put(VehicleKind.KART,           new Profile(60, 220, 0.20, 0.20, 0.03, 0.26, 0.09, 0.9));
        PROFILES.put(VehicleKind.STREET,         new Profile(35, 120, 0.15, 0.18, 0.04, 0.34, 0.14, 1.35));
    }

    // Getter profilati (fallback ai default se kind ignoto)
    public static double profThrOscDps(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).thrOscDps; }
    public static double profSteerRevRate(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).steerRevRate; }
    public static double profAbsMuch(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).absMuch; }
    public static double profTcMuch(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).tcMuch; }
    public static double profTrailLow(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).trailLow; }
    public static double profTrailHigh(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).trailHigh; }
    public static double profCoastBad(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).coastBad; }
    public static double profExitGasDelay(VehicleKind k){ return PROFILES.getOrDefault(k, new Profile(THR_OSC_DPS, STEER_REV_RATE, ABS_MUCH, TC_MUCH, TRAIL_LOW, TRAIL_HIGH, COAST_BAD, EXIT_GAS_80_DELAY_S)).exitGasDelay; }
}
