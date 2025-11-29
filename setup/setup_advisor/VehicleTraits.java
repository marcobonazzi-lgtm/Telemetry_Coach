package org.simulator.setup.setup_advisor;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.coach.CoachCore;

import java.util.List;

/**
 * Rileva i tratti del veicolo e fornisce i TARGET FISICI (Pressioni, Temperature).
 * Ottimizzato per Assetto Corsa / LMU / ACC.
 * * NOTA SUI FRENI:
 * - ROAD (Acciaio stradale): Fade > 500°C. Target basso.
 * - GT (Acciaio Racing): Finestra ottimale 300-600°C (ACC).
 * - FORMULA/PROTO (Carbonio): Non frenano sotto i 400°C. Picchi > 800°C.
 */
public final class VehicleTraits {

    public enum Category { FORMULA, PROTOTYPE, GT, ROAD, OTHER }
    public enum Drivetrain { RWD, FWD, AWD, UNKNOWN }
    public enum Powertrain { NA, TURBO, HYBRID, UNKNOWN }

    public final Category category;
    public final Drivetrain drivetrain;
    public final Powertrain powertrain;
    public final TargetWindow targets;

    public VehicleTraits(Category c, Drivetrain d, Powertrain p){
        this.category = c;
        this.drivetrain = d;
        this.powertrain = p;
        this.targets = getTargetsFor(c);
    }

    // --- DATABASE TARGET FISICI ---
    public record TargetWindow(
            double psiMin, double psiMax,             // PSI a caldo (Hot Pressure)
            double tempCoreMin, double tempCoreMax,   // °C Gomma Core (Internal Temp)
            double tempBrakeMin, double tempBrakeMax  // °C Dischi Freno (Brake Disc)
    ) {}

    private static TargetWindow getTargetsFor(Category c) {
        return switch (c) {
            // ACC GT3/GT4: Finestra verde freni 300-600. Gomme 26-27 psi.
            case GT -> new TargetWindow(26.0, 27.8, 75.0, 100.0, 300.0, 650.0);

            // Carbon Brakes: Hanno bisogno di molto calore. Sotto i 400°C "vetrificano" o non frenano.
            case FORMULA -> new TargetWindow(21.5, 24.5, 90.0, 120.0, 400.0, 1000.0);

            // LMU Hypercar: Simili alle Formula ma pesano di più, temperature molto alte.
            case PROTOTYPE -> new TargetWindow(26.0, 29.0, 80.0, 110.0, 400.0, 900.0);

            // Auto Stradali (AC): Freni in acciaio standard/sportivo. Fade sopra i 500°C.
            case ROAD -> new TargetWindow(30.0, 38.0, 60.0, 90.0, 100.0, 480.0);

            // Fallback generico
            default -> new TargetWindow(25.0, 28.0, 70.0, 100.0, 150.0, 600.0);
        };
    }

    public static VehicleTraits detect(List<Lap> laps){
        if (laps == null || laps.isEmpty()) {
            return new VehicleTraits(Category.OTHER, Drivetrain.UNKNOWN, Powertrain.UNKNOWN);
        }

        // ===== 1. POWERTRAIN DETECTION =====
        boolean ersSeen=false, ersActive=false, turboSeen=false, turboActive=false;
        double rpmP98 = Double.NaN;
        double maxSpeed = 0.0;

        for (Lap l: laps){
            if (l==null || l.samples==null) continue;

            // ERS check
            ersSeen |= CoachCore.has(l, Channel.ERS_IS_CHARGING) || CoachCore.has(l, Channel.KERS_DEPLOYED_ENERGY);
            if (ersSeen){
                double rec = CoachCore.fractionActive(l, Channel.ERS_IS_CHARGING);
                double use = CoachCore.fraction(l, s -> {
                    double e = CoachCore.val(s, Channel.KERS_DEPLOYED_ENERGY);
                    return !Double.isNaN(e) && e > 0;
                });
                ersActive |= (rec > 0.01 || use > 0.01);
            }

            // Turbo Check
            turboSeen |= CoachCore.has(l, Channel.TURBO_BOOST) || CoachCore.has(l, Channel.MAX_TURBO_BOOST);
            if (turboSeen){
                double q = CoachCore.fraction(l, s -> {
                    double tb = CoachCore.val(s, Channel.TURBO_BOOST);
                    return !Double.isNaN(tb) && tb > 0.15; // Soglia minima bar
                });
                turboActive |= (q > 0.02);
            }

            // RPM & Speed Stats
            double q98 = CoachCore.rpmQuantile(l, 0.98);
            if (!Double.isNaN(q98)) rpmP98 = Double.isNaN(rpmP98) ? q98 : Math.max(rpmP98, q98);

            for (Sample s: l.samples){
                double v = CoachCore.val(s, Channel.SPEED);
                if (!Double.isNaN(v)) maxSpeed = Math.max(maxSpeed, v);
            }
        }

        Powertrain pt = Powertrain.UNKNOWN;
        if (ersActive) pt = Powertrain.HYBRID;
        else if (turboActive) pt = Powertrain.TURBO;
        else pt = Powertrain.NA;

        // ===== 2. CATEGORY DETECTION =====
        boolean drsAvail = false;
        boolean hasDownforce = false; // Euristica basata su velocità in curva se avessimo G-Lat

        for (Lap l: laps){
            drsAvail |= CoachCore.fractionActive(l, Channel.DRS_AVAILABLE) > 0.01;
            if (drsAvail) break;
        }

        Category cat;

        // Logica Gerarchica
        if (drsAvail || (!Double.isNaN(rpmP98) && rpmP98 >= 12500)) {
            // RPM altissimi o DRS indicano quasi sempre Formula o High-Tier Prototype
            cat = Category.FORMULA;
        } else if (pt == Powertrain.HYBRID && maxSpeed > 270) {
            // Hybrid veloce ma rpm < 12.5k (es. LMDh / Hypercar)
            cat = Category.PROTOTYPE;
        } else {
            // Distinzione GT vs Road
            boolean absCap=false, tcCap=false;
            for (Lap l: laps){
                absCap |= CoachCore.has(l, Channel.ABS_ENABLED) || CoachCore.has(l, Channel.ABS_ACTIVE);
                tcCap  |= CoachCore.has(l, Channel.TC_ENABLED)  || CoachCore.has(l, Channel.TC_ACTIVE);
            }

            // Euristica: Se va molto forte (260+) è probabile sia GT3/GTE.
            // Se ha ABS/TC ma va piano, è stradale.
            // Se è molto rigida (non abbiamo susp travel qui) -> GT.
            if (maxSpeed >= 260) {
                cat = Category.GT;
            } else if (maxSpeed < 220 && (absCap || tcCap)) {
                // Bassa velocità + controlli elettronici = Road Car
                cat = Category.ROAD;
            } else {
                // Zona grigia (GT4, Cup Cars, Supercars stradali)
                // Defaultiamo a GT per sicurezza sui freni (meglio target alti che bassi)
                cat = Category.GT;
            }
        }

        // ===== 3. DRIVETRAIN DETECTION =====
        // (Invariata, la logica dello slip ratio è buona)
        double frontHigh=0, rearHigh=0, base=0;
        for (Lap l: laps){
            if (l==null || l.samples==null) continue;
            for (int i=0; i<l.samples.size(); i+=5){ // Sampling rate optimization (ogni 5 sample)
                Sample s = l.samples.get(i);
                double thr = CoachCore.val(s, Channel.THROTTLE);
                double st  = CoachCore.val(s, Channel.STEER_ANGLE);

                if (Double.isNaN(thr) || Double.isNaN(st)) continue;

                // Cerchiamo trazione pura in rettilineo
                boolean accelStraight = thr > 85 && Math.abs(st) < 4;
                if (!accelStraight) continue;

                double fl = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_FL);
                double fr = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_FR);
                double rl = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_RL);
                double rr = CoachCore.val(s, Channel.TIRE_SLIP_RATIO_RR);

                // Soglia slip significativa
                boolean fH = (!Double.isNaN(fl) && Math.abs(fl) > 0.05) || (!Double.isNaN(fr) && Math.abs(fr) > 0.05);
                boolean rH = (!Double.isNaN(rl) && Math.abs(rl) > 0.05) || (!Double.isNaN(rr) && Math.abs(rr) > 0.05);

                if (fH) frontHigh++;
                if (rH) rearHigh++;
                base++;
            }
        }

        Drivetrain dt = Drivetrain.UNKNOWN;
        if (base >= 10) { // Bastano pochi campioni chiari
            if (rearHigh > frontHigh * 2.0) dt = Drivetrain.RWD;
            else if (frontHigh > rearHigh * 2.0) dt = Drivetrain.FWD;
            else dt = Drivetrain.AWD;
        }

        // Fallback basato sulla categoria se la telemetria slip non è chiara
        if (dt == Drivetrain.UNKNOWN) {
            if (cat == Category.FORMULA || cat == Category.PROTOTYPE) dt = Drivetrain.RWD;
            else if (cat == Category.GT) dt = Drivetrain.RWD; // Maggior parte GT3 sono RWD
        }

        return new VehicleTraits(cat, dt, pt);
    }
}