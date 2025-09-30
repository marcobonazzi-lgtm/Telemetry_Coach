package org.simulator.analisi_base.session_analysis;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** Helper numerici per analisi di sessione (estesi per coprire tutte le voci dei lap stats). */
final class SessionMath {

    private SessionMath() {}

    // ===================== METRICHE "STORICHE" (lasciate intatte) =====================

    /** media delle velocità valide in un giro */
    static double avgSpeedLap(Lap lap) {
        return avgLapChannel(lap, Channel.SPEED);
    }

    /** media degli RPM validi in un giro */
    static double avgRpmLap(Lap lap) {
        return avgLapChannel(lap, Channel.ENGINE_RPM);
    }

    /** carburante usato in un giro = first(FUEL_LEVEL) - last(FUEL_LEVEL) (>=0) */
    static double fuelUsedLap(Lap lap) {
        Double first = null, last = null;
        for (Sample s : lap.samples) {
            Double v = s.values().get(Channel.FUEL_LEVEL);
            if (v == null || v.isNaN()) continue;
            if (first == null) first = v;
            last = v;
        }
        if (first == null || last == null) return Double.NaN;
        double used = first - last;
        return used >= 0 ? used : Double.NaN;
    }

    /** media (semplice) delle temperature Middle per la ruota indicata (FL/FR/RL/RR) su tutti i giri */
    static double avgTyreTemp(List<Lap> laps, String wheel) {
        Channel ch = switch (wheel == null ? "" : wheel.toUpperCase()) {
            case "FL" -> Channel.TIRE_TEMP_MIDDLE_FL;
            case "FR" -> Channel.TIRE_TEMP_MIDDLE_FR;
            case "RL" -> Channel.TIRE_TEMP_MIDDLE_RL;
            case "RR" -> Channel.TIRE_TEMP_MIDDLE_RR;
            default -> null;
        };
        if (ch == null || laps == null || laps.isEmpty()) return Double.NaN;
        return avgOverLaps(laps, ch);
    }

    /** media della funzione f(lap) ignorando NaN */
    static double avgOf(List<Lap> laps, ToDoubleFunction<Lap> f) {
        double sum = 0; int n = 0;
        if (laps == null) return Double.NaN;
        for (Lap lap : laps) {
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            double v = f.applyAsDouble(lap);
            if (!Double.isNaN(v)) { sum += v; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    // ===================== NUOVI HELPER GENERICI (SESSIONE) =====================

    /** Media di un canale sui campioni di un giro (helper interno). */
    private static double avgLapChannel(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return Double.NaN;
        double sum = 0; int n = 0;
        for (Sample s : lap.samples) {
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v != null && !v.isNaN() && !v.isInfinite()) { sum += v; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    /** Max di un canale sui campioni di un giro. */
    private static double maxLapChannel(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return Double.NaN;
        double mx = Double.NEGATIVE_INFINITY; boolean any = false;
        for (Sample s : lap.samples) {
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v != null && !v.isNaN() && !v.isInfinite()) { mx = Math.max(mx, v); any = true; }
        }
        return any ? mx : Double.NaN;
    }

    /** Min di un canale sui campioni di un giro. */
    private static double minLapChannel(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return Double.NaN;
        double mn = Double.POSITIVE_INFINITY; boolean any = false;
        for (Sample s : lap.samples) {
            Double v = s.values().getOrDefault(ch, Double.NaN);
            if (v != null && !v.isNaN() && !v.isInfinite()) { mn = Math.min(mn, v); any = true; }
        }
        return any ? mn : Double.NaN;
    }

    /** Media "su sessione" di un canale = media delle medie per giro (ignora NaN). */
    static double avgOverLaps(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double sum = 0; int n = 0;
        for (Lap lap : laps) {
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            if (Boolean.TRUE.equals(lap.isInvalid())) continue;
            double m = avgLapChannel(lap, ch);
            if (!Double.isNaN(m)) { sum += m; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    /** Max "su sessione" di un canale = max dei max per giro. */
    static double maxOverLaps(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double mx = Double.NEGATIVE_INFINITY; boolean any = false;
        for (Lap lap : laps) {
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            if (Boolean.TRUE.equals(lap.isInvalid())) continue;
            double v = maxLapChannel(lap, ch);
            if (!Double.isNaN(v)) { mx = Math.max(mx, v); any = true; }
        }
        return any ? mx : Double.NaN;
    }

    /** Min "su sessione" di un canale = min dei min per giro. */
    static double minOverLaps(List<Lap> laps, Channel ch) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double mn = Double.POSITIVE_INFINITY; boolean any = false;
        for (Lap lap : laps) {
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            if (Boolean.TRUE.equals(lap.isInvalid())) continue;
            double v = minLapChannel(lap, ch);
            if (!Double.isNaN(v)) { mn = Math.min(mn, v); any = true; }
        }
        return any ? mn : Double.NaN;
    }

    /**
     * Percentuale media (su sessione) in cui un canale "booleano" è attivo (> soglia) — es. TC/ABS/DRS.
     * Calcolata come media delle percentuali per giro.
     */
    static double fractionActiveOverLaps(List<Lap> laps, Channel ch, double threshold) {
        if (laps == null || laps.isEmpty()) return Double.NaN;
        double sumPct = 0; int nLaps = 0;
        for (Lap lap : laps) {
            if (lap == null || lap.samples == null || lap.samples.isEmpty()) continue;
            if (Boolean.TRUE.equals(lap.isInvalid())) continue;
            int on = 0, tot = 0;
            for (Sample s : lap.samples) {
                Double v = s.values().getOrDefault(ch, Double.NaN);
                if (v == null || v.isNaN() || v.isInfinite()) continue;
                tot++;
                if (v > threshold) on++;
            }
            if (tot > 0) { sumPct += (on * 1.0 / tot); nLaps++; }
        }
        return nLaps > 0 ? (sumPct / nLaps) : Double.NaN;
    }

    // ===================== CONVENIENCE PER GRUPPI (usabili da Session UI) =====================

    // --- Gomme
    static double avgTyrePressureFL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_PRESSURE_FL); }
    static double avgTyrePressureFR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_PRESSURE_FR); }
    static double avgTyrePressureRL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_PRESSURE_RL); }
    static double avgTyrePressureRR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_PRESSURE_RR); }

    static double avgTyreTempInnerFL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_INNER_FL); }
    static double avgTyreTempMiddleFL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_TEMP_MIDDLE_FL); }
    static double avgTyreTempOuterFL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_OUTER_FL); }
    static double avgTyreTempInnerFR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_INNER_FR); }
    static double avgTyreTempMiddleFR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_TEMP_MIDDLE_FR); }
    static double avgTyreTempOuterFR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_OUTER_FR); }
    static double avgTyreTempInnerRL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_INNER_RL); }
    static double avgTyreTempMiddleRL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_TEMP_MIDDLE_RL); }
    static double avgTyreTempOuterRL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_OUTER_RL); }
    static double avgTyreTempInnerRR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_INNER_RR); }
    static double avgTyreTempMiddleRR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_TEMP_MIDDLE_RR); }
    static double avgTyreTempOuterRR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_TEMP_OUTER_RR); }

    static double avgTyreLoadFL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_LOAD_FL); }
    static double avgTyreLoadFR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_LOAD_FR); }
    static double avgTyreLoadRL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_LOAD_RL); }
    static double avgTyreLoadRR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_LOAD_RR); }

    static double avgTyreGripFL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_RUBBER_GRIP_FL); }
    static double avgTyreGripFR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_RUBBER_GRIP_FR); }
    static double avgTyreGripRL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_RUBBER_GRIP_RL); }
    static double avgTyreGripRR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_RUBBER_GRIP_RR); }

    static double avgTyreDirtFL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_DIRT_LEVEL_FL); }
    static double avgTyreDirtFR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_DIRT_LEVEL_FR); }
    static double avgTyreDirtRL(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_DIRT_LEVEL_RL); }
    static double avgTyreDirtRR(List<Lap> laps) { return avgOverLaps(laps, Channel.TIRE_DIRT_LEVEL_RR); }

    static double avgSlipAngleFL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_ANGLE_FL); }
    static double avgSlipAngleFR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_ANGLE_FR); }
    static double avgSlipAngleRL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_ANGLE_RL); }
    static double avgSlipAngleRR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_ANGLE_RR); }
    static double avgSlipRatioFL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_RATIO_FL); }
    static double avgSlipRatioFR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_RATIO_FR); }
    static double avgSlipRatioRL(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_RATIO_RL); }
    static double avgSlipRatioRR(List<Lap> laps)  { return avgOverLaps(laps, Channel.TIRE_SLIP_RATIO_RR); }

    static double avgWheelAngSpeedFL(List<Lap> laps) { return avgOverLaps(laps, Channel.WHEEL_ANGULAR_SPEED_FL); }
    static double avgWheelAngSpeedFR(List<Lap> laps) { return avgOverLaps(laps, Channel.WHEEL_ANGULAR_SPEED_FR); }
    static double avgWheelAngSpeedRL(List<Lap> laps) { return avgOverLaps(laps, Channel.WHEEL_ANGULAR_SPEED_RL); }
    static double avgWheelAngSpeedRR(List<Lap> laps) { return avgOverLaps(laps, Channel.WHEEL_ANGULAR_SPEED_RR); }

    // --- Freni
    static double avgBrakeTempFL(List<Lap> laps) { return avgOverLaps(laps, Channel.BRAKE_TEMP_FL); }
    static double avgBrakeTempFR(List<Lap> laps) { return avgOverLaps(laps, Channel.BRAKE_TEMP_FR); }
    static double avgBrakeTempRL(List<Lap> laps) { return avgOverLaps(laps, Channel.BRAKE_TEMP_RL); }
    static double avgBrakeTempRR(List<Lap> laps) { return avgOverLaps(laps, Channel.BRAKE_TEMP_RR); }

    // --- Sospensioni / altezze
    static double avgRideHeightFL(List<Lap> laps) { return avgOverLaps(laps, Channel.RIDE_HEIGHT_FL); }
    static double avgRideHeightFR(List<Lap> laps) { return avgOverLaps(laps, Channel.RIDE_HEIGHT_FR); }
    static double avgRideHeightRL(List<Lap> laps) { return avgOverLaps(laps, Channel.RIDE_HEIGHT_RL); }
    static double avgRideHeightRR(List<Lap> laps) { return avgOverLaps(laps, Channel.RIDE_HEIGHT_RR); }

    static double avgSuspTravelFL(List<Lap> laps) { return avgOverLaps(laps, Channel.SUSP_TRAVEL_FL); }
    static double avgSuspTravelFR(List<Lap> laps) { return avgOverLaps(laps, Channel.SUSP_TRAVEL_FR); }
    static double avgSuspTravelRL(List<Lap> laps) { return avgOverLaps(laps, Channel.SUSP_TRAVEL_RL); }
    static double avgSuspTravelRR(List<Lap> laps) { return avgOverLaps(laps, Channel.SUSP_TRAVEL_RR); }

    static double maxSuspTravelFL(List<Lap> laps) { return maxOverLaps(laps, Channel.MAX_SUS_TRAVEL_FL); }
    static double maxSuspTravelFR(List<Lap> laps) { return maxOverLaps(laps, Channel.MAX_SUS_TRAVEL_FR); }
    static double maxSuspTravelRL(List<Lap> laps) { return maxOverLaps(laps, Channel.MAX_SUS_TRAVEL_RL); }
    static double maxSuspTravelRR(List<Lap> laps) { return maxOverLaps(laps, Channel.MAX_SUS_TRAVEL_RR); }

    // --- Assetto & dinamica
    static double avgBrakeBias(List<Lap> laps) { return avgOverLaps(laps, Channel.BRAKE_BIAS); }
    static double avgSteerAngle(List<Lap> laps) { return avgOverLaps(laps, Channel.STEER_ANGLE); }
    static double avgPitchAngle(List<Lap> laps) { return avgOverLaps(laps, Channel.CHASSIS_PITCH_ANGLE); }
    static double avgRollAngle(List<Lap> laps)  { return avgOverLaps(laps, Channel.CHASSIS_ROLL_ANGLE); }
    static double avgYawRate(List<Lap> laps)    { return avgOverLaps(laps, Channel.CHASSIS_YAW_RATE); }
    static double avgAccelLat(List<Lap> laps)   { return avgOverLaps(laps, Channel.CG_ACCEL_LATERAL); }
    static double avgAccelLong(List<Lap> laps)  { return avgOverLaps(laps, Channel.CG_ACCEL_LONGITUDINAL); }
    static double avgAccelVert(List<Lap> laps)  { return avgOverLaps(laps, Channel.CG_ACCEL_VERTICAL); }

    // --- Elettronica / aiuti (percentuali ON)
    static double pctTcActive(List<Lap> laps)  { return fractionActiveOverLaps(laps, Channel.TC_ACTIVE, 0) * 100.0; }
    static double pctAbsActive(List<Lap> laps) { return fractionActiveOverLaps(laps, Channel.ABS_ACTIVE, 0) * 100.0; }
    static double pctDrsActive(List<Lap> laps) { return fractionActiveOverLaps(laps, Channel.DRS_ACTIVE, 0) * 100.0; }
    static double pctInPit(List<Lap> laps)     { return fractionActiveOverLaps(laps, Channel.IN_PIT, 0) * 100.0; }

    // --- Meteo & pista
    static double avgAirTemp(List<Lap> laps)  { return avgOverLaps(laps, Channel.AIR_TEMP); }
    static double avgRoadTemp(List<Lap> laps) { return avgOverLaps(laps, Channel.ROAD_TEMP); }
    static double avgWindSpeed(List<Lap> laps){ return avgOverLaps(laps, Channel.WIND_SPEED); }
    static double avgSurfaceGrip(List<Lap> laps){ return avgOverLaps(laps, Channel.SURFACE_GRIP); }

    // --- Utility
    static double meanIgnoringNaN(double... v) {
        double s = 0; int n = 0;
        for (double x : v) { if (!Double.isNaN(x) && !Double.isInfinite(x)) { s += x; n++; } }
        return n > 0 ? s / n : Double.NaN;
    }
}
