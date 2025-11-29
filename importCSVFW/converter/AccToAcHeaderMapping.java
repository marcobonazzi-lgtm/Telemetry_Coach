package org.simulator.importCSVFW.converter;

import java.util.*;

/**
 * Mappatura header ACC -> nomi "tipo AC".
 * - Time (ACC)  -> "Lap Time" (AC)
 * - TIME (ACC)  -> "Time"     (AC)
 * - LAP_BEACON  -> "Lap Beacon"
 */
public final class AccToAcHeaderMapping implements HeaderMapping {
    private final Map<String, String> mapping;

    public AccToAcHeaderMapping() {
        Map<String, String> m = new LinkedHashMap<>();

        // Tempo / distanza / beacon
        m.put("Time",                "Lap Time");   // <— richiesto
        m.put("TIME",                "Time");       // <— richiesto
        m.put("Distance",            "Distance");
        m.put("LAP_BEACON",          "Lap Beacon");

        // Dinamica & comandi
        m.put("G_LAT",               "CG Accel Lateral");
        m.put("ROTY",                "Chassis Yaw Rate");
        m.put("STEERANGLE",          "Steering Angle");
        m.put("SPEED",               "Ground Speed");
        m.put("THROTTLE",            "Throttle Pos");
        m.put("BRAKE",               "Brake Pos");
        m.put("GEAR",                "Gear");
        m.put("G_LON",               "CG Accel Longitudinal");
        m.put("CLUTCH",              "Clutch Pos");
        m.put("RPMS",                "Engine RPM");
        m.put("TC",                  "TC Enabled");
        m.put("ABS",                 "ABS Enabled");

        // Sospensioni
        m.put("SUS_TRAVEL_LF",       "Suspension Travel FL");
        m.put("SUS_TRAVEL_RF",       "Suspension Travel FR");
        m.put("SUS_TRAVEL_LR",       "Suspension Travel RL");
        m.put("SUS_TRAVEL_RR",       "Suspension Travel RR");

        // Freni
        m.put("BRAKE_TEMP_LF",       "Brake Temp FL");
        m.put("BRAKE_TEMP_RF",       "Brake Temp FR");
        m.put("BRAKE_TEMP_LR",       "Brake Temp RL");
        m.put("BRAKE_TEMP_RR",       "Brake Temp RR");

        // Pressioni
        m.put("TYRE_PRESS_LF",       "Tire Pressure FL");
        m.put("TYRE_PRESS_RF",       "Tire Pressure FR");
        m.put("TYRE_PRESS_LR",       "Tire Pressure RL");
        m.put("TYRE_PRESS_RR",       "Tire Pressure RR");

        // Velocità ruota
        m.put("WHEEL_SPEED_LF",      "Wheel Angular Speed FL");
        m.put("WHEEL_SPEED_RF",      "Wheel Angular Speed FR");
        m.put("WHEEL_SPEED_LR",      "Wheel Angular Speed RL");
        m.put("WHEEL_SPEED_RR",      "Wheel Angular Speed RR");

        // Non mappati (TYRE_TAIR_*, BUMPSTOP*, EN_*): ignorabili con stripExtras=true

        this.mapping = Collections.unmodifiableMap(m);
    }

    @Override
    public Map<String, String> map() {
        return mapping;
    }
}
