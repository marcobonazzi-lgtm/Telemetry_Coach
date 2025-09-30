package org.simulator.canale;

public enum Channel {
    // ===== Unione di canali già presenti nel progetto + tutti i mancanti dell'header =====

    // Tempo / distanza / giri
    TIME,
    DISTANCE,
    LAP,
    LAP_TIME,
    BEST_LAP_TIME,
    LAST_LAP_TIME,
    LAST_SECTOR_TIME,
    BEST_LAP_DELTA,
    LAP_TIME2,
    SESSION_LAP_COUNT,
    SESSION_TIME_LEFT,

    // Posizione / coordinate / velocità
    POSITION,
    CAR_COORD_X,
    CAR_COORD_Y,
    CAR_COORD_Z,
    CAR_POS_NORM,
    SPEED,
    GROUND_SPEED,
    DRIVE_TRAIN_SPEED,
    CHASSIS_VELOCITY_X,
    CHASSIS_VELOCITY_Y,
    CHASSIS_VELOCITY_Z,

    // Powertrain / cambio
    ENGINE_RPM,
    MAX_RPM,
    GEAR,
    CLUTCH,
    CLUTCH_POS,
    THROTTLE,
    THROTTLE_POS,
    BRAKE,
    BRAKE_POS,
    ENGINE_LIMITER,
    ENGINE_BRAKE_SETTING,

    // Elettrico / ibrido (ERS/KERS)
    ERS_HEAT_CHARGING,
    ERS_IS_CHARGING,
    ERS_MAX_ENERGY,
    ERS_POWER_LEVEL,
    ERS_RECOVERY_LEVEL,
    KERS_CHARGE,
    KERS_DEPLOYED_ENERGY,
    KERS_INPUT,
    KERS_MAX_ENERGY,

    // DRS
    DRS_ACTIVE,
    DRS_AVAILABLE,

    // Aiuti di guida (AID)
    ABS_ACTIVE,
    ABS_ENABLED,
    TC_ACTIVE,
    TC_ENABLED,
    AID_ALLOW_TIRE_BLANKETS,
    AID_AUTO_BLIP,
    AID_AUTO_CLUTCH,
    AID_AUTO_SHIFT,
    AID_FUEL_RATE,
    AID_IDEAL_LINE,
    AID_MECH_DAMAGE,
    AID_STABILITY,
    AID_TIRE_WEAR_RATE,
    PENALTIES_ENABLED,

    // Fuel
    FUEL_LEVEL,
    MAX_FUEL,
    FUEL_RATE,              // alias possibile dell’header AID Fuel Rate, mantenuto separato se serve

    // Forze pedali / FFB
    THROTTLE_FORCE,
    BRAKE_FORCE,
    CLUTCH_FORCE,
    FFB,
    SELF_ALIGN_TORQUE_FL,
    SELF_ALIGN_TORQUE_FR,
    SELF_ALIGN_TORQUE_RL,
    SELF_ALIGN_TORQUE_RR,

    // Accelerazioni / assetto / dinamica
    CG_ACCEL_LATERAL,
    CG_ACCEL_LONGITUDINAL,
    CG_ACCEL_VERTICAL,
    CG_HEIGHT,
    CHASSIS_PITCH_ANGLE,
    CHASSIS_PITCH_RATE,
    CHASSIS_ROLL_ANGLE,
    CHASSIS_ROLL_RATE,
    CHASSIS_YAW_RATE,
    STEER_ANGLE,
    BRAKE_BIAS,

    // Camber / caster / toe
    CAMBER_FL, CAMBER_FR, CAMBER_RL, CAMBER_RR,
    CASTER_FL, CASTER_FR,
    TOE_IN_FL, TOE_IN_FR, TOE_IN_RL, TOE_IN_RR,

    // Sospensioni / altezze / viaggi
    RIDE_HEIGHT_FL, RIDE_HEIGHT_FR, RIDE_HEIGHT_RL, RIDE_HEIGHT_RR,
    SUSP_TRAVEL_FL, SUSP_TRAVEL_FR, SUSP_TRAVEL_RL, SUSP_TRAVEL_RR,
    MAX_SUS_TRAVEL_FL, MAX_SUS_TRAVEL_FR, MAX_SUS_TRAVEL_RL, MAX_SUS_TRAVEL_RR,

    // Temperature freni
    BRAKE_TEMP_FL, BRAKE_TEMP_FR, BRAKE_TEMP_RL, BRAKE_TEMP_RR,

    // Gomme: carico / raggio / pressione / grip / slip / sporcizia
    TIRE_LOAD_FL, TIRE_LOAD_FR, TIRE_LOAD_RL, TIRE_LOAD_RR,
    TIRE_LOADED_RADIUS_FL, TIRE_LOADED_RADIUS_FR, TIRE_LOADED_RADIUS_RL, TIRE_LOADED_RADIUS_RR,
    TIRE_RADIUS_FL, TIRE_RADIUS_FR, TIRE_RADIUS_RL, TIRE_RADIUS_RR,
    TIRE_PRESSURE_FL, TIRE_PRESSURE_FR, TIRE_PRESSURE_RL, TIRE_PRESSURE_RR,
    TIRE_RUBBER_GRIP_FL, TIRE_RUBBER_GRIP_FR, TIRE_RUBBER_GRIP_RL, TIRE_RUBBER_GRIP_RR,
    TIRE_SLIP_ANGLE_FL, TIRE_SLIP_ANGLE_FR, TIRE_SLIP_ANGLE_RL, TIRE_SLIP_ANGLE_RR,
    TIRE_SLIP_RATIO_FL, TIRE_SLIP_RATIO_FR, TIRE_SLIP_RATIO_RL, TIRE_SLIP_RATIO_RR,
    TIRE_DIRT_LEVEL_FL, TIRE_DIRT_LEVEL_FR, TIRE_DIRT_LEVEL_RL, TIRE_DIRT_LEVEL_RR,

    // Gomme: temperature core / inner-middle-outer
    TIRE_TEMP_CORE_FL,   TIRE_TEMP_CORE_FR,   TIRE_TEMP_CORE_RL,   TIRE_TEMP_CORE_RR,
    TIRE_TEMP_INNER_FL,  TIRE_TEMP_MIDDLE_FL, TIRE_TEMP_OUTER_FL,
    TIRE_TEMP_INNER_FR,  TIRE_TEMP_MIDDLE_FR, TIRE_TEMP_OUTER_FR,
    TIRE_TEMP_INNER_RL,  TIRE_TEMP_MIDDLE_RL, TIRE_TEMP_OUTER_RL,
    TIRE_TEMP_INNER_RR,  TIRE_TEMP_MIDDLE_RR, TIRE_TEMP_OUTER_RR,

    // Ruote: velocità angolare
    WHEEL_ANGULAR_SPEED_FL, WHEEL_ANGULAR_SPEED_FR, WHEEL_ANGULAR_SPEED_RL, WHEEL_ANGULAR_SPEED_RR,

    // Danni vettura
    CAR_DAMAGE_FRONT, CAR_DAMAGE_LEFT, CAR_DAMAGE_REAR, CAR_DAMAGE_RIGHT,

    // Meteo / ambiente / pista
    AIR_DENSITY,
    AIR_TEMP,
    ROAD_TEMP,
    WIND_DIRECTION,
    WIND_SPEED,
    SURFACE_GRIP,

    // Aerodinamica / zavorra / boost
    BALLAST,
    TURBO_BOOST,
    MAX_TURBO_BOOST,
    MAX_POWER,
    MAX_TORQUE,

    // Limiti / max vari
    // già sopra ma ripetuto nel gruppo “Fuel” – tenerne uno solo nel codice reale
    MAX_POWER_LIMIT,   // opzionale se vuoi distinguere dal MAX_POWER dell’engine map
    RAW_DATA_SAMPLE_RATE,

    // Coordinate / flags / in pit / lap validity / off-track
    FLAGS,
    IN_PIT,
    LAP_INVALIDATED,
    NUM_TIRES_OFF_TRACK,

    // Kinematics aggiuntive
    CAR_DAMAGE,        // opzionale: totale aggregato (se vuoi calcolarlo)
    HR_SAMPLE_CLOCK,
    LR_SAMPLE_CLOCK,
    MR_SAMPLE_CLOCK,

    // Extra dall’header non ancora categorizzati esplicitamente
            // già in “Posizione”, qui per completezza
    MAX_FUEL_CAPACITY,     // se vuoi distinguere da MAX_FUEL telemetrico
    DRIVE_TRAIN_RPM,       // alias eventuale
    RAW_SAMPLE_RATE, SEAT_FORCE, PEDAL_FORCE, SEAT_FORCE_LEFT, SEAT_FORCE_RIGHT, SEAT_FORCE_REAR, ACC_LAT, ACC_LONG, ACC_VERT, WORLD_X, WORLD_Z,       // alias eventuale di RAW_DATA_SAMPLE_RATE
}
