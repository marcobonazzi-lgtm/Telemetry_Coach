package org.simulator.analisi_base.session_analysis;

import org.simulator.canale.Channel;
import org.simulator.canale.Lap;
import org.simulator.canale.Sample;
import org.simulator.analisi_base.force_stats.ForceStats;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Implementazione delle statistiche medie di sessione con le stesse voci del miglior giro (aggregate). */
final class SessionAverages {

    private SessionAverages() {}

    static Map<String, Double> compute(List<Lap> laps) {
        Map<String, Double> st = new LinkedHashMap<>();
        if (laps == null || laps.isEmpty()) return st;

        // Considera solo giri "completi" (validi) per le medie di sessione
        // NB: Lap.isComplete usa mediana tempo/distanza e il flag LAP_INVALIDATED
        //     Se vuoi includere anche i giri non validi ma completi, cambia condizione.
        // (Qui usiamo comunque la lista intera per i confronti di completezza)
        // ---------------------------------------------------------------------

        // ====== GIRO ======
        st.put("Lap time medio [s]", avgLapTimeSafe(laps));
        st.put("Vmax [km/h]",       SessionMath.maxOverLaps(laps, Channel.SPEED));
        st.put("Vmedia [km/h]",     SessionMath.avgOverLaps(laps, Channel.SPEED));

        // RPM medio
        st.put("RPM medio", SessionMath.avgOverLaps(laps, Channel.ENGINE_RPM));

        // % tempo pieno gas / frenata
        st.put("Tempo pieno gas >90% [%]", SessionMath.fractionActiveOverLaps(laps, Channel.THROTTLE, 90.0) * 100.0);
        st.put("Tempo in frenata >20% [%]", SessionMath.fractionActiveOverLaps(laps, Channel.BRAKE, 20.0) * 100.0);

        // TC / ABS attivi (percentuale campioni ON)
        st.put("TC attivo [% campioni]",  SessionMath.pctTcActive(laps));
        st.put("ABS attivo [% campioni]", SessionMath.pctAbsActive(laps));

        // ====== CARBURANTE ======
        double fuelStart = avgFuelFirst(laps);
        double fuelEnd   = avgFuelLast(laps);
        double fuelUsed  = SessionMath.avgOf(laps, SessionMath::fuelUsedLap);
        st.put("Fuel start", fuelStart);
        st.put("Fuel end",   fuelEnd);
        st.put("Fuel used (lap)", fuelUsed);
        // ——— Carburante (stessa sezione "Fuel" dell’accordion) ———
        st.put("Fuel consumo medio (L/giro)", SessionMath.avgOf(laps, SessionMath::fuelUsedLap));
        st.put("Fuel residuo ultimo giro [L]", fuelEnd);
        if (!Double.isNaN(fuelUsed) && fuelUsed > 0 && !Double.isNaN(fuelEnd)) {
            st.put("Fuel giri rimanenti (stima)", fuelEnd / fuelUsed);
        }


        // ====== SUPERFICIE / PISTA ======
        double gripAvg = SessionMath.avgSurfaceGrip(laps);
        if (!Double.isNaN(gripAvg)) st.put("Surface grip medio", gripAvg);

        // ====== GOMME: temperature (Mid), pressioni ======
        // Temperature Mid per ruota
        st.put("Tyre temp FL (Mid) [°C]", SessionMath.avgTyreTempMiddleFL(laps));
        st.put("Tyre temp FR (Mid) [°C]", SessionMath.avgTyreTempMiddleFR(laps));
        st.put("Tyre temp RL (Mid) [°C]", SessionMath.avgTyreTempMiddleRL(laps));
        st.put("Tyre temp RR (Mid) [°C]", SessionMath.avgTyreTempMiddleRR(laps));
        st.put("Tyre temp media (Mid) [°C]",
                SessionMath.meanIgnoringNaN(
                        SessionMath.avgTyreTempMiddleFL(laps),
                        SessionMath.avgTyreTempMiddleFR(laps),
                        SessionMath.avgTyreTempMiddleRL(laps),
                        SessionMath.avgTyreTempMiddleRR(laps)));

        // Pressioni gomme (psi)
        double pFL = SessionMath.avgTyrePressureFL(laps);
        double pFR = SessionMath.avgTyrePressureFR(laps);
        double pRL = SessionMath.avgTyrePressureRL(laps);
        double pRR = SessionMath.avgTyrePressureRR(laps);
        st.put("Tyre pressure FL [psi]", pFL);
        st.put("Tyre pressure FR [psi]", pFR);
        st.put("Tyre pressure RL [psi]", pRL);
        st.put("Tyre pressure RR [psi]", pRR);
        st.put("Tyre pressure media [psi]", SessionMath.meanIgnoringNaN(pFL, pFR, pRL, pRR));

        // ====== FRENI: temperature ======
        double bFL = SessionMath.avgBrakeTempFL(laps);
        double bFR = SessionMath.avgBrakeTempFR(laps);
        double bRL = SessionMath.avgBrakeTempRL(laps);
        double bRR = SessionMath.avgBrakeTempRR(laps);
        st.put("Brake temp FL [°C]", bFL);
        st.put("Brake temp FR [°C]", bFR);
        st.put("Brake temp RL [°C]", bRL);
        st.put("Brake temp RR [°C]", bRR);
        st.put("Brake temp media [°C]", SessionMath.meanIgnoringNaN(bFL, bFR, bRL, bRR));

        // ====== DANNI ======
        double dF  = SessionMath.avgOverLaps(laps, Channel.CAR_DAMAGE_FRONT);
        double dL  = SessionMath.avgOverLaps(laps, Channel.CAR_DAMAGE_LEFT);
        double dR  = SessionMath.avgOverLaps(laps, Channel.CAR_DAMAGE_REAR);
        double dRt = SessionMath.avgOverLaps(laps, Channel.CAR_DAMAGE_RIGHT);
        st.put("Danno Front [%]", dF);
        st.put("Danno Left  [%]", dL);
        st.put("Danno Rear  [%]", dR);
        st.put("Danno Right [%]", dRt);
        st.put("Danno medio [%]", SessionMath.meanIgnoringNaN(dF, dL, dR, dRt));

        // ====== FORZE PEDALE / SEDILE ======
        double pedalF = SessionMath.avgOverLaps(laps, Channel.PEDAL_FORCE);
        double seatF  = SessionMath.avgOverLaps(laps, Channel.SEAT_FORCE);
        if (!Double.isNaN(pedalF)) st.put("Pedal force media [N]", pedalF);
        if (!Double.isNaN(seatF))  st.put("Seat force media [N]",  seatF);

        double pedalMax = SessionMath.maxOverLaps(laps, Channel.PEDAL_FORCE);
        if (!Double.isNaN(pedalMax)) st.put("Pedal force max [N]", pedalMax);

        // ====== (OPZIONALE) ROUGHNESS & DISTRIBUZIONE SEDILE ======
        // Commenta questo blocco se la tua build non include ForceStats
        double roughPct = avgSeatRoughnessPct(laps) * 100.0;
        if (!Double.isNaN(roughPct)) st.put("Seat roughness [%]", roughPct);
        ForceStats.SeatDistribution dist = avgSeatDistribution(laps);
        if (dist != null) {
            st.put("Seat dist SX [%]",   dist.left  * 100.0);
            st.put("Seat dist POST [%]", dist.rear  * 100.0);
            st.put("Seat dist DX [%]",   dist.right * 100.0);
        }

        // ====== SOSPENSIONI / ALTEZZE ======
        st.put("Ride height FL [mm]", SessionMath.avgRideHeightFL(laps));
        st.put("Ride height FR [mm]", SessionMath.avgRideHeightFR(laps));
        st.put("Ride height RL [mm]", SessionMath.avgRideHeightRL(laps));
        st.put("Ride height RR [mm]", SessionMath.avgRideHeightRR(laps));

        st.put("Susp travel FL [mm]", SessionMath.avgSuspTravelFL(laps));
        st.put("Susp travel FR [mm]", SessionMath.avgSuspTravelFR(laps));
        st.put("Susp travel RL [mm]", SessionMath.avgSuspTravelRL(laps));
        st.put("Susp travel RR [mm]", SessionMath.avgSuspTravelRR(laps));

        st.put("Max susp travel FL [mm]", SessionMath.maxSuspTravelFL(laps));
        st.put("Max susp travel FR [mm]", SessionMath.maxSuspTravelFR(laps));
        st.put("Max susp travel RL [mm]", SessionMath.maxSuspTravelRL(laps));
        st.put("Max susp travel RR [mm]", SessionMath.maxSuspTravelRR(laps));

        // ====== ASSETTO & DINAMICA ======
        st.put("Brake bias [%]", SessionMath.avgBrakeBias(laps));
        st.put("Steering angle [deg]", SessionMath.avgSteerAngle(laps));
        st.put("Chassis pitch angle [deg]", SessionMath.avgPitchAngle(laps));
        st.put("Chassis roll angle [deg]",  SessionMath.avgRollAngle(laps));
        st.put("Chassis yaw rate [deg/s]",  SessionMath.avgYawRate(laps));
        st.put("CG accel lateral [G]",      SessionMath.avgAccelLat(laps));
        st.put("CG accel longitudinal [G]", SessionMath.avgAccelLong(laps));
        st.put("CG accel vertical [G]",     SessionMath.avgAccelVert(laps));

        // ====== METEO & PISTA ======
        st.put("Air temp [°C]",  SessionMath.avgAirTemp(laps));
        st.put("Road temp [°C]", SessionMath.avgRoadTemp(laps));
        st.put("Wind speed [km/h]", SessionMath.avgWindSpeed(laps));

        return st;
    }

    // ---------------------- helper interni (solo per fuel e seat) ----------------------

    private static double avgFuelFirst(List<Lap> laps) {
        double sum = 0; int n = 0;
        for (Lap lap : laps) {
            Double v = firstValid(lap, Channel.FUEL_LEVEL);
            if (v != null && !v.isNaN() && !v.isInfinite()) { sum += v; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    private static double avgFuelLast(List<Lap> laps) {
        double sum = 0; int n = 0;
        for (Lap lap : laps) {
            Double v = lastValid(lap, Channel.FUEL_LEVEL);
            if (v != null && !v.isNaN() && !v.isInfinite()) { sum += v; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    private static Double firstValid(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return null;
        for (Sample s : lap.samples) {
            Double v = s.values().get(ch);
            if (v != null && !v.isNaN() && !v.isInfinite()) return v;
        }
        return null;
    }

    private static Double lastValid(Lap lap, Channel ch) {
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return null;
        for (int i = lap.samples.size() - 1; i >= 0; i--) {
            Double v = lap.samples.get(i).values().get(ch);
            if (v != null && !v.isNaN() && !v.isInfinite()) return v;
        }
        return null;
    }

    private static double avgLapTimeSafe(List<Lap> laps) {
        double sum = 0; int n = 0;
        for (Lap l : laps) {
            if (l == null) continue;
            if (!l.isComplete(laps)) continue;
            double t = l.lapTimeSafe();
            if (!Double.isNaN(t) && t > 0) { sum += t; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    // ----- Seat roughness / distribuzione (media sui giri) -----
    private static double avgSeatRoughnessPct(List<Lap> laps) {
        double sum = 0; int n = 0;
        for (Lap l : laps) {
            if (l == null || l.samples == null || l.samples.isEmpty()) continue;
            double r = ForceStats.seatRoughnessPct(l);
            if (!Double.isNaN(r) && !Double.isInfinite(r)) { sum += r; n++; }
        }
        return n > 0 ? sum / n : Double.NaN;
    }

    private static ForceStats.SeatDistribution avgSeatDistribution(List<Lap> laps) {
        double sL=0, sR=0, sB=0; int n=0;
        for (Lap l : laps) {
            if (l == null || l.samples == null || l.samples.isEmpty()) continue;
            ForceStats.SeatDistribution d = ForceStats.distribution(l);
            if (d == null) continue;
            if (valid(d.left) && valid(d.right) && valid(d.rear)) {
                sL += d.left; sR += d.right; sB += d.rear; n++;
            }
        }
        if (n == 0) return null;
        ForceStats.SeatDistribution out = new ForceStats.SeatDistribution();
        out.left  = sL / n;
        out.right = sR / n;
        out.rear  = sB / n;
        return out;
    }

    private static boolean valid(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
}
