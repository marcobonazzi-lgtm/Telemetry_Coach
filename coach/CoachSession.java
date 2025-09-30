package org.simulator.coach;

import org.simulator.analisi_base.lap_analysis.LapAnalysis;
import org.simulator.canale.Channel;
import org.simulator.canale.Lap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.simulator.coach.CoachCore.*;

final class CoachSession {

    private CoachSession(){}

    static List<String> generateSessionNotes(List<Lap> laps){
        if (laps == null || laps.isEmpty()) return List.of();

        VehicleKind kind = CoachCore.detectVehicleKind(laps);

        int N = laps.size();
        int manyBrakes=0, absOften=0, coastingBad=0, trailLow=0, trailHigh=0, thrOsc=0, steerHarsh=0,
                tcOften=0, ffbClip=0, limiter=0, noLift=0, oversteer=0, badDownshift=0,
                limitsRisk=0, invalidLaps=0, drsUnderuse=0, ersConservative=0;

        double sumCoast=0, nCoast=0, sumTrail=0, nTrail=0, sumOsc=0, nOsc=0, sumHarsh=0, nHarsh=0;
        List<Double> allDelays = new ArrayList<>();

        for (Lap lap : laps){
            if (lap == null || lap.samples.isEmpty()) continue;

            var brakes = LapAnalysis.brakeEvents(lap, BRK_ON*100, 5);
            if (brakes.size() > 10) manyBrakes++;

            if (has(lap, Channel.ABS_ACTIVE) && fractionActive(lap, Channel.ABS_ACTIVE) > profAbsMuch(kind)) absOften++;

            if (has(lap, Channel.THROTTLE) || has(lap, Channel.BRAKE)) {
                double c = fraction(lap, s -> nearZero(val(s, Channel.THROTTLE), COAST_THR)
                        && nearZero(val(s, Channel.BRAKE), COAST_THR));
                if (c > profCoastBad(kind)) coastingBad++;
                if (!Double.isNaN(c)) { sumCoast += c; nCoast++; }
            }
            if (has(lap, Channel.BRAKE) && has(lap, Channel.STEER_ANGLE)) {
                double trail = fraction(lap, s -> val(s, Channel.BRAKE) > BRK_TRAIL*100
                        && Math.abs(val(s, Channel.STEER_ANGLE)) > 10);
                if (trail < profTrailLow(kind)) trailLow++;
                else if (trail > profTrailHigh(kind)) trailHigh++;
                if (!Double.isNaN(trail)) { sumTrail += trail; nTrail++; }
            }
            if (has(lap, Channel.THROTTLE)) {
                double osc = throttleOscillationPct(lap, profThrOscDps(kind));
                if (osc > 0.10) thrOsc++;
                if (!Double.isNaN(osc)) { sumOsc += osc; nOsc++; }
            }
            if (has(lap, Channel.STEER_ANGLE)) {
                double harsh = steeringHarshPct(lap, profSteerRevRate(kind));
                if (harsh > 0.10) steerHarsh++;
                if (!Double.isNaN(harsh)) { sumHarsh += harsh; nHarsh++; }
            }
            if (has(lap, Channel.TC_ACTIVE) && fractionActive(lap, Channel.TC_ACTIVE) > profTcMuch(kind)) tcOften++;

            if (has(lap, Channel.FFB) && fraction(lap, s -> {
                double f = val(s, Channel.FFB);
                if (Double.isNaN(f)) return false;
                double fn = (f>1.5)?f/100.0:f;
                return fn>=FFB_CLIP;
            }) > FFB_BADPCT) ffbClip++;

            if (has(lap, Channel.ENGINE_RPM) && has(lap, Channel.THROTTLE)) {
                double q = rpmQuantile(lap, UPSHIFT_RPM_HIGH_Q);
                if (!Double.isNaN(q) && fraction(lap, s -> val(s, Channel.ENGINE_RPM) >= q
                        && val(s, Channel.THROTTLE) > THR_FULL*100) > 0.05) limiter++;
            }
            if (has(lap, Channel.THROTTLE) && has(lap, Channel.BRAKE) && lateLiftBeforeBrake(lap, LIFT_BEFORE_BRAKE_S)) noLift++;
            if (suspectedOversteer(lap)) oversteer++;
            if (downshiftsWithHighThrottle(lap) > 0) badDownshift++;

            if (has(lap, Channel.NUM_TIRES_OFF_TRACK)) {
                double offFrac = fraction(lap, s -> val(s, Channel.NUM_TIRES_OFF_TRACK) >= 2.0);
                if (offFrac > OFFTRACK_BAD) limitsRisk++;
            }
            if (has(lap, Channel.LAP_INVALIDATED)) {
                boolean invalid = fraction(lap, s -> val(s, Channel.LAP_INVALIDATED) >= 0.5) > 0.0;
                if (invalid) invalidLaps++;
            }
            if (has(lap, Channel.DRS_AVAILABLE) && has(lap, Channel.DRS_ACTIVE)) {
                double avail = fractionActive(lap, Channel.DRS_AVAILABLE);
                double used  = fractionActive(lap, Channel.DRS_ACTIVE);
                if (avail > 0.02 && used < avail * DRS_UNUSED_FACTOR) drsUnderuse++;
            }
            if (has(lap, Channel.ERS_IS_CHARGING) && has(lap, Channel.KERS_DEPLOYED_ENERGY)) {
                double chg = fractionActive(lap, Channel.ERS_IS_CHARGING);
                double dep = fraction(lap, s -> !Double.isNaN(val(s, Channel.KERS_DEPLOYED_ENERGY)) && val(s, Channel.KERS_DEPLOYED_ENERGY) > 0);
                if (chg > dep * ERS_RECOV_OVER_DEPLOY) ersConservative++;
            }

            var apexes = LapAnalysis.apexes(lap, 5);
            DelayStats ds = apexToThrottleStats(lap, apexes, 0.80);
            if (ds.valid) allDelays.addAll(ds.all);
        }

        List<Note> out = new ArrayList<>();

        // GUIDA
        add(out, manyBrakes>0, Priority.MEDIUM, Category.SESSIONE, "Freni spesso: in " + manyBrakes + "/" + N + " giri. Lavora su scorrevolezza e punti di frenata.");
        add(out, absOften>0,   Priority.HIGH,   Category.SESSIONE, "ABS frequente: " + absOften + "/" + N + " giri. Modula di più ed entra più progressivo.");
        add(out, coastingBad>0,Priority.MEDIUM, Category.SESSIONE, "Coasting elevato: " + coastingBad + "/" + N + " giri" + avgPct(" (avg ", sumCoast, nCoast) + "). Anticipa gas o ritarda frenata.");
        add(out, trailLow>0,   Priority.MEDIUM, Category.SESSIONE, "Trail-braking poco usato: " + trailLow + "/" + N + " giri" + avgPct(" (avg ", sumTrail, nTrail) + "). Rilascia più graduale in inserimento.");
        add(out, trailHigh>0,  Priority.MEDIUM, Category.SESSIONE, "Troppo trail-braking: " + trailHigh + "/" + N + " giri. Rilascia prima per evitare sottosterzo.");
        add(out, thrOsc>0,     Priority.MEDIUM, Category.SESSIONE, "Apertura gas irregolare: " + thrOsc + "/" + N + " giri" + avgPct(" (avg ", sumOsc, nOsc) + "). Apri più progressivo.");
        add(out, steerHarsh>0, Priority.MEDIUM, Category.SESSIONE, "Sterzo nervoso: " + steerHarsh + "/" + N + " giri" + avgPct(" (avg ", sumHarsh, nHarsh) + "). Pulisci input e traiettoria.");
        add(out, oversteer>0,  Priority.HIGH,   Category.SESSIONE, "Segnali di sovrasterzo: " + oversteer + "/" + N + " giri. Lavora su trazione e tempistica gas.");

        // TRASM
        add(out, limiter>0,     Priority.LOW,   Category.SESSIONE, "Vicino al limitatore: " + limiter + "/" + N + " giri. Cambia un filo prima.");
        add(out, badDownshift>0,Priority.HIGH,  Category.SESSIONE, "Downshift con gas alto in rettilineo: " + badDownshift + ". Verifica mappatura comandi.");
        add(out, drsUnderuse>0, Priority.LOW,   Category.SESSIONE, "DRS disponibile ma poco usato in " + drsUnderuse + "/" + N + " giri: ottimizza l’uso in rettilineo.");
        add(out, ersConservative>0, Priority.LOW, Category.SESSIONE, "ERS conservativo in " + ersConservative + "/" + N + " giri: aumenta leggermente il deploy in uscita curva.");

        // LIMITI
        add(out, limitsRisk>0,  Priority.MEDIUM, Category.SESSIONE, "Track limits a rischio in " + limitsRisk + "/" + N + " giri: lascia un margine in uscita curva.");
        add(out, invalidLaps>0, Priority.LOW,    Category.SESSIONE, "Giri invalidati: " + invalidLaps + "/" + N + ".");

        // Ritardo apertura gas post-apex
        if (!allDelays.isEmpty()){
            double avg = mean(allDelays), min = Collections.min(allDelays), max = Collections.max(allDelays);
            add(out, avg > profExitGasDelay(kind), Priority.MEDIUM, Category.SESSIONE,
                    "Ritardo medio gas post-apex: avg " + fmt1s(avg) + " (min " + fmt1s(min) + ", max " + fmt1s(max) + "). Anticipa leggermente la trazione.");
        }

        // Meteo/pista (soft)
        double road = 0, air = 0, grip = 1.0, wind = 0; int metN=0;
        for (Lap l: laps){
            double r = firstNonNaN(l, Channel.ROAD_TEMP); if (!Double.isNaN(r)) { road+=r; metN++; }
            double a = firstNonNaN(l, Channel.AIR_TEMP);  if (!Double.isNaN(a))  air +=a;
            double g = firstNonNaN(l, Channel.SURFACE_GRIP); if (!Double.isNaN(g)) grip = Math.min(grip, g);
            double w = firstNonNaN(l, Channel.WIND_SPEED); if (!Double.isNaN(w)) wind = Math.max(w, w);
        }
        if (metN>0){
            int rAvg = (int)Math.round(road/metN);
            add(out, true, Priority.LOW, Category.SESSIONE, "Meteo: asfalto ~" + rAvg + "°C" + (air>0?(", aria ~"+(int)Math.round(air/metN)+"°C"):"") + ".");
            if (!Double.isNaN(grip) && grip < GRIP_LOW) add(out, true, Priority.LOW, Category.SESSIONE, "Grip pista basso: occhio a out-lap e trazione.");
            if (wind > WIND_STRONG) add(out, true, Priority.LOW, Category.SESSIONE, "Vento forte (~" + fmt1(wind) + " m/s): stabilità variabile nei rettilinei.");
        }

        // Merge concetti + sort
        out = mergeByConcept(out);
        out.sort(java.util.Comparator.comparingInt((Note n) -> priorityRank(n.priority)).reversed());

        List<String> result = new ArrayList<>(out.size());
        for (Note n: out) result.add(n.render());
        return dedupByStem(result);
    }

    private static int priorityRank(Priority p){
        return switch (p){
            case HIGH -> 3; case MEDIUM -> 2; case LOW -> 1;
        };
    }

    private enum Concept { FFB_CLIP, PEDAL_STOMP, KERB_OVERUSE, COASTING, TRAIL_BRAKING, APEX_SLOW, OPEN_THROTTLE_DELAY, ABS_ACTIVE, TC_UP, TC_DOWN, TYRE_PRESS, TYRE_TEMP, BRAKE_DUCTS_OPEN, BRAKE_DUCTS_CLOSE, BRAKE_BIAS, DIFFERENTIAL, DAMPERS, ARB, AERO, OTHER }

    private static String normText(String s){
        if (s == null) return "";
        String t = s.toLowerCase(java.util.Locale.ROOT);
        t = t.replace('à','a').replace('è','e').replace('é','e').replace('ì','i').replace('ò','o').replace('ù','u');
        t = t.replaceAll("[\\[\\]\\(\\)\\.,:;!\\?]", " ").replaceAll("\\s+", " ").trim();
        return t;
    }

    private static Concept conceptOf(Note n){
        String s = normText(n.text);
        if ((s.contains("ffb") || s.contains("force feedback")) && (s.contains("clip") || s.contains("satur"))) return Concept.FFB_CLIP;
        if ((s.contains("frenate a colpi") || s.contains("spike") || s.contains("stomp")) && (s.contains("pedal") || s.contains("pedale") || s.contains("brake"))) return Concept.PEDAL_STOMP;
        if (s.contains("cordol") || s.contains("kerb")) return Concept.KERB_OVERUSE;
        if (s.contains("coast")) return Concept.COASTING;
        if (s.contains("trail") && s.contains("brak")) return Concept.TRAIL_BRAKING;
        if (s.contains("apex") && (s.contains("lenti") || s.contains("lento") || s.contains("slow"))) return Concept.APEX_SLOW;
        if (s.contains("apertura del gas") || (s.contains("ritardo") && s.contains("gas"))) return Concept.OPEN_THROTTLE_DELAY;
        if (s.contains("abs") && (s.contains("spesso") || s.contains("alto") || s.contains("frequente"))) return Concept.ABS_ACTIVE;
        if (s.contains(" tc ") || s.contains("traction")) { if (s.contains("+1") || s.contains("piu") || s.contains("aumenta")) return Concept.TC_UP; if (s.contains("-1") || s.contains("meno") || s.contains("riduci")) return Concept.TC_DOWN; }
        if (s.contains("psi") || s.contains("pression")) return Concept.TYRE_PRESS;
        if ((s.contains("gomme") && s.contains("cald")) || (s.contains("gomme") && s.contains("fredd")) || s.contains("temperat")) return Concept.TYRE_TEMP;
        if (s.contains("duct")) { if (s.contains("apri")) return Concept.BRAKE_DUCTS_OPEN; if (s.contains("chiudi")) return Concept.BRAKE_DUCTS_CLOSE; }
        if (s.contains("bias") && s.contains("fren")) return Concept.BRAKE_BIAS;
        if (s.contains("differenziale") || s.contains("preload") || s.contains("power")) return Concept.DIFFERENTIAL;
        if (s.contains("ammortizz") || s.contains("bump") || s.contains("rebound")) return Concept.DAMPERS;
        if (s.contains("barra") || s.contains("antirollio") || s.contains("arb")) return Concept.ARB;
        if (s.contains("ala") || s.contains("aero")) return Concept.AERO;
        return Concept.OTHER;
    }

    private static List<Note> mergeByConcept(List<Note> in){
        java.util.Map<Concept, Note> best = new java.util.EnumMap<>(Concept.class);
        java.util.List<Note> others = new java.util.ArrayList<>();
        for (Note n: in){
            Concept c = conceptOf(n);
            if (c == Concept.OTHER){ others.add(n); continue; }
            Note prev = best.get(c);
            if (prev == null || priorityRank(n.priority) > priorityRank(prev.priority)) best.put(c, n);
        }
        java.util.List<Note> out = new java.util.ArrayList<>(best.values());
        out.addAll(others);
        return out;
    }
}
