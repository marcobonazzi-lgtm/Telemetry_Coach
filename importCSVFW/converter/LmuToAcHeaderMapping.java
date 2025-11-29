package org.simulator.importCSVFW.converter;

import java.util.*;

/**
 * Mappatura dei nomi LMU -> nomi “tipo AC”.
 * Nota: NON mappiamo "Steering Wheel Position" per facilitare la deduplica univoca dello steering.
 */
public final class LmuToAcHeaderMapping implements HeaderMapping {
    private final Map<String, String> mapping;

    public LmuToAcHeaderMapping() {
        Map<String, String> m = new LinkedHashMap<>();

        // Base/time
        m.put("Time", "Lap Time");
        m.put("Distance", "Distance");

        // Speed & inputs
        m.put("Ground Speed", "Ground Speed");
        m.put("Throttle Pos", "Throttle Pos");
        m.put("Brake Pos", "Brake Pos");
        m.put("Clutch Pos", "Clutch Pos");

        // Steering: mappiamo solo "Steering" -> "Steer Angle".
        // "Steering Wheel Position" resta non mappato (verrà rimosso se duplicato di sterzo).
        m.put("Steering", "Steer Angle");

        // Deltas / session
        m.put("Realtime Loss", "Best Lap Delta");
        m.put("Session Elapsed Time", "Session Time Left");
        m.put("Lap Number", "Session Lap Count");
        m.put("Delta Best", "Best Lap Delta");

        // Powertrain / dynamics
        m.put("Engine RPM", "Engine RPM");
        m.put("Gear", "Gear");
        m.put("G Force Lat", "CG Accel Lateral");
        m.put("G Force Long", "CG Accel Longitudinal");
        m.put("G Force Vert", "CG Accel Vertical");
        m.put("Fuel Level", "Fuel Level");

        // Brakes
        m.put("Brake Temp FL", "Brake Temp FL");
        m.put("Brake Temp FR", "Brake Temp FR");
        m.put("Brake Temp RL", "Brake Temp RL");
        m.put("Brake Temp RR", "Brake Temp RR");

        // GPS / ambient
        m.put("GPS Latitude", "GPS Latitude");
        m.put("GPS Longitude", "GPS Longitude");
        m.put("Ambient Temperature", "Air Temp");
        m.put("Track Temperature", "Road Temp");

        // Wheels
        m.put("Wheel Rot Speed FL", "Wheel Angular Speed FL");
        m.put("Wheel Rot Speed FR", "Wheel Angular Speed FR");
        m.put("Wheel Rot Speed RL", "Wheel Angular Speed RL");
        m.put("Wheel Rot Speed RR", "Wheel Angular Speed RR");

        // Pressures
        m.put("Tyre Pressure FL", "Tire Pressure FL");
        m.put("Tyre Pressure FR", "Tire Pressure FR");
        m.put("Tyre Pressure RL", "Tire Pressure RL");
        m.put("Tyre Pressure RR", "Tire Pressure RR");

        // Loads
        m.put("Tyre Load FL", "Tire Load FL");
        m.put("Tyre Load FR", "Tire Load FR");
        m.put("Tyre Load RL", "Tire Load RL");
        m.put("Tyre Load RR", "Tire Load RR");

        // Grip
        m.put("Grip Fract FL", "Tire Rubber Grip FL");
        m.put("Grip Fract FR", "Tire Rubber Grip FR");
        m.put("Grip Fract RL", "Tire Rubber Grip RL");
        m.put("Grip Fract RR", "Tire Rubber Grip RR");

        // Ride height
        m.put("Ride Height FL", "Ride Height FL");
        m.put("Ride Height FR", "Ride Height FR");
        m.put("Ride Height RL", "Ride Height RL");
        m.put("Ride Height RR", "Ride Height RR");

        // Tyre temps
        m.put("Tyre Temp FL Outer", "Tire Temp Outer FL");
        m.put("Tyre Temp FL Centre", "Tire Temp Middle FL");
        m.put("Tyre Temp FL Inner", "Tire Temp Inner FL");

        m.put("Tyre Temp FR Outer", "Tire Temp Outer FR");
        m.put("Tyre Temp FR Centre", "Tire Temp Middle FR");
        m.put("Tyre Temp FR Inner", "Tire Temp Inner FR");

        m.put("Tyre Temp RL Outer", "Tire Temp Outer RL");
        m.put("Tyre Temp RL Centre", "Tire Temp Middle RL");
        m.put("Tyre Temp RL Inner", "Tire Temp Inner RL");

        m.put("Tyre Temp RR Outer", "Tire Temp Outer RR");
        m.put("Tyre Temp RR Centre", "Tire Temp Middle RR");
        m.put("Tyre Temp RR Inner", "Tire Temp Inner RR");

        // Visibilità FFB anche con stripExtras=true
        m.put("FFB Output", "FFB Output");
        // Brake bias naming
        m.put("Brake Bias Rear", "Brake Bias");

        this.mapping = Collections.unmodifiableMap(m);
    }

    @Override public Map<String, String> map() { return mapping; }
}
