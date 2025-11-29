package org.simulator.analisi_base.lap_analysis;

import org.simulator.analisi_base.force_stats.ForceStats;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.LinkedHashMap;
import java.util.Map;

/** Statistiche base per un giro, estese ai nuovi canali. */
final class BasicLapStats {

    private BasicLapStats() {}

    static Map<String, Double> compute(Lap lap) {
        Map<String, Double> st = new LinkedHashMap<>();
        if (lap == null || lap.samples == null || lap.samples.isEmpty()) return st;

        // -------------------- GIRO --------------------
        double lapTime = !Double.isNaN(lap.lapTime) ? lap.lapTime : SampleMath.lastNonNaN(lap, Channel.LAP_TIME);
        if (Double.isNaN(lapTime)) {
            double t0 = SampleMath.firstNonNaN(lap, Channel.TIME);
            double t1 = SampleMath.lastNonNaN(lap, Channel.TIME);
            lapTime = (!Double.isNaN(t0) && !Double.isNaN(t1)) ? Math.max(0, t1 - t0) : Double.NaN;
        }
        st.put("Lap time [s]", lapTime);
        st.put("Vmax [km/h]",   SampleMath.max(lap, Channel.SPEED));
        st.put("Vmedia [km/h]", SampleMath.avg(lap, Channel.SPEED));

        // -------------------- MOTORE --------------------
        st.put("RPM medio", SampleMath.avg(lap, Channel.ENGINE_RPM));
        st.put("Gear medio", SampleMath.avg(lap, Channel.GEAR));
        double limiter = SampleMath.avg(lap, Channel.ENGINE_LIMITER);
        if (!Double.isNaN(limiter)) st.put("Engine limiter (medio)", limiter);

        // -------------------- CONTROLLI & AIUTI --------------------
        st.put("Tempo pieno gas >90% [%]", SampleMath.fractionAbove(lap, Channel.THROTTLE, 90.0) * 100.0);
        st.put("Tempo in frenata >20% [%]", SampleMath.fractionAbove(lap, Channel.BRAKE, 20.0) * 100.0);

        double tcPct  = SampleMath.fractionActive(lap, Channel.TC_ACTIVE);
        double absPct = SampleMath.fractionActive(lap, Channel.ABS_ACTIVE);
        if (tcPct  >= 0) st.put("TC attivo [% campioni]",  tcPct  * 100.0);
        if (absPct >= 0) st.put("ABS attivo [% campioni]", absPct * 100.0);

        double tcEnabled  = SampleMath.fractionActive(lap, Channel.TC_ENABLED);
        double absEnabled = SampleMath.fractionActive(lap, Channel.ABS_ENABLED);
        if (tcEnabled  >= 0)  st.put("TC abilitato [% campioni]",  tcEnabled  * 100.0);
        if (absEnabled >= 0)  st.put("ABS abilitato [% campioni]", absEnabled * 100.0);

        double drsAvail = SampleMath.fractionActive(lap, Channel.DRS_AVAILABLE);
        double drsActive = SampleMath.fractionActive(lap, Channel.DRS_ACTIVE);
        if (drsAvail >= 0)  st.put("DRS disponibile [% campioni]", drsAvail * 100.0);
        if (drsActive >= 0) st.put("DRS attivo [% campioni]",      drsActive * 100.0);

        // -------------------- FUEL --------------------
        double fuelStart = SampleMath.firstNonNaN(lap, Channel.FUEL_LEVEL);
        double fuelEnd   = SampleMath.lastNonNaN(lap, Channel.FUEL_LEVEL);
        double fuelUsed  = (!Double.isNaN(fuelStart) && !Double.isNaN(fuelEnd)) ? Math.max(0, fuelStart - fuelEnd) : Double.NaN;
        st.put("Fuel start [l]", fuelStart);
        st.put("Fuel end [l]",   fuelEnd);
        st.put("Fuel used (lap) [l]", fuelUsed);

        // -------------------- SOSPENSIONI --------------------
        // Ride height (mm)
        double rhFL = SampleMath.avg(lap, Channel.RIDE_HEIGHT_FL);
        double rhFR = SampleMath.avg(lap, Channel.RIDE_HEIGHT_FR);
        double rhRL = SampleMath.avg(lap, Channel.RIDE_HEIGHT_RL);
        double rhRR = SampleMath.avg(lap, Channel.RIDE_HEIGHT_RR);
        if (!SampleMath.allNaN(rhFL, rhFR, rhRL, rhRR)) {
            st.put("Ride Height FL [mm]", rhFL);
            st.put("Ride Height FR [mm]", rhFR);
            st.put("Ride Height RL [mm]", rhRL);
            st.put("Ride Height RR [mm]", rhRR);
            st.put("Ride Height medio [mm]", SampleMath.meanIgnoringNaN(rhFL, rhFR, rhRL, rhRR));
        }

        // Suspension travel (mm)
        double trFL = SampleMath.avg(lap, Channel.SUSP_TRAVEL_FL);
        double trFR = SampleMath.avg(lap, Channel.SUSP_TRAVEL_FR);
        double trRL = SampleMath.avg(lap, Channel.SUSP_TRAVEL_RL);
        double trRR = SampleMath.avg(lap, Channel.SUSP_TRAVEL_RR);
        if (!SampleMath.allNaN(trFL, trFR, trRL, trRR)) {
            st.put("Susp Travel FL [mm]", trFL);
            st.put("Susp Travel FR [mm]", trFR);
            st.put("Susp Travel RL [mm]", trRL);
            st.put("Susp Travel RR [mm]", trRR);
            st.put("Susp Travel medio [mm]", SampleMath.meanIgnoringNaN(trFL, trFR, trRL, trRR));
        }

        // Max suspension travel (mm)
        double mFL = SampleMath.max(lap, Channel.MAX_SUS_TRAVEL_FL);
        double mFR = SampleMath.max(lap, Channel.MAX_SUS_TRAVEL_FR);
        double mRL = SampleMath.max(lap, Channel.MAX_SUS_TRAVEL_RL);
        double mRR = SampleMath.max(lap, Channel.MAX_SUS_TRAVEL_RR);
        if (!SampleMath.allNaN(mFL, mFR, mRL, mRR)) {
            st.put("Max Susp Travel FL [mm]", mFL);
            st.put("Max Susp Travel FR [mm]", mFR);
            st.put("Max Susp Travel RL [mm]", mRL);
            st.put("Max Susp Travel RR [mm]", mRR);
        }

        // -------------------- TYRES --------------------
        // Temperature mid (°C) – già presenti
        double tFL = SampleMath.avg(lap, Channel.TIRE_TEMP_MIDDLE_FL);
        double tFR = SampleMath.avg(lap, Channel.TIRE_TEMP_MIDDLE_FR);
        double tRL = SampleMath.avg(lap, Channel.TIRE_TEMP_MIDDLE_RL);
        double tRR = SampleMath.avg(lap, Channel.TIRE_TEMP_MIDDLE_RR);
        if (!SampleMath.allNaN(tFL, tFR, tRL, tRR)) {
            st.put("Tyre temp FL (Mid) [°C]", tFL);
            st.put("Tyre temp FR (Mid) [°C]", tFR);
            st.put("Tyre temp RL (Mid) [°C]", tRL);
            st.put("Tyre temp RR (Mid) [°C]", tRR);
            st.put("Tyre temp media (Mid) [°C]", SampleMath.meanIgnoringNaN(tFL, tFR, tRL, tRR));
        }

        // Temperature core (°C)
        double cFL = SampleMath.avg(lap, Channel.TIRE_TEMP_CORE_FL);
        double cFR = SampleMath.avg(lap, Channel.TIRE_TEMP_CORE_FR);
        double cRL = SampleMath.avg(lap, Channel.TIRE_TEMP_CORE_RL);
        double cRR = SampleMath.avg(lap, Channel.TIRE_TEMP_CORE_RR);
        if (!SampleMath.allNaN(cFL, cFR, cRL, cRR)) {
            st.put("Tyre temp CORE FL [°C]", cFL);
            st.put("Tyre temp CORE FR [°C]", cFR);
            st.put("Tyre temp CORE RL [°C]", cRL);
            st.put("Tyre temp CORE RR [°C]", cRR);
            st.put("Tyre temp CORE media [°C]", SampleMath.meanIgnoringNaN(cFL, cFR, cRL, cRR));
        }

        // Pressioni (psi) – già presenti
        double pFL = SampleMath.avg(lap, Channel.TIRE_PRESSURE_FL);
        double pFR = SampleMath.avg(lap, Channel.TIRE_PRESSURE_FR);
        double pRL = SampleMath.avg(lap, Channel.TIRE_PRESSURE_RL);
        double pRR = SampleMath.avg(lap, Channel.TIRE_PRESSURE_RR);
        if (!SampleMath.allNaN(pFL, pFR, pRL, pRR)) {
            st.put("Tyre pressure FL [psi]", pFL);
            st.put("Tyre pressure FR [psi]", pFR);
            st.put("Tyre pressure RL [psi]", pRL);
            st.put("Tyre pressure RR [psi]", pRR);
            st.put("Tyre pressure media [psi]", SampleMath.meanIgnoringNaN(pFL, pFR, pRL, pRR));
        }

        // Carichi ruota (N)
        double lFL = SampleMath.avg(lap, Channel.TIRE_LOAD_FL);
        double lFR = SampleMath.avg(lap, Channel.TIRE_LOAD_FR);
        double lRL = SampleMath.avg(lap, Channel.TIRE_LOAD_RL);
        double lRR = SampleMath.avg(lap, Channel.TIRE_LOAD_RR);
        if (!SampleMath.allNaN(lFL, lFR, lRL, lRR)) {
            st.put("Tyre load FL [N]", lFL);
            st.put("Tyre load FR [N]", lFR);
            st.put("Tyre load RL [N]", lRL);
            st.put("Tyre load RR [N]", lRR);
            st.put("Tyre load medio [N]", SampleMath.meanIgnoringNaN(lFL, lFR, lRL, lRR));
        }

        // Grip gomma (%) e sporcizia
        double gFL = SampleMath.avg(lap, Channel.TIRE_RUBBER_GRIP_FL);
        double gFR = SampleMath.avg(lap, Channel.TIRE_RUBBER_GRIP_FR);
        double gRL = SampleMath.avg(lap, Channel.TIRE_RUBBER_GRIP_RL);
        double gRR = SampleMath.avg(lap, Channel.TIRE_RUBBER_GRIP_RR);
        if (!SampleMath.allNaN(gFL, gFR, gRL, gRR)) {
            st.put("Tyre rubber grip FL [%]", gFL);
            st.put("Tyre rubber grip FR [%]", gFR);
            st.put("Tyre rubber grip RL [%]", gRL);
            st.put("Tyre rubber grip RR [%]", gRR);
            st.put("Tyre rubber grip medio [%]", SampleMath.meanIgnoringNaN(gFL, gFR, gRL, gRR));
        }

        double dFLt = SampleMath.avg(lap, Channel.TIRE_DIRT_LEVEL_FL);
        double dFRt = SampleMath.avg(lap, Channel.TIRE_DIRT_LEVEL_FR);
        double dRLt = SampleMath.avg(lap, Channel.TIRE_DIRT_LEVEL_RL);
        double dRRt = SampleMath.avg(lap, Channel.TIRE_DIRT_LEVEL_RR);
        if (!SampleMath.allNaN(dFLt, dFRt, dRLt, dRRt)) {
            st.put("Tyre dirt level FL [%]", dFLt);
            st.put("Tyre dirt level FR [%]", dFRt);
            st.put("Tyre dirt level RL [%]", dRLt);
            st.put("Tyre dirt level RR [%]", dRRt);
            st.put("Tyre dirt level medio [%]", SampleMath.meanIgnoringNaN(dFLt, dFRt, dRLt, dRRt));
        }

        // Slip (media)
        double saFL = SampleMath.avg(lap, Channel.TIRE_SLIP_ANGLE_FL);
        double saFR = SampleMath.avg(lap, Channel.TIRE_SLIP_ANGLE_FR);
        double saRL = SampleMath.avg(lap, Channel.TIRE_SLIP_ANGLE_RL);
        double saRR = SampleMath.avg(lap, Channel.TIRE_SLIP_ANGLE_RR);
        if (!SampleMath.allNaN(saFL, saFR, saRL, saRR)) {
            st.put("Tyre slip angle FL [deg]", saFL);
            st.put("Tyre slip angle FR [deg]", saFR);
            st.put("Tyre slip angle RL [deg]", saRL);
            st.put("Tyre slip angle RR [deg]", saRR);
        }
        double srFL = SampleMath.avg(lap, Channel.TIRE_SLIP_RATIO_FL);
        double srFR = SampleMath.avg(lap, Channel.TIRE_SLIP_RATIO_FR);
        double srRL = SampleMath.avg(lap, Channel.TIRE_SLIP_RATIO_RL);
        double srRR = SampleMath.avg(lap, Channel.TIRE_SLIP_RATIO_RR);
        if (!SampleMath.allNaN(srFL, srFR, srRL, srRR)) {
            st.put("Tyre slip ratio FL [%]", srFL);
            st.put("Tyre slip ratio FR [%]", srFR);
            st.put("Tyre slip ratio RL [%]", srRL);
            st.put("Tyre slip ratio RR [%]", srRR);
        }

        // Wheel angular speed (rad/s)
        double wFL = SampleMath.avg(lap, Channel.WHEEL_ANGULAR_SPEED_FL);
        double wFR = SampleMath.avg(lap, Channel.WHEEL_ANGULAR_SPEED_FR);
        double wRL = SampleMath.avg(lap, Channel.WHEEL_ANGULAR_SPEED_RL);
        double wRR = SampleMath.avg(lap, Channel.WHEEL_ANGULAR_SPEED_RR);
        if (!SampleMath.allNaN(wFL, wFR, wRL, wRR)) {
            st.put("Wheel ang speed FL [rad/s]", wFL);
            st.put("Wheel ang speed FR [rad/s]", wFR);
            st.put("Wheel ang speed RL [rad/s]", wRL);
            st.put("Wheel ang speed RR [rad/s]", wRR);
        }

        // -------------------- BRAKES --------------------
        double bFL = SampleMath.avg(lap, Channel.BRAKE_TEMP_FL);
        double bFR = SampleMath.avg(lap, Channel.BRAKE_TEMP_FR);
        double bRL = SampleMath.avg(lap, Channel.BRAKE_TEMP_RL);
        double bRR = SampleMath.avg(lap, Channel.BRAKE_TEMP_RR);
        if (!SampleMath.allNaN(bFL, bFR, bRL, bRR)) {
            st.put("Brake temp FL [°C]", bFL);
            st.put("Brake temp FR [°C]", bFR);
            st.put("Brake temp RL [°C]", bRL);
            st.put("Brake temp RR [°C]", bRR);
            st.put("Brake temp media [°C]", SampleMath.meanIgnoringNaN(bFL, bFR, bRL, bRR));
        }

        // Self-aligning torque (N·m)
        double satFL = SampleMath.avg(lap, Channel.SELF_ALIGN_TORQUE_FL);
        double satFR = SampleMath.avg(lap, Channel.SELF_ALIGN_TORQUE_FR);
        double satRL = SampleMath.avg(lap, Channel.SELF_ALIGN_TORQUE_RL);
        double satRR = SampleMath.avg(lap, Channel.SELF_ALIGN_TORQUE_RR);
        if (!SampleMath.allNaN(satFL, satFR, satRL, satRR)) {
            st.put("Self Align Torque FL [N·m]", satFL);
            st.put("Self Align Torque FR [N·m]", satFR);
            st.put("Self Align Torque RL [N·m]", satRL);
            st.put("Self Align Torque RR [N·m]", satRR);
            st.put("Self Align Torque medio [N·m]", SampleMath.meanIgnoringNaN(satFL, satFR, satRL, satRR));
        }

        // -------------------- ASSETTO & DINAMICA --------------------
        double bias = SampleMath.avg(lap, Channel.BRAKE_BIAS);
        if (!Double.isNaN(bias)) st.put("Brake bias medio [%]", bias);

        double aLat = SampleMath.avg(lap, Channel.CG_ACCEL_LATERAL);
        double aLon = SampleMath.avg(lap, Channel.CG_ACCEL_LONGITUDINAL);
        double aVer = SampleMath.avg(lap, Channel.CG_ACCEL_VERTICAL);
        if (!SampleMath.allNaN(aLat, aLon, aVer)) {
            st.put("Accel laterale media [G]", aLat);
            st.put("Accel longitudinale media [G]", aLon);
            st.put("Accel verticale media [G]", aVer);
        }

        double pitch = SampleMath.avg(lap, Channel.CHASSIS_PITCH_ANGLE);
        double roll  = SampleMath.avg(lap, Channel.CHASSIS_ROLL_ANGLE);
        double yawR  = SampleMath.avg(lap, Channel.CHASSIS_YAW_RATE);
        if (!SampleMath.allNaN(pitch, roll, yawR)) {
            st.put("Pitch angle medio [deg]", pitch);
            st.put("Roll angle medio [deg]",  roll);
            st.put("Yaw rate medio [deg/s]",  yawR);
        }

        // -------------------- METEO & PISTA --------------------
        double gripAvg = SampleMath.avg(lap, Channel.SURFACE_GRIP);
        if (!Double.isNaN(gripAvg)) st.put("Surface grip medio [%]", gripAvg);

        double airT = SampleMath.avg(lap, Channel.AIR_TEMP);
        double roadT = SampleMath.avg(lap, Channel.ROAD_TEMP);
        if (!SampleMath.allNaN(airT, roadT)) {
            st.put("Air temp media [°C]", airT);
            st.put("Road temp media [°C]", roadT);
        }
        double windS = SampleMath.avg(lap, Channel.WIND_SPEED);
        if (!Double.isNaN(windS)) st.put("Wind speed medio [km/h]", windS);

        // -------------------- TELEMETRIA / REGOLAMENTI --------------------
        double offTrackPct = SampleMath.fractionAbove(lap, Channel.NUM_TIRES_OFF_TRACK, 0.0);
        if (offTrackPct >= 0) st.put("Tempo con off-track [% campioni]", offTrackPct * 100.0);

        double inPitPct = SampleMath.fractionActive(lap, Channel.IN_PIT);
        if (inPitPct >= 0) st.put("Tempo in pit [% campioni]", inPitPct * 100.0);

        // -------------------- DANNI --------------------
        double dF  = SampleMath.damageAvg(lap, Channel.CAR_DAMAGE_FRONT);
        double dL  = SampleMath.damageAvg(lap, Channel.CAR_DAMAGE_LEFT);
        double dR  = SampleMath.damageAvg(lap, Channel.CAR_DAMAGE_REAR);
        double dRt = SampleMath.damageAvg(lap, Channel.CAR_DAMAGE_RIGHT);
        if (!SampleMath.allNaN(dF, dL, dR, dRt)) {
            st.put("Danno Front [%]", dF);
            st.put("Danno Left  [%]", dL);
            st.put("Danno Rear  [%]", dR);
            st.put("Danno Right [%]", dRt);
            st.put("Danno medio [%]", SampleMath.meanIgnoringNaN(dF, dL, dR, dRt));
        }

        // -------------------- FORZE PEDALI / SEDILE --------------------
        double throttleF = SampleMath.avg(lap, Channel.THROTTLE_FORCE);
        double brakeF    = SampleMath.avg(lap, Channel.BRAKE_FORCE);
        double clutchF   = SampleMath.avg(lap, Channel.CLUTCH_FORCE);
        if (!Double.isNaN(throttleF)) st.put("Throttle force media [N]", throttleF);
        if (!Double.isNaN(brakeF))    st.put("Brake force media [N]",    brakeF);
        if (!Double.isNaN(clutchF))   st.put("Clutch force media [N]",   clutchF);

        double pedalF = SampleMath.avg(lap, Channel.PEDAL_FORCE);
        double seatF  = SampleMath.avg(lap, Channel.SEAT_FORCE);
        if (!Double.isNaN(pedalF)) st.put("Pedal force media [N]", pedalF);
        if (!Double.isNaN(seatF))  st.put("Seat force media [N]",  seatF);
        double pedalMax = SampleMath.max(lap, Channel.PEDAL_FORCE);
        if (!Double.isNaN(pedalMax)) st.put("Pedal force max [N]", pedalMax);

        double roughPct = ForceStats.seatRoughnessPct(lap) * 100.0;
        st.put("Seat roughness [%]", roughPct);
        ForceStats.SeatDistribution dist = ForceStats.distribution(lap);
        st.put("Seat dist SX [%]",   dist.left  * 100.0);
        st.put("Seat dist POST [%]", dist.rear  * 100.0);
        st.put("Seat dist DX [%]",   dist.right * 100.0);

        // -------------------- ERS / KERS (se disponibili) --------------------
        double ersChargePct = SampleMath.fractionActive(lap, Channel.ERS_IS_CHARGING);
        if (ersChargePct >= 0) st.put("ERS in carica [% campioni]", ersChargePct * 100.0);
        double ersHeatCharge = SampleMath.fractionActive(lap, Channel.ERS_HEAT_CHARGING);
        if (ersHeatCharge >= 0) st.put("ERS heat charging [% campioni]", ersHeatCharge * 100.0);
        double ersPowerLevel = SampleMath.avg(lap, Channel.ERS_POWER_LEVEL);
        if (!Double.isNaN(ersPowerLevel)) st.put("ERS power level (medio)", ersPowerLevel);
        double ersRecovery = SampleMath.avg(lap, Channel.ERS_RECOVERY_LEVEL);
        if (!Double.isNaN(ersRecovery)) st.put("ERS recovery level (medio)", ersRecovery);

        // Energia KERS: delta cumulativo se il canale è cumulato, altrimenti media
        double kersE0 = SampleMath.firstNonNaN(lap, Channel.KERS_DEPLOYED_ENERGY);
        double kersE1 = SampleMath.lastNonNaN(lap, Channel.KERS_DEPLOYED_ENERGY);
        if (!Double.isNaN(kersE0) && !Double.isNaN(kersE1) && kersE1 >= kersE0) {
            st.put("KERS energia deploy (lap) [kJ]", kersE1 - kersE0);
        } else {
            double kersEavg = SampleMath.avg(lap, Channel.KERS_DEPLOYED_ENERGY);
            if (!Double.isNaN(kersEavg)) st.put("KERS energia deploy (media) [kJ]", kersEavg);
        }
        double kersCharge = SampleMath.avg(lap, Channel.KERS_CHARGE);
        if (!Double.isNaN(kersCharge)) st.put("KERS charge (medio) [%]", kersCharge);
        double kersInput = SampleMath.avg(lap, Channel.KERS_INPUT);
        if (!Double.isNaN(kersInput)) st.put("KERS input (medio) [%]", kersInput);

        return st;
    }
}
