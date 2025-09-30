package org.simulator.importCSVFW;

import org.simulator.canale.Channel;

import java.util.*;

/** Dizionario di alias + normalizzazione header (completo e deduplicato). */
public final class ChannelAliases {

    private ChannelAliases(){}

    // ---- Normalizzazione testo
    public static String norm(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replace('_', ' ').replace('-', ' ');
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    // ---- Ricerca indice header per alias (match esatto poi contains)
    static int indexOf(String[] header, List<String> aliases) {
        for (int i = 0; i < header.length; i++) {
            String h = norm(header[i]);
            for (String a : aliases) if (h.equals(a)) return i;
        }
        for (int i = 0; i < header.length; i++) {
            String h = norm(header[i]);
            for (String a : aliases) if (!a.isEmpty() && h.contains(a)) return i;
        }
        return -1;
    }

    // ---- Alias di base utili ai detector
    static final List<String> TIME_ALIASES    = List.of("time", "time [s]", "logged time", "absolute time");
    static final List<String> DIST_ALIASES    = List.of("distance", "distance [m]", "lap distance", "s distance");
    static final List<String> LAP_ALIASES     = List.of("lap", "lap #", "lap number", "current lap", "session lap count");
    static final List<String> LAPTIME_ALIASES = List.of("lap time", "lap time [s]", "current lap time", "laptime");

    // ---- Header probabili (aiuta auto-riconoscimento)
    static final Set<String> HEADER_CANDIDATES = new HashSet<>(List.of(
            // tempo/giro/distanza
            "time","time [s]","distance","distance [m]","lap","lap #","lap number","lap time","lap time [s]",
            "session lap count","best lap time","last lap time","last sector time","best lap delta","lap time2",
            // dinamica base
            "speed","speed [km/h]","ground speed","engine rpm","rpm","rpm [1/min]","gear",
            "throttle","throttle pos [%]","brake","brake pos [%]","clutch","clutch pos [%]","steer angle","steer angle [deg]",
            // accelerazioni
            "long accel [g]","lat accel [g]","vert accel [g]",
            // aids / tc / abs
            "tc active","tc enabled","abs active","abs enabled","aid auto shift","aid auto blip","aid auto clutch",
            "aid ideal line","aid fuel rate","aid tire wear rate","aid mech damage","aid stability",
            "aid allow tire blankets","penalties enabled",
            // ibrido / drs
            "ers heat charging","ers is charging","ers max energy","ers power level","ers recovery level",
            "kers charge","kers deployed energy","kers input","kers max energy",
            "drs active","drs available",
            // varie powertrain
            "engine brake setting","engine limiter",
            // meteo / pista
            "air density","air temp","road temp","wind direction","wind speed","surface grip","ballast",
            // chassis / dinamica
            "cg accel lateral","cg accel longitudinal","cg accel vertical","cg height",
            "chassis pitch angle","chassis pitch rate","chassis roll angle","chassis roll rate","chassis yaw rate",
            "chassis velocity x","chassis velocity y","chassis velocity z",
            // sospensioni
            "ride height fl","ride height fr","ride height rl","ride height rr",
            "suspension travel fl","suspension travel fr","suspension travel rl","suspension travel rr",
            "max sus travel fl","max sus travel fr","max sus travel rl","max sus travel rr",
            // sterzo / freni
            "brake bias","brake temp fl","brake temp fr","brake temp rl","brake temp rr",
            "self align torque fl","self align torque fr","self align torque rl","self align torque rr",
            // pneumatici
            "tire pressure fl","tire pressure fr","tire pressure rl","tire pressure rr",
            "tire load fl","tire load fr","tire load rl","tire load rr",
            "tire loaded radius fl","tire loaded radius fr","tire loaded radius rl","tire loaded radius rr",
            "tire radius fl","tire radius fr","tire radius rl","tire radius rr",
            "tire rubber grip fl","tire rubber grip fr","tire rubber grip rl","tire rubber grip rr",
            "tire slip angle fl","tire slip angle fr","tire slip angle rl","tire slip angle rr",
            "tire slip ratio fl","tire slip ratio fr","tire slip ratio rl","tire slip ratio rr",
            "tire dirt level fl","tire dirt level fr","tire dirt level rl","tire dirt level rr",
            "tire temp core fl","tire temp core fr","tire temp core rl","tire temp core rr",
            "tire temp inner fl","tire temp middle fl","tire temp outer fl",
            "tire temp inner fr","tire temp middle fr","tire temp outer fr",
            "tire temp inner rl","tire temp middle rl","tire temp outer rl",
            "tire temp inner rr","tire temp middle rr","tire temp outer rr",
            // ruote
            "wheel angular speed fl","wheel angular speed fr","wheel angular speed rl","wheel angular speed rr",
            // geometria ruote
            "camber fl","camber fr","camber rl","camber rr","caster fl","caster fr","toe in fl","toe in fr","toe in rl","toe in rr",
            // danni
            "car damage front","car damage left","car damage rear","car damage right",
            // coordinate
            "car coord x","car coord y","car coord z","car pos norm",
            // flags & pit & validità
            "flags","in pit","lap invalidated","num tires off track",
            // limiti / max / telemetria
            "max fuel","max power","max rpm","max torque","max turbo boost","raw data sample rate",
            "hr sample clock","mr sample clock","lr sample clock",
            // extra
            "drivetrain speed","drive train speed","ffb","force feedback","wheel force"
    ));

    static final List<String> PARTIAL_KEYS = List.of(
            "time","distance","speed","throttle","brake","steer","rpm","gear","accel","ffb","lap",
            "force","seat","left","right","rear","post","tire","wheel","susp","ride","temp","torque","pressure","radius","load","grip"
    );

    // ---- Mappa alias → Channel
    static final Map<String, Channel> ALIAS_MAP = buildAliasMap();

    private static Map<String, Channel> buildAliasMap() {
        Map<String, Channel> m = new HashMap<>();

        // Helper
        final var PUT = (java.util.function.BiConsumer<String, Channel>) (k, ch) -> put(m, k, ch);

        // ===== Tempo / Distanza / Giri =====
        PUT.accept("time", Channel.TIME); PUT.accept("time [s]", Channel.TIME);
        PUT.accept("logged time", Channel.TIME); PUT.accept("absolute time", Channel.TIME);

        PUT.accept("lap", Channel.LAP); PUT.accept("lap #", Channel.LAP);
        PUT.accept("lap number", Channel.LAP); PUT.accept("current lap", Channel.LAP);
        PUT.accept("session lap count", Channel.SESSION_LAP_COUNT);
        put(m, "ffb", Channel.FFB); put(m, "force feedback", Channel.FFB); put(m, "wheel force", Channel.FFB);
        PUT.accept("lap time", Channel.LAP_TIME); PUT.accept("lap time [s]", Channel.LAP_TIME);
        PUT.accept("current lap time", Channel.LAP_TIME); PUT.accept("laptime", Channel.LAP_TIME);
        PUT.accept("best lap time", Channel.BEST_LAP_TIME);
        PUT.accept("best lap delta", Channel.BEST_LAP_DELTA);
        PUT.accept("last lap time", Channel.LAST_LAP_TIME);
        PUT.accept("last sector time", Channel.LAST_SECTOR_TIME);
        PUT.accept("lap time2", Channel.LAP_TIME2);

        PUT.accept("distance", Channel.DISTANCE); PUT.accept("distance [m]", Channel.DISTANCE);
        PUT.accept("lap distance", Channel.DISTANCE); PUT.accept("s distance", Channel.DISTANCE);

        // ===== Velocità / Posizione / Kinematics =====
        PUT.accept("speed", Channel.SPEED); PUT.accept("speed [km/h]", Channel.SPEED);
        PUT.accept("ground speed", Channel.GROUND_SPEED);

        PUT.accept("car coord x", Channel.CAR_COORD_X);
        PUT.accept("car coord y", Channel.CAR_COORD_Y);
        PUT.accept("car coord z", Channel.CAR_COORD_Z);
        PUT.accept("car pos norm", Channel.CAR_POS_NORM);

        PUT.accept("chassis velocity x", Channel.CHASSIS_VELOCITY_X);
        PUT.accept("chassis velocity y", Channel.CHASSIS_VELOCITY_Y);
        PUT.accept("chassis velocity z", Channel.CHASSIS_VELOCITY_Z);

        PUT.accept("drive train speed", Channel.DRIVE_TRAIN_SPEED);
        PUT.accept("drivetrain speed", Channel.DRIVE_TRAIN_SPEED);

        // ===== Powertrain / Comandi =====
        PUT.accept("engine rpm", Channel.ENGINE_RPM); PUT.accept("rpm", Channel.ENGINE_RPM);
        PUT.accept("rpm [1/min]", Channel.ENGINE_RPM); PUT.accept("engine speed", Channel.ENGINE_RPM);
        PUT.accept("engine speed [rpm]", Channel.ENGINE_RPM); PUT.accept("engine revs", Channel.ENGINE_RPM);
        PUT.accept("revs", Channel.ENGINE_RPM);

        PUT.accept("max rpm", Channel.MAX_RPM);

        PUT.accept("gear", Channel.GEAR);

        PUT.accept("throttle", Channel.THROTTLE); PUT.accept("throttle pos", Channel.THROTTLE);
        PUT.accept("throttle pos [%]", Channel.THROTTLE);

        PUT.accept("brake", Channel.BRAKE); PUT.accept("brake pos", Channel.BRAKE);
        PUT.accept("brake pos [%]", Channel.BRAKE);

        PUT.accept("clutch", Channel.CLUTCH); PUT.accept("clutch pos", Channel.CLUTCH);
        PUT.accept("clutch pos [%]", Channel.CLUTCH);

        PUT.accept("engine limiter", Channel.ENGINE_LIMITER);
        PUT.accept("engine brake setting", Channel.ENGINE_BRAKE_SETTING);

        // ===== Aiuti di guida / Regolamenti =====
        PUT.accept("abs active", Channel.ABS_ACTIVE);
        PUT.accept("abs enabled", Channel.ABS_ENABLED);
        PUT.accept("tc active", Channel.TC_ACTIVE);
        PUT.accept("tc enabled", Channel.TC_ENABLED);

        PUT.accept("aid auto shift", Channel.AID_AUTO_SHIFT);
        PUT.accept("aid auto blip", Channel.AID_AUTO_BLIP);
        PUT.accept("aid auto clutch", Channel.AID_AUTO_CLUTCH);
        PUT.accept("aid ideal line", Channel.AID_IDEAL_LINE);
        PUT.accept("aid fuel rate", Channel.AID_FUEL_RATE);
        PUT.accept("aid tire wear rate", Channel.AID_TIRE_WEAR_RATE);
        PUT.accept("aid mech damage", Channel.AID_MECH_DAMAGE);
        PUT.accept("aid stability", Channel.AID_STABILITY);
        PUT.accept("aid allow tire blankets", Channel.AID_ALLOW_TIRE_BLANKETS);
        PUT.accept("penalties enabled", Channel.PENALTIES_ENABLED);

        // ===== ERS / KERS / DRS =====
        PUT.accept("ers heat charging", Channel.ERS_HEAT_CHARGING);
        PUT.accept("ers is charging", Channel.ERS_IS_CHARGING);
        PUT.accept("ers max energy", Channel.ERS_MAX_ENERGY);
        PUT.accept("ers power level", Channel.ERS_POWER_LEVEL);
        PUT.accept("ers recovery level", Channel.ERS_RECOVERY_LEVEL);

        PUT.accept("kers charge", Channel.KERS_CHARGE);
        PUT.accept("kers deployed energy", Channel.KERS_DEPLOYED_ENERGY);
        PUT.accept("kers input", Channel.KERS_INPUT);
        PUT.accept("kers max energy", Channel.KERS_MAX_ENERGY);

        PUT.accept("drs active", Channel.DRS_ACTIVE);
        PUT.accept("drs available", Channel.DRS_AVAILABLE);

        // ===== Accelerometri / assetto =====
        PUT.accept("long accel [g]", Channel.CG_ACCEL_LONGITUDINAL);
        PUT.accept("lat accel [g]",  Channel.CG_ACCEL_LATERAL);
        PUT.accept("vert accel [g]", Channel.CG_ACCEL_VERTICAL);
        PUT.accept("cg accel longitudinal", Channel.CG_ACCEL_LONGITUDINAL);
        PUT.accept("cg accel lateral",      Channel.CG_ACCEL_LATERAL);
        PUT.accept("cg accel vertical",     Channel.CG_ACCEL_VERTICAL);

        PUT.accept("cg height", Channel.CG_HEIGHT);

        PUT.accept("chassis pitch angle", Channel.CHASSIS_PITCH_ANGLE);
        PUT.accept("chassis pitch rate",  Channel.CHASSIS_PITCH_RATE);
        PUT.accept("chassis roll angle",  Channel.CHASSIS_ROLL_ANGLE);
        PUT.accept("chassis roll rate",   Channel.CHASSIS_ROLL_RATE);
        PUT.accept("chassis yaw rate",    Channel.CHASSIS_YAW_RATE);

        PUT.accept("steer angle", Channel.STEER_ANGLE);
        PUT.accept("steer angle [deg]", Channel.STEER_ANGLE);
        PUT.accept("steering", Channel.STEER_ANGLE);
        PUT.accept("steering angle", Channel.STEER_ANGLE);

        PUT.accept("brake bias", Channel.BRAKE_BIAS);

        // ===== Sospensioni / altezze =====
        PUT.accept("ride height fl", Channel.RIDE_HEIGHT_FL);
        PUT.accept("ride height fr", Channel.RIDE_HEIGHT_FR);
        PUT.accept("ride height rl", Channel.RIDE_HEIGHT_RL);
        PUT.accept("ride height rr", Channel.RIDE_HEIGHT_RR);

        PUT.accept("suspension travel fl", Channel.SUSP_TRAVEL_FL);
        PUT.accept("suspension travel fr", Channel.SUSP_TRAVEL_FR);
        PUT.accept("suspension travel rl", Channel.SUSP_TRAVEL_RL);
        PUT.accept("suspension travel rr", Channel.SUSP_TRAVEL_RR);

        PUT.accept("max sus travel fl", Channel.MAX_SUS_TRAVEL_FL);
        PUT.accept("max sus travel fr", Channel.MAX_SUS_TRAVEL_FR);
        PUT.accept("max sus travel rl", Channel.MAX_SUS_TRAVEL_RL);
        PUT.accept("max sus travel rr", Channel.MAX_SUS_TRAVEL_RR);

        // ===== Geometrie (camber/caster/toe) =====
        PUT.accept("camber fl", Channel.CAMBER_FL); PUT.accept("camber fr", Channel.CAMBER_FR);
        PUT.accept("camber rl", Channel.CAMBER_RL); PUT.accept("camber rr", Channel.CAMBER_RR);
        PUT.accept("caster fl", Channel.CASTER_FL); PUT.accept("caster fr", Channel.CASTER_FR);

        PUT.accept("toe in fl", Channel.TOE_IN_FL); PUT.accept("toe in fr", Channel.TOE_IN_FR);
        PUT.accept("toe in rl", Channel.TOE_IN_RL); PUT.accept("toe in rr", Channel.TOE_IN_RR);

        // ===== Freni =====
        PUT.accept("brake temp fl", Channel.BRAKE_TEMP_FL);
        PUT.accept("brake temp fr", Channel.BRAKE_TEMP_FR);
        PUT.accept("brake temp rl", Channel.BRAKE_TEMP_RL);
        PUT.accept("brake temp rr", Channel.BRAKE_TEMP_RR);


        // ===== Fuel / potenze / limiti =====
        PUT.accept("fuel level", Channel.FUEL_LEVEL);
        PUT.accept("max fuel",   Channel.MAX_FUEL);
        PUT.accept("max power",  Channel.MAX_POWER);
        PUT.accept("max torque", Channel.MAX_TORQUE);
        PUT.accept("max turbo boost", Channel.MAX_TURBO_BOOST);

        // ===== Meteo / Pista / Aero =====
        PUT.accept("air density", Channel.AIR_DENSITY);
        PUT.accept("air temp",    Channel.AIR_TEMP);
        PUT.accept("road temp",   Channel.ROAD_TEMP);
        PUT.accept("wind direction", Channel.WIND_DIRECTION);
        PUT.accept("wind speed",     Channel.WIND_SPEED);
        PUT.accept("surface grip",   Channel.SURFACE_GRIP);
        PUT.accept("ballast",        Channel.BALLAST);
        PUT.accept("turbo boost",    Channel.TURBO_BOOST);

        // ===== Pneumatici =====
        PUT.accept("tire pressure fl", Channel.TIRE_PRESSURE_FL);
        PUT.accept("tire pressure fr", Channel.TIRE_PRESSURE_FR);
        PUT.accept("tire pressure rl", Channel.TIRE_PRESSURE_RL);
        PUT.accept("tire pressure rr", Channel.TIRE_PRESSURE_RR);

        PUT.accept("tire load fl", Channel.TIRE_LOAD_FL);
        PUT.accept("tire load fr", Channel.TIRE_LOAD_FR);
        PUT.accept("tire load rl", Channel.TIRE_LOAD_RL);
        PUT.accept("tire load rr", Channel.TIRE_LOAD_RR);

        PUT.accept("tire loaded radius fl", Channel.TIRE_LOADED_RADIUS_FL);
        PUT.accept("tire loaded radius fr", Channel.TIRE_LOADED_RADIUS_FR);
        PUT.accept("tire loaded radius rl", Channel.TIRE_LOADED_RADIUS_RL);
        PUT.accept("tire loaded radius rr", Channel.TIRE_LOADED_RADIUS_RR);

        PUT.accept("tire radius fl", Channel.TIRE_RADIUS_FL);
        PUT.accept("tire radius fr", Channel.TIRE_RADIUS_FR);
        PUT.accept("tire radius rl", Channel.TIRE_RADIUS_RL);
        PUT.accept("tire radius rr", Channel.TIRE_RADIUS_RR);

        PUT.accept("tire rubber grip fl", Channel.TIRE_RUBBER_GRIP_FL);
        PUT.accept("tire rubber grip fr", Channel.TIRE_RUBBER_GRIP_FR);
        PUT.accept("tire rubber grip rl", Channel.TIRE_RUBBER_GRIP_RL);
        PUT.accept("tire rubber grip rr", Channel.TIRE_RUBBER_GRIP_RR);

        PUT.accept("tire slip angle fl", Channel.TIRE_SLIP_ANGLE_FL);
        PUT.accept("tire slip angle fr", Channel.TIRE_SLIP_ANGLE_FR);
        PUT.accept("tire slip angle rl", Channel.TIRE_SLIP_ANGLE_RL);
        PUT.accept("tire slip angle rr", Channel.TIRE_SLIP_ANGLE_RR);

        PUT.accept("tire slip ratio fl", Channel.TIRE_SLIP_RATIO_FL);
        PUT.accept("tire slip ratio fr", Channel.TIRE_SLIP_RATIO_FR);
        PUT.accept("tire slip ratio rl", Channel.TIRE_SLIP_RATIO_RL);
        PUT.accept("tire slip ratio rr", Channel.TIRE_SLIP_RATIO_RR);

        PUT.accept("tire dirt level fl", Channel.TIRE_DIRT_LEVEL_FL);
        PUT.accept("tire dirt level fr", Channel.TIRE_DIRT_LEVEL_FR);
        PUT.accept("tire dirt level rl", Channel.TIRE_DIRT_LEVEL_RL);
        PUT.accept("tire dirt level rr", Channel.TIRE_DIRT_LEVEL_RR);

        PUT.accept("tire temp core fl", Channel.TIRE_TEMP_CORE_FL);
        PUT.accept("tire temp core fr", Channel.TIRE_TEMP_CORE_FR);
        PUT.accept("tire temp core rl", Channel.TIRE_TEMP_CORE_RL);
        PUT.accept("tire temp core rr", Channel.TIRE_TEMP_CORE_RR);

        PUT.accept("tire temp inner fl", Channel.TIRE_TEMP_INNER_FL);
        PUT.accept("tire temp middle fl", Channel.TIRE_TEMP_MIDDLE_FL);
        PUT.accept("tire temp outer fl", Channel.TIRE_TEMP_OUTER_FL);

        PUT.accept("tire temp inner fr", Channel.TIRE_TEMP_INNER_FR);
        PUT.accept("tire temp middle fr", Channel.TIRE_TEMP_MIDDLE_FR);
        PUT.accept("tire temp outer fr", Channel.TIRE_TEMP_OUTER_FR);

        PUT.accept("tire temp inner rl", Channel.TIRE_TEMP_INNER_RL);
        PUT.accept("tire temp middle rl", Channel.TIRE_TEMP_MIDDLE_RL);
        PUT.accept("tire temp outer rl", Channel.TIRE_TEMP_OUTER_RL);

        PUT.accept("tire temp inner rr", Channel.TIRE_TEMP_INNER_RR);
        PUT.accept("tire temp middle rr", Channel.TIRE_TEMP_MIDDLE_RR);
        PUT.accept("tire temp outer rr", Channel.TIRE_TEMP_OUTER_RR);

        // ===== Ruote =====
        PUT.accept("wheel angular speed fl", Channel.WHEEL_ANGULAR_SPEED_FL);
        PUT.accept("wheel angular speed fr", Channel.WHEEL_ANGULAR_SPEED_FR);
        PUT.accept("wheel angular speed rl", Channel.WHEEL_ANGULAR_SPEED_RL);
        PUT.accept("wheel angular speed rr", Channel.WHEEL_ANGULAR_SPEED_RR);

        // ===== Danni =====
        PUT.accept("car damage front", Channel.CAR_DAMAGE_FRONT);
        PUT.accept("car damage left",  Channel.CAR_DAMAGE_LEFT);
        PUT.accept("car damage rear",  Channel.CAR_DAMAGE_REAR);
        PUT.accept("car damage right", Channel.CAR_DAMAGE_RIGHT);

        // ===== Flags / pit / validazione =====
        PUT.accept("flags", Channel.FLAGS);
        PUT.accept("in pit", Channel.IN_PIT);
        PUT.accept("lap invalidated", Channel.LAP_INVALIDATED);
        PUT.accept("num tires off track", Channel.NUM_TIRES_OFF_TRACK);

        // ===== Telemetria/sample clock =====
        PUT.accept("raw data sample rate", Channel.RAW_DATA_SAMPLE_RATE);
        PUT.accept("hr sample clock", Channel.HR_SAMPLE_CLOCK);
        PUT.accept("mr sample clock", Channel.MR_SAMPLE_CLOCK);
        PUT.accept("lr sample clock", Channel.LR_SAMPLE_CLOCK);
        // Distance (TUTTI → DISTANCE)
        PUT.accept("distance [m]",    Channel.DISTANCE);
        PUT.accept("lap distance",    Channel.DISTANCE);
        PUT.accept("s distance",      Channel.DISTANCE);

        // Speed (ok)
        PUT.accept("speed",           Channel.SPEED);
        PUT.accept("speed [km/h]",    Channel.SPEED);
        PUT.accept("ground speed",    Channel.SPEED); // oppure Channel.GROUND_SPEED se distinto

        // ===== FFB / Force Feedback (raw) =====
        PUT.accept("ffb",                 Channel.FFB);
        PUT.accept("ffb [0..1]",          Channel.FFB);
        PUT.accept("ffb (0..1)",          Channel.FFB);
        PUT.accept("ffb output",          Channel.FFB);
        PUT.accept("ffb %",               Channel.FFB);
        PUT.accept("force feedback",      Channel.FFB);
        PUT.accept("steering feedback",   Channel.FFB);
        PUT.accept("steering force",      Channel.FFB);
        PUT.accept("wheel force",         Channel.FFB);
        PUT.accept("steer force",         Channel.FFB);
        PUT.accept("steer torque",        Channel.FFB);
        PUT.accept("wheel torque",        Channel.FFB);

        // (facoltativo) clipping: mappa solo se hai un Channel dedicato
        PUT.accept("ffb",                 Channel.FFB);
        PUT.accept("force feedback",      Channel.FFB);
        PUT.accept("steering torque",     Channel.FFB);
        PUT.accept("steering moment",     Channel.FFB);
        PUT.accept("wheel torque",        Channel.FFB);
        PUT.accept("steer torque",        Channel.FFB);
        PUT.accept("wheel force",         Channel.FFB);
        PUT.accept("steering force",      Channel.FFB);

        // <<< chiave >>>
        PUT.accept("self align torque fl", Channel.FFB);
        PUT.accept("self align torque fr", Channel.FFB);

        return m;
    }

    private static void put(Map<String, Channel> m, String key, Channel ch) {
        m.put(norm(key), ch);
    }

    // ======== GETTER PUBBLICI (read-only, per GuideGlossaryView) ========
    /** Insieme completo degli header candidati (immutabile). */
    public static Set<String> headerCandidates() {
        return Collections.unmodifiableSet(HEADER_CANDIDATES);
    }

    /** Parole chiave parziali utili alla ricerca di alias (immutabile). */
    public static List<String> partialKeys() {
        return Collections.unmodifiableList(PARTIAL_KEYS);
    }

    /** Mappa alias→Channel completa (immutabile). */
    public static Map<String, Channel> aliasMap() {
        return Collections.unmodifiableMap(ALIAS_MAP);
    }

    /** Facciata pubblica a indexOf senza cambiare la visibilità originale. */
    public static int indexOfPublic(String[] header, List<String> aliases) {
        return indexOf(header, aliases);
    }
}
