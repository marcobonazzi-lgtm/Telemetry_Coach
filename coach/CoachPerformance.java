package org.simulator.coach;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.setup.setup_advisor.VehicleTraits;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.simulator.coach.CoachCore.*;

final class CoachPerformance {

    private CoachPerformance(){}

    /** Note per singolo giro (driving performance). */
    public static List<CoachCore.Note> generate(Lap lap){
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return Collections.emptyList();
        List<CoachCore.Note> out = new ArrayList<>();
        try {
            CoachCore.VehicleKind kind = detectVehicleKind(lap);

            // 1) Brake analysis (entry/trail)
            BrakeFeat br = brakeFeatures(lap);
            if (!Double.isNaN(br.peak) && !Double.isNaN(br.trailFrac) && !Double.isNaN(br.timeOnBrake)) {
                double low  = profTrailLow(kind);
                double high = profTrailHigh(kind);

                if (br.trailFrac < low && br.peak > 0.7) {
                    out.add(new CoachCore.Note(Priority.HIGH, Category.FRENI,
                            "Rilascio freno troppo brusco: allunga il trail-braking per aiutare la rotazione."));
                } else if (br.trailFrac > high) {
                    // NEW: Se è una Formula o un prototipo LMU, tolleriamo di più il freno pizzicato per l'aero
                    if (kind != VehicleKind.FORMULA && kind != VehicleKind.LMP && kind != VehicleKind.FORMULA_HYBRID) {
                        out.add(new CoachCore.Note(Priority.MEDIUM, Category.FRENI,
                                "Mantieni leggermente freno oltre l’apex: rilascia prima per non trascinare velocità."));
                    }
                }
            }

            // 2) Rotation (Derivata sterzo)
            SteerFeat st = steeringFeatures(lap);
            if (!Double.isNaN(st.revPerMin) && st.revPerMin > profSteerRevRate(kind)){
                out.add(new CoachCore.Note(Priority.MEDIUM, Category.GUIDA,
                        "Troppe correzioni di sterzo: pulisci la linea e ruota l’auto prima dell’apex."));
            }

            // 3) Apex speed / min speed vs laterale
            double minSpeed = minChannel(lap, Channel.SPEED);
            // Fallback per LMU se ACC_LAT manca ma c'è CG_ACCEL...
            double latGpeak = maxChannel(lap, Channel.ACC_LAT);
            if (Double.isNaN(latGpeak)) latGpeak = maxChannel(lap, Channel.CG_ACCEL_LATERAL);

            if (!Double.isNaN(minSpeed) && !Double.isNaN(latGpeak)){
                // Se vado piano E non carico le gomme lateralmente -> sono troppo cauto
                if (minSpeed < 50 && latGpeak < profLatGMin(kind) * 0.8) {
                    out.add(new CoachCore.Note(Priority.MEDIUM, Category.GUIDA,
                            "Velocità minima bassa senza alto carico laterale: prova ad entrare con più velocità."));
                }
            }

            // 4) Traction & slip (exit)
            SlipFeat sl = slipFeatures(lap);
            if (!Double.isNaN(sl.rearSlip) && sl.rearSlip > 0.18) {
                out.add(new CoachCore.Note(Priority.HIGH, Category.TRASM,
                        "Uscita in pattinamento: dosa il gas progressivo e ritarda leggermente il 100%."));
            }

            // 5) Throttle discipline
            ThrFeat th = throttleFeatures(lap);
            if (!Double.isNaN(th.oscDps) && th.oscDps > profThrOscDps(kind)){
                out.add(new CoachCore.Note(Priority.LOW, Category.TRASM,
                        "Oscillazioni gas frequenti: costruisci il carico con progressione pulita."));
            }

            // 6) Shift
            ShiftFeat sh = shiftFeatures(lap);
            if (!Double.isNaN(sh.overRevPct) && sh.overRevPct > 0.12){
                out.add(new CoachCore.Note(Priority.LOW, Category.TRASM,
                        "Over-rev in cambiata: anticipa leggermente lo shift."));
            }

        } catch (Throwable ignore){}
        return out;
    }

    /** Note aggregate di sessione */
    public static List<CoachCore.Note> generateSession(List<Lap> session){
        if (session == null || session.isEmpty()) return Collections.emptyList();
        List<CoachCore.Note> out = new ArrayList<>();
        try {
            CoachCore.VehicleKind kind = detectVehicleKind(session);

            // Consistenza
            double[] laps = session.stream().mapToDouble(Lap::lapTimeSafe).filter(v -> v>0 && !Double.isNaN(v)).toArray();
            if (laps.length >= 3){
                double mean = 0; for (double v: laps) mean += v; mean/=laps.length;
                double var = 0; for (double v: laps) var += (v-mean)*(v-mean); var/=laps.length;
                double std = Math.sqrt(var);
                if (std > 0.7){ // Soglia leggermente alzata per sessioni pubbliche
                    out.add(new CoachCore.Note(Priority.MEDIUM, Category.SESSIONE,
                            String.format(Locale.ITALIAN, "Variazione tempi alta (±%.1fs): lavora sulla ripetibilità.", std)));
                } else {
                    out.add(new CoachCore.Note(Priority.LOW, Category.SESSIONE, "Buona consistenza dei tempi lap-to-lap."));
                }
            }
        } catch (Throwable ignore){}
        return out;
    }

    // ================= Metrics =================
    static final class BrakeFeat { final double peak, trailFrac, timeOnBrake; BrakeFeat(double p,double t,double tob){peak=p;trailFrac=t;timeOnBrake=tob;} }
    static final class SteerFeat { final double revPerMin; SteerFeat(double r){revPerMin=r;} }
    static final class SlipFeat  { final double frontSlip, rearSlip; SlipFeat(double f,double r){frontSlip=f;rearSlip=r;} }
    static final class ThrFeat   { final double oscDps, exitDelay80; ThrFeat(double o,double e){oscDps=o;exitDelay80=e;} }
    static final class ShiftFeat { final double overRevPct; ShiftFeat(double o){overRevPct=o;} }

    static BrakeFeat brakeFeatures(Lap lap){
        double peak = maxChannel(lap, Channel.BRAKE_POS);
        int n=0, nb=0, nt=0;
        if (lap!=null && lap.samples!=null){
            for (Sample s: lap.samples){
                n++;
                double b = get(s, Channel.BRAKE_POS);
                if (b > 0.05){ nb++; if (b < 0.5) nt++; }
            }
        }
        double timeOnBrake = n>0 ? (double)nb/n : Double.NaN;
        double trailFrac = nb>0 ? (double)nt/nb : Double.NaN;
        return new BrakeFeat(peak, trailFrac, timeOnBrake);
    }

    static SteerFeat steeringFeatures(Lap lap){
        double prev = Double.NaN; int rev=0; int n=0;
        if (lap!=null && lap.samples!=null){
            for (Sample s: lap.samples){
                double a = get(s, Channel.STEER_ANGLE);
                if (!Double.isNaN(a)){
                    if (!Double.isNaN(prev)){
                        if (Math.signum(a) != Math.signum(prev) && Math.abs(a) > 2 && Math.abs(prev) > 2) rev++;
                    }
                    prev = a; n++;
                }
            }
        }
        double hz = 20.0; // Assunzione campionamento medio se Time non disponibile
        double seconds = n>0 ? n/hz : 0.0;
        double revPerMin = seconds>0 ? (rev / seconds) * 60.0 : Double.NaN;
        return new SteerFeat(revPerMin);
    }

    static SlipFeat slipFeatures(Lap lap){
        double f = timeAbove(lap, Channel.TIRE_SLIP_RATIO_FL, 0.15);
        double f2= timeAbove(lap, Channel.TIRE_SLIP_RATIO_FR, 0.15);
        double r = timeAbove(lap, Channel.TIRE_SLIP_RATIO_RL, 0.18);
        double r2= timeAbove(lap, Channel.TIRE_SLIP_RATIO_RR, 0.18);
        double front = mean(new double[]{f,f2});
        double rear  = mean(new double[]{r,r2});
        return new SlipFeat(front, rear);
    }

    static ThrFeat throttleFeatures(Lap lap){
        double prev = Double.NaN; double prevd = Double.NaN; int osc=0; int n=0;
        if (lap!=null && lap.samples!=null){
            for (Sample s: lap.samples){
                double t = get(s, Channel.THROTTLE);
                if (!Double.isNaN(t)){
                    if (!Double.isNaN(prev)){
                        double d = t - prev;
                        if (!Double.isNaN(prevd) && Math.signum(d) != Math.signum(prevd) && Math.abs(d) > 0.02) osc++;
                        prevd = d;
                    }
                    prev = t; n++;
                }
            }
        }
        double hz = 20.0;
        double seconds = n>0 ? n/hz : 0.0;
        double oscDps = seconds>0 ? osc/seconds : Double.NaN;
        return new ThrFeat(oscDps, 0);
    }

    static ShiftFeat shiftFeatures(Lap lap){
        double pct = 0.0; int n=0; int over=0;
        if (lap!=null && lap.samples!=null){
            double rpmMax = maxChannel(lap, Channel.ENGINE_RPM);
            for (Sample s: lap.samples){
                double rpm = get(s, Channel.ENGINE_RPM);
                double thr = get(s, Channel.THROTTLE);
                if (!Double.isNaN(rpm) && !Double.isNaN(thr)){
                    n++;
                    if (thr > 0.6 && rpmMax>0 && rpm > 0.98 * rpmMax) over++;
                }
            }
        }
        pct = n>0 ? (double)over / n : Double.NaN;
        return new ShiftFeat(pct);
    }

    // ====== low-level helpers ======
    static double get(Sample s, Channel ch){
        if (s==null || s.values()==null) return Double.NaN;
        Double v = s.values().get(ch);
        return (v==null || v.isNaN() || v.isInfinite()) ? Double.NaN : v.doubleValue();
    }
    static double maxChannel(Lap lap, Channel ch){
        double m = Double.NaN;
        if (lap==null || lap.samples==null) return m;
        for (Sample s: lap.samples){
            double v = get(s,ch);
            if (!Double.isNaN(v)) m = Double.isNaN(m) ? v : Math.max(m, v);
        }
        return m;
    }
    static double minChannel(Lap lap, Channel ch){
        double m = Double.NaN;
        if (lap==null || lap.samples==null) return m;
        for (Sample s: lap.samples){
            double v = get(s,ch);
            if (!Double.isNaN(v)) m = Double.isNaN(m) ? v : Math.min(m, v);
        }
        return m;
    }
    static double timeAbove(Lap lap, Channel ch, double thr){
        int n=0, k=0;
        if (lap==null || lap.samples==null) return Double.NaN;
        for (Sample s: lap.samples){
            double v = get(s,ch);
            if (!Double.isNaN(v)){ n++; if (v > thr) k++; }
        }
        return n>0 ? ((double)k)/n : Double.NaN;
    }
}