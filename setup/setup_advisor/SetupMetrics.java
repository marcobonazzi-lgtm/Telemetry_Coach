package org.simulator.setup.setup_advisor;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.coach.CoachCore;

import java.util.Locale;

final class SetupMetrics {

    private SetupMetrics(){}

    static SetupAdvisor.StyleMetrics compute(Lap lap){
        if (lap == null || lap.samples == null || lap.samples.isEmpty()){
            return new SetupAdvisor.StyleMetrics(0,0,false,0,0,0,0,0,Double.NaN,Double.NaN,"n/d");
        }

        // ===== metriche guida di base =====
        double thrOsc     = CoachCore.throttleOscillationPct(lap, CoachCore.THR_OSC_DPS);
        double steerHarsh = CoachCore.steeringHarshPct(lap, CoachCore.STEER_REV_RATE);
        boolean over      = CoachCore.suspectedOversteer(lap);

        double stomp   = brakeStompPct(lap);
        double overlap = throttleBrakeOverlap(lap);
        double coast   = coastingPct(lap);
        double revMin  = steeringReversalsPerMin(lap);
        double clip    = CoachCore.ffbClipFraction(lap);

        double tyres   = avg(CoachCore.avgTyreTemps(lap));
        double brakes  = avg(CoachCore.avgBrakeTemps(lap));

        // ===== altri segnali (vanno solo nelle note) =====
        double slipAngF = meanPair(lap, Channel.TIRE_SLIP_ANGLE_FL, Channel.TIRE_SLIP_ANGLE_FR);
        double slipAngR = meanPair(lap, Channel.TIRE_SLIP_ANGLE_RL, Channel.TIRE_SLIP_ANGLE_RR);
        double slipRatF = meanPair(lap, Channel.TIRE_SLIP_RATIO_FL, Channel.TIRE_SLIP_RATIO_FR);
        double slipRatR = meanPair(lap, Channel.TIRE_SLIP_RATIO_RL, Channel.TIRE_SLIP_RATIO_RR);

        double psiFL = CoachCore.meanChannel(lap, Channel.TIRE_PRESSURE_FL);
        double psiFR = CoachCore.meanChannel(lap, Channel.TIRE_PRESSURE_FR);
        double psiRL = CoachCore.meanChannel(lap, Channel.TIRE_PRESSURE_RL);
        double psiRR = CoachCore.meanChannel(lap, Channel.TIRE_PRESSURE_RR);

        double kerbPct = CoachCore.seatKerbPct(lap);

        double brakeBias = CoachCore.firstNonNaN(lap, Channel.BRAKE_BIAS);
        double engBrake  = CoachCore.firstNonNaN(lap, Channel.ENGINE_BRAKE_SETTING);

        double rideF = meanPair(lap, Channel.RIDE_HEIGHT_FL, Channel.RIDE_HEIGHT_FR);
        double rideR = meanPair(lap, Channel.RIDE_HEIGHT_RL, Channel.RIDE_HEIGHT_RR);

        double drsAvail = CoachCore.has(lap, Channel.DRS_AVAILABLE) ? CoachCore.fractionActive(lap, Channel.DRS_AVAILABLE) : 0.0;
        double drsUsed  = CoachCore.has(lap, Channel.DRS_ACTIVE)    ? CoachCore.fractionActive(lap, Channel.DRS_ACTIVE)    : 0.0;
        double ersRec   = CoachCore.has(lap, Channel.ERS_IS_CHARGING) ? CoachCore.fractionActive(lap, Channel.ERS_IS_CHARGING) : 0.0;
        double ersUse   = CoachCore.fraction(lap, s -> {
            double e = CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY);
            return !Double.isNaN(e) && e > 0;
        });

        double roadT = CoachCore.firstNonNaN(lap, Channel.ROAD_TEMP);
        double airT  = CoachCore.firstNonNaN(lap, Channel.AIR_TEMP);
        double grip  = CoachCore.firstNonNaN(lap, Channel.SURFACE_GRIP);
        double wind  = CoachCore.firstNonNaN(lap, Channel.WIND_SPEED);

        String notes = String.format(Locale.ITALIAN,
                "thrOsc=%.0f%%, steer=%.0f%%, stomp=%.0f%%, overlap=%.0f%%, coast=%.0f%%, rev=%.1f/min, clip=%.0f%% | " +
                        "tyre=%.1f°C, brake=%.0f°C | slipA(F/R)=%s/%s°, slipR(F/R)=%s/%s | psi FL/FR/RL/RR=%s/%s/%s/%s | " +
                        "kerb=%s, bias=%s, EB=%s | ride F/R=%s/%s mm | DRS used/avail=%s/%s, ERS use/rec=%s/%s | " +
                        "track=%s°C, air=%s°C, grip=%s, wind=%s m/s",
                100*thrOsc, 100*steerHarsh, 100*stomp, 100*overlap, 100*coast, revMin, 100*clip,
                tyres, brakes,
                safe1(slipAngF), safe1(slipAngR), safe2(slipRatF), safe2(slipRatR),
                safe1(psiFL), safe1(psiFR), safe1(psiRL), safe1(psiRR),
                pctStr(kerbPct), safe1(brakeBias), safe1(engBrake),
                safe1(rideF), safe1(rideR),
                pctStr(drsUsed), pctStr(drsAvail), pctStr(ersUse), pctStr(ersRec),
                safe0(roadT), safe0(airT), safe2(grip), safe1(wind)
        );

        return new SetupAdvisor.StyleMetrics(thrOsc, steerHarsh, over, stomp, overlap, coast, revMin, clip, tyres, brakes, notes);
    }

    // ================== metriche “classiche” (compat) ==================
    static double brakeStompPct(Lap lap){
        final double TH = 0.65, WIN_S = 0.20;
        double tPrev = Double.NaN, bPrev = Double.NaN;
        int events = 0, base = 0;
        for (Sample s : lap.samples){
            Double b = s.values().getOrDefault(Channel.BRAKE, Double.NaN);
            Double t = s.values().getOrDefault(Channel.TIME,  Double.NaN);
            if (b==null || b.isNaN()) continue;
            if (Double.isNaN(t)) {
                if (!Double.isNaN(bPrev)) {
                    if (b - bPrev > TH) events++;
                    base++;
                }
            } else {
                if (!Double.isNaN(tPrev) && (t - tPrev) <= WIN_S) {
                    if (!Double.isNaN(bPrev) && b - bPrev > TH) events++;
                }
                base++;
                tPrev = t;
            }
            bPrev = b;
        }
        return base>0 ? (double)events/base : 0.0;
    }

    static double throttleBrakeOverlap(Lap lap){
        int n=0, k=0;
        for (Sample s: lap.samples){
            Double th = s.values().getOrDefault(Channel.THROTTLE, Double.NaN);
            Double br = s.values().getOrDefault(Channel.BRAKE,    Double.NaN);
            if (th==null || br==null || th.isNaN() || br.isNaN()) continue;
            n++; if (th>0.06 && br>0.06) k++;
        }
        return n>0 ? (double)k/n : 0.0;
    }

    static double coastingPct(Lap lap){
        int n=0, k=0;
        for (Sample s: lap.samples){
            Double th = s.values().getOrDefault(Channel.THROTTLE, Double.NaN);
            Double br = s.values().getOrDefault(Channel.BRAKE,    Double.NaN);
            Double sp = s.values().getOrDefault(Channel.SPEED,    Double.NaN);
            if (th==null || br==null || th.isNaN() || br.isNaN()) continue;
            if (sp!=null && !sp.isNaN() && sp < 10) continue; // esclude pit/partenze
            n++; if (th<0.03 && br<0.03) k++;
        }
        return n>0 ? (double)k/n : 0.0;
    }

    static double steeringReversalsPerMin(Lap lap){
        Double prev = null;
        int reversals = 0;
        double t0 = first(lap, Channel.TIME);
        double t1 = last(lap, Channel.TIME);
        for (Sample s: lap.samples){
            Double st = s.values().getOrDefault(Channel.STEER_ANGLE, Double.NaN);
            if (st==null || st.isNaN()) continue;
            if (prev != null){
                // cross-zero con isteresi minima per evitare rumore
                if (Math.signum(prev) != Math.signum(st) && Math.abs(prev - st) > 8.0) reversals++;
            }
            prev = st;
        }
        double durMin = (t1>t0) ? (t1 - t0)/60.0 : Math.max(1.0, lap.samples.size()/60.0);
        return reversals / Math.max(0.5, durMin);
    }

    // ================== helper locali ==================
    static double avg(double[] a){
        if (a==null || a.length==0) return Double.NaN;
        double s=0; int n=0; for(double v: a){ if(!Double.isNaN(v)){ s+=v; n++; } }
        return n>0? s/n : Double.NaN;
    }
    static double first(Lap lap, Channel ch){
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) return v;
        }
        return Double.NaN;
    }
    static double last(Lap lap, Channel ch){
        double out = Double.NaN;
        for (Sample s: lap.samples){
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v!=null && !v.isNaN()) out = v;
        }
        return out;
    }

    /** media di due canali sullo stesso asse (NaN-safe). */
    private static double meanPair(Lap lap, Channel a, Channel b){
        double va = CoachCore.meanChannel(lap, a);
        double vb = CoachCore.meanChannel(lap, b);
        if (Double.isNaN(va) && Double.isNaN(vb)) return Double.NaN;
        if (Double.isNaN(va)) return vb;
        if (Double.isNaN(vb)) return va;
        return (va+vb)/2.0;
    }

    // ----- formattazioni safe per notes -----
    private static String safe0(double d){ return Double.isNaN(d) ? "--" : String.format(Locale.ITALIAN, "%.0f", d); }
    private static String safe1(double d){ return Double.isNaN(d) ? "--" : String.format(Locale.ITALIAN, "%.1f", d); }
    private static String safe2(double d){ return Double.isNaN(d) ? "--" : String.format(Locale.ITALIAN, "%.2f", d); }
    private static String pctStr(double p){
        if (Double.isNaN(p)) return "--";
        return String.format(Locale.ITALIAN, "%.0f%%", 100.0*p);
    }
}
