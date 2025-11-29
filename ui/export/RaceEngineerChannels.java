package org.simulator.ui.export;

import org.simulator.canale.Channel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RaceEngineerChannels {
    private RaceEngineerChannels(){}

    public static final List<Channel> PRIMARY = List.of(
            // Meta & driver I/O
            Channel.TIME, Channel.LAP, Channel.LAP_TIME, Channel.DISTANCE,
            Channel.SPEED, Channel.ENGINE_RPM, Channel.GEAR, Channel.THROTTLE, Channel.BRAKE, Channel.STEER_ANGLE,
            // Dynamics
            Channel.CG_ACCEL_LONGITUDINAL, Channel.CG_ACCEL_LATERAL,
            Channel.WHEEL_ANGULAR_SPEED_FL, Channel.WHEEL_ANGULAR_SPEED_FR, Channel.WHEEL_ANGULAR_SPEED_RL, Channel.WHEEL_ANGULAR_SPEED_RR,
            // Tyres core
            Channel.TIRE_LOAD_FL, Channel.TIRE_LOAD_FR, Channel.TIRE_LOAD_RL, Channel.TIRE_LOAD_RR,
            Channel.TIRE_PRESSURE_FL, Channel.TIRE_PRESSURE_FR, Channel.TIRE_PRESSURE_RL, Channel.TIRE_PRESSURE_RR,
            Channel.TIRE_TEMP_INNER_FL, Channel.TIRE_TEMP_MIDDLE_FL, Channel.TIRE_TEMP_OUTER_FL,
            Channel.TIRE_TEMP_INNER_FR, Channel.TIRE_TEMP_MIDDLE_FR, Channel.TIRE_TEMP_OUTER_FR,
            Channel.TIRE_TEMP_INNER_RL, Channel.TIRE_TEMP_MIDDLE_RL, Channel.TIRE_TEMP_OUTER_RL,
            Channel.TIRE_TEMP_INNER_RR, Channel.TIRE_TEMP_MIDDLE_RR, Channel.TIRE_TEMP_OUTER_RR,
            // Brakes
            Channel.BRAKE_TEMP_FL, Channel.BRAKE_TEMP_FR, Channel.BRAKE_TEMP_RL, Channel.BRAKE_TEMP_RR, Channel.BRAKE_BIAS,
            // Suspension / Heights
            Channel.RIDE_HEIGHT_FL, Channel.RIDE_HEIGHT_FR, Channel.RIDE_HEIGHT_RL, Channel.RIDE_HEIGHT_RR,
            Channel.SUSP_TRAVEL_FL, Channel.SUSP_TRAVEL_FR, Channel.SUSP_TRAVEL_RL, Channel.SUSP_TRAVEL_RR
    );

    public static final List<Channel> SECONDARY = List.of(
            // Meta extra
            Channel.BEST_LAP_TIME, Channel.LAST_LAP_TIME, Channel.SESSION_LAP_COUNT, Channel.GROUND_SPEED,
            // Body dynamics
            Channel.CHASSIS_YAW_RATE, Channel.CHASSIS_PITCH_RATE, Channel.CHASSIS_ROLL_RATE,
            Channel.CHASSIS_PITCH_ANGLE, Channel.CHASSIS_ROLL_ANGLE,
            // Tyre advanced
            Channel.TIRE_SLIP_RATIO_FL, Channel.TIRE_SLIP_RATIO_FR, Channel.TIRE_SLIP_RATIO_RL, Channel.TIRE_SLIP_RATIO_RR,
            Channel.TIRE_SLIP_ANGLE_FL, Channel.TIRE_SLIP_ANGLE_FR, Channel.TIRE_SLIP_ANGLE_RL, Channel.TIRE_SLIP_ANGLE_RR,
            Channel.CAMBER_FL, Channel.CAMBER_FR, Channel.CAMBER_RL, Channel.CAMBER_RR,
            Channel.TOE_IN_FL, Channel.TOE_IN_FR, Channel.TOE_IN_RL, Channel.TOE_IN_RR,
            Channel.NUM_TIRES_OFF_TRACK,
            // Power / systems
            Channel.FUEL_LEVEL, Channel.MAX_FUEL, Channel.TURBO_BOOST, Channel.ENGINE_LIMITER,
            Channel.ERS_POWER_LEVEL, Channel.ERS_RECOVERY_LEVEL, Channel.DRS_AVAILABLE, Channel.DRS_ACTIVE,
            Channel.ABS_ENABLED, Channel.ABS_ACTIVE, Channel.TC_ENABLED, Channel.TC_ACTIVE,
            // Environment
            Channel.AIR_TEMP, Channel.ROAD_TEMP, Channel.AIR_DENSITY, Channel.WIND_SPEED, Channel.WIND_DIRECTION
    );

    /** Interseca i canali proposti con quelli realmente presenti nei dati. */
    public static Set<Channel> resolvePresent(Set<Channel> present){
        LinkedHashSet<Channel> out = new LinkedHashSet<>();
        for (Channel c : PRIMARY)   if (present.contains(c)) out.add(c);
        for (Channel c : SECONDARY) if (present.contains(c)) out.add(c);
        return out;
    }
}
