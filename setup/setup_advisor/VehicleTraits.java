package org.simulator.setup.setup_advisor;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.coach.CoachCore;

import java.util.List;

/**
 * Rileva in modo euristico i tratti del veicolo dalla telemetria disponibile:
 * - Category: FORMULA / PROTOTYPE / GT / ROAD / OTHER
 * - Drivetrain: RWD / FWD / AWD / UNKNOWN
 * - Powertrain: NA / TURBO / HYBRID / UNKNOWN
 *
 * Robusto a dati mancanti: degrada con fallback sensati.
 */
public final class VehicleTraits {

    public enum Category { FORMULA, PROTOTYPE, GT, ROAD, OTHER }
    public enum Drivetrain { RWD, FWD, AWD, UNKNOWN }
    public enum Powertrain { NA, TURBO, HYBRID, UNKNOWN }

    public final Category category;
    public final Drivetrain drivetrain;
    public final Powertrain powertrain;

    VehicleTraits(Category c, Drivetrain d, Powertrain p){
        this.category = c; this.drivetrain = d; this.powertrain = p;
    }

    public static VehicleTraits detect(List<Lap> laps){
        if (laps == null || laps.isEmpty()) {
            return new VehicleTraits(Category.OTHER, Drivetrain.UNKNOWN, Powertrain.UNKNOWN);
        }

        // ===== POWERTRAIN =====
        boolean ersSeen=false, ersActive=false, turboSeen=false, turboActive=false;
        double rpmP98 = Double.NaN; // quantile 98%
        double maxSpeed = 0.0;
        int nRPM=0;
        for (Lap l: laps){
            if (l==null || l.samples==null) continue;
            ersSeen |= CoachCore.has(l, Channel.ERS_IS_CHARGING) || CoachCore.has(l, Channel.KERS_DEPLOYED_ENERGY);
            if (ersSeen){
                double rec = CoachCore.fractionActive(l, Channel.ERS_IS_CHARGING);
                double use = CoachCore.fraction(l, s -> {
                    double e = CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY);
                    return !Double.isNaN(e) && e>0;
                });
                ersActive |= (rec>0.01 || use>0.01);
            }
            turboSeen |= CoachCore.has(l, Channel.TURBO_BOOST) || CoachCore.has(l, Channel.MAX_TURBO_BOOST);
            if (turboSeen){
                double q = CoachCore.fraction(l, s -> {
                    double tb = CoachCore.val(s, Channel.TURBO_BOOST);
                    return !Double.isNaN(tb) && tb > 0.15;
                });
                turboActive |= (q>0.02);
            }
            // rpm quantile
            double q98 = CoachCore.rpmQuantile(l, 0.98);
            if (!Double.isNaN(q98)){ rpmP98 = Double.isNaN(rpmP98) ? q98 : Math.max(rpmP98, q98); nRPM++; }

            // max speed
            for (Sample s: l.samples){
                double v = CoachCore.val(s, Channel.SPEED);
                if (!Double.isNaN(v)) maxSpeed = Math.max(maxSpeed, v);
            }
        }
        Powertrain pt = Powertrain.UNKNOWN;
        if (ersActive) pt = Powertrain.HYBRID;
        else if (turboActive) pt = Powertrain.TURBO;
        else pt = Powertrain.NA;

        // ===== CATEGORY =====
        boolean drsAvail=false;
        for (Lap l: laps){
            drsAvail |= CoachCore.fractionActive(l, Channel.DRS_AVAILABLE) > 0.01;
            if (drsAvail) break;
        }
        Category cat;
        if (drsAvail || (!Double.isNaN(rpmP98) && rpmP98 >= 14000)) {
            cat = Category.FORMULA;
        } else if (pt == Powertrain.HYBRID) {
            // in molti sim, l'ibrido "vero" è tipico di LMP/Hypercar
            cat = Category.PROTOTYPE;
        } else {
            // distingue GT vs ROAD con max speed e ABS/TC presence
            boolean absCap=false, tcCap=false;
            for (Lap l: laps){
                absCap |= CoachCore.has(l, Channel.ABS_ENABLED) || CoachCore.has(l, Channel.ABS_ACTIVE);
                tcCap  |= CoachCore.has(l, Channel.TC_ENABLED)  || CoachCore.has(l, Channel.TC_ACTIVE);
            }
            if (absCap || tcCap) {
                // se molto veloci → GT, altrimenti ROAD
                cat = (maxSpeed >= 260) ? Category.GT : Category.ROAD;
            } else {
                cat = Category.GT; // fallback sportivo
            }
        }

        // ===== DRIVETRAIN ===== (da slip in trazione dritta)
        double frontHigh=0, rearHigh=0, base=0;
        for (Lap l: laps){
            if (l==null || l.samples==null) continue;
            for (int i=0;i<l.samples.size();i++){
                Sample s = l.samples.get(i);
                double thr = CoachCore.val(s, Channel.THROTTLE);
                double st  = CoachCore.val(s, Channel.STEER_ANGLE);
                if (Double.isNaN(thr) || Double.isNaN(st)) continue;
                boolean accelStraight = thr > 70 && Math.abs(st) < 5;
                if (!accelStraight) continue;

                double fl = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_FL);
                double fr = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_FR);
                double rl = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_RL);
                double rr = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_RR);
                boolean fH = (!Double.isNaN(fl) && Math.abs(fl)>0.12) || (!Double.isNaN(fr) && Math.abs(fr)>0.12);
                boolean rH = (!Double.isNaN(rl) && Math.abs(rl)>0.12) || (!Double.isNaN(rr) && Math.abs(rr)>0.12);
                if (fH) frontHigh++; if (rH) rearHigh++; base++;
            }
        }
        Drivetrain dt = Drivetrain.UNKNOWN;
        if (base >= 20) {
            if (rearHigh > frontHigh * 1.3) dt = Drivetrain.RWD;
            else if (frontHigh > rearHigh * 1.3) dt = Drivetrain.FWD;
            else dt = Drivetrain.AWD;
        }

        return new VehicleTraits(cat, dt, pt);
    }
}
